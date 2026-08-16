package fr.rp.virisur.gui;

import fr.rp.virisur.ViriSur;
import fr.rp.virisur.models.ProtectedChest;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LootGui {

    private final ViriSur plugin;
    // joueur -> coffre en cours de loot
    private final Map<UUID, ProtectedChest> activeLoot = new HashMap<>();

    public LootGui(ViriSur plugin) {
        this.plugin = plugin;
    }

    public ProtectedChest getActive(UUID uuid) {
        return activeLoot.get(uuid);
    }

    public void clear(UUID uuid) {
        activeLoot.remove(uuid);
    }

    public void openLoot(Player thief, ProtectedChest chest) {
        World world = Bukkit.getWorld(chest.getWorld());
        if (world == null) {
            thief.sendMessage(ChatColor.RED + "Monde introuvable.");
            return;
        }
        Block block = world.getBlockAt(chest.getX(), chest.getY(), chest.getZ());
        if (!(block.getState() instanceof Container container)) {
            thief.sendMessage(ChatColor.RED + "Ce n'est plus un conteneur.");
            return;
        }

        // Collecte tous les items non-vides (inclut double coffre)
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                contents.add(item.clone());
            }
        }
        Block other = plugin.getChestManager().getOtherChestHalf(block);
        if (other != null && other.getState() instanceof Container otherCont) {
            for (ItemStack item : otherCont.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                    contents.add(item.clone());
                }
            }
        }

        if (contents.isEmpty()) {
            thief.sendMessage(ChatColor.YELLOW + "Le coffre est vide...");
            return;
        }

        int min = plugin.getConfig().getInt("vol.items-min", 1);
        int max = plugin.getConfig().getInt("vol.items-max", 3);
        int count = ThreadLocalRandom.current().nextInt(min, max + 1);
        count = Math.min(count, contents.size());

        Collections.shuffle(contents);
        List<ItemStack> chosen = contents.subList(0, count);

        int pct = plugin.getConfig().getInt("vol.quantite-max-pourcent", 50);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "💀 Butin (1 clic = prendre)");

        activeLoot.put(thief.getUniqueId(), chest);

        int slot = 10;
        for (ItemStack stack : chosen) {
            int maxTake = Math.max(1, (int) Math.ceil(stack.getAmount() * (pct / 100.0)));
            int take = ThreadLocalRandom.current().nextInt(1, maxTake + 1);
            take = Math.min(take, stack.getAmount());

            ItemStack display = stack.clone();
            display.setAmount(take);
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GREEN + "Clique pour voler x" + take);
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(slot, display);
            slot++;
            if (slot == 17) slot = 19;
        }

        inv.setItem(22, createItem(Material.BARRIER, ChatColor.RED + "Partir", List.of(ChatColor.GRAY + "Fermer sans tout prendre")));

        // Alerte serveur + alarme
        String msg = plugin.getConfig().getString("messages.vol-reussi", "&cBRAQUAGE !");
        msg = msg.replace("%proprio%", chest.getOwnerName());
        String full = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix", "") + msg);
        Bukkit.broadcastMessage(full);

        plugin.getChestManager().triggerAlarm(chest);
        plugin.getChestManager().setCooldown(thief.getUniqueId());

        String alarmMsg = plugin.getConfig().getString("messages.alarme", "&cAlarme !");
        alarmMsg = alarmMsg.replace("%proprio%", chest.getOwnerName());
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix", "") + alarmMsg));

        thief.openInventory(inv);
        thief.playSound(thief.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
    }

    /** Appelé quand le voleur clique un item du butin */
    public void takeItem(Player thief, ItemStack displayItem, int slot, Inventory inv) {
        ProtectedChest chest = activeLoot.get(thief.getUniqueId());
        if (chest == null) return;
        if (displayItem == null || displayItem.getType() == Material.AIR || displayItem.getType() == Material.BARRIER) {
            if (displayItem != null && displayItem.getType() == Material.BARRIER) {
                activeLoot.remove(thief.getUniqueId());
                thief.closeInventory();
            }
            return;
        }

        World world = Bukkit.getWorld(chest.getWorld());
        if (world == null) return;
        Block block = world.getBlockAt(chest.getX(), chest.getY(), chest.getZ());
        if (!(block.getState() instanceof Container container)) return;

        int amount = displayItem.getAmount();
        Material type = displayItem.getType();

        // Retire du conteneur reel
        int remaining = amount;
        remaining = removeFromInventory(container.getInventory(), type, remaining);
        Block other = plugin.getChestManager().getOtherChestHalf(block);
        if (remaining > 0 && other != null && other.getState() instanceof Container oc) {
            removeFromInventory(oc.getInventory(), type, remaining);
        }

        // Donne au voleur (item propre sans lore custom)
        ItemStack give = new ItemStack(type, amount);
        HashMap<Integer, ItemStack> overflow = thief.getInventory().addItem(give);
        for (ItemStack left : overflow.values()) {
            thief.getWorld().dropItemNaturally(thief.getLocation(), left);
        }

        inv.setItem(slot, null);
        thief.playSound(thief.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        thief.sendMessage(ChatColor.GREEN + "Tu as vole " + amount + "x " + type.name().toLowerCase().replace('_', ' '));
    }

    private int removeFromInventory(Inventory inv, Material type, int amount) {
        int left = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() == type) {
                int take = Math.min(left, it.getAmount());
                it.setAmount(it.getAmount() - take);
                left -= take;
                if (it.getAmount() <= 0) inv.setItem(i, null);
                else inv.setItem(i, it);
            }
        }
        return left;
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }
}

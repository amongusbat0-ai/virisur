package fr.rp.virisur.listeners;

import fr.rp.virisur.ViriSur;
import fr.rp.virisur.models.ProtectedChest;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

public class ChestListener implements Listener {

    private final ViriSur plugin;

    public ChestListener(ViriSur plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!plugin.getChestManager().isProtectedType(block.getType())) return;

        Player player = event.getPlayer();
        // 1 tick plus tard : le double coffre est formé
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getChestManager().registerWithAdjacent(block, player);
            player.sendMessage(msg("place"));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ProtectedChest chest = plugin.getChestManager().getIncludingDouble(block);
        if (chest == null) return;

        Player player = event.getPlayer();
        if (!chest.getOwner().equals(player.getUniqueId())
                && !player.hasPermission("virisur.admin")
                && !plugin.getChestManager().hasGlobalAccess(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(msg("destroy-refuse"));
            return;
        }

        plugin.getChestManager().unregisterPair(block);
    }

    /**
     * Intercepte le clic AVANT l'ouverture (plus fiable que InventoryOpenEvent pour les doubles).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!plugin.getChestManager().isProtectedType(block.getType())) return;

        Player player = event.getPlayer();

        // Ignore si déjà en mini-jeu / loot
        if (plugin.getLockpickGui().getSession(player.getUniqueId()) != null) return;
        if (plugin.getLootGui().getActive(player.getUniqueId()) != null) return;

        // Accès global admin
        if (plugin.getChestManager().hasGlobalAccess(player.getUniqueId())) return;

        ProtectedChest chest = plugin.getChestManager().getIncludingDouble(block);
        if (chest == null) return;

        // Accès libre
        if (plugin.getChestManager().canAccess(chest, player.getUniqueId())) return;

        // Admin sneak = bypass ponctuel
        if (player.hasPermission("virisur.admin") && player.isSneaking()) return;

        // Cooldown
        if (plugin.getChestManager().isOnCooldown(player.getUniqueId())) {
            event.setCancelled(true);
            long sec = plugin.getChestManager().getCooldownRemainingSec(player.getUniqueId());
            String m = plugin.getConfig().getString("messages.cooldown", "&cCooldown %time%s")
                    .replace("%time%", String.valueOf(sec));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.prefix", "") + m));
            return;
        }

        // Bloquer ouverture + crochetage
        event.setCancelled(true);
        plugin.getLockpickGui().start(player, chest);
    }

    /**
     * Filet de sécurité si l'ouverture passe quand même (double coffre / plugins).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (plugin.getLockpickGui().getSession(player.getUniqueId()) != null) return;
        if (plugin.getLootGui().getActive(player.getUniqueId()) != null) return;

        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.CHEST && type != InventoryType.BARREL && type != InventoryType.SHULKER_BOX) {
            return;
        }

        if (plugin.getChestManager().hasGlobalAccess(player.getUniqueId())) return;

        Block block = resolveBlock(event.getInventory().getHolder());
        if (block == null) return;
        if (!plugin.getChestManager().isProtectedType(block.getType())) return;

        ProtectedChest chest = plugin.getChestManager().getIncludingDouble(block);
        if (chest == null) return;

        if (plugin.getChestManager().canAccess(chest, player.getUniqueId())) return;
        if (player.hasPermission("virisur.admin") && player.isSneaking()) return;

        if (plugin.getChestManager().isOnCooldown(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        // Ne relance pas le mini-jeu si interact l'a déjà fait le même tick
        if (plugin.getLockpickGui().getSession(player.getUniqueId()) == null) {
            plugin.getLockpickGui().start(player, chest);
        }
    }

    private Block resolveBlock(InventoryHolder holder) {
        if (holder == null) return null;

        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            InventoryHolder right = doubleChest.getRightSide();
            Block b = blockFromHolder(left);
            if (b != null) return b;
            return blockFromHolder(right);
        }

        return blockFromHolder(holder);
    }

    private Block blockFromHolder(InventoryHolder holder) {
        if (holder == null) return null;
        if (holder instanceof org.bukkit.block.Container container) {
            return container.getBlock();
        }
        if (holder instanceof BlockState bs) {
            return bs.getBlock();
        }
        return null;
    }

    private String msg(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String m = plugin.getConfig().getString("messages." + key, key);
        return ChatColor.translateAlternateColorCodes('&', prefix + m);
    }
}

package fr.rp.virisur.gui;

import fr.rp.virisur.ViriSur;
import fr.rp.virisur.models.ProtectedChest;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LockpickGui {

    public enum GameType { SEQUENCE, MEMORY, REFLEX, COLORS, WIRES, MATH }

    public static class Session {
        public final ProtectedChest chest;
        public final GameType type;
        public final List<Integer> sequence = new ArrayList<>();
        public int progress = 0;
        public int fails = 0;
        public int targetSlot = -1;
        public int targetNumber = -1;
        public Material targetColor = null;
        public long expiresAt;
        public BukkitTask timeoutTask;
        public boolean active = true;
        public boolean inputEnabled = true;

        public Session(ProtectedChest chest, GameType type, long timeoutMs) {
            this.chest = chest;
            this.type = type;
            this.expiresAt = System.currentTimeMillis() + timeoutMs;
        }
    }

    private final ViriSur plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public LockpickGui(ViriSur plugin) {
        this.plugin = plugin;
    }

    public Session getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public void removeSession(UUID uuid) {
        Session s = sessions.remove(uuid);
        if (s != null && s.timeoutTask != null) s.timeoutTask.cancel();
    }

    public void start(Player player, ProtectedChest chest) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(color("&cTu es deja en train de crocheter."));
            return;
        }

        GameType type = GameType.values()[ThreadLocalRandom.current().nextInt(GameType.values().length)];
        int timeoutSec = plugin.getConfig().getInt("minijeux.timeout-secondes", 25);
        Session session = new Session(chest, type, timeoutSec * 1000L);
        sessions.put(player.getUniqueId(), session);

        session.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session s = sessions.get(player.getUniqueId());
            if (s != null && s.active) {
                s.active = false;
                sessions.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(msg("vol-echec") + color(" &7(temps ecoule)"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            }
        }, timeoutSec * 20L);

        player.sendMessage(color("&e⏱ Temps limite : &c" + timeoutSec + "s"));

        switch (type) {
            case SEQUENCE -> openSequence(player, session);
            case MEMORY -> openMemory(player, session);
            case REFLEX -> openReflex(player, session);
            case COLORS -> openColors(player, session);
            case WIRES -> openWires(player, session);
            case MATH -> openMath(player, session);
        }
    }

    private void openSequence(Player player, Session session) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "🔓 Sequence");
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        Collections.shuffle(order);
        session.progress = 0;
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < 6; i++) {
            int num = order.get(i) + 1;
            inv.setItem(slots[i], item(Material.IRON_NUGGET, ChatColor.YELLOW + "" + num,
                    List.of(ChatColor.GRAY + "Clique 1 → 6 dans l'ordre")));
        }
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Annuler", List.of()));
        inv.setItem(4, timerItem(session));
        player.openInventory(inv);
        player.sendMessage(color("&eSequence : clique &a1 → 6&e dans l'ordre !"));
    }

    private void openMemory(Player player, Session session) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "🔓 Memoire");
        List<Integer> positions = new ArrayList<>(List.of(10, 11, 12, 13, 14, 15, 16));
        Collections.shuffle(positions);
        session.sequence.clear();
        for (int i = 0; i < 5; i++) session.sequence.add(positions.get(i));
        session.progress = 0;
        session.inputEnabled = false;
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Annuler", List.of()));
        inv.setItem(4, timerItem(session));
        player.openInventory(inv);
        player.sendMessage(color("&dMemoire : regarde la sequence (5 notes)..."));
        showMemorySequence(player, session, 0);
    }

    private void showMemorySequence(Player player, Session session, int index) {
        if (!session.active || !sessions.containsKey(player.getUniqueId())) return;
        if (index >= session.sequence.size()) {
            Inventory inv = player.getOpenInventory().getTopInventory();
            for (int i = 0; i < 27; i++) {
                if (i == 22 || i == 4) continue;
                inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "?",
                        List.of(ChatColor.GRAY + "Reproduis la sequence")));
            }
            session.inputEnabled = true;
            player.sendMessage(color("&aA toi ! Reproduis la sequence."));
            return;
        }
        int slot = session.sequence.get(index);
        Inventory inv = player.getOpenInventory().getTopInventory();
        inv.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "!", List.of()));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + index * 0.12f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!session.active) return;
            inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
            Bukkit.getScheduler().runTaskLater(plugin, () -> showMemorySequence(player, session, index + 1), 6L);
        }, 10L);
    }

    private void openReflex(Player player, Session session) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "🔓 Reflexe");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        }
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Annuler", List.of()));
        inv.setItem(4, timerItem(session));
        player.openInventory(inv);
        player.sendMessage(color("&6Reflexe : clique le &averde&6 (6 fois) !"));
        session.progress = 0;
        scheduleReflexTarget(player, session);
    }

    private void scheduleReflexTarget(Player player, Session session) {
        if (!session.active || session.progress >= 6) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!session.active || !sessions.containsKey(player.getUniqueId())) return;
            Inventory inv = player.getOpenInventory().getTopInventory();
            for (int i = 0; i < 27; i++) {
                if (i == 22 || i == 4) continue;
                inv.setItem(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
            }
            int slot = ThreadLocalRandom.current().nextInt(0, 27);
            while (slot == 22 || slot == 4) slot = ThreadLocalRandom.current().nextInt(0, 27);
            session.targetSlot = slot;
            inv.setItem(slot, item(Material.LIME_CONCRETE, ChatColor.GREEN + "CLIQUE !", List.of(ChatColor.YELLOW + "Vite !")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
            final int s = slot;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!session.active) return;
                if (session.targetSlot == s) {
                    session.targetSlot = -1;
                    inv.setItem(s, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
                    session.fails++;
                    if (session.fails >= maxFails()) fail(player, session);
                    else {
                        player.sendMessage(color("&cTrop lent ! (" + session.fails + "/" + maxFails() + ")"));
                        scheduleReflexTarget(player, session);
                    }
                }
            }, 18L);
        }, 10L);
    }

    private void openColors(Player player, Session session) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.AQUA + "🔓 Couleurs");
        Material[] colors = {
                Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE,
                Material.GREEN_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE
        };
        String[] names = {"ROUGE", "BLEU", "JAUNE", "VERT", "ORANGE", "VIOLET"};
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < 6; i++) {
            inv.setItem(slots[i], item(colors[i], ChatColor.WHITE + names[i], List.of(ChatColor.GRAY + "Clique la couleur demandee")));
        }
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Annuler", List.of()));
        session.progress = 0;
        pickNextColor(session, colors);
        inv.setItem(13, item(session.targetColor, ChatColor.GOLD + "→ " + colorName(session.targetColor) + " ←",
                List.of(ChatColor.YELLOW + "Clique cette couleur !")));
        inv.setItem(4, timerItem(session));
        player.openInventory(inv);
        player.sendMessage(color("&bCouleurs : clique &e" + colorName(session.targetColor) + "&b !"));
    }

    private void pickNextColor(Session session, Material[] colors) {
        session.targetColor = colors[ThreadLocalRandom.current().nextInt(colors.length)];
    }

    private String colorName(Material m) {
        return switch (m) {
            case RED_CONCRETE -> "ROUGE";
            case BLUE_CONCRETE -> "BLEU";
            case YELLOW_CONCRETE -> "JAUNE";
            case GREEN_CONCRETE -> "VERT";
            case ORANGE_CONCRETE -> "ORANGE";
            case PURPLE_CONCRETE -> "VIOLET";
            default -> m.name();
        };
    }

    private void openWires(Player player, Session session) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.RED + "🔓 Fils");
        Material[] wires = {
                Material.RED_WOOL, Material.BLUE_WOOL, Material.YELLOW_WOOL,
                Material.GREEN_WOOL, Material.WHITE_WOOL
        };
        List<Integer> displayOrder = new ArrayList<>(List.of(0, 1, 2, 3, 4));
        Collections.shuffle(displayOrder);
        int[] slots = {11, 12, 13, 14, 15};
        session.progress = 0;
        for (int i = 0; i < 5; i++) {
            int wireIdx = displayOrder.get(i);
            int cutOrder = wireIdx + 1;
            inv.setItem(slots[i], item(wires[wireIdx], ChatColor.YELLOW + "Fil #" + cutOrder,
                    List.of(ChatColor.GRAY + "Coupe dans l'ordre 1 → 5")));
        }
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Annuler", List.of()));
        inv.setItem(4, timerItem(session));
        inv.setItem(0, item(Material.PAPER, ChatColor.GOLD + "Ordre : 1 → 2 → 3 → 4 → 5", List.of()));
        player.openInventory(inv);
        player.sendMessage(color("&cFils : coupe &e#1 puis #2 ... #5&c !"));
    }

    private void openMath(Player player, Session session) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.BLUE + "🔓 Calcul");
        session.progress = 0;
        generateMath(session, inv);
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Annuler", List.of()));
        player.openInventory(inv);
        player.sendMessage(color("&9Calcul : trouve le bon resultat (3 calculs) !"));
    }

    private void generateMath(Session session, Inventory inv) {
        int a = ThreadLocalRandom.current().nextInt(2, 12);
        int b = ThreadLocalRandom.current().nextInt(2, 12);
        boolean add = ThreadLocalRandom.current().nextBoolean();
        int result = add ? a + b : a * b;
        session.targetNumber = result;
        for (int i = 0; i < 27; i++) {
            if (i != 22) inv.setItem(i, null);
        }
        inv.setItem(4, item(Material.PAPER, ChatColor.AQUA + a + (add ? " + " : " x ") + b + " = ?",
                List.of(ChatColor.YELLOW + "Clique la bonne reponse",
                        ChatColor.GRAY + "Calcul " + (session.progress + 1) + "/3")));
        Set<Integer> answers = new LinkedHashSet<>();
        answers.add(result);
        while (answers.size() < 5) {
            int fake = result + ThreadLocalRandom.current().nextInt(-8, 9);
            if (fake != result && fake > 0) answers.add(fake);
        }
        List<Integer> shuffled = new ArrayList<>(answers);
        Collections.shuffle(shuffled);
        int[] slots = {11, 12, 13, 14, 15};
        for (int i = 0; i < 5; i++) {
            inv.setItem(slots[i], item(Material.GOLD_NUGGET, ChatColor.YELLOW + "" + shuffled.get(i),
                    List.of(ChatColor.GRAY + "Clique si c'est la reponse")));
        }
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Annuler", List.of()));
    }

    public void handleClick(Player player, int slot, Inventory inv) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.active) return;
        ItemStack clicked = inv.getItem(slot);
        if (clicked != null && clicked.getType() == Material.BARRIER) {
            session.active = false;
            removeSession(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(color("&eCrochetage annule."));
            return;
        }
        switch (session.type) {
            case SEQUENCE -> handleSequence(player, session, slot, inv);
            case MEMORY -> handleMemory(player, session, slot);
            case REFLEX -> handleReflex(player, session, slot);
            case COLORS -> handleColors(player, session, slot, inv);
            case WIRES -> handleWires(player, session, slot, inv);
            case MATH -> handleMath(player, session, slot, inv);
        }
    }

    private void handleSequence(Player player, Session session, int slot, Inventory inv) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) return;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        int num;
        try { num = Integer.parseInt(name.trim()); } catch (NumberFormatException e) { return; }
        if (num == session.progress + 1) {
            session.progress++;
            inv.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "OK", List.of()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + session.progress * 0.15f);
            if (session.progress >= 6) succeed(player, session);
        } else failClick(player, session);
    }

    private void handleMemory(Player player, Session session, int slot) {
        if (!session.inputEnabled || slot == 22 || slot == 4) return;
        int expected = session.sequence.get(session.progress);
        if (slot == expected) {
            session.progress++;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + session.progress * 0.15f);
            Inventory inv = player.getOpenInventory().getTopInventory();
            inv.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "OK", List.of()));
            if (session.progress >= session.sequence.size()) succeed(player, session);
        } else {
            session.fails++;
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            if (session.fails >= maxFails()) fail(player, session);
            else {
                player.sendMessage(color("&cMauvais ! On reaffiche..."));
                session.progress = 0;
                session.inputEnabled = false;
                showMemorySequence(player, session, 0);
            }
        }
    }

    private void handleReflex(Player player, Session session, int slot) {
        if (slot == session.targetSlot) {
            session.progress++;
            session.targetSlot = -1;
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            if (session.progress >= 6) succeed(player, session);
            else scheduleReflexTarget(player, session);
        } else if (slot != 22 && slot != 4) failClick(player, session);
    }

    private void handleColors(Player player, Session session, int slot, Inventory inv) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getType() == Material.BARRIER || item.getType() == Material.PAPER || item.getType() == Material.CLOCK) return;
        if (item.getType() == session.targetColor) {
            session.progress++;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            if (session.progress >= 5) succeed(player, session);
            else {
                Material[] colors = {
                        Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE,
                        Material.GREEN_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE
                };
                pickNextColor(session, colors);
                inv.setItem(13, item(session.targetColor, ChatColor.GOLD + "-> " + colorName(session.targetColor) + " <-",
                        List.of(ChatColor.YELLOW + "Clique cette couleur ! (" + session.progress + "/5)")));
                player.sendMessage(color("&bMaintenant : &e" + colorName(session.targetColor)));
            }
        } else failClick(player, session);
    }

    private void handleWires(Player player, Session session, int slot, Inventory inv) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) return;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (!name.startsWith("Fil #")) return;
        int num;
        try { num = Integer.parseInt(name.replace("Fil #", "").trim()); } catch (NumberFormatException e) { return; }
        if (num == session.progress + 1) {
            session.progress++;
            inv.setItem(slot, item(Material.GRAY_WOOL, ChatColor.DARK_GRAY + "Coupe", List.of()));
            player.playSound(player.getLocation(), Sound.BLOCK_SHEEP_SHEAR, 1f, 1f);
            if (session.progress >= 5) succeed(player, session);
        } else failClick(player, session);
    }

    private void handleMath(Player player, Session session, int slot, Inventory inv) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) return;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        int num;
        try { num = Integer.parseInt(name.trim()); } catch (NumberFormatException e) { return; }
        if (num == session.targetNumber) {
            session.progress++;
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
            if (session.progress >= 3) succeed(player, session);
            else {
                player.sendMessage(color("&aBon ! Calcul " + (session.progress + 1) + "/3"));
                generateMath(session, inv);
            }
        } else failClick(player, session);
    }

    private void failClick(Player player, Session session) {
        session.fails++;
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        if (session.fails >= maxFails()) fail(player, session);
        else player.sendMessage(color("&cRate ! (" + session.fails + "/" + maxFails() + ")"));
    }

    private void succeed(Player player, Session session) {
        session.active = false;
        removeSession(player.getUniqueId());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendMessage(color("&aCrochetage reussi ! Tu fouilles le coffre..."));
        plugin.getLootGui().openLoot(player, session.chest);
    }

    private void fail(Player player, Session session) {
        session.active = false;
        removeSession(player.getUniqueId());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.5f);
        player.sendMessage(msg("vol-echec"));
    }

    private int maxFails() {
        return plugin.getConfig().getInt("minijeux.max-echecs", 3);
    }

    private ItemStack timerItem(Session session) {
        long left = Math.max(0, (session.expiresAt - System.currentTimeMillis()) / 1000);
        return item(Material.CLOCK, ChatColor.RED + "⏱ " + left + "s", List.of(ChatColor.GRAY + "Temps restant"));
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        m.setLore(lore);
        i.setItemMeta(m);
        return i;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String msg(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String m = plugin.getConfig().getString("messages." + key, key);
        return color(prefix + m);
    }
}

package fr.rp.virisur.commands;

import fr.rp.virisur.ViriSur;
import fr.rp.virisur.models.ProtectedChest;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ChestCommand implements CommandExecutor, TabCompleter {

    private final ViriSur plugin;

    public ChestCommand(ViriSur plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = label.toLowerCase();

        // /virisur peut être utilisé par la console pour certaines sous-commandes
        if (cmd.equals("virisur")) {
            return handleViriSur(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Joueurs uniquement.");
            return true;
        }

        switch (cmd) {
            case "add", "cadd" -> handleAdd(player, args);
            case "unadd", "cremove", "cretrait" -> handleUnadd(player, args);
            case "coffre", "chest" -> handleCoffre(player, args);
            default -> player.sendMessage(ChatColor.RED + "Commande inconnue.");
        }
        return true;
    }

    private boolean handleViriSur(CommandSender sender, String[] args) {
        if (!sender.hasPermission("virisur.admin")) {
            sender.sendMessage(ChatColor.RED + "Permission refusee.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== /virisur admin ===");
            sender.sendMessage(ChatColor.YELLOW + "/virisur bypass [joueur]" + ChatColor.GRAY + " — acces a TOUS les coffres");
            sender.sendMessage(ChatColor.YELLOW + "/virisur reload" + ChatColor.GRAY + " — recharge config");
            sender.sendMessage(ChatColor.YELLOW + "/virisur info" + ChatColor.GRAY + " — stats");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Config ViriSur rechargee.");
            }
            case "bypass", "access", "global" -> {
                Player cible;
                if (args.length >= 2) {
                    cible = Bukkit.getPlayer(args[1]);
                    if (cible == null) {
                        sender.sendMessage(ChatColor.RED + "Joueur hors ligne.");
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    cible = p;
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /virisur bypass <joueur>");
                    return true;
                }
                boolean now = plugin.getChestManager().toggleGlobalAccess(cible.getUniqueId());
                if (now) {
                    sender.sendMessage(ChatColor.GREEN + "✔ " + cible.getName() + " peut ouvrir TOUS les coffres.");
                    if (!cible.equals(sender)) {
                        cible.sendMessage(ChatColor.GREEN + "✔ Acces global ViriSur active (tous les coffres).");
                    }
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Acces global retire pour " + cible.getName() + ".");
                    if (!cible.equals(sender)) {
                        cible.sendMessage(ChatColor.YELLOW + "Acces global ViriSur desactive.");
                    }
                }
            }
            case "info" -> {
                sender.sendMessage(ChatColor.GOLD + "Coffres proteges : " +
                        ChatColor.WHITE + plugin.getChestManager().getAll().size());
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /virisur <bypass|reload|info>");
        }
        return true;
    }

    private void handleAdd(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /add <joueur>  (en regardant le coffre)");
            return;
        }
        ProtectedChest chest = getTargetChest(player);
        if (chest == null) return;
        if (!chest.getOwner().equals(player.getUniqueId()) && !player.hasPermission("virisur.admin")) {
            player.sendMessage(msg("pas-proprio"));
            return;
        }

        Player cible = Bukkit.getPlayer(args[0]);
        if (cible == null) {
            player.sendMessage(ChatColor.RED + "Joueur introuvable ou hors ligne.");
            return;
        }
        if (cible.getUniqueId().equals(chest.getOwner())) {
            player.sendMessage(ChatColor.RED + "C'est deja le proprietaire.");
            return;
        }

        plugin.getChestManager().addMember(chest, cible.getUniqueId());
        String m = plugin.getConfig().getString("messages.add-ok", "&a%joueur% ajoute")
                .replace("%joueur%", cible.getName());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix", "") + m));
        cible.sendMessage(ChatColor.GREEN + "Tu as maintenant acces au coffre de " + chest.getOwnerName() + ".");
    }

    private void handleUnadd(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /unadd <joueur>  (en regardant le coffre)");
            return;
        }
        ProtectedChest chest = getTargetChest(player);
        if (chest == null) return;
        if (!chest.getOwner().equals(player.getUniqueId()) && !player.hasPermission("virisur.admin")) {
            player.sendMessage(msg("pas-proprio"));
            return;
        }

        String nameArg = args[0];
        UUID targetUuid = null;
        String targetName = nameArg;

        Player online = Bukkit.getPlayer(nameArg);
        if (online != null) {
            targetUuid = online.getUniqueId();
            targetName = online.getName();
        } else {
            // OfflinePlayer
            OfflinePlayer off = Bukkit.getOfflinePlayer(nameArg);
            if (chest.getMembers().contains(off.getUniqueId())) {
                targetUuid = off.getUniqueId();
                if (off.getName() != null) targetName = off.getName();
            } else {
                // Cherche par nom parmi les membres
                for (UUID m : chest.getMembers()) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(m);
                    if (op.getName() != null && op.getName().equalsIgnoreCase(nameArg)) {
                        targetUuid = m;
                        targetName = op.getName();
                        break;
                    }
                }
            }
        }

        if (targetUuid == null || !chest.getMembers().contains(targetUuid)) {
            player.sendMessage(ChatColor.RED + "'" + nameArg + "' n'est pas dans la liste d'acces de ce coffre.");
            player.sendMessage(ChatColor.GRAY + "Utilise /coffre pour voir les membres.");
            return;
        }

        plugin.getChestManager().removeMember(chest, targetUuid);
        String m = plugin.getConfig().getString("messages.remove-ok", "&e%joueur% retire")
                .replace("%joueur%", targetName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix", "") + m));
    }

    private void handleCoffre(Player player, String[] args) {
        ProtectedChest chest = getTargetChest(player);
        if (chest == null) return;

        player.sendMessage(ChatColor.GOLD + "=== Coffre protege ===");
        player.sendMessage(ChatColor.YELLOW + "Proprietaire : " + ChatColor.WHITE + chest.getOwnerName());
        player.sendMessage(ChatColor.YELLOW + "Position : " + ChatColor.GRAY +
                chest.getX() + ", " + chest.getY() + ", " + chest.getZ());
        player.sendMessage(ChatColor.YELLOW + "Alarme : " +
                (chest.isAlarmActive() ? ChatColor.RED + "ACTIVE" : ChatColor.GREEN + "inactive"));
        if (chest.getMembers().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Aucun membre. /add <joueur> pour partager.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Membres (" + chest.getMembers().size() + ") :");
            for (UUID m : chest.getMembers()) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(m);
                player.sendMessage(ChatColor.GRAY + " - " + (off.getName() != null ? off.getName() : m.toString()));
            }
            player.sendMessage(ChatColor.DARK_GRAY + "Retirer : /unadd <joueur>");
        }
    }

    private ProtectedChest getTargetChest(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !plugin.getChestManager().isProtectedType(target.getType())) {
            player.sendMessage(msg("pas-coffre"));
            return null;
        }
        ProtectedChest chest = plugin.getChestManager().getIncludingDouble(target);
        if (chest == null) {
            player.sendMessage(msg("pas-coffre"));
            return null;
        }
        return chest;
    }

    private String msg(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String m = plugin.getConfig().getString("messages." + key, key);
        return ChatColor.translateAlternateColorCodes('&', prefix + m);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String a = alias.toLowerCase();
        if (args.length == 1) {
            if (a.equals("add") || a.equals("unadd") || a.equals("cadd") || a.equals("cremove")) {
                String p = args[0].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(p))
                        .collect(Collectors.toList());
            }
            if (a.equals("virisur")) {
                return filter(List.of("bypass", "reload", "info"), args[0]);
            }
        }
        if (args.length == 2 && a.equals("virisur") && args[0].equalsIgnoreCase("bypass")) {
            String p = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(p))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(lower)) out.add(s);
        }
        return out;
    }
}

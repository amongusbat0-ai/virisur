package fr.rp.virisur.listeners;

import fr.rp.virisur.ViriSur;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class GuiListener implements Listener {

    private final ViriSur plugin;

    public GuiListener(ViriSur plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.contains("Crochetage")) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null) return;
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            plugin.getLockpickGui().handleClick(player, event.getRawSlot(), event.getView().getTopInventory());
            return;
        }

        if (title.contains("Butin")) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null) return;
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            plugin.getLootGui().takeItem(player, event.getCurrentItem(), event.getRawSlot(),
                    event.getView().getTopInventory());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.contains("Crochetage")) {
            var session = plugin.getLockpickGui().getSession(player.getUniqueId());
            if (session != null && session.active) {
                session.active = false;
                plugin.getLockpickGui().removeSession(player.getUniqueId());
            }
        }

        if (title.contains("Butin")) {
            plugin.getLootGui().clear(player.getUniqueId());
        }
    }
}

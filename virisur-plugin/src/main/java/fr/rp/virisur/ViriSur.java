package fr.rp.virisur;

import fr.rp.virisur.commands.ChestCommand;
import fr.rp.virisur.gui.LockpickGui;
import fr.rp.virisur.gui.LootGui;
import fr.rp.virisur.listeners.ChestListener;
import fr.rp.virisur.listeners.GuiListener;
import fr.rp.virisur.managers.ChestManager;
import fr.rp.virisur.managers.DatabaseManager;
import fr.rp.virisur.models.ProtectedChest;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ViriSur extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ChestManager chestManager;
    private LockpickGui lockpickGui;
    private LootGui lootGui;
    private BukkitTask alarmTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        chestManager = new ChestManager(this);
        chestManager.load();

        lockpickGui = new LockpickGui(this);
        lootGui = new LootGui(this);

        ChestCommand chestCmd = new ChestCommand(this);
        getCommand("add").setExecutor(chestCmd);
        getCommand("add").setTabCompleter(chestCmd);
        getCommand("unadd").setExecutor(chestCmd);
        getCommand("unadd").setTabCompleter(chestCmd);
        getCommand("coffre").setExecutor(chestCmd);
        getCommand("virisur").setExecutor(chestCmd);
        getCommand("virisur").setTabCompleter(chestCmd);

        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        startAlarmLoop();

        getLogger().info("ViriSur active !");
    }

    @Override
    public void onDisable() {
        if (alarmTask != null) alarmTask.cancel();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("ViriSur desactive.");
    }

    private void startAlarmLoop() {
        int interval = getConfig().getInt("alarme.intervalle-ticks", 40);
        int distance = getConfig().getInt("alarme.distance", 32);
        String soundName = getConfig().getString("alarme.son", "BLOCK_NOTE_BLOCK_BELL");
        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            sound = Sound.BLOCK_NOTE_BLOCK_BELL;
        }

        final Sound alarmSound = sound;
        alarmTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            chestManager.clearExpiredAlarms();
            for (String key : chestManager.getAlarmesActives()) {
                ProtectedChest chest = chestManager.getByKey(key);
                if (chest == null || !chest.isAlarmActive()) continue;

                World world = Bukkit.getWorld(chest.getWorld());
                if (world == null) continue;
                Location loc = new Location(world, chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);

                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(loc) <= (double) distance * distance) {
                        p.playSound(loc, alarmSound, 1.2f, 0.8f);
                        // Particules
                        p.spawnParticle(Particle.ELECTRIC_SPARK, loc, 8, 0.4, 0.4, 0.4, 0.02);
                    }
                }
            }
        }, interval, interval);
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ChestManager getChestManager() { return chestManager; }
    public LockpickGui getLockpickGui() { return lockpickGui; }
    public LootGui getLootGui() { return lootGui; }
}

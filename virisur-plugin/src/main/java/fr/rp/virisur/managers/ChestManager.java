package fr.rp.virisur.managers;

import fr.rp.virisur.ViriSur;
import fr.rp.virisur.models.ProtectedChest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChestManager {

    private final ViriSur plugin;
    private final Map<String, ProtectedChest> coffres = new ConcurrentHashMap<>();
    // Cooldown vol : uuid joueur -> timestamp fin
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    // Alarmes actives (keys)
    private final Set<String> alarmesActives = ConcurrentHashMap.newKeySet();
    // Acces global (admin) : peut ouvrir tous les coffres sans crochetage
    private final Set<UUID> globalAccess = ConcurrentHashMap.newKeySet();

    public ChestManager(ViriSur plugin) {
        this.plugin = plugin;
    }

    public void grantGlobalAccess(UUID uuid) {
        globalAccess.add(uuid);
    }

    public void revokeGlobalAccess(UUID uuid) {
        globalAccess.remove(uuid);
    }

    public boolean hasGlobalAccess(UUID uuid) {
        return globalAccess.contains(uuid);
    }

    public boolean toggleGlobalAccess(UUID uuid) {
        if (globalAccess.contains(uuid)) {
            globalAccess.remove(uuid);
            return false;
        }
        globalAccess.add(uuid);
        return true;
    }

    public void load() {
        coffres.clear();
        coffres.putAll(plugin.getDatabaseManager().loadAll());
        for (ProtectedChest c : coffres.values()) {
            if (c.isAlarmActive()) alarmesActives.add(c.key());
        }
        plugin.getLogger().info("Coffres proteges charges : " + coffres.size());
    }

    public boolean isProtectedType(Material mat) {
        List<String> list = plugin.getConfig().getStringList("conteneurs");
        return list.contains(mat.name());
    }

    public ProtectedChest get(Location loc) {
        if (loc.getWorld() == null) return null;
        return coffres.get(ProtectedChest.key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public ProtectedChest get(Block block) {
        return get(block.getLocation());
    }

    /** Trouve le coffre protege en regardant aussi le double coffre adjacent */
    public ProtectedChest getIncludingDouble(Block block) {
        ProtectedChest c = get(block);
        if (c != null) return c;
        Block other = getOtherChestHalf(block);
        if (other != null) {
            c = get(other);
            if (c != null) return c;
        }
        // Fallback : scan 4 directions (au cas où le BlockData n'est pas encore LEFT/RIGHT)
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adj = block.getRelative(face);
            if (isProtectedType(adj.getType()) && adj.getType() == block.getType()) {
                c = get(adj);
                if (c != null) return c;
            }
        }
        return null;
    }

    public Block getOtherChestHalf(Block block) {
        if (!(block.getBlockData() instanceof Chest chestData)) {
            // Scan adjacent same-type containers
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block adj = block.getRelative(face);
                if (adj.getType() == block.getType()) return adj;
            }
            return null;
        }
        if (chestData.getType() == Chest.Type.SINGLE) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block adj = block.getRelative(face);
                if (adj.getType() == block.getType() && adj.getBlockData() instanceof Chest cd
                        && cd.getType() != Chest.Type.SINGLE) {
                    return adj;
                }
            }
            return null;
        }
        BlockFace face = chestData.getFacing();
        BlockFace side = chestData.getType() == Chest.Type.LEFT ? rotateRight(face) : rotateLeft(face);
        return block.getRelative(side);
    }

    /** Enregistre le bloc + l'autre moitié si double coffre */
    public void registerWithAdjacent(Block block, Player owner) {
        register(block, owner);
        Block other = getOtherChestHalf(block);
        if (other != null && isProtectedType(other.getType())) {
            ProtectedChest existing = get(other);
            if (existing == null) {
                register(other, owner);
            } else {
                // Sync membres vers la nouvelle moitié
                ProtectedChest mine = get(block);
                if (mine != null) {
                    for (UUID m : existing.getMembers()) {
                        mine.addMember(m);
                        plugin.getDatabaseManager().addMember(mine, m);
                    }
                    for (UUID m : mine.getMembers()) {
                        if (!existing.getMembers().contains(m)) {
                            existing.addMember(m);
                            plugin.getDatabaseManager().addMember(existing, m);
                        }
                    }
                }
            }
        }
    }

    public void unregisterPair(Block block) {
        ProtectedChest chest = getIncludingDouble(block);
        if (chest != null) unregister(chest);
        Block other = getOtherChestHalf(block);
        if (other != null) {
            ProtectedChest otherC = get(other);
            if (otherC != null) unregister(otherC);
        }
        // Aussi désenregistrer le bloc cliqué s'il restait
        ProtectedChest direct = get(block);
        if (direct != null) unregister(direct);
    }

    private BlockFace rotateLeft(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> f;
        };
    }

    private BlockFace rotateRight(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> f;
        };
    }

    public ProtectedChest register(Block block, Player owner) {
        Location loc = block.getLocation();
        ProtectedChest chest = new ProtectedChest(
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                owner.getUniqueId(), owner.getName()
        );
        coffres.put(chest.key(), chest);
        plugin.getDatabaseManager().saveChest(chest);

        // Si double coffre, enregistrer aussi l'autre moitie avec le meme proprio
        Block other = getOtherChestHalf(block);
        if (other != null && isProtectedType(other.getType())) {
            ProtectedChest otherChest = new ProtectedChest(
                    other.getWorld().getName(),
                    other.getX(), other.getY(), other.getZ(),
                    owner.getUniqueId(), owner.getName()
            );
            coffres.put(otherChest.key(), otherChest);
            plugin.getDatabaseManager().saveChest(otherChest);
        }
        return chest;
    }

    public void unregister(ProtectedChest chest) {
        coffres.remove(chest.key());
        alarmesActives.remove(chest.key());
        plugin.getDatabaseManager().deleteChest(chest.getWorld(), chest.getX(), chest.getY(), chest.getZ());
    }

    public void addMember(ProtectedChest chest, UUID member) {
        chest.addMember(member);
        plugin.getDatabaseManager().addMember(chest, member);
        // Sync double chest
        syncMembersToDouble(chest);
    }

    public void removeMember(ProtectedChest chest, UUID member) {
        chest.removeMember(member);
        plugin.getDatabaseManager().removeMember(chest, member);
        syncMembersToDouble(chest);
    }

    private void syncMembersToDouble(ProtectedChest chest) {
        Location loc = new Location(
                plugin.getServer().getWorld(chest.getWorld()),
                chest.getX(), chest.getY(), chest.getZ()
        );
        if (loc.getWorld() == null) return;
        Block block = loc.getBlock();
        Block other = getOtherChestHalf(block);
        if (other == null) return;
        ProtectedChest otherC = get(other);
        if (otherC == null) return;
        otherC.getMembers().clear();
        otherC.getMembers().addAll(chest.getMembers());
        for (UUID m : chest.getMembers()) {
            plugin.getDatabaseManager().addMember(otherC, m);
        }
        plugin.getDatabaseManager().saveChest(otherC);
    }

    public boolean canAccess(ProtectedChest chest, UUID player) {
        return chest.isMember(player);
    }

    public boolean isOnCooldown(UUID player) {
        Long end = cooldowns.get(player);
        if (end == null) return false;
        if (System.currentTimeMillis() >= end) {
            cooldowns.remove(player);
            return false;
        }
        return true;
    }

    public long getCooldownRemainingSec(UUID player) {
        Long end = cooldowns.get(player);
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(UUID player) {
        int min = plugin.getConfig().getInt("vol.cooldown-minutes", 2);
        cooldowns.put(player, System.currentTimeMillis() + min * 60_000L);
    }

    public void triggerAlarm(ProtectedChest chest) {
        int min = plugin.getConfig().getInt("alarme.duree-minutes", 5);
        chest.triggerAlarm(min * 60_000L);
        alarmesActives.add(chest.key());
        plugin.getDatabaseManager().saveChest(chest);
    }

    public Set<String> getAlarmesActives() {
        return alarmesActives;
    }

    public Collection<ProtectedChest> getAll() {
        return coffres.values();
    }

    public ProtectedChest getByKey(String key) {
        return coffres.get(key);
    }

    public void clearExpiredAlarms() {
        long now = System.currentTimeMillis();
        Iterator<String> it = alarmesActives.iterator();
        while (it.hasNext()) {
            String key = it.next();
            ProtectedChest c = coffres.get(key);
            if (c == null || c.getAlarmUntil() <= now) {
                it.remove();
                if (c != null) {
                    c.setAlarmUntil(0);
                    plugin.getDatabaseManager().saveChest(c);
                }
            }
        }
    }
}

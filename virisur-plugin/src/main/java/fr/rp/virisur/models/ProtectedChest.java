package fr.rp.virisur.models;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ProtectedChest {

    private final String world;
    private final int x, y, z;
    private final UUID owner;
    private final String ownerName;
    private final Set<UUID> members = new HashSet<>();
    private long alarmUntil = 0L;

    public ProtectedChest(String world, int x, int y, int z, UUID owner, String ownerName) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public Set<UUID> getMembers() { return members; }

    public boolean isMember(UUID uuid) {
        return owner.equals(uuid) || members.contains(uuid);
    }

    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }

    public boolean isAlarmActive() {
        return System.currentTimeMillis() < alarmUntil;
    }

    public void triggerAlarm(long durationMs) {
        alarmUntil = System.currentTimeMillis() + durationMs;
    }

    public long getAlarmUntil() { return alarmUntil; }
    public void setAlarmUntil(long t) { this.alarmUntil = t; }

    public String key() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}

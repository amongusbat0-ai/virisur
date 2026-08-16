package fr.rp.virisur.managers;

import fr.rp.virisur.ViriSur;
import fr.rp.virisur.models.ProtectedChest;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final ViriSur plugin;
    private Connection connection;

    public DatabaseManager(ViriSur plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(folder, "virisur.db").getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS coffres (
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        owner TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        alarm_until INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (world, x, y, z)
                    )
                """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS membres (
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        member_uuid TEXT NOT NULL,
                        PRIMARY KEY (world, x, y, z, member_uuid)
                    )
                """);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "DB connect error", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "DB close error", e);
        }
    }

    public void saveChest(ProtectedChest chest) {
        String sql = """
            INSERT INTO coffres (world, x, y, z, owner, owner_name, alarm_until)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(world, x, y, z) DO UPDATE SET
                owner = excluded.owner,
                owner_name = excluded.owner_name,
                alarm_until = excluded.alarm_until
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, chest.getWorld());
            ps.setInt(2, chest.getX());
            ps.setInt(3, chest.getY());
            ps.setInt(4, chest.getZ());
            ps.setString(5, chest.getOwner().toString());
            ps.setString(6, chest.getOwnerName());
            ps.setLong(7, chest.getAlarmUntil());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "saveChest", e);
        }
    }

    public void deleteChest(String world, int x, int y, int z) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM coffres WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "deleteChest", e);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM membres WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "deleteMembers", e);
        }
    }

    public void addMember(ProtectedChest chest, UUID member) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO membres (world, x, y, z, member_uuid) VALUES (?,?,?,?,?)")) {
            ps.setString(1, chest.getWorld());
            ps.setInt(2, chest.getX());
            ps.setInt(3, chest.getY());
            ps.setInt(4, chest.getZ());
            ps.setString(5, member.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "addMember", e);
        }
    }

    public void removeMember(ProtectedChest chest, UUID member) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM membres WHERE world=? AND x=? AND y=? AND z=? AND member_uuid=?")) {
            ps.setString(1, chest.getWorld());
            ps.setInt(2, chest.getX());
            ps.setInt(3, chest.getY());
            ps.setInt(4, chest.getZ());
            ps.setString(5, member.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "removeMember", e);
        }
    }

    public Map<String, ProtectedChest> loadAll() {
        Map<String, ProtectedChest> map = new HashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM coffres")) {
            while (rs.next()) {
                ProtectedChest c = new ProtectedChest(
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        UUID.fromString(rs.getString("owner")),
                        rs.getString("owner_name")
                );
                c.setAlarmUntil(rs.getLong("alarm_until"));
                map.put(c.key(), c);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "loadAll", e);
        }

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM membres")) {
            while (rs.next()) {
                String key = ProtectedChest.key(
                        rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                ProtectedChest c = map.get(key);
                if (c != null) {
                    c.addMember(UUID.fromString(rs.getString("member_uuid")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "loadMembers", e);
        }
        return map;
    }
}

package com.hollowcrown.hub.lobby;

import com.hollowcrown.hub.HollowCrownHubPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MySQL mirror for {@link HubLobbyInstanceRegistry}.
 */
final class HubLobbyJdbcSync {

    private HubLobbyJdbcSync() {
    }

    static boolean loadIfPresent(HollowCrownHubPlugin plugin, DataSource ds, Map<String, HubLobbyInstanceRecord> byId) throws SQLException {
        try (Connection c = ds.getConnection();
             var ps = c.prepareStatement("SELECT COUNT(*) FROM hc_lobby_instance");
             var rs = ps.executeQuery()) {
            if (!rs.next() || rs.getInt(1) <= 0) {
                return false;
            }
        }
        byId.clear();
        try (Connection c = ds.getConnection();
             var ps = c.prepareStatement(
                     "SELECT id, template_key, world_name, world_uuid, lobby_index, created_at, priority_sequence, state "
                             + "FROM hc_lobby_instance");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString(1);
                String templateKey = rs.getString(2);
                String worldName = rs.getString(3);
                UUID worldUuid = UUID.fromString(rs.getString(4));
                int lobbyIndex = rs.getInt(5);
                long createdAt = rs.getLong(6);
                long prioritySequence = rs.getLong(7);
                LobbyInstanceState st = LobbyInstanceState.valueOf(rs.getString(8));
                byId.put(canonicalId(id), new HubLobbyInstanceRecord(
                        id, templateKey, worldName, worldUuid, lobbyIndex, createdAt, prioritySequence, st));
            }
        }
        plugin.getLogger().info("Hub lobby instances: loaded " + byId.size() + " row(s) from MySQL.");
        return true;
    }

    static void save(HollowCrownHubPlugin plugin, DataSource ds, Map<String, HubLobbyInstanceRecord> byId) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (var st = c.createStatement()) {
                    st.executeUpdate("DELETE FROM hc_lobby_instance");
                }
                try (var ins = c.prepareStatement(
                        "INSERT INTO hc_lobby_instance(id, template_key, world_name, world_uuid, lobby_index, created_at, "
                                + "priority_sequence, state) VALUES(?,?,?,?,?,?,?,?)")) {
                    for (HubLobbyInstanceRecord r : byId.values()) {
                        ins.setString(1, r.id());
                        ins.setString(2, r.templateKey());
                        ins.setString(3, r.worldName());
                        ins.setString(4, r.worldUuid().toString());
                        ins.setInt(5, r.lobbyIndex());
                        ins.setLong(6, r.createdAt());
                        ins.setLong(7, r.prioritySequence());
                        ins.setString(8, r.state().name());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                }
                plugin.getLogger().log(Level.SEVERE, "Hub lobby instances: MySQL save failed (YAML still on disk).", ex);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Hub lobby instances: MySQL connection failed during save.", ex);
        }
    }

    private static String canonicalId(String id) {
        return id.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

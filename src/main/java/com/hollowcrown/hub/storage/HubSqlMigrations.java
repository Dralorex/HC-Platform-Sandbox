package com.hollowcrown.hub.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Lazy DDL for Hollow Crown Hub MySQL hot store. Bump {@link #HUB_SQL_VERSION} when adding ALTER paths.
 */
public final class HubSqlMigrations {

    /** Logical migration version stored in {@code hc_hub_schema}. */
    public static final int HUB_SQL_VERSION = 1;

    private HubSqlMigrations() {
    }

    public static void runMigrations(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hc_hub_schema (
                      domain VARCHAR(64) NOT NULL PRIMARY KEY,
                      version INT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hc_party (
                      party_id VARCHAR(64) NOT NULL PRIMARY KEY,
                      display_name VARCHAR(128) NOT NULL,
                      leader_uuid CHAR(36) NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hc_party_member (
                      party_id VARCHAR(64) NOT NULL,
                      ord INT NOT NULL,
                      member_uuid CHAR(36) NOT NULL,
                      PRIMARY KEY (party_id, member_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hc_party_invite (
                      invitee_uuid CHAR(36) NOT NULL,
                      party_id VARCHAR(64) NOT NULL,
                      created_ms BIGINT NOT NULL,
                      PRIMARY KEY (invitee_uuid, party_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hc_lobby_instance (
                      id VARCHAR(128) NOT NULL PRIMARY KEY,
                      template_key VARCHAR(128) NOT NULL,
                      world_name VARCHAR(256) NOT NULL,
                      world_uuid CHAR(36) NOT NULL,
                      lobby_index INT NOT NULL,
                      created_at BIGINT NOT NULL,
                      priority_sequence BIGINT NOT NULL,
                      state VARCHAR(32) NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hc_queue_snapshot (
                      template_key VARCHAR(128) NOT NULL PRIMARY KEY,
                      payload_json MEDIUMTEXT NOT NULL,
                      updated_ms BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
        ensureDomainVersion(c, "hub", HUB_SQL_VERSION);
    }

    private static void ensureDomainVersion(Connection c, String domain, int target) throws SQLException {
        try (var ps = c.prepareStatement("SELECT version FROM hc_hub_schema WHERE domain=?")) {
            ps.setString(1, domain);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    if (v < target) {
                        try (var up = c.prepareStatement("UPDATE hc_hub_schema SET version=? WHERE domain=?")) {
                            up.setInt(1, target);
                            up.setString(2, domain);
                            up.executeUpdate();
                        }
                    }
                    return;
                }
            }
        }
        try (var ins = c.prepareStatement("INSERT INTO hc_hub_schema(domain, version) VALUES(?, ?)")) {
            ins.setString(1, domain);
            ins.setInt(2, target);
            ins.executeUpdate();
        }
    }
}

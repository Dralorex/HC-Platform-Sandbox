package com.hollowcrown.hub.storage;

import com.hollowcrown.api.storage.StorageBackend;
import com.hollowcrown.api.storage.StorageProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link StorageProvider} backed by {@code hc_hub_schema} on MySQL.
 */
public final class HubSqlStorageProvider implements StorageProvider {

    private final DataSource dataSource;

    public HubSqlStorageProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.MYSQL;
    }

    @Override
    public int readSchemaVersion(String domain) {
        if (domain == null || domain.isBlank()) {
            return 0;
        }
        try (Connection c = dataSource.getConnection();
             var ps = c.prepareStatement("SELECT version FROM hc_hub_schema WHERE domain=?")) {
            ps.setString(1, domain.trim());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    @Override
    public void writeSchemaVersion(String domain, int version) {
        if (domain == null || domain.isBlank()) {
            return;
        }
        try (Connection c = dataSource.getConnection();
             var ps = c.prepareStatement(
                     "INSERT INTO hc_hub_schema(domain, version) VALUES(?, ?) "
                             + "ON DUPLICATE KEY UPDATE version=VALUES(version)")) {
            ps.setString(1, domain.trim());
            ps.setInt(2, version);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}

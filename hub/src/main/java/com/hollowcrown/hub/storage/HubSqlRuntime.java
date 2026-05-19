package com.hollowcrown.hub.storage;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.logging.Level;

/**
 * Optional MySQL pool + schema provider. When {@link #tryConnect(HollowCrownHubPlugin)} returns null,
 * callers must use YAML-only paths and log loudly.
 */
public final class HubSqlRuntime implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final HubSqlStorageProvider storageProvider;

    private HubSqlRuntime(HikariDataSource dataSource, HubSqlStorageProvider storageProvider) {
        this.dataSource = dataSource;
        this.storageProvider = storageProvider;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public HubSqlStorageProvider storageProvider() {
        return storageProvider;
    }

    /**
     * Returns a live pool when {@code storage.type} is MYSQL and JDBC succeeds; otherwise null after logging.
     */
    public static HubSqlRuntime tryConnect(HollowCrownHubPlugin plugin) {
        String type = plugin.getConfig().getString("storage.type", "YAML");
        if (type == null || !"MYSQL".equalsIgnoreCase(type.trim())) {
            return null;
        }
        String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfig().getString("storage.mysql.database", "");
        String user = plugin.getConfig().getString("storage.mysql.username", "");
        String pass = plugin.getConfig().getString("storage.mysql.password", "");
        String extra = plugin.getConfig().getString("storage.mysql.jdbcUrlParams",
                "useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        if (database == null || database.isBlank()) {
            plugin.getLogger().severe("*** HollowCrownHub: storage.type=MYSQL but storage.mysql.database is empty — "
                    + "FALLING BACK TO YAML. Fix config. ***");
            return null;
        }
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + extra;

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(user == null ? "" : user);
        hc.setPassword(pass == null ? "" : pass);
        hc.setPoolName("HollowCrownHub");
        hc.setMaximumPoolSize(Math.max(2, plugin.getConfig().getInt("storage.mysql.poolSize", 4)));
        hc.setConnectionTimeout(plugin.getConfig().getLong("storage.mysql.connectionTimeoutMs", 10_000L));
        hc.setAutoCommit(true);

        HikariDataSource ds = new HikariDataSource(hc);
        try {
            try (Connection c = ds.getConnection()) {
                HubSqlMigrations.runMigrations(c);
            }
            HubSqlStorageProvider sp = new HubSqlStorageProvider(ds);
            plugin.getLogger().info("HollowCrownHub MySQL hot store connected (jdbc:mysql://" + host + ":" + port + "/" + database + ").");
            return new HubSqlRuntime(ds, sp);
        } catch (Throwable t) {
            try {
                ds.close();
            } catch (Throwable ignored) {
            }
            plugin.getLogger().severe("*** HollowCrownHub: MYSQL CONNECTION OR MIGRATION FAILED — "
                    + "USING YAML FALLBACK. HOT DATA WILL NOT SYNC TO SQL UNTIL THIS IS FIXED. ***");
            plugin.getLogger().log(Level.SEVERE, t.toString(), t);
            return null;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

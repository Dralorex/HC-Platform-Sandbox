package com.hollowcrown.hub.lobby;

import com.hollowcrown.hub.HollowCrownHubPlugin;

import javax.sql.DataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persists open/broken lobby worlds under {@code lobby-instances.yml}.
 */
public final class HubLobbyInstanceRegistry {

    private static final int FILE_VERSION = 1;

    private final HollowCrownHubPlugin plugin;
    private final DataSource jdbc;
    private final File file;
    private final Map<String, HubLobbyInstanceRecord> byId = new ConcurrentHashMap<>();

    public HubLobbyInstanceRegistry(HollowCrownHubPlugin plugin, DataSource jdbc) {
        this.plugin = plugin;
        this.jdbc = jdbc;
        this.file = new File(plugin.getDataFolder(), "lobby-instances.yml");
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        loadFromDisk();
        if (jdbc != null) {
            try {
                if (HubLobbyJdbcSync.loadIfPresent(plugin, jdbc, byId)) {
                    plugin.getLogger().info("Hub lobby instances: MySQL overwrote YAML snapshot in memory.");
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Hub lobby instances: MySQL load failed; using YAML only.", ex);
            }
        }
    }

    public Collection<HubLobbyInstanceRecord> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(byId.values()));
    }

    public Optional<HubLobbyInstanceRecord> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(canonicalId(id)));
    }

    public void put(HubLobbyInstanceRecord record) {
        byId.put(canonicalId(record.id()), record);
        saveToDisk();
    }

    public void remove(String id) {
        if (id == null) {
            return;
        }
        if (byId.remove(canonicalId(id)) != null) {
            saveToDisk();
        }
    }

    public void updateState(String id, LobbyInstanceState state) {
        if (id == null) {
            return;
        }
        HubLobbyInstanceRecord r = byId.get(canonicalId(id));
        if (r == null) {
            return;
        }
        r.setState(state);
        saveToDisk();
    }

    private void loadFromDisk() {
        byId.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int ver = yaml.getInt("version", 0);
        if (ver > FILE_VERSION) {
            plugin.getLogger().warning("lobby-instances.yml version " + ver + " > supported " + FILE_VERSION);
        }
        ConfigurationSection sec = yaml.getConfigurationSection("instances");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection row = sec.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            try {
                String id = row.getString("id", key);
                String templateKey = row.getString("templateKey", "");
                String worldName = row.getString("worldName", "");
                String uuidStr = row.getString("worldUuid", "");
                int lobbyIndex = row.getInt("lobbyIndex", 0);
                long createdAt = row.getLong("createdAt", 0L);
                long prioritySequence = row.getLong("prioritySequence", 0L);
                LobbyInstanceState st = LobbyInstanceState.valueOf(row.getString("state", "OPEN"));
                UUID wu = UUID.fromString(uuidStr);
                byId.put(canonicalId(id),
                        new HubLobbyInstanceRecord(id, templateKey, worldName, wu, lobbyIndex, createdAt, prioritySequence, st));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skip bad lobby instance row '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void saveToDisk() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", FILE_VERSION);
        for (HubLobbyInstanceRecord r : byId.values()) {
            String path = "instances." + r.id();
            yaml.set(path + ".id", r.id());
            yaml.set(path + ".templateKey", r.templateKey());
            yaml.set(path + ".worldName", r.worldName());
            yaml.set(path + ".worldUuid", r.worldUuid().toString());
            yaml.set(path + ".lobbyIndex", r.lobbyIndex());
            yaml.set(path + ".createdAt", r.createdAt());
            yaml.set(path + ".prioritySequence", r.prioritySequence());
            yaml.set(path + ".state", r.state().name());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save lobby-instances.yml", ex);
        }
        if (jdbc != null) {
            HubLobbyJdbcSync.save(plugin, jdbc, byId);
        }
    }

    private static String canonicalId(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}

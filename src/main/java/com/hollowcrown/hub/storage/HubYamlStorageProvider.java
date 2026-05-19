package com.hollowcrown.hub.storage;

import com.hollowcrown.api.storage.StorageBackend;
import com.hollowcrown.api.storage.StorageProvider;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * YAML fallback storage; SQL backends can be swapped in later behind {@link StorageProvider}.
 */
public final class HubYamlStorageProvider implements StorageProvider {

    private final JavaPlugin plugin;
    private final File metaFile;

    public HubYamlStorageProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.metaFile = new File(plugin.getDataFolder(), "storage-meta.yml");
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        if (!metaFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                metaFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create storage-meta.yml", e);
            }
        }
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.YAML;
    }

    @Override
    public int readSchemaVersion(String domain) {
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(metaFile);
        return yaml.getInt("schema." + domain, 0);
    }

    @Override
    public void writeSchemaVersion(String domain, int version) {
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(metaFile);
        yaml.set("schema." + domain, version);
        try {
            yaml.save(metaFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save storage-meta.yml", e);
        }
    }
}

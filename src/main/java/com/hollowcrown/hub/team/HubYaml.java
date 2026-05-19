package com.hollowcrown.hub.team;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Minimal YAML helper (similar to IL util). */
final class HubYaml {

    private HubYaml() {}

    static FileConfiguration loadOrCreate(JavaPlugin plugin, String filename) {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        File f = new File(plugin.getDataFolder(), filename);
        if (!f.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                f.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create " + filename, e);
            }
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    static void save(JavaPlugin plugin, String filename, FileConfiguration cfg) {
        File f = new File(plugin.getDataFolder(), filename);
        try {
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + filename, e);
        }
    }
}


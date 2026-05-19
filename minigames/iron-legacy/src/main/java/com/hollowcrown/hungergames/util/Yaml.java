package com.hollowcrown.hungergames.util;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class Yaml {
    private Yaml() {}

    public static FileConfiguration load(JavaPlugin plugin, String path) {
        File f = new File(plugin.getDataFolder(), path);
        if (!f.exists()) return null;
        return YamlConfiguration.loadConfiguration(f);
    }

    public static FileConfiguration loadOrCreate(JavaPlugin plugin, String path) {
        File f = new File(plugin.getDataFolder(), path);
        if (!f.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                f.createNewFile();
            } catch (IOException ignored) {}
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    public static void save(JavaPlugin plugin, String path, FileConfiguration cfg) {
        File f = new File(plugin.getDataFolder(), path);
        try { cfg.save(f); } catch (IOException ignored) {}
    }
}



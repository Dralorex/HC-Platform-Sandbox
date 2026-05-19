package com.hollowcrown.hungergames.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Per-template envoy + replenish YAML paths under {@code world-envoys/<key>/}. */
public final class WorldEnvoyStorage {
    private static final String ROOT = "world-envoys";

    private WorldEnvoyStorage() {}

    public static String safeFolder(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return "_unknown";
        String s = templateKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return s.isBlank() ? "_unknown" : s;
    }

    public static String replenishPath(String templateKey) {
        return ROOT + "/" + safeFolder(templateKey) + "/replenish.yml";
    }

    public static String envoysPath(String templateKey) {
        return ROOT + "/" + safeFolder(templateKey) + "/envoys.yml";
    }

    private static void extractResource(JavaPlugin plugin, String resourceName, File dest) throws IOException {
        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) throw new IOException("Missing jar resource: " + resourceName);
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Create scoped replenish.yml from legacy root file or default jar resource. */
    public static void ensureReplenish(JavaPlugin plugin, String templateKey) {
        File dest = new File(plugin.getDataFolder(), replenishPath(templateKey));
        if (dest.exists()) return;
        try {
            File legacy = new File(plugin.getDataFolder(), "replenish.yml");
            if (legacy.exists()) {
                File parent = dest.getParentFile();
                if (parent != null) parent.mkdirs();
                Files.copy(legacy.toPath(), dest.toPath());
            } else {
                extractResource(plugin, "replenish.yml", dest);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not create " + dest + ": " + ex.getMessage());
        }
    }

    /** Create scoped envoys.yml from legacy root file or default jar resource. */
    public static void ensureEnvoys(JavaPlugin plugin, String templateKey) {
        File dest = new File(plugin.getDataFolder(), envoysPath(templateKey));
        if (dest.exists()) return;
        try {
            File legacy = new File(plugin.getDataFolder(), "envoys.yml");
            if (legacy.exists()) {
                File parent = dest.getParentFile();
                if (parent != null) parent.mkdirs();
                Files.copy(legacy.toPath(), dest.toPath());
            } else {
                extractResource(plugin, "envoys.yml", dest);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not create " + dest + ": " + ex.getMessage());
        }
    }
}

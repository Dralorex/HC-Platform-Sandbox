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
    /** Shared loot tiers + schedule (plugins/HCIronLegacy/replenish.yml). */
    public static final String BASE_REPLENISH = "replenish.yml";

    private WorldEnvoyStorage() {}

    public static String safeFolder(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return "_unknown";
        String s = templateKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return s.isBlank() ? "_unknown" : s;
    }

    /** Per-map replenish chest coordinates only. */
    public static String replenishLocationsPath(String templateKey) {
        return ROOT + "/" + safeFolder(templateKey) + "/replenish-locations.yml";
    }

    /** @deprecated Use {@link #replenishLocationsPath} for coords; loot uses {@link #BASE_REPLENISH}. */
    public static String replenishPath(String templateKey) {
        return replenishLocationsPath(templateKey);
    }

    /** Old per-template file that duplicated base loot — read only for location migration. */
    public static String legacyScopedReplenishPath(String templateKey) {
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

    /** Ensures plugins/HCIronLegacy/replenish.yml exists (jar default on first run). */
    public static void ensureBaseReplenish(JavaPlugin plugin) {
        File dest = new File(plugin.getDataFolder(), BASE_REPLENISH);
        if (dest.exists()) {
            return;
        }
        try {
            extractResource(plugin, BASE_REPLENISH, dest);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not create " + dest + ": " + ex.getMessage());
        }
    }

    /** Creates per-template replenish chest location file (empty list if new). */
    public static void ensureReplenishLocations(JavaPlugin plugin, String templateKey) {
        File dest = new File(plugin.getDataFolder(), replenishLocationsPath(templateKey));
        if (dest.exists()) {
            return;
        }
        try {
            File parent = dest.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            if (!dest.createNewFile()) {
                return;
            }
            // Yaml.loadOrCreate will initialize; migration from legacy scoped file happens in ReplenishManager.
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not create " + dest + ": " + ex.getMessage());
        }
    }

    /** @deprecated Use {@link #ensureBaseReplenish} + {@link #ensureReplenishLocations}. */
    public static void ensureReplenish(JavaPlugin plugin, String templateKey) {
        ensureBaseReplenish(plugin);
        ensureReplenishLocations(plugin, templateKey);
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

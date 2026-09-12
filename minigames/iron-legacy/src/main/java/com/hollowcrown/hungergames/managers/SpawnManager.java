package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Per-template match spawn pads stored under {@code worlds.yml} → {@code worlds.<key>.spawnPads}.
 * Coordinates are world-independent (x/y/z/yaw/pitch); the runtime world is applied at match start.
 */
public class SpawnManager {
    public record PadCoord(double x, double y, double z, float yaw, float pitch) {
        Location toLocation(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }

        static PadCoord from(Location loc) {
            return new PadCoord(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        }
    }

    private final HungerGamesPlugin plugin;
    /** templateKey (lowercase) → pads */
    private final ConcurrentHashMap<String, List<PadCoord>> padsByTemplate = new ConcurrentHashMap<>();

    public SpawnManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Load pads from {@code worlds.yml}; migrate legacy global {@code config.yml} {@code spawns.pads} once. */
    public void load() {
        padsByTemplate.clear();
        TemplateWorldManager templates = plugin.templates();
        if (templates == null) {
            return;
        }
        FileConfiguration worlds = templates.config();
        ConfigurationSection worldsSec = worlds.getConfigurationSection("worlds");
        if (worldsSec != null) {
            for (String key : worldsSec.getKeys(false)) {
                String canon = key.toLowerCase(Locale.ROOT);
                List<PadCoord> pads = readPadsFromSection(worlds, "worlds." + canon + ".spawnPads");
                if (!pads.isEmpty()) {
                    padsByTemplate.put(canon, pads);
                }
            }
        }
        migrateLegacyGlobalPads(templates);
    }

    private void migrateLegacyGlobalPads(TemplateWorldManager templates) {
        List<String> legacy = plugin.getConfig().getStringList("spawns.pads");
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        int migrated = 0;
        for (String raw : legacy) {
            try {
                String[] p = raw.split(",");
                if (p.length < 6) continue;
                String worldName = p[0];
                PadCoord pad = new PadCoord(
                        Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]),
                        Float.parseFloat(p[4]),
                        Float.parseFloat(p[5]));
                String templateKey = null;
                var byWorld = templates.getTemplateByWorldName(worldName);
                if (byWorld != null) {
                    templateKey = byWorld.key();
                } else {
                    templateKey = templates.resolveTemplateKeyForWorld(worldName).orElse(null);
                }
                if (templateKey == null || templateKey.isBlank()) {
                    plugin.getLogger().warning("Legacy spawn pad skipped (unknown world/template): " + raw);
                    continue;
                }
                String canon = templateKey.toLowerCase(Locale.ROOT);
                padsByTemplate.compute(canon, (k, list) -> {
                    List<PadCoord> out = list != null ? new ArrayList<>(list) : new ArrayList<>();
                    out.add(pad);
                    return out;
                });
                migrated++;
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to migrate legacy spawn pad: " + raw);
            }
        }
        if (migrated > 0) {
            for (String key : new ArrayList<>(padsByTemplate.keySet())) {
                persistTemplate(key);
            }
            plugin.getConfig().set("spawns.pads", List.of());
            plugin.saveConfig();
            plugin.getLogger().info("Migrated " + migrated + " legacy global spawn pad(s) into worlds.yml per template.");
        }
    }

    private List<PadCoord> readPadsFromSection(FileConfiguration cfg, String path) {
        List<PadCoord> out = new ArrayList<>();
        List<?> raw = cfg.getList(path);
        if (raw == null) return out;
        for (Object entry : raw) {
            PadCoord pad = parsePadEntry(entry);
            if (pad != null) out.add(pad);
        }
        return out;
    }

    private PadCoord parsePadEntry(Object entry) {
        try {
            if (entry instanceof Map<?, ?> map) {
                double x = toDouble(map.get("x"));
                double y = toDouble(map.get("y"));
                double z = toDouble(map.get("z"));
                float yaw = (float) toDouble(map.containsKey("yaw") ? map.get("yaw") : 0);
                float pitch = (float) toDouble(map.containsKey("pitch") ? map.get("pitch") : 0);
                return new PadCoord(x, y, z, yaw, pitch);
            }
            String s = String.valueOf(entry);
            String[] p = s.split(",");
            if (p.length == 6) {
                // legacy world,x,y,z,yaw,pitch — ignore world
                return new PadCoord(
                        Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        Float.parseFloat(p[4]), Float.parseFloat(p[5]));
            }
            if (p.length == 5) {
                return new PadCoord(
                        Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                        Float.parseFloat(p[3]), Float.parseFloat(p[4]));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    private void persistTemplate(String templateKey) {
        String canon = templateKey.toLowerCase(Locale.ROOT);
        TemplateWorldManager templates = plugin.templates();
        if (templates == null) return;
        FileConfiguration worlds = templates.config();
        String path = "worlds." + canon + ".spawnPads";
        List<PadCoord> pads = padsByTemplate.getOrDefault(canon, List.of());
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (PadCoord pad : pads) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", pad.x());
            m.put("y", pad.y());
            m.put("z", pad.z());
            m.put("yaw", pad.yaw());
            m.put("pitch", pad.pitch());
            serialized.add(m);
        }
        if (!worlds.isConfigurationSection("worlds." + canon)) {
            worlds.createSection("worlds." + canon);
        }
        worlds.set(path, serialized);
        templates.save();
    }

    public List<PadCoord> getPadCoords(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return List.of();
        return List.copyOf(padsByTemplate.getOrDefault(templateKey.toLowerCase(Locale.ROOT), List.of()));
    }

    /** Pads bound to {@code world} for teleport/display (coords from template key). */
    public List<Location> getSpawnPads(String templateKey, World world) {
        if (world == null) return List.of();
        return getPadCoords(templateKey).stream()
                .map(p -> p.toLocation(world))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** @deprecated Prefer {@link #getSpawnPads(String, World)}. Empty when no template context. */
    @Deprecated
    public List<Location> getSpawnPads() {
        return List.of();
    }

    public int count(String templateKey) {
        return getPadCoords(templateKey).size();
    }

    public void add(String templateKey, Location loc) {
        String canon = requireKey(templateKey);
        padsByTemplate.compute(canon, (k, list) -> {
            List<PadCoord> out = list != null ? new ArrayList<>(list) : new ArrayList<>();
            out.add(PadCoord.from(loc));
            return out;
        });
        persistTemplate(canon);
    }

    public void clear(String templateKey) {
        String canon = requireKey(templateKey);
        padsByTemplate.put(canon, new ArrayList<>());
        persistTemplate(canon);
    }

    public Location removeNearest(String templateKey, Location from) {
        String canon = requireKey(templateKey);
        List<PadCoord> pads = new ArrayList<>(padsByTemplate.getOrDefault(canon, List.of()));
        if (pads.isEmpty() || from.getWorld() == null) return null;
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < pads.size(); i++) {
            Location loc = pads.get(i).toLocation(from.getWorld());
            double d = loc.distanceSquared(from);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best < 0) return null;
        PadCoord removed = pads.remove(best);
        padsByTemplate.put(canon, pads);
        persistTemplate(canon);
        return removed.toLocation(from.getWorld());
    }

    public List<Location> addRing(String templateKey, Location center, int count, double radius, boolean useWorldSpawn) {
        String canon = requireKey(templateKey);
        World world = center.getWorld();
        if (world == null) return List.of();
        Location base = useWorldSpawn ? world.getSpawnLocation() : center.clone();
        List<Location> added = new ArrayList<>();
        List<PadCoord> pads = new ArrayList<>(padsByTemplate.getOrDefault(canon, List.of()));
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI) * i / count;
            double x = base.getX() + radius * Math.cos(angle);
            double z = base.getZ() + radius * Math.sin(angle);
            int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
            Location spot = new Location(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, 0f, 0f);
            pads.add(PadCoord.from(spot));
            added.add(spot);
        }
        padsByTemplate.put(canon, pads);
        persistTemplate(canon);
        return added;
    }

    private static String requireKey(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey required for spawn pads");
        }
        return templateKey.toLowerCase(Locale.ROOT);
    }
}

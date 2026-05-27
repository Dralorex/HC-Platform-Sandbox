package com.hollowcrown.hub.limbo;

import com.hollowcrown.api.integration.IronLegacyWorldsCatalog;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes Iron Legacy {@code worlds.yml} limbo sections only (zone corners + spawn).
 * Runtime gameplay still reads these paths via HCIronLegacy {@link com.hollowcrown.api.integration.IronLegacyTemplateAdmin#reloadWorldsYamlFromDisk()}.
 */
public final class HubLimboWorldsYaml implements IronLegacyWorldsCatalog {

    static final Pattern SAFE_TEMPLATE_KEY = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    private static final String FILE_NAME = "worlds.yml";

    private final HollowCrownHubPlugin plugin;
    private final Object fileLock = new Object();

    public HubLimboWorldsYaml(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    private File ironLegacyDataFolder() {
        String name = plugin.getConfig().getString("ironLegacy.pluginName", "HCIronLegacy").trim();
        if (name.isEmpty()) {
            name = "HCIronLegacy";
        }
        Plugin il = Bukkit.getPluginManager().getPlugin(name);
        if (il != null) {
            return il.getDataFolder();
        }
        return new File(plugin.getDataFolder().getParentFile(), name);
    }

    private File worldsFile() {
        File folder = ironLegacyDataFolder();
        if (!folder.isDirectory()) {
            folder.mkdirs();
        }
        return new File(folder, FILE_NAME);
    }

    private YamlConfiguration loadFresh() {
        File f = worldsFile();
        YamlConfiguration yaml;
        if (!f.exists()) {
            yaml = new YamlConfiguration();
        } else {
            yaml = YamlConfiguration.loadConfiguration(f);
        }
        ensureRoots(yaml);
        return yaml;
    }

    private void save(YamlConfiguration yaml) throws IOException {
        ensureRoots(yaml);
        yaml.save(worldsFile());
    }

    private static void ensureRoots(YamlConfiguration yaml) {
        if (!yaml.isConfigurationSection("worlds")) {
            yaml.createSection("worlds");
        }
        if (!yaml.isConfigurationSection("instances")) {
            yaml.createSection("instances");
        }
    }

    private static String base(String templateKey) {
        return "worlds." + templateKey.toLowerCase(Locale.ROOT);
    }

    private void notifyIronLegacyReload() {
        if (!plugin.isEnabled()) {
            return;
        }
        HubIronLegacySupport.ilAdmin().ifPresent(admin ->
                Bukkit.getScheduler().runTask(plugin, admin::reloadWorldsYamlFromDisk));
    }

    /** Minimal {@code worlds.<key>} row when saving limbo before any other tooling created it. */
    private static void ensureMinimalWorldSection(YamlConfiguration yaml, String templateKey, String worldFolderName) {
        String b = base(templateKey);
        if (!yaml.isConfigurationSection(b)) {
            yaml.createSection(b);
        }
        if (!yaml.contains(b + ".worldName")) {
            yaml.set(b + ".worldName", worldFolderName);
        }
        if (!yaml.contains(b + ".displayName")) {
            yaml.set(b + ".displayName", templateKey);
        }
        yaml.set(b + ".enabled", yaml.getBoolean(b + ".enabled", true));
        yaml.set(b + ".status", yaml.getString(b + ".status", "EDIT"));
        yaml.set(b + ".maxPlayers", yaml.getInt(b + ".maxPlayers", 100));
        yaml.set(b + ".teamSizeMax", yaml.getInt(b + ".teamSizeMax", 5));
        yaml.set(b + ".iconKey", yaml.getString(b + ".iconKey", "default"));
        yaml.set(b + ".version", yaml.getInt(b + ".version", 0));
        int pct = yaml.getInt(b + ".limbo.minimumPercentage", 80);
        yaml.set(b + ".limbo.minimumPercentage", Math.max(1, Math.min(100, pct)));
    }

    private static void writeCorner(YamlConfiguration yaml, String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x", loc.getX());
        yaml.set(path + ".y", loc.getY());
        yaml.set(path + ".z", loc.getZ());
    }

    private Location readCorner(YamlConfiguration yaml, String path) {
        String worldName = yaml.getString(path + ".world", "");
        World world = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = yaml.getDouble(path + ".x");
        double y = yaml.getDouble(path + ".y");
        double z = yaml.getDouble(path + ".z");
        return new Location(world, x, y, z);
    }

    /**
     * Finds {@code worlds.yml} key whose {@code worldName} matches the loaded world folder (case-insensitive).
     */
    public Optional<String> findTemplateKeyForWorldFolder(String worldFolderName) {
        if (worldFolderName == null || worldFolderName.isBlank()) {
            return Optional.empty();
        }
        synchronized (fileLock) {
            YamlConfiguration yaml = loadFresh();
            ConfigurationSection worlds = yaml.getConfigurationSection("worlds");
            if (worlds == null) {
                return Optional.empty();
            }
            for (String key : worlds.getKeys(false)) {
                String wn = yaml.getString(base(key) + ".worldName", key);
                if (wn.equalsIgnoreCase(worldFolderName)) {
                    return Optional.of(key.toLowerCase(Locale.ROOT));
                }
            }
            return Optional.empty();
        }
    }

    public Optional<LimboZone> limboZone(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return Optional.empty();
        }
        synchronized (fileLock) {
            YamlConfiguration yaml = loadFresh();
            String b = base(templateKey);
            String p1 = b + ".limbo.zone.pos1";
            String p2 = b + ".limbo.zone.pos2";
            if (!yaml.contains(p1 + ".x") || !yaml.contains(p2 + ".x")) {
                return Optional.empty();
            }
            Location a = readCorner(yaml, p1);
            Location c = readCorner(yaml, p2);
            if (a == null || c == null) {
                return Optional.empty();
            }
            return Optional.of(new LimboZone(a, c));
        }
    }

    @Override
    public List<TemplateInfo> listTemplateInfos() {
        synchronized (fileLock) {
            YamlConfiguration yaml = loadFresh();
            ConfigurationSection sec = yaml.getConfigurationSection("worlds");
            List<TemplateInfo> out = new ArrayList<>();
            if (sec == null) return out;
            for (String key : sec.getKeys(false)) {
                String b = base(key);
                out.add(new TemplateInfo(
                        key.toLowerCase(Locale.ROOT),
                        yaml.getString(b + ".worldName", key),
                        yaml.getString(b + ".displayName", key),
                        yaml.getString(b + ".status", "EDIT"),
                        yaml.getBoolean(b + ".enabled", true),
                        yaml.getInt(b + ".maxPlayers", 100),
                        yaml.getInt(b + ".teamSizeMax", 5),
                        yaml.getString(b + ".iconKey", "default"),
                        yaml.getInt(b + ".version", 0)
                ));
            }
            return out;
        }
    }

    @Override
    public Optional<TemplateInfo> templateByKeyOrWorld(String keyOrWorld) {
        if (keyOrWorld == null || keyOrWorld.isBlank()) return Optional.empty();
        String k = keyOrWorld.toLowerCase(Locale.ROOT);
        synchronized (fileLock) {
            YamlConfiguration yaml = loadFresh();
            String b = base(k);
            if (yaml.isConfigurationSection(b)) {
                return Optional.of(new TemplateInfo(
                        k,
                        yaml.getString(b + ".worldName", k),
                        yaml.getString(b + ".displayName", k),
                        yaml.getString(b + ".status", "EDIT"),
                        yaml.getBoolean(b + ".enabled", true),
                        yaml.getInt(b + ".maxPlayers", 100),
                        yaml.getInt(b + ".teamSizeMax", 5),
                        yaml.getString(b + ".iconKey", "default"),
                        yaml.getInt(b + ".version", 0)
                ));
            }
            return resolveTemplateKeyForWorld(keyOrWorld).flatMap(this::templateByKeyOrWorld);
        }
    }

    @Override
    public Optional<String> resolveTemplateKeyForWorld(String worldName) {
        return findTemplateKeyForWorldFolder(worldName);
    }

    @Override
    public int minimumPercentage(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return 80;
        synchronized (fileLock) {
            YamlConfiguration yaml = loadFresh();
            int pct = yaml.getInt(base(templateKey) + ".limbo.minimumPercentage", 80);
            return Math.max(1, Math.min(100, pct));
        }
    }

    @Override
    public Optional<IronLegacyWorldsCatalog.LimboZoneCorners> limboZoneCorners(String templateKey) {
        return limboZone(templateKey).map(z -> new IronLegacyWorldsCatalog.LimboZoneCorners(
                z.pos1().getX(), z.pos1().getY(), z.pos1().getZ(), z.pos2().getX(), z.pos2().getY(), z.pos2().getZ()));
    }

    @Override
    public Optional<IronLegacyWorldsCatalog.LimboSpawn> limboSpawn(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return Optional.empty();
        synchronized (fileLock) {
            YamlConfiguration yaml = loadFresh();
            String b = base(templateKey);
            if (!yaml.contains(b + ".limbo.x")) return Optional.empty();
            String world = yaml.getString(b + ".limbo.world", "");
            return Optional.of(new IronLegacyWorldsCatalog.LimboSpawn(
                    world,
                    yaml.getDouble(b + ".limbo.x"),
                    yaml.getDouble(b + ".limbo.y"),
                    yaml.getDouble(b + ".limbo.z"),
                    (float) yaml.getDouble(b + ".limbo.yaw", 0.0),
                    (float) yaml.getDouble(b + ".limbo.pitch", 0.0)
            ));
        }
    }

    @Override
    public String resolveServerTarget(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return "";
        synchronized (fileLock) {
            YamlConfiguration yaml = loadFresh();
            String configured = yaml.getString(base(templateKey) + ".serverTarget", "");
            if (configured != null && !configured.isBlank()) return configured.trim();
            return "game-" + templateKey.toLowerCase(Locale.ROOT);
        }
    }

    private interface YamlEdit {
        boolean apply(YamlConfiguration yaml, String b);
    }

    private boolean editTemplate(String templateKey, YamlEdit edit) {
        if (templateKey == null || templateKey.isBlank()) return false;
        synchronized (fileLock) {
            try {
                YamlConfiguration yaml = loadFresh();
                String b = base(templateKey);
                if (!yaml.isConfigurationSection(b)) return false;
                if (!edit.apply(yaml, b)) return false;
                save(yaml);
                notifyIronLegacyReload();
                return true;
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save worlds.yml: " + ex.getMessage());
                return false;
            }
        }
    }

    @Override public boolean setDisplayName(String templateKey, String displayName) {
        if (displayName == null || displayName.isBlank()) return false;
        return editTemplate(templateKey, (yaml, b) -> { yaml.set(b + ".displayName", displayName.trim()); return true; });
    }
    @Override public boolean setEnabled(String templateKey, boolean enabled) {
        return editTemplate(templateKey, (yaml, b) -> { yaml.set(b + ".enabled", enabled); return true; });
    }
    @Override public boolean setMaxPlayers(String templateKey, int maxPlayers) {
        if (maxPlayers < 1) return false;
        return editTemplate(templateKey, (yaml, b) -> { yaml.set(b + ".maxPlayers", maxPlayers); return true; });
    }
    @Override public boolean setTeamSizeMax(String templateKey, int teamSizeMax) {
        if (teamSizeMax < 1) return false;
        return editTemplate(templateKey, (yaml, b) -> { yaml.set(b + ".teamSizeMax", teamSizeMax); return true; });
    }
    @Override public boolean setIconKey(String templateKey, String iconKey) {
        if (iconKey == null || iconKey.isBlank()) return false;
        return editTemplate(templateKey, (yaml, b) -> { yaml.set(b + ".iconKey", iconKey.trim()); return true; });
    }
    @Override public boolean setMinimumPercentage(String templateKey, int percentage) {
        int pct = Math.max(1, Math.min(100, percentage));
        return editTemplate(templateKey, (yaml, b) -> { yaml.set(b + ".limbo.minimumPercentage", pct); return true; });
    }
    @Override public boolean setPublishGameRule(String templateKey, String ruleName, boolean value) {
        if (ruleName == null || ruleName.isBlank()) return false;
        return editTemplate(templateKey, (yaml, b) -> { yaml.set(b + ".publish.gamerules." + ruleName.trim(), value); return true; });
    }
    @Override public boolean publishTemplate(String templateKey) {
        boolean ok = editTemplate(templateKey, (yaml, b) -> {
            yaml.set(b + ".status", "PUBLISHED");
            yaml.set(b + ".enabled", true);
            yaml.set(b + ".version", Math.max(0, yaml.getInt(b + ".version", 0)) + 1);
            return true;
        });
        if (ok) {
            plugin.lobbyService().refreshLobbiesAfterPublish(templateKey);
        }
        return ok;
    }

    public boolean writeLimboZone(String templateKey, Location pos1, Location pos2) {
        if (templateKey == null || templateKey.isBlank()
                || pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return false;
        }
        synchronized (fileLock) {
            try {
                YamlConfiguration yaml = loadFresh();
                String b = base(templateKey);
                if (!yaml.isConfigurationSection(b)) {
                    ensureMinimalWorldSection(yaml, templateKey, pos1.getWorld().getName());
                }
                writeCorner(yaml, b + ".limbo.zone.pos1", pos1);
                writeCorner(yaml, b + ".limbo.zone.pos2", pos2);
                save(yaml);
                notifyIronLegacyReload();
                return true;
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save limbo zone to worlds.yml: " + ex.getMessage());
                return false;
            }
        }
    }

    public boolean writeLimboSpawn(String templateKey, Location loc) {
        if (templateKey == null || templateKey.isBlank() || loc == null || loc.getWorld() == null) {
            return false;
        }
        synchronized (fileLock) {
            try {
                YamlConfiguration yaml = loadFresh();
                String b = base(templateKey);
                if (!yaml.isConfigurationSection(b)) {
                    ensureMinimalWorldSection(yaml, templateKey, loc.getWorld().getName());
                }
                yaml.set(b + ".limbo.world", loc.getWorld().getName());
                yaml.set(b + ".limbo.x", loc.getX());
                yaml.set(b + ".limbo.y", loc.getY());
                yaml.set(b + ".limbo.z", loc.getZ());
                yaml.set(b + ".limbo.yaw", loc.getYaw());
                yaml.set(b + ".limbo.pitch", loc.getPitch());
                save(yaml);
                notifyIronLegacyReload();
                return true;
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save limbo spawn to worlds.yml: " + ex.getMessage());
                return false;
            }
        }
    }

    public record LimboZone(Location pos1, Location pos2) {
        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null || pos1 == null || pos2 == null) {
                return false;
            }
            if (pos1.getWorld() == null || pos2.getWorld() == null) {
                return false;
            }
            if (!pos1.getWorld().getName().equalsIgnoreCase(loc.getWorld().getName())) {
                return false;
            }
            if (!pos2.getWorld().getName().equalsIgnoreCase(loc.getWorld().getName())) {
                return false;
            }
            double minX = Math.min(pos1.getX(), pos2.getX());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());
            return loc.getX() >= minX && loc.getX() <= maxX
                    && loc.getY() >= minY && loc.getY() <= maxY
                    && loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }
    }
}

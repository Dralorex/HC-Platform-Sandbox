package com.hollowcrown.hungergames.managers;

import com.hollowcrown.api.platform.MatchInstanceReservation;
import com.hollowcrown.api.template.TemplateEntry;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.integration.EnvironmentalHazardsBridge;
import com.hollowcrown.hungergames.util.Yaml;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Manages premade game template worlds and worlds.yml registry. */
public class TemplateWorldManager {
    /** Hub lobby folders are {@code <templateKey>-L<index>}; used when SPI registry was cleared (e.g. plugman). */
    private static final Pattern HUB_LOBBY_WORLD = Pattern.compile("^(.+)-L(\\d+)$");

    private static final String FILE = "worlds.yml";
    private static final String SNAPSHOT_ROOT = "template-snapshots";
    private static final String MANAGED_WORLD_ROOT = "hcil-worlds";
    private static final String MANAGED_INSTANCE_DIR = "instances";
    /** Hub editable template worlds live under the server container at this relative path. */
    public static final String MANAGED_TEMPLATE_ROOT = "hch-worlds/templates";
    private final HungerGamesPlugin plugin;
    private FileConfiguration worldsCfg;
    private final Map<String, Map<UUID, EditorInventorySnapshot>> editorInventories = new HashMap<>();
    /** Hub lobby build mode before entering template edit (only set when leaving lobby for a template). */
    private final Map<UUID, Boolean> hubBuildModeBeforeTemplateEdit = new HashMap<>();
    /** Last edit position per template key per player (resume on next editor teleport). */
    private final Map<String, Map<UUID, Location>> editorResumeByTemplate = new HashMap<>();

    public TemplateWorldManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        worldsCfg = Yaml.loadOrCreate(plugin, FILE);
        if (!worldsCfg.isConfigurationSection("worlds")) {
            worldsCfg.createSection("worlds");
        }
        if (!worldsCfg.isConfigurationSection("instances")) {
            worldsCfg.createSection("instances");
        }
        save();
    }

    public void save() {
        Yaml.save(plugin, FILE, worldsCfg);
    }

    public FileConfiguration config() {
        return worldsCfg;
    }

    public boolean existsTemplate(String name) {
        if (name == null || name.isBlank()) return false;
        return worldsCfg.isConfigurationSection("worlds." + name.toLowerCase(Locale.ROOT));
    }

    public List<String> listTemplateNames() {
        ConfigurationSection sec = worldsCfg.getConfigurationSection("worlds");
        if (sec == null) return List.of();
        return new ArrayList<>(sec.getKeys(false));
    }

    public List<TemplateInfo> listTemplateInfos() {
        List<TemplateInfo> out = new ArrayList<>();
        ConfigurationSection sec = worldsCfg.getConfigurationSection("worlds");
        if (sec == null) return out;
        for (String key : sec.getKeys(false)) {
            String base = "worlds." + key;
            String worldName = worldsCfg.getString(base + ".worldName", key);
            String displayName = worldsCfg.getString(base + ".displayName", worldName);
            String status = worldsCfg.getString(base + ".status", "EDIT");
            boolean enabled = worldsCfg.getBoolean(base + ".enabled", true);
            int maxPlayers = worldsCfg.getInt(base + ".maxPlayers", 100);
            int teamSizeMax = worldsCfg.getInt(base + ".teamSizeMax", 5);
            String iconKey = worldsCfg.getString(base + ".iconKey", "default");
            int version = worldsCfg.getInt(base + ".version", 0);
            out.add(new TemplateInfo(key, worldName, displayName, status, enabled, maxPlayers, teamSizeMax, iconKey, version));
        }
        if (plugin.getConfig().getBoolean("integrations.mergeHubTemplatesIntoList", true)) {
            Set<String> present = new LinkedHashSet<>();
            for (TemplateInfo ti : out) {
                present.add(ti.key().toLowerCase(Locale.ROOT));
            }
            plugin.hollowTemplateRegistry().ifPresent(reg -> {
                for (TemplateEntry e : reg.list()) {
                    if (!"iron-legacy".equalsIgnoreCase(e.mechanicId())) {
                        continue;
                    }
                    String k = e.templateKey().toLowerCase(Locale.ROOT);
                    if (present.contains(k)) {
                        continue;
                    }
                    boolean has = e.hasSourceWorld();
                    int cap = e.queueMaxUnitSize();
                    out.add(new TemplateInfo(
                            k,
                            e.sourceWorldName(),
                            e.effectiveDisplayName(),
                            has ? "PUBLISHED" : "EDIT",
                            has,
                            Math.max(cap, 24),
                            Math.max(1, Math.min(cap, 64)),
                            "default",
                            0));
                    present.add(k);
                }
            });
        }
        return out;
    }

    public TemplateInfo getTemplateByNameKey(String keyOrWorld) {
        if (keyOrWorld == null || keyOrWorld.isBlank()) return null;
        String key = keyOrWorld.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        if (worldsCfg.isConfigurationSection(base)) {
            String worldName = worldsCfg.getString(base + ".worldName", keyOrWorld);
            String displayName = worldsCfg.getString(base + ".displayName", worldName);
            String status = worldsCfg.getString(base + ".status", "EDIT");
            boolean enabled = worldsCfg.getBoolean(base + ".enabled", true);
            int maxPlayers = worldsCfg.getInt(base + ".maxPlayers", 100);
            int teamSizeMax = worldsCfg.getInt(base + ".teamSizeMax", 5);
            String iconKey = worldsCfg.getString(base + ".iconKey", "default");
            int version = worldsCfg.getInt(base + ".version", 0);
            return new TemplateInfo(key, worldName, displayName, status, enabled, maxPlayers, teamSizeMax, iconKey, version);
        }
        return getTemplateByWorldName(keyOrWorld);
    }

    public TemplateInfo getTemplateByWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;
        for (TemplateInfo info : listTemplateInfos()) {
            if (info.worldName().equalsIgnoreCase(worldName)) return info;
        }
        return null;
    }

    public boolean canUseWorldForMatch(String worldName) {
        TemplateInfo info = getTemplateByWorldName(worldName);
        if (info == null) return true; // non-template worlds unchanged
        return "PUBLISHED".equalsIgnoreCase(info.status()) && info.enabled();
    }

    /**
     * Hub {@code iron-legacy} template keys (for tab-complete / targeted import).
     */
    public List<String> listHubIronLegacyTemplateKeys() {
        return plugin.hollowTemplateRegistry()
                .map(reg -> reg.list().stream()
                        .filter(e -> "iron-legacy".equalsIgnoreCase(e.mechanicId()))
                        .map(e -> e.templateKey().toLowerCase(Locale.ROOT))
                        .sorted()
                        .toList())
                .orElseGet(List::of);
    }

    public record HubYamlImportReport(
            int newRowsWouldWrite,
            int newRowsWritten,
            int skippedAlreadyPresent,
            int skippedNoHubSource,
            int metadataSyncWouldWrite,
            int metadataSyncWritten,
            boolean dryRun,
            List<String> messages
    ) {
        public HubYamlImportReport {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    /**
     * Writes {@code worlds.<key>} rows from Hub {@link com.hollowcrown.api.template.TemplateRegistry}
     * for {@code iron-legacy} templates, optionally syncing display/max/team from Hub for keys already in YAML.
     *
     * @param onlyTemplateKey optional normalized key; when null/blank, all Hub iron-legacy templates are considered
     */
    public HubYamlImportReport importFromHubTemplateRegistry(boolean dryRun, boolean syncMetadata, String onlyTemplateKey) {
        List<String> messages = new ArrayList<>();
        if (plugin.hollowTemplateRegistry().isEmpty()) {
            return new HubYamlImportReport(0, 0, 0, 0, 0, 0, dryRun,
                    List.of("No Hub TemplateRegistry (is HollowCrownHub enabled?)"));
        }
        var reg = plugin.hollowTemplateRegistry().get();
        int newWould = 0;
        int newWritten = 0;
        int skippedExisting = 0;
        int skippedNoSource = 0;
        int syncWould = 0;
        int syncWritten = 0;
        boolean filter = onlyTemplateKey != null && !onlyTemplateKey.isBlank();
        String want = filter ? onlyTemplateKey.trim().toLowerCase(Locale.ROOT) : null;
        boolean matchedFilter = !filter;
        for (TemplateEntry e : reg.list()) {
            if (!"iron-legacy".equalsIgnoreCase(e.mechanicId())) {
                continue;
            }
            String key = e.templateKey().toLowerCase(Locale.ROOT);
            if (filter && !key.equals(want)) {
                continue;
            }
            matchedFilter = true;
            if (!e.hasSourceWorld()) {
                skippedNoSource++;
                messages.add("- skip " + key + " (Hub has no source world / setsource)");
                continue;
            }
            if (!existsTemplate(key)) {
                if (dryRun) {
                    newWould++;
                    messages.add("+ would import " + key + " (worldName=" + e.sourceWorldName().trim() + ")");
                } else {
                    applyNewTemplateRowFromHub(e);
                    newWritten++;
                    messages.add("+ imported " + key);
                }
                continue;
            }
            if (syncMetadata) {
                if (dryRun) {
                    syncWould++;
                    messages.add("~ would sync metadata " + key);
                } else {
                    syncWorldsYmlFromHubMetadata(key, e);
                    syncWritten++;
                    messages.add("~ synced metadata " + key);
                }
            } else {
                skippedExisting++;
                messages.add("- unchanged (already in worlds.yml) " + key);
            }
        }
        if (filter && !matchedFilter && want != null) {
            messages.add("No Hub iron-legacy template matched: " + want);
        }
        return new HubYamlImportReport(newWould, newWritten, skippedExisting, skippedNoSource, syncWould, syncWritten, dryRun, messages);
    }

    private void applyNewTemplateRowFromHub(TemplateEntry e) {
        String key = e.templateKey().toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        if (!worldsCfg.isConfigurationSection(base)) {
            worldsCfg.createSection(base);
        }
        String worldName = e.sourceWorldName().trim();
        worldsCfg.set(base + ".worldName", worldName);
        worldsCfg.set(base + ".displayName", e.effectiveDisplayName());
        worldsCfg.set(base + ".enabled", true);
        worldsCfg.set(base + ".status", "EDIT");
        int cap = e.queueMaxUnitSize();
        worldsCfg.set(base + ".maxPlayers", Math.max(cap, 24));
        worldsCfg.set(base + ".teamSizeMax", Math.max(1, Math.min(cap, 64)));
        worldsCfg.set(base + ".iconKey", "default");
        worldsCfg.set(base + ".limbo.minimumPercentage", clampPercentage(80));
        worldsCfg.set(base + ".serverTarget", "");
        worldsCfg.set(base + ".version", 0);
        worldsCfg.set(base + ".publishedAt", 0L);
        worldsCfg.set(base + ".publishRules.doDaylightCycle", true);
        worldsCfg.set(base + ".publishRules.doWeatherCycle", false);
        worldsCfg.set(base + ".hubImported", true);
        save();
    }

    private void syncWorldsYmlFromHubMetadata(String key, TemplateEntry e) {
        String base = "worlds." + key;
        if (!worldsCfg.isConfigurationSection(base)) {
            return;
        }
        worldsCfg.set(base + ".displayName", e.effectiveDisplayName());
        int cap = e.queueMaxUnitSize();
        worldsCfg.set(base + ".maxPlayers", Math.max(cap, 24));
        worldsCfg.set(base + ".teamSizeMax", Math.max(1, Math.min(cap, 64)));
        worldsCfg.set(base + ".enabled", e.hasSourceWorld());
        if (worldsCfg.getBoolean(base + ".hubImported", false) && e.hasSourceWorld()) {
            worldsCfg.set(base + ".worldName", e.sourceWorldName().trim());
        }
        save();
    }

    private MatchInstance registerRunningMatchInstanceFromHubWorld(String key, MatchInstanceReservation r) {
        World w = Bukkit.getWorld(r.worldName());
        if (w == null) {
            plugin.getLogger().warning("Hub match world not loaded: " + r.worldName());
            return null;
        }
        applyTemplateDefaults(w);
        String sourceWorldName = worldsCfg.getString("worlds." + key + ".worldName", key);
        String id = "inst-" + System.currentTimeMillis() + "-" + Math.abs((int) (Math.random() * 100000));
        String ibase = "instances." + id;
        worldsCfg.set(ibase + ".id", id);
        worldsCfg.set(ibase + ".templateKey", key);
        worldsCfg.set(ibase + ".templateWorldName", sourceWorldName);
        worldsCfg.set(ibase + ".worldName", r.worldName());
        worldsCfg.set(ibase + ".status", "RUNNING");
        worldsCfg.set(ibase + ".createdAt", System.currentTimeMillis());
        worldsCfg.set(ibase + ".hubProvisioned", true);
        save();
        return new MatchInstance(id, key, sourceWorldName, r.worldName(), System.currentTimeMillis());
    }

    public MatchInstance createMatchInstanceFromTemplate(String templateName) {
        var runtime = plugin.matchInstanceRuntime();
        if (runtime.isPresent()) {
            var hub = runtime.get().createMatchInstance(templateName).orElse(null);
            if (hub != null) {
                return new MatchInstance(hub.id(), hub.templateKey(), hub.templateWorldName(), hub.worldName(), hub.createdAt());
            }
        }
        if (templateName == null || templateName.isBlank()) return null;
        String key = templateName.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        if (!worldsCfg.isConfigurationSection(base)) return null;

        if (plugin.getConfig().getBoolean("integrations.preferHubMatchInstanceProvisioning", false)
                && plugin.hollowGamePlatform() != null) {
            try {
                Optional<MatchInstanceReservation> res = plugin.hollowGamePlatform().createMatchWorld(templateName);
                if (res.isPresent()) {
                    MatchInstance hubInst = registerRunningMatchInstanceFromHubWorld(key, res.get());
                    if (hubInst != null) {
                        plugin.getLogger().info("Match instance from Hub: " + hubInst.worldName() + " template=" + templateName);
                        return hubInst;
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().fine("Hub createMatchWorld fell back to local clone: " + t.getMessage());
            }
        }

        String sourceWorldName = worldsCfg.getString(base + ".worldName", templateName);
        Path primarySource = resolveMatchInstanceSourceFolder(key);
        Path fallbackSource = worldFolder(sourceWorldName);
        if (!Files.exists(primarySource)) {
            plugin.getLogger().warning("Instance source missing for template " + templateName
                    + " (checked snapshot + world folder). primary=" + primarySource + " fallback=" + fallbackSource);
            return null;
        }

        String id = "inst-" + System.currentTimeMillis() + "-" + Math.abs((int) (Math.random() * 100000));
        String worldName = managedInstanceWorldName(key, System.currentTimeMillis());
        Path targetWorld = worldFolder(worldName);

        plugin.getLogger().info("Creating match instance for template " + templateName + " from source folder: " + primarySource);

        try {
            copyDirectory(primarySource, targetWorld);
        } catch (IOException ex) {
            plugin.getLogger().warning("Instance copy failed from " + primarySource + " -> " + targetWorld + ": " + ex.getMessage());
            plugin.getLogger().log(Level.WARNING, "Instance copy stack trace", ex);
            if (!primarySource.equals(fallbackSource) && Files.exists(fallbackSource)) {
                try {
                    if (Files.exists(targetWorld)) deleteDirectory(targetWorld);
                    copyDirectory(fallbackSource, targetWorld);
                    plugin.getLogger().info("Instance copy succeeded using fallback source: " + fallbackSource);
                } catch (IOException ex2) {
                    plugin.getLogger().warning("Fallback instance copy also failed from " + fallbackSource + ": " + ex2.getMessage());
                    try { if (Files.exists(targetWorld)) deleteDirectory(targetWorld); } catch (IOException ignored) {}
                    return null;
                }
            } else {
                try { if (Files.exists(targetWorld)) deleteDirectory(targetWorld); } catch (IOException ignored) {}
                return null;
            }
        }

        World w = Bukkit.createWorld(new WorldCreator(worldName));
        if (w == null) {
            try { deleteDirectory(targetWorld); } catch (IOException ignored) {}
            return null;
        }
        applyTemplateDefaults(w);

        String ibase = "instances." + id;
        worldsCfg.set(ibase + ".id", id);
        worldsCfg.set(ibase + ".templateKey", key);
        worldsCfg.set(ibase + ".templateWorldName", sourceWorldName);
        worldsCfg.set(ibase + ".worldName", worldName);
        worldsCfg.set(ibase + ".status", "RUNNING");
        worldsCfg.set(ibase + ".createdAt", System.currentTimeMillis());
        save();
        return new MatchInstance(id, key, sourceWorldName, worldName, System.currentTimeMillis());
    }

    public boolean destroyMatchInstanceByWorld(String worldName) {
        var runtime = plugin.matchInstanceRuntime();
        if (runtime.isPresent()) {
            boolean ok = runtime.get().destroyMatchInstanceByWorld(worldName);
            if (ok) {
                String id = findInstanceIdByWorldName(worldName);
                if (id != null) {
                    worldsCfg.set("instances." + id + ".status", "CLEANED");
                    worldsCfg.set("instances." + id + ".cleanedAt", System.currentTimeMillis());
                    save();
                }
            }
            return ok;
        }
        if (worldName == null || worldName.isBlank()) return false;
        String id = findInstanceIdByWorldName(worldName);
        if (id == null) return false;

        EnvironmentalHazardsBridge.onMatchWorldDestroyed(worldName);

        World w = Bukkit.getWorld(worldName);
        if (w != null) {
            if (!w.getPlayers().isEmpty()) return false;
            if (!Bukkit.unloadWorld(w, false)) return false;
        }

        boolean deleted = deleteWorldFolder(worldName);
        if (!deleted) return false;

        worldsCfg.set("instances." + id + ".status", "CLEANED");
        worldsCfg.set("instances." + id + ".cleanedAt", System.currentTimeMillis());
        save();
        return true;
    }

    public boolean isManagedInstanceWorld(String worldName) {
        return findInstanceIdByWorldName(worldName) != null;
    }

    /** Crash-safe cleanup for stale runtime instances left from previous runs. */
    public RecoveryReport recoverStaleInstances() {
        ConfigurationSection sec = worldsCfg.getConfigurationSection("instances");
        if (sec == null) return new RecoveryReport(0, 0, 0, 0);

        int scanned = 0;
        int cleaned = 0;
        int skipped = 0;
        int failed = 0;

        for (String id : sec.getKeys(false)) {
            scanned++;
            String base = "instances." + id;
            String status = worldsCfg.getString(base + ".status", "RUNNING");
            String worldName = worldsCfg.getString(base + ".worldName", "");
            if (worldName.isBlank()) {
                worldsCfg.set(base + ".status", "INVALID");
                worldsCfg.set(base + ".recoveredAt", System.currentTimeMillis());
                failed++;
                continue;
            }
            if ("CLEANED".equalsIgnoreCase(status)) {
                skipped++;
                continue;
            }

            boolean ok = destroyMatchInstanceByWorld(worldName);
            if (ok) {
                worldsCfg.set(base + ".status", "CLEANED");
                worldsCfg.set(base + ".recoveredAt", System.currentTimeMillis());
                cleaned++;
            } else {
                World w = Bukkit.getWorld(worldName);
                if (w != null && !w.getPlayers().isEmpty()) {
                    worldsCfg.set(base + ".status", "SKIPPED_PLAYERS_PRESENT");
                    worldsCfg.set(base + ".recoveredAt", System.currentTimeMillis());
                    skipped++;
                } else {
                    worldsCfg.set(base + ".status", "RECOVERY_FAILED");
                    worldsCfg.set(base + ".recoveredAt", System.currentTimeMillis());
                    failed++;
                }
            }
        }
        save();
        return new RecoveryReport(scanned, cleaned, skipped, failed);
    }

    public List<String> summaryLines() {
        List<String> out = new ArrayList<>();
        ConfigurationSection sec = worldsCfg.getConfigurationSection("worlds");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            out.add("\u00A7eNo templates registered.");
            return out;
        }
        out.add("\u00A76Templates:");
        for (String key : sec.getKeys(false)) {
            String base = "worlds." + key;
            String name = worldsCfg.getString(base + ".worldName", key);
            String status = worldsCfg.getString(base + ".status", "EDIT");
            int ver = worldsCfg.getInt(base + ".version", 0);
            boolean enabled = worldsCfg.getBoolean(base + ".enabled", true);
            out.add("\u00A7e- \u00A7f" + name + "\u00A77 status=\u00A7b" + status + "\u00A77 v=\u00A7a" + ver + "\u00A77 enabled=\u00A7a" + enabled);
        }
        return out;
    }

    public boolean setDisplayName(String templateName, String displayName) {
        if (templateName == null || templateName.isBlank() || displayName == null || displayName.isBlank()) return false;
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) return false;
        worldsCfg.set(base + ".displayName", displayName);
        save();
        return true;
    }

    public boolean setEnabled(String templateName, boolean enabled) {
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) return false;
        worldsCfg.set(base + ".enabled", enabled);
        save();
        return true;
    }

    public boolean setMaxPlayers(String templateName, int maxPlayers) {
        if (maxPlayers < 1) return false;
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) return false;
        worldsCfg.set(base + ".maxPlayers", maxPlayers);
        save();
        return true;
    }

    public boolean setTeamSizeMax(String templateName, int size) {
        if (size < 1) return false;
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) return false;
        worldsCfg.set(base + ".teamSizeMax", size);
        save();
        return true;
    }

    public boolean setIconKey(String templateName, String iconKey) {
        if (iconKey == null || iconKey.isBlank()) return false;
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) return false;
        worldsCfg.set(base + ".iconKey", iconKey.trim());
        save();
        return true;
    }

    public int minimumPercentage(String templateName) {
        String base = basePath(templateName);
        return clampPercentage(worldsCfg.getInt(base + ".limbo.minimumPercentage", 80));
    }

    public boolean setMinimumPercentage(String templateName, int value) {
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) return false;
        worldsCfg.set(base + ".limbo.minimumPercentage", clampPercentage(value));
        save();
        return true;
    }

    public Optional<LimboZone> limboZone(String templateName) {
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) return Optional.empty();
        if (!worldsCfg.contains(base + ".limbo.zone.pos1.x")) return Optional.empty();
        if (!worldsCfg.contains(base + ".limbo.zone.pos2.x")) return Optional.empty();
        Location p1 = readCorner(base + ".limbo.zone.pos1");
        Location p2 = readCorner(base + ".limbo.zone.pos2");
        if (p1 == null || p2 == null) return Optional.empty();
        return Optional.of(new LimboZone(p1, p2));
    }

    public boolean setLimboZone(String templateName, Location pos1, Location pos2) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) return false;
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) {
            registerTemplate(templateName, pos1.getWorld().getName());
        }
        writeCorner(base + ".limbo.zone.pos1", pos1);
        writeCorner(base + ".limbo.zone.pos2", pos2);
        save();
        return true;
    }

    public boolean isInsideLimboZone(String templateName, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        var zone = limboZone(templateName).orElse(null);
        if (zone == null) return false;
        return zone.contains(loc);
    }

    public boolean setLimboSpawn(String templateName, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) {
            registerTemplate(templateName, loc.getWorld().getName());
        }
        worldsCfg.set(base + ".limbo.world", loc.getWorld().getName());
        worldsCfg.set(base + ".limbo.x", loc.getX());
        worldsCfg.set(base + ".limbo.y", loc.getY());
        worldsCfg.set(base + ".limbo.z", loc.getZ());
        worldsCfg.set(base + ".limbo.yaw", loc.getYaw());
        worldsCfg.set(base + ".limbo.pitch", loc.getPitch());
        save();
        return true;
    }

    public String resolveServerTarget(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return "";
        String base = basePath(templateKey);
        String configured = worldsCfg.getString(base + ".serverTarget", "");
        if (configured != null && !configured.isBlank()) return configured.trim();
        return "game-" + templateKey.toLowerCase(Locale.ROOT);
    }

    /**
     * Loads the editor/template world for a key. Uses {@code worlds.yml} when present; otherwise bootstraps from
     * Hollow Crown Hub {@code TemplateRegistry} for {@code iron-legacy} maps created via {@code /hch template create}.
     */
    public World getTemplateWorld(String templateName) {
        if (templateName == null || templateName.isBlank()) return null;
        String key = templateName.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        String worldName;
        if (worldsCfg.isConfigurationSection(base)) {
            worldName = worldsCfg.getString(base + ".worldName", templateName);
        } else {
            Optional<TemplateEntry> hubRow = plugin.hollowTemplateRegistry()
                    .flatMap(r -> r.findByTemplateKey(key))
                    .filter(e -> "iron-legacy".equalsIgnoreCase(e.mechanicId()) && e.hasSourceWorld());
            if (hubRow.isEmpty()) {
                return null;
            }
            worldName = hubRow.get().sourceWorldName();
            registerTemplate(key, worldName);
            String disp = hubRow.get().effectiveDisplayName();
            if (disp != null && !disp.isBlank()) {
                worldsCfg.set(base + ".displayName", disp);
                save();
            }
        }
        World w = Bukkit.getWorld(worldName);
        if (w != null) return w;
        return Bukkit.createWorld(new WorldCreator(worldName));
    }

    /** Call before TP into template when coming from hub — records hub build mode for restore on /hub return. */
    public void captureHubBuildModeIfLeavingLobby(Player p) {
        if (p == null) return;
        if (!plugin.lobby().isInLobby(p)) return;
        hubBuildModeBeforeTemplateEdit.put(p.getUniqueId(), plugin.lobby().isBuildMode(p));
    }

    /** Save standing location in this template before returning to hub so the next edit session can resume here. */
    public void rememberResumeSpotLeavingTemplate(Player p) {
        if (p == null || p.getWorld() == null) return;
        TemplateInfo info = getTemplateByWorldName(p.getWorld().getName());
        if (info == null) return;
        Location loc = p.getLocation().clone();
        editorResumeByTemplate
                .computeIfAbsent(info.key().toLowerCase(Locale.ROOT), k -> new HashMap<>())
                .put(p.getUniqueId(), loc);
    }

    /**
     * Restore lobby build mode vs normal hub state after teleporting home from template edit.
     * Does nothing if the player did not enter template from hub (no captured preference).
     */
    public void restoreLobbyAfterLeavingTemplate(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        Boolean hadBuildMode = hubBuildModeBeforeTemplateEdit.remove(id);
        if (hadBuildMode == null) return;
        if (hadBuildMode) {
            plugin.lobby().setBuildMode(p, true);
        } else {
            plugin.lobby().applyLobbyAfterTemplateExitWithoutPriorBuildMode(p);
        }
    }

    private Location resolveStoredResume(UUID playerId, String templateKeyLower, World w) {
        Map<UUID, Location> m = editorResumeByTemplate.get(templateKeyLower);
        if (m == null) return null;
        Location resume = m.get(playerId);
        if (resume == null || resume.getWorld() == null || !w.equals(resume.getWorld())) return null;
        return resume.clone();
    }

    /** Teleport into template world spawn or last remembered edit location for this player. */
    public void teleportToTemplateResumeOrSpawn(Player p, World w, String templateKeyForResume) {
        if (p == null || w == null || templateKeyForResume == null) return;
        Location spawn = w.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        String key = templateKeyForResume.toLowerCase(Locale.ROOT);
        Location resume = resolveStoredResume(p.getUniqueId(), key, w);
        if (resume != null) {
            p.teleport(resume);
        } else {
            p.teleport(spawn);
        }
    }

    public boolean teleportEditor(Player p, String templateName) {
        captureHubBuildModeIfLeavingLobby(p);
        World w = getTemplateWorld(templateName);
        if (w == null) return false;
        applyEditorWorldRules(w);
        teleportToTemplateResumeOrSpawn(p, w, templateName);
        return true;
    }

    public void applyEditorWorldRules(World w) {
        if (w == null) return;
        w.setTime(1000L);
        w.setStorm(false);
        w.setThundering(false);
        try {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        } catch (Throwable ignored) {}
    }

    public void saveEditorInventoryIfTemplate(Player p) {
        if (p == null) return;
        World w = p.getWorld();
        if (w == null) return;
        var info = getTemplateByWorldName(w.getName());
        if (info == null) return;
        String key = info.key().toLowerCase(Locale.ROOT);
        editorInventories.computeIfAbsent(key, k -> new HashMap<>())
                .put(p.getUniqueId(), captureInventory(p));
    }

    public boolean restoreEditorInventory(Player p, String templateName) {
        if (p == null || templateName == null || templateName.isBlank()) return false;
        String key = templateName.toLowerCase(Locale.ROOT);
        Map<UUID, EditorInventorySnapshot> byPlayer = editorInventories.get(key);
        if (byPlayer == null) return false;
        EditorInventorySnapshot snap = byPlayer.get(p.getUniqueId());
        if (snap == null) return false;
        applyInventory(p, snap);
        return true;
    }

    /**
     * Creates a minimal {@code worlds.yml} row when a folder exists on disk but no row is registered yet.
     */
    public boolean ensureTemplateRow(String templateKeyOrWorldFolder) {
        if (templateKeyOrWorldFolder == null || templateKeyOrWorldFolder.isBlank()) {
            return false;
        }
        String raw = templateKeyOrWorldFolder.trim().replace('\\', '/');
        while (raw.startsWith("./")) {
            raw = raw.substring(2);
        }
        String keyGuess = leafKeyFromPath(raw).toLowerCase(Locale.ROOT);
        if (existsTemplate(keyGuess) || existsTemplate(raw.toLowerCase(Locale.ROOT))) {
            return true;
        }
        for (String k : listTemplateNames()) {
            if (k.equalsIgnoreCase(raw) || k.equalsIgnoreCase(keyGuess)) {
                return true;
            }
            String wn = worldsCfg.getString("worlds." + k + ".worldName", k);
            if (wn != null && (wn.equalsIgnoreCase(raw) || wn.equalsIgnoreCase(keyGuess)
                    || wn.replace('\\', '/').equalsIgnoreCase(raw))) {
                return true;
            }
        }
        Optional<String> found = resolveExistingWorldFolderPath(raw);
        if (found.isEmpty()) {
            return false;
        }
        String worldName = found.get();
        String key = leafKeyFromPath(worldName).toLowerCase(Locale.ROOT);
        registerTemplate(key, worldName);
        return true;
    }

    /**
     * Scans {@link #MANAGED_TEMPLATE_ROOT} for world folders missing from {@code worlds.yml}
     * and registers them as EDIT rows. Drop a map into {@code hch-worlds/templates/<name>}
     * then discover (or edit by leaf name) to make it usable.
     *
     * @return newly registered template keys (leaf names)
     */
    public List<String> discoverManagedTemplateFolders() {
        Path root = worldFolder(MANAGED_TEMPLATE_ROOT);
        List<String> registered = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return registered;
        }
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (!Files.isDirectory(child) || !looksLikeMinecraftWorld(child)) {
                    continue;
                }
                String leaf = child.getFileName().toString();
                String worldName = MANAGED_TEMPLATE_ROOT + "/" + leaf;
                String key = leaf.toLowerCase(Locale.ROOT);
                if (existsTemplate(key)) {
                    continue;
                }
                boolean alreadyMapped = false;
                for (TemplateInfo info : listTemplateInfos()) {
                    if (info.worldName() != null
                            && info.worldName().replace('\\', '/').equalsIgnoreCase(worldName)) {
                        alreadyMapped = true;
                        break;
                    }
                }
                if (alreadyMapped) {
                    continue;
                }
                registerTemplate(key, worldName);
                registered.add(key);
                plugin.getLogger().info("Discovered managed template folder: " + worldName + " (key=" + key + ")");
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed scanning " + MANAGED_TEMPLATE_ROOT + ": " + ex.getMessage());
        }
        return registered;
    }

    /** Resolves an on-disk world folder path under the server container, preferring managed templates. */
    public Optional<String> resolveExistingWorldFolderPath(String keyOrPath) {
        if (keyOrPath == null || keyOrPath.isBlank()) {
            return Optional.empty();
        }
        String raw = keyOrPath.trim().replace('\\', '/');
        while (raw.startsWith("./")) {
            raw = raw.substring(2);
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(raw);
        String leaf = leafKeyFromPath(raw);
        String managed = MANAGED_TEMPLATE_ROOT + "/" + leaf;
        if (!raw.equalsIgnoreCase(managed)) {
            candidates.add(managed);
        }
        if (!raw.equalsIgnoreCase(leaf)) {
            candidates.add(leaf);
        }
        for (String c : candidates) {
            Path p = worldFolder(c);
            if (Files.isDirectory(p) && looksLikeMinecraftWorld(p)) {
                return Optional.of(c.replace('\\', '/'));
            }
        }
        return Optional.empty();
    }

    private static String leafKeyFromPath(String path) {
        String n = path.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        return slash >= 0 ? n.substring(slash + 1) : n;
    }

    private static boolean looksLikeMinecraftWorld(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return false;
        }
        if (Files.isRegularFile(folder.resolve("level.dat"))
                || Files.isRegularFile(folder.resolve("level.dat_old"))) {
            return true;
        }
        return Files.isDirectory(folder.resolve("region"))
                || Files.isDirectory(folder.resolve("DIM-1"))
                || Files.isDirectory(folder.resolve("DIM1"));
    }

    public boolean publishTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) return false;
        String key = templateName.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        if (!worldsCfg.isConfigurationSection(base)) return false;
        World w = getTemplateWorld(templateName);
        if (w != null) {
            applyPublishRulesToWorld(key, w);
        }
        if (createSnapshot(templateName, "publish") == null) return false;
        int ver = worldsCfg.getInt(base + ".version", 0) + 1;
        worldsCfg.set(base + ".status", "PUBLISHED");
        worldsCfg.set(base + ".version", ver);
        worldsCfg.set(base + ".publishedAt", System.currentTimeMillis());
        save();
        EnvironmentalHazardsBridge.onTemplatePublished(key);
        return true;
    }

    public boolean deleteTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) return false;
        String key = templateName.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        if (!worldsCfg.isConfigurationSection(base)) return false;

        String worldName = worldsCfg.getString(base + ".worldName", templateName);
        if (createSnapshot(templateName, "delete") == null) return false;
        World w = Bukkit.getWorld(worldName);
        if (w != null) {
            if (!w.getPlayers().isEmpty()) return false;
            boolean unloaded = Bukkit.unloadWorld(w, true);
            if (!unloaded) return false;
        }

        boolean deleted = deleteWorldFolder(worldName);
        if (!deleted) return false;

        EnvironmentalHazardsBridge.onTemplateDeleted(key);
        worldsCfg.set(base, null);
        save();
        return true;
    }

    public String createSnapshot(String templateName, String reason) {
        if (templateName == null || templateName.isBlank()) return null;
        String key = templateName.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        if (!worldsCfg.isConfigurationSection(base)) return null;

        String worldName = worldsCfg.getString(base + ".worldName", templateName);
        Path source = worldFolder(worldName);
        if (!Files.exists(source)) return null;

        String stamp = String.valueOf(System.currentTimeMillis());
        String snapshotId = stamp + "-" + sanitizeSnapshotReason(reason);
        Path target = snapshotsDir(templateName).resolve(snapshotId);

        try {
            copyDirectory(source, target);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed snapshot for template " + templateName + ": " + ex.getMessage());
            return null;
        }

        worldsCfg.set(base + ".lastSnapshotId", snapshotId);
        worldsCfg.set(base + ".lastSnapshotAt", System.currentTimeMillis());
        save();
        return snapshotId;
    }

    public boolean rollbackTemplate(String templateName, String snapshotIdOrLatest) {
        if (templateName == null || templateName.isBlank()) return false;
        String key = templateName.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        if (!worldsCfg.isConfigurationSection(base)) return false;

        String worldName = worldsCfg.getString(base + ".worldName", templateName);
        World w = Bukkit.getWorld(worldName);
        if (w != null) {
            if (!w.getPlayers().isEmpty()) return false;
            boolean unloaded = Bukkit.unloadWorld(w, false);
            if (!unloaded) return false;
        }

        String snapshotId = resolveSnapshotId(templateName, snapshotIdOrLatest);
        if (snapshotId == null) return false;
        Path snapshotPath = snapshotsDir(templateName).resolve(snapshotId);
        if (!Files.exists(snapshotPath)) return false;

        Path worldPath = worldFolder(worldName);
        try {
            deleteDirectory(worldPath);
            copyDirectory(snapshotPath, worldPath);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed rollback for template " + templateName + ": " + ex.getMessage());
            return false;
        }

        worldsCfg.set(base + ".status", "EDIT");
        worldsCfg.set(base + ".rolledBackFromSnapshot", snapshotId);
        worldsCfg.set(base + ".rolledBackAt", System.currentTimeMillis());
        save();
        return true;
    }

    public List<String> listSnapshotIds(String templateName) {
        if (templateName == null || templateName.isBlank()) return List.of();
        Path dir = snapshotsDir(templateName);
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private void registerTemplate(String templateKey, String worldName) {
        String key = templateKey.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        if (!worldsCfg.isConfigurationSection(base)) worldsCfg.createSection(base);
        worldsCfg.set(base + ".worldName", worldName);
        worldsCfg.set(base + ".displayName", worldsCfg.getString(base + ".displayName", templateKey));
        worldsCfg.set(base + ".enabled", true);
        worldsCfg.set(base + ".status", "EDIT");
        worldsCfg.set(base + ".maxPlayers", 100);
        worldsCfg.set(base + ".teamSizeMax", worldsCfg.getInt(base + ".teamSizeMax", 5));
        worldsCfg.set(base + ".iconKey", "default");
        worldsCfg.set(base + ".limbo.minimumPercentage", clampPercentage(worldsCfg.getInt(base + ".limbo.minimumPercentage", 80)));
        worldsCfg.set(base + ".serverTarget", worldsCfg.getString(base + ".serverTarget", ""));
        worldsCfg.set(base + ".version", worldsCfg.getInt(base + ".version", 0));
        worldsCfg.set(base + ".publishedAt", worldsCfg.getLong(base + ".publishedAt", 0L));
        worldsCfg.set(base + ".publishRules.doDaylightCycle", worldsCfg.getBoolean(base + ".publishRules.doDaylightCycle", true));
        worldsCfg.set(base + ".publishRules.doWeatherCycle", worldsCfg.getBoolean(base + ".publishRules.doWeatherCycle", false));
        save();
    }

    public boolean setPublishGameRule(String templateName, String ruleName, boolean value) {
        String base = basePath(templateName);
        if (!worldsCfg.isConfigurationSection(base)) return false;
        String rule = ruleName == null ? "" : ruleName.trim().toLowerCase(Locale.ROOT);
        if ("dodaylightcycle".equals(rule)) {
            worldsCfg.set(base + ".publishRules.doDaylightCycle", value);
        } else if ("doweathercycle".equals(rule)) {
            worldsCfg.set(base + ".publishRules.doWeatherCycle", value);
        } else {
            return false;
        }
        save();
        return true;
    }

    private void applyPublishRulesToWorld(String templateKey, World w) {
        if (w == null) return;
        String base = basePath(templateKey);
        boolean doDay = worldsCfg.getBoolean(base + ".publishRules.doDaylightCycle", true);
        boolean doWeather = worldsCfg.getBoolean(base + ".publishRules.doWeatherCycle", false);
        try {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, doDay);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, doWeather);
        } catch (Throwable ignored) {}
        w.setStorm(false);
        w.setThundering(false);
        if (!doDay) w.setTime(1000L);
    }

    public String findInstanceIdByWorldName(String worldName) {
        ConfigurationSection sec = worldsCfg.getConfigurationSection("instances");
        if (sec == null) return null;
        for (String id : sec.getKeys(false)) {
            String wn = worldsCfg.getString("instances." + id + ".worldName", "");
            if (wn.equalsIgnoreCase(worldName)) return id;
        }
        return null;
    }

    /** Template key for a runtime match instance world, or template editor world if registered. */
    public Optional<String> resolveTemplateKeyForWorld(String runtimeWorldName) {
        if (runtimeWorldName == null || runtimeWorldName.isBlank()) return Optional.empty();
        String id = findInstanceIdByWorldName(runtimeWorldName);
        if (id != null) {
            String tk = worldsCfg.getString("instances." + id + ".templateKey", "");
            if (tk != null && !tk.isBlank()) return Optional.of(tk);
        }
        TemplateInfo inf = getTemplateByWorldName(runtimeWorldName);
        if (inf != null) {
            return Optional.of(inf.key());
        }
        World lw = Bukkit.getWorld(runtimeWorldName);
        if (lw != null) {
            Optional<String> hub = plugin.hubManagedLobbies().templateKeyForWorld(lw);
            if (hub.isPresent()) {
                return hub;
            }
        }
        return hubLobbyNameToRegisteredTemplateKey(runtimeWorldName);
    }

    private Optional<String> hubLobbyNameToRegisteredTemplateKey(String worldFolderName) {
        if (worldFolderName == null || worldFolderName.isBlank()) {
            return Optional.empty();
        }
        var m = HUB_LOBBY_WORLD.matcher(worldFolderName);
        if (!m.matches()) {
            return Optional.empty();
        }
        String base = m.group(1);
        if (base == null || base.isBlank()) {
            return Optional.empty();
        }
        return plugin.hollowTemplateRegistry()
                .flatMap(r -> r.findByTemplateKey(base.trim()))
                .filter(e -> "iron-legacy".equalsIgnoreCase(e.mechanicId()))
                .map(e -> e.templateKey().toLowerCase(Locale.ROOT));
    }

    private void applyTemplateDefaults(World w) {
        w.setAutoSave(true);
        w.setStorm(false);
        w.setThundering(false);
        w.setDifficulty(Difficulty.NORMAL);
        w.setTime(6000L);
        try {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            w.setGameRule(GameRule.KEEP_INVENTORY, false);
        } catch (Throwable ignored) {}
    }

    private boolean deleteWorldFolder(String worldName) {
        Path worldPath = worldFolder(worldName);
        if (!Files.exists(worldPath)) return true;
        try {
            deleteDirectory(worldPath);
            return !Files.exists(worldPath);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed deleting world folder: " + worldName + " (" + ex.getMessage() + ")");
            return false;
        }
    }

    private Path worldFolder(String worldName) {
        File root = plugin.getServer().getWorldContainer();
        return new File(root, worldName).toPath();
    }

    private String managedInstanceWorldName(String templateKey, long stamp) {
        String safe = templateKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return MANAGED_WORLD_ROOT + "/" + MANAGED_INSTANCE_DIR + "/hg_inst_" + safe + "_" + stamp;
    }

    /**
     * For PUBLISHED templates, prefer the on-disk publish snapshot under the plugin data folder
     * ({@code template-snapshots/<key>/<timestamp>-publish/}) so cloning does not read region files
     * while the template world is still loaded (common Windows locking issue). Otherwise uses the
     * live world folder. Snapshots are not stored inside the world directory.
     */
    private Path resolveMatchInstanceSourceFolder(String templateKey) {
        String key = templateKey.toLowerCase(Locale.ROOT);
        String base = "worlds." + key;
        String worldName = worldsCfg.getString(base + ".worldName", key);
        Path live = worldFolder(worldName);

        String status = worldsCfg.getString(base + ".status", "EDIT");
        if (!"PUBLISHED".equalsIgnoreCase(status)) {
            return live;
        }

        String lastId = worldsCfg.getString(base + ".lastSnapshotId", "");
        if (lastId != null && !lastId.isBlank()) {
            Path snap = snapshotsDir(key).resolve(lastId);
            if (Files.exists(snap) && Files.isDirectory(snap)) return snap;
        }
        String latest = resolveSnapshotId(key, "latest");
        if (latest != null) {
            Path snap = snapshotsDir(key).resolve(latest);
            if (Files.exists(snap) && Files.isDirectory(snap)) return snap;
        }
        return live;
    }

    private Path snapshotsDir(String templateName) {
        return plugin.getDataFolder().toPath()
                .resolve(SNAPSHOT_ROOT)
                .resolve(templateName.toLowerCase(Locale.ROOT));
    }

    private String resolveSnapshotId(String templateName, String snapshotIdOrLatest) {
        if (snapshotIdOrLatest != null && !snapshotIdOrLatest.isBlank() && !"latest".equalsIgnoreCase(snapshotIdOrLatest)) {
            return snapshotIdOrLatest.trim();
        }
        List<String> ids = listSnapshotIds(templateName);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String sanitizeSnapshotReason(String reason) {
        if (reason == null || reason.isBlank()) return "manual";
        String cleaned = reason.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "-");
        return cleaned.isBlank() ? "manual" : cleaned;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path src : walk.toList()) {
                Path relative = source.relativize(src);
                Path dst = target.resolve(relative.toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    String fileName = src.getFileName().toString();
                    // Do not copy world identity/lock files into runtime instances.
                    if ("session.lock".equalsIgnoreCase(fileName) || "uid.dat".equalsIgnoreCase(fileName)) continue;
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        }
    }

    private String basePath(String templateName) {
        return "worlds." + templateName.toLowerCase(Locale.ROOT);
    }

    private int clampPercentage(int value) {
        return Math.max(1, Math.min(100, value));
    }

    private Location readCorner(String path) {
        String worldName = worldsCfg.getString(path + ".world", "");
        World world = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = worldsCfg.getDouble(path + ".x");
        double y = worldsCfg.getDouble(path + ".y");
        double z = worldsCfg.getDouble(path + ".z");
        return new Location(world, x, y, z);
    }

    private void writeCorner(String path, Location loc) {
        worldsCfg.set(path + ".world", loc.getWorld().getName());
        worldsCfg.set(path + ".x", loc.getX());
        worldsCfg.set(path + ".y", loc.getY());
        worldsCfg.set(path + ".z", loc.getZ());
    }

    public record LimboZone(Location pos1, Location pos2) {
        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null || pos1 == null || pos2 == null) return false;
            if (pos1.getWorld() == null || pos2.getWorld() == null) return false;
            if (!pos1.getWorld().getName().equalsIgnoreCase(loc.getWorld().getName())) return false;
            if (!pos2.getWorld().getName().equalsIgnoreCase(loc.getWorld().getName())) return false;
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

    public record TemplateInfo(
            String key,
            String worldName,
            String displayName,
            String status,
            boolean enabled,
            int maxPlayers,
            int teamSizeMax,
            String iconKey,
            int version
    ) {}

    public record MatchInstance(
            String id,
            String templateKey,
            String templateWorldName,
            String worldName,
            long createdAt
    ) {}

    public record RecoveryReport(
            int scanned,
            int cleaned,
            int skipped,
            int failed
    ) {}

    private EditorInventorySnapshot captureInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        return new EditorInventorySnapshot(
                cloneItems(inv.getContents()),
                cloneItems(inv.getArmorContents()),
                cloneItems(inv.getExtraContents()),
                cloneItem(inv.getItemInOffHand()),
                inv.getHeldItemSlot()
        );
    }

    private void applyInventory(Player p, EditorInventorySnapshot snap) {
        PlayerInventory inv = p.getInventory();
        inv.setContents(cloneItems(snap.contents()));
        inv.setArmorContents(cloneItems(snap.armor()));
        inv.setExtraContents(cloneItems(snap.extra()));
        inv.setItemInOffHand(cloneItem(snap.offHand()));
        inv.setHeldItemSlot(Math.max(0, Math.min(8, snap.heldSlot())));
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) return null;
        ItemStack[] out = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) out[i] = cloneItem(source[i]);
        return out;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private record EditorInventorySnapshot(
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack[] extra,
            ItemStack offHand,
            int heldSlot
    ) {}
}


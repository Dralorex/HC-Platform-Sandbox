package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.util.WorldEnvoyStorage;
import com.hollowcrown.hungergames.util.Yaml;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.file.FileConfiguration;

import com.hollowcrown.hungergames.crates.CrateType;
import com.hollowcrown.hungergames.crates.FallingCrate;

/** Built-in envoy system: falling crates with parachutes, fireworks, and loot packages. */
public class EnvoyManager {
    private final HungerGamesPlugin plugin;

    // spawn locations (block centers)
    private final List<Location> spawnLocations = new ArrayList<>();

    // Active block locations for packages (block coordinates, not centers)
    private final Set<Location> activePackages = new HashSet<>();
    private final Map<Location, PackageData> packageData = new HashMap<>();
    
    // TODO: Migrate from ArmorStand to blocks
    private final Map<Location, ArmorStand> activeCrates = new HashMap<>();
    
    public static class PackageData {
        public final CrateType type;
        public final String tier;
        public PackageData(CrateType type, String tier) { this.type = type; this.tier = tier; }
    }

    private boolean running = false;
    /** While envoys are ticking, YAML reads use this template's scoped {@code world-envoys/<key>/envoys.yml}. */
    private volatile String runtimeYamlTemplateKey;
    private BukkitRunnable ticker;
    private BukkitRunnable borderChecker; // Check for packages outside border
    private final Map<Location, Long> borderViolations = new HashMap<>(); // Location -> time when detected

    public EnvoyManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        plugin.saveResource("envoys.yml", false);
        plugin.saveResource("replenish.yml", false);
    }

    public boolean isRunning() { return running; }

    public String getRuntimeYamlTemplateKey() {
        return runtimeYamlTemplateKey;
    }

    /** Spawn markers for the ticking envoy session (populated by {@link #start(String)}). */
    public List<Location> getSpawnLocations() {
        return Collections.unmodifiableList(spawnLocations);
    }

    public List<Location> getSpawnLocations(String templateKey) {
        return Collections.unmodifiableList(readSpawnLocationsFromYaml(templateKey));
    }

    public void addLocation(Location l, String templateKey) {
        WorldEnvoyStorage.ensureEnvoys(plugin, templateKey);
        Location c = l.getBlock().getLocation().add(0.5, 0, 0.5);
        FileConfiguration yml = Yaml.loadOrCreate(plugin, WorldEnvoyStorage.envoysPath(templateKey));
        List<Map<String, Object>> list = loadLocationsList(yml);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", c.getWorld() == null ? "world" : c.getWorld().getName());
        m.put("x", c.getX());
        m.put("y", c.getY());
        m.put("z", c.getZ());
        list.add(m);
        yml.set("locations", list);
        Yaml.save(plugin, WorldEnvoyStorage.envoysPath(templateKey), yml);
        if (running && templateKey.equalsIgnoreCase(runtimeYamlTemplateKey)) {
            spawnLocations.clear();
            spawnLocations.addAll(readSpawnLocationsFromYaml(templateKey));
        }
    }

    public void clearLocations(String templateKey) {
        WorldEnvoyStorage.ensureEnvoys(plugin, templateKey);
        FileConfiguration yml = Yaml.loadOrCreate(plugin, WorldEnvoyStorage.envoysPath(templateKey));
        yml.set("locations", new ArrayList<>());
        Yaml.save(plugin, WorldEnvoyStorage.envoysPath(templateKey), yml);
        if (running && templateKey.equalsIgnoreCase(runtimeYamlTemplateKey)) spawnLocations.clear();
    }

    public boolean removeNearest(Location ref, String templateKey) {
        List<Location> locs = readSpawnLocationsFromYaml(templateKey);
        if (locs.isEmpty()) return false;
        Location best = null;
        double bd = Double.MAX_VALUE;
        for (Location l : locs) {
            if (!Objects.equals(l.getWorld(), ref.getWorld())) continue;
            double d = l.distanceSquared(ref);
            if (d < bd) {
                bd = d;
                best = l;
            }
        }
        if (best == null) return false;
        final Location rm = best;
        WorldEnvoyStorage.ensureEnvoys(plugin, templateKey);
        FileConfiguration yml = Yaml.loadOrCreate(plugin, WorldEnvoyStorage.envoysPath(templateKey));
        List<Map<String, Object>> list = loadLocationsList(yml);
        list.removeIf(m -> locationMatches(m, rm));
        yml.set("locations", list);
        Yaml.save(plugin, WorldEnvoyStorage.envoysPath(templateKey), yml);
        if (running && templateKey.equalsIgnoreCase(runtimeYamlTemplateKey)) {
            spawnLocations.removeIf(sl ->
                    sl.getWorld().equals(rm.getWorld())
                            && sl.getBlockX() == rm.getBlockX()
                            && sl.getBlockY() == rm.getBlockY()
                            && sl.getBlockZ() == rm.getBlockZ());
        }
        return true;
    }

    private static boolean locationMatches(Map<String, Object> m, Location target) {
        try {
            String world = Objects.toString(m.get("world"), "");
            double x = Double.parseDouble(Objects.toString(m.get("x")));
            double y = Double.parseDouble(Objects.toString(m.get("y")));
            double z = Double.parseDouble(Objects.toString(m.get("z")));
            World w = Bukkit.getWorld(world);
            return w != null && target.getWorld().equals(w)
                    && target.getBlockX() == (int) Math.floor(x)
                    && target.getBlockY() == (int) Math.floor(y)
                    && target.getBlockZ() == (int) Math.floor(z);
        } catch (Exception e) {
            return false;
        }
    }

    private List<Map<String, Object>> loadLocationsList(FileConfiguration yml) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) yml.get("locations");
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private List<Location> readSpawnLocationsFromYaml(String templateKey) {
        WorldEnvoyStorage.ensureEnvoys(plugin, templateKey);
        FileConfiguration yml = Yaml.loadOrCreate(plugin, WorldEnvoyStorage.envoysPath(templateKey));
        List<Location> out = new ArrayList<>();
        List<Map<String, Object>> list = (List<Map<String, Object>>) yml.get("locations");
        if (list == null) return out;
        for (Map<String, Object> m : list) {
            try {
                String world = Objects.toString(m.get("world"), "");
                double x = Double.parseDouble(Objects.toString(m.get("x")));
                double y = Double.parseDouble(Objects.toString(m.get("y")));
                double z = Double.parseDouble(Objects.toString(m.get("z")));
                World w = Bukkit.getWorld(world);
                if (w != null) out.add(new Location(w, x, y, z));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public FileConfiguration yamlForTemplate(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return Yaml.loadOrCreate(plugin, "envoys.yml");
        }
        WorldEnvoyStorage.ensureEnvoys(plugin, templateKey);
        return Yaml.loadOrCreate(plugin, WorldEnvoyStorage.envoysPath(templateKey));
    }

    public void saveYamlTemplate(String templateKey, FileConfiguration cfg) {
        if (templateKey == null || templateKey.isBlank()) {
            Yaml.save(plugin, "envoys.yml", cfg);
            return;
        }
        Yaml.save(plugin, WorldEnvoyStorage.envoysPath(templateKey), cfg);
    }

    private FileConfiguration yamlForContext(Location loc) {
        if (loc != null && loc.getWorld() != null) {
            Optional<String> tk = plugin.templates().resolveTemplateKeyForWorld(loc.getWorld().getName());
            if (tk.isPresent()) return yamlForTemplate(tk.get());
        }
        return getYaml();
    }

    /** Begin envoy waves using scoped {@code envoys.yml} for this template. */
    public void start(String templateKey) {
        if (running) return;
        WorldEnvoyStorage.ensureEnvoys(plugin, templateKey);
        runtimeYamlTemplateKey = templateKey;
        spawnLocations.clear();
        spawnLocations.addAll(readSpawnLocationsFromYaml(templateKey));
        if (spawnLocations.isEmpty()) {
            runtimeYamlTemplateKey = null;
            Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                    "[HCIronLegacy] No envoy spawn locations configured for this map.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        running = true;
        long intervalSec = getYaml().getLong("timing.spawnIntervalSeconds", 180);
        final long nextWaveDelay = getYaml().getLong("timing.nextWaveDelaySeconds", 5) * 20L; // 5 seconds default
        
        ticker = new BukkitRunnable() {
            @Override public void run() {
                int min = getYaml().getInt("wave.minPerWave", 3);
                int max = getYaml().getInt("wave.maxPerWave", 10);
                int count = ThreadLocalRandom.current().nextInt(min, max + 1);
                spawnWave(count);
                
                // Spawn random forager drops if enabled (after a short delay from regular wave)
                if (getYaml().getBoolean("wave.randomForager.enabled", false)) {
                    new BukkitRunnable() {
                        @Override public void run() {
                            int minForager = getYaml().getInt("wave.randomForager.minDrops", 1);
                            int maxForager = getYaml().getInt("wave.randomForager.maxDrops", 3);
                            int foragerCount = ThreadLocalRandom.current().nextInt(minForager, maxForager + 1);
                            spawnRandomForagerDrops(foragerCount);
                        }
                    }.runTaskLater(plugin, nextWaveDelay);
                }
            }
        };
        ticker.runTaskTimer(plugin, 0L, intervalSec * 20L);
        
        // Start border checker - monitors packages for border violations
        borderChecker = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long removalDelay = 5000L; // 5 seconds in milliseconds
                
                for (Location loc : new ArrayList<>(activePackages)) {
                    if (loc.getWorld() == null) continue;
                    WorldBorder wb = loc.getWorld().getWorldBorder();
                    if (!withinBorder(loc)) {
                        // Package is outside border
                        if (!borderViolations.containsKey(loc)) {
                            // First time detected - start timer
                            borderViolations.put(loc, currentTime);
                        } else {
                            // Check if 5 seconds have passed
                            long violationTime = borderViolations.get(loc);
                            if (currentTime - violationTime >= removalDelay) {
                                // Remove the package
                                removePackage(loc);
                                borderViolations.remove(loc);
                            }
                        }
                    } else {
                        // Package is back inside border - remove from violations
                        borderViolations.remove(loc);
                    }
                }
            }
        };
        borderChecker.runTaskTimer(plugin, 0L, 20L); // Check every second
        
        Bukkit.broadcast(net.kyori.adventure.text.Component.text("[HCIronLegacy] Envoys started.", net.kyori.adventure.text.format.NamedTextColor.AQUA));
    }

    public void stop() {
        if (!running) return;
        running = false;
        runtimeYamlTemplateKey = null;
        if (ticker != null) ticker.cancel();
        if (borderChecker != null) borderChecker.cancel();
        borderViolations.clear();
        clearCrates();
        Bukkit.broadcast(net.kyori.adventure.text.Component.text("[HCIronLegacy] Envoys stopped.", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
    }

    /** Manual wave while envoys are stopped; uses scoped YAML for this template. */
    public void spawnWave(String templateKey, int count) {
        WorldEnvoyStorage.ensureEnvoys(plugin, templateKey);
        spawnWave0(readSpawnLocationsFromYaml(templateKey), yamlForTemplate(templateKey), count);
    }

    public void spawnWave(int count) {
        List<Location> bases = !spawnLocations.isEmpty()
                ? spawnLocations
                : (runtimeYamlTemplateKey != null ? readSpawnLocationsFromYaml(runtimeYamlTemplateKey) : List.of());
        spawnWave0(bases, getYaml(), count);
    }

    private void spawnWave0(List<Location> bases, FileConfiguration cfg, int count) {
        if (bases.isEmpty()) return;
        List<Location> pool = new ArrayList<>();
        for (Location base : bases) {
            Location adj = adjustUnderCanopy(base.clone());
            if (withinBorder(adj)) {
                // Check for overlap - don't use locations with active packages within 3 blocks
                boolean tooClose = false;
                Location adjBlock = adj.getBlock().getLocation();
                for (Location existing : activePackages) {
                    if (existing.getWorld() == adjBlock.getWorld() && 
                        existing.distanceSquared(adjBlock) < 9.0) { // 3 block radius
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) pool.add(adj);
            }
        }
        Collections.shuffle(pool);

        // Only spawn one crate per drop point - limit to available spawn points
        final int targetCount = Math.min(Math.max(0, count), pool.size());
        final int batch = Math.max(1, (int) cfg.getLong("timing.spawnBatchSize", 10));
        final FileConfiguration visualCfg = cfg;

        new BukkitRunnable() {
            int index = 0;
            int spawned = 0;
            @Override public void run() {
                int budget = batch;
                while (budget-- > 0 && index < pool.size() && spawned < targetCount) {
                    Location base = pool.get(index++);
                    
                    // Double-check overlap - one spawn per point
                    Location baseBlock = base.getBlock().getLocation();
                    boolean tooClose = false;
                    for (Location existing : activePackages) {
                        if (existing.getWorld() == baseBlock.getWorld() && 
                            existing.distanceSquared(baseBlock) < 9.0) {
                            tooClose = true;
                            break;
                        }
                    }
                    if (tooClose) continue;
                    
                    // Spawn directly above the drop point (no radius)
                    Location finalLoc = base.clone();
                    if (!withinBorder(finalLoc)) continue;
                    
                    CrateType type = pickTypeWithPriority(cfg);
                    String tier = pickTier(cfg);
                    double fall = cfg.getDouble("timing.fallSpeedBlocksPerTick", 0.08);

                    // Create falling crate - beep from actual crate location as it falls
                    final Location[] beepLoc = {finalLoc.clone()}; // Track current location
                    FallingCrate fc = new FallingCrate(plugin, type, finalLoc.clone(), fall, landed -> {
                        placeEnvoyBlock(landed, type, tier);
                        scheduleBeep(landed, false); // Beep from landed location
                    }, visualCfg);
                    // Set beep callback to update location for beeping
                    fc.setBeepCallback((currentLoc) -> {
                        beepLoc[0] = currentLoc.clone(); // Update location as crate falls
                    });
                    // Start beeping from the crate's current location (updates dynamically)
                    scheduleBeepFromDynamicLocation(beepLoc, true);
                    fc.start();
                    spawned++;
                }
                if (spawned >= targetCount || index >= pool.size()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void spawnRandomForagerDrops(String templateKey, int count) {
        WorldEnvoyStorage.ensureEnvoys(plugin, templateKey);
        FileConfiguration cfg = yamlForTemplate(templateKey);
        World w = readSpawnLocationsFromYaml(templateKey).stream()
                .map(Location::getWorld)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (w == null) {
            Player any = Bukkit.getOnlinePlayers().stream().findAny().orElse(null);
            if (any != null) w = any.getWorld();
        }
        if (w == null) return;
        spawnRandomForager0(w, cfg, count);
    }

    public void spawnRandomForagerDrops(int count) {
        World w = null;
        for (Location loc : spawnLocations) {
            if (loc.getWorld() != null) {
                w = loc.getWorld();
                break;
            }
        }
        if (w == null) {
            Player any = Bukkit.getOnlinePlayers().stream().findAny().orElse(null);
            if (any != null) w = any.getWorld();
        }
        if (w == null) return;
        spawnRandomForager0(w, getYaml(), count);
    }

    private void spawnRandomForager0(World finalW, FileConfiguration cfg, int count) {
        WorldBorder wb = finalW.getWorldBorder();
        final Location center = wb.getCenter();
        final double halfSize = wb.getSize() / 2.0;

        final int targetCount = Math.max(0, count);
        final int batch = Math.max(1, (int) cfg.getLong("timing.spawnBatchSize", 10));
        final FileConfiguration visualCfgRf = cfg;

        new BukkitRunnable() {
            int spawned = 0;
            int attempts = 0;
            static final int MAX_ATTEMPTS_PER_SPAWN = 50;

            @Override public void run() {
                int budget = batch;
                while (budget-- > 0 && spawned < targetCount) {
                    attempts++;
                    if (attempts > targetCount * MAX_ATTEMPTS_PER_SPAWN) {
                        cancel();
                        break;
                    }

                    final double x = center.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * halfSize * 2;
                    final double z = center.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * halfSize * 2;

                    Location testLoc = new Location(finalW, x, finalW.getMaxHeight() - 1, z);
                    Location finalLoc = adjustUnderCanopy(testLoc);
                    if (!withinBorder(finalLoc)) continue;

                    final Location blockLoc = finalLoc.getBlock().getLocation();
                    boolean tooClose = false;
                    for (Location existing : activePackages) {
                        if (existing.getWorld() == blockLoc.getWorld()
                                && existing.distanceSquared(blockLoc) < 9.0) {
                            tooClose = true;
                            break;
                        }
                    }
                    if (tooClose) continue;

                    CrateType type = pickTypeWithPriority(cfg);
                    String tier = pickTier(cfg);
                    double fall = cfg.getDouble("timing.fallSpeedBlocksPerTick", 0.08);

                    scheduleBeep(finalLoc, true);
                    FallingCrate fc = new FallingCrate(plugin, type, finalLoc.clone(), fall, landed -> {
                        placeEnvoyBlock(landed, type, tier);
                        scheduleBeep(landed, false);
                    }, visualCfgRf);
                    fc.start();
                    spawned++;
                    attempts = 0;
                }

                if (spawned >= targetCount) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean withinBorder(Location l) {
        WorldBorder wb = l.getWorld().getWorldBorder();
        double half = wb.getSize() / 2.0;
        Location c = wb.getCenter();
        return Math.abs(l.getX() - c.getX()) <= half && Math.abs(l.getZ() - c.getZ()) <= half;
    }

    private Location adjustUnderCanopy(Location l) {
        World w = l.getWorld();
        int y = w.getMaxHeight() - 1;
        int minY = w.getMinHeight() + 1;
        // descend until we hit first solid that is not leaves/log, and ensure air above
        for (int yy = y; yy >= minY; yy--) {
            Material m = w.getBlockAt(l.getBlockX(), yy, l.getBlockZ()).getType();
            if (m.isAir()) continue;
            if (isLeafOrLog(m)) continue;
            return new Location(w, l.getX(), yy + 1, l.getZ());
        }
        return l;
    }

    private boolean isLeafOrLog(Material m) {
        String n = m.name();
        return n.endsWith("_LEAVES") || n.endsWith("_LOG") || n.contains("MANGROVE_ROOTS");
    }

    private void scheduleBeep(Location base, boolean falling) {
        long interval = 40L; // 2 seconds for the pattern
        double maxVolumeRadius = getYaml().getDouble("timing.audioFalloff.maxVolumeRadius", 5.0);
        double minVolumeRadius = getYaml().getDouble("timing.audioFalloff.minVolumeRadius", 50.0);
        double maxVolume = getYaml().getDouble("timing.audioFalloff.maxVolume", 1.0);
        if (falling) {
            // For falling crates, apply multiplier to both radii
            double multiplier = Math.max(1.0, getYaml().getDouble("timing.fallingBeepRadiusMultiplier", 1.5));
            maxVolumeRadius *= multiplier;
            minVolumeRadius *= multiplier;
        }
        final double finalMaxRadius = maxVolumeRadius;
        final double finalMinRadius = minVolumeRadius;
        final double finalMaxVol = Math.max(0.0, Math.min(1.0, maxVolume));
        final Location finalBase = base.getBlock().getLocation().add(0.5,0,0.5);
        final Location key = finalBase.getBlock().getLocation();
        new BukkitRunnable() {
            int noteCount = 0;
            @Override public void run() {
                if (finalBase.getWorld() == null) { cancel(); return; }
                boolean exists = activePackages.contains(key);
                if (!falling && !exists) { cancel(); return; }
                if (falling && exists) { cancel(); return; }
                
                // Play 3 F# notes 0.2s apart
                for (int i = 0; i < 3; i++) {
                    final int delay = i * 4; // 0.2s = 4 ticks
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (finalBase.getWorld() == null) return;
                            for (Player pl : finalBase.getWorld().getPlayers()) {
                                if (pl.getWorld() != finalBase.getWorld()) continue;
                                double distance = pl.getLocation().distance(finalBase);
                                
                                // Calculate volume based on distance
                                float volume = 0.0f;
                                if (distance <= finalMaxRadius) {
                                    // At max volume within this radius
                                    volume = (float) finalMaxVol;
                                } else if (distance <= finalMinRadius) {
                                    // Taper from max to 0 between max and min radius
                                    double range = finalMinRadius - finalMaxRadius;
                                    double progress = (distance - finalMaxRadius) / range;
                                    volume = (float) (finalMaxVol * (1.0 - progress));
                                }
                                // Beyond minRadius, volume stays at 0
                                
                                if (volume > 0.01f) { // Only play if volume is audible
                                    // Use Sound API for volume control - play F# note (highest pitch)
                                    // F# note block sound at highest octave = pitch 2.0f
                                    pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, 
                                            (float) volume, 2.0f);
                                }
                            }
                        }
                    }.runTaskLater(plugin, delay);
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }
    
    private void scheduleBeepFromDynamicLocation(Location[] locationRef, boolean falling) {
        long interval = 40L; // 2 seconds for the pattern
        double maxVolumeRadius = getYaml().getDouble("timing.audioFalloff.maxVolumeRadius", 5.0);
        double minVolumeRadius = getYaml().getDouble("timing.audioFalloff.minVolumeRadius", 50.0);
        double maxVolume = getYaml().getDouble("timing.audioFalloff.maxVolume", 1.0);
        if (falling) {
            double multiplier = Math.max(1.0, getYaml().getDouble("timing.fallingBeepRadiusMultiplier", 1.5));
            maxVolumeRadius *= multiplier;
            minVolumeRadius *= multiplier;
        }
        final double finalMaxRadius = maxVolumeRadius;
        final double finalMinRadius = minVolumeRadius;
        final double finalMaxVol = Math.max(0.0, Math.min(1.0, maxVolume));
        new BukkitRunnable() {
            @Override public void run() {
                if (locationRef[0] == null || locationRef[0].getWorld() == null) { cancel(); return; }
                Location currentLoc = locationRef[0].getBlock().getLocation().add(0.5, 0, 0.5);
                // Check if crate has landed (package exists at location)
                boolean exists = activePackages.contains(currentLoc.getBlock().getLocation());
                if (!falling && !exists) { cancel(); return; }
                if (falling && exists) { cancel(); return; }
                
                // Play 3 F# notes 0.2s apart
                for (int i = 0; i < 3; i++) {
                    final int delay = i * 4;
                    final Location beepFrom = currentLoc.clone();
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (beepFrom.getWorld() == null) return;
                            for (Player pl : beepFrom.getWorld().getPlayers()) {
                                if (pl.getWorld() != beepFrom.getWorld()) continue;
                                double distance = pl.getLocation().distance(beepFrom);
                                
                                float volume = 0.0f;
                                if (distance <= finalMaxRadius) {
                                    volume = (float) finalMaxVol;
                                } else if (distance <= finalMinRadius) {
                                    double range = finalMinRadius - finalMaxRadius;
                                    double progress = (distance - finalMaxRadius) / range;
                                    volume = (float) (finalMaxVol * (1.0 - progress));
                                }
                                
                                if (volume > 0.01f) {
                                    pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.RECORDS, (float) volume, 2.0f);
                                }
                            }
                        }
                    }.runTaskLater(plugin, delay);
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    private void placeEnvoyBlock(Location landed, CrateType type, String tier) {
        Block b = landed.getBlock();
        if (!b.getType().isAir()) return;
        
        b.setType(type.displayBlock);
        BlockState state = b.getState();
        state.setMetadata("hcil_envoy", new FixedMetadataValue(plugin, true));
        state.setMetadata("hcil_unbreakable", new FixedMetadataValue(plugin, true));
        state.update();
        
        Location blockLoc = b.getLocation();
        activePackages.add(blockLoc);
        packageData.put(blockLoc, new PackageData(type, tier));
    }
    
    private CrateType pickType() {
        String s = getYaml().getString("types.default", "BLOCKS");
        try { return CrateType.valueOf(s.toUpperCase()); } catch (Exception ignored) { return CrateType.BLOCKS; }
    }
    
    private CrateType pickTypeWithPriority() {
        return pickTypeWithPriority(getYaml());
    }

    private CrateType pickTypeWithPriority(FileConfiguration y) {
        ConfigurationSection pri = y.getConfigurationSection("types.priority");
        if (pri == null) return CrateType.BLOCKS;

        List<CrateType> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;

        for (CrateType t : CrateType.values()) {
            double w = pri.getDouble(t.name(), 1.0);
            candidates.add(t);
            weights.add(w);
            total += w;
        }

        if (total == 0) return CrateType.BLOCKS;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return candidates.get(i);
        }
        return CrateType.BLOCKS;
    }

    private String pickTier() {
        return pickTier(getYaml());
    }

    private String pickTier(FileConfiguration y) {
        if (!y.getBoolean("tiers.enabled", false)) return "T1";
        List<String> allowed = y.getStringList("tiers.allowed");
        if (allowed.isEmpty()) return "T1";

        List<String> tiers = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;

        for (String tier : allowed) {
            double w = y.getDouble("tiers." + tier + ".weight", 1.0);
            tiers.add(tier);
            weights.add(w);
            total += w;
        }

        if (total == 0) return "T1";
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < tiers.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return tiers.get(i);
        }
        return tiers.get(0);
    }

    public boolean tryOpen(Player p, Location l) {
        Location blockLoc = l.getBlock().getLocation();
        PackageData data = packageData.remove(blockLoc);
        if (data == null || !activePackages.remove(blockLoc)) return false;
        
        Block b = blockLoc.getBlock();
        if (b.getType() != data.type.displayBlock) return false;
        
        // Remove the block
        b.setType(Material.AIR);

        FileConfiguration yLoot = yamlForContext(blockLoc);
        List<ItemStack> drops = rollPackageDropsForType(data.type, data.tier, yLoot);
        for (ItemStack it : drops) {
            HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(it);
            overflow.values().forEach(rem -> {
                var dropped = p.getWorld().dropItemNaturally(p.getLocation(), rem);
                HungerGamesPlugin.get().pickupGuard().guard(dropped, p.getUniqueId(), 15000L);
            });
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        
        String name = yLoot.getString("tiers." + data.tier + ".name", data.tier);
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aOpened §e" + data.type.name() + " " + name + " §aForager Crate!"));
        return true;
    }

    public void clearCrates() {
        for (ArmorStand as : activeCrates.values()) try { as.remove(); } catch (Throwable ignored) {}
        activeCrates.clear();
        for (Location loc : new ArrayList<>(activePackages)) {
            Block b = loc.getBlock();
            PackageData data = packageData.get(loc);
            if (data != null && b.getType() == data.type.displayBlock) {
                b.setType(Material.AIR);
            }
        }
        activePackages.clear();
        packageData.clear();
    }
    
    public Set<Location> getActivePackages() {
        return Collections.unmodifiableSet(activePackages);
    }
    
    public PackageData getPackageData(Location l) {
        return packageData.get(l.getBlock().getLocation());
    }
    
    public boolean removePackage(Location l) {
        Location blockLoc = l.getBlock().getLocation();
        PackageData data = packageData.remove(blockLoc);
        if (data != null && activePackages.remove(blockLoc)) {
            Block b = blockLoc.getBlock();
            if (b.getType() == data.type.displayBlock) {
                b.setType(Material.AIR);
                return true;
            }
        }
        return false;
    }
    
    public String getTierName(String tier) {
        return getYaml().getString("tiers." + tier + ".name", tier);
    }

    public String getTierName(String tier, Location context) {
        return yamlForContext(context).getString("tiers." + tier + ".name", tier);
    }

    /** True if this block is a placed envoy loot package (interaction opens; breaks are cancelled). */
    public boolean isActiveEnvoyPackage(Location l) {
        return packageData.containsKey(l.getBlock().getLocation());
    }

    // ---- packages per type ----
    public List<ItemStack> rollPackageDropsForType(CrateType type, String tier) {
        return rollPackageDropsForType(type, tier, getYaml());
    }

    public List<ItemStack> rollPackageDropsForType(CrateType type, String tier, Location context) {
        return rollPackageDropsForType(type, tier, yamlForContext(context));
    }

    public List<ItemStack> rollPackageDropsForType(CrateType type, String tier, FileConfiguration y) {
        ConfigurationSection tsec = y.getConfigurationSection("types." + type.name());
        if (tsec == null) {
            return List.of(new ItemStack(Material.IRON_INGOT, 3));
        }
        List<?> allPackages = y.getList("types." + type.name() + ".packages");
        if (allPackages == null || allPackages.isEmpty()) return List.of(new ItemStack(Material.IRON_INGOT, 3));
        
        // Filter packages by tier if tiers are enabled and tier is specified
        List<Map<?, ?>> eligiblePackages = new ArrayList<>();
        boolean tiersEnabled = y.getBoolean("tiers.enabled", false);
        
        for (Object pkgObj : allPackages) {
            if (!(pkgObj instanceof Map)) continue;
            Map<?, ?> pkg = (Map<?, ?>) pkgObj;
            
            // If tiers enabled and tier specified, only include matching packages
            if (tiersEnabled && tier != null && !tier.isEmpty()) {
                Object pkgTier = pkg.get("tier");
                String pkgTierStr = pkgTier != null ? String.valueOf(pkgTier) : null;
                if (!tier.equalsIgnoreCase(pkgTierStr)) continue;
            }
            
            eligiblePackages.add(pkg);
        }
        
        // Fallback to all packages if no tier match found
        if (eligiblePackages.isEmpty()) {
            for (Object pkgObj : allPackages) {
                if (pkgObj instanceof Map) eligiblePackages.add((Map<?, ?>) pkgObj);
            }
        }
        
        if (eligiblePackages.isEmpty()) return List.of(new ItemStack(Material.IRON_INGOT, 3));
        
        Map<?, ?> pick = eligiblePackages.get(ThreadLocalRandom.current().nextInt(eligiblePackages.size()));
        return extractItemsFromPackage(pick);
    }
    
    private List<ItemStack> rollPackageDrops(CrateType type) {
        ConfigurationSection tsec = getYaml().getConfigurationSection("types." + type.name());
        if (tsec == null) {
            // fallback: simple iron/food
            return List.of(new ItemStack(Material.IRON_INGOT, 3));
        }
        List<?> packages = getYaml().getList("types." + type.name() + ".packages");
        if (packages == null || packages.isEmpty()) return List.of(new ItemStack(Material.IRON_INGOT, 3));
        Object pickObj = packages.get(ThreadLocalRandom.current().nextInt(packages.size()));
        if (!(pickObj instanceof Map)) return List.of(new ItemStack(Material.IRON_INGOT, 3));
        Map<?, ?> pick = (Map<?, ?>) pickObj;
        return extractItemsFromPackage(pick);
    }
    
    private List<ItemStack> extractItemsFromPackage(Map<?, ?> pick) {
        Object itemsObj = pick.get("items");
        if (!(itemsObj instanceof List)) return List.of(new ItemStack(Material.IRON_INGOT, 3));
        List<?> items = (List<?>) itemsObj;
        List<ItemStack> out = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            try {
                Material mat = Material.valueOf(Objects.toString(m.get("material"), "STONE").toUpperCase());
                int min = Integer.parseInt(Objects.toString(m.get("min"), "1"));
                int max = Integer.parseInt(Objects.toString(m.get("max"), "1"));
                int amt = Math.max(1, ThreadLocalRandom.current().nextInt(min, max + 1));
                out.add(new ItemStack(mat, amt));
            } catch (Exception ignored) {}
        }
        if (out.isEmpty()) out.add(new ItemStack(Material.COAL, 1));
        return out;
    }

    private FileConfiguration getYaml() {
        if (runtimeYamlTemplateKey != null && !runtimeYamlTemplateKey.isBlank()) {
            WorldEnvoyStorage.ensureEnvoys(plugin, runtimeYamlTemplateKey);
            return Yaml.loadOrCreate(plugin, WorldEnvoyStorage.envoysPath(runtimeYamlTemplateKey));
        }
        return Yaml.loadOrCreate(plugin, "envoys.yml");
    }
}



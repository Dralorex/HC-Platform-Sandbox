package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.crates.CrateType;
import com.hollowcrown.hungergames.crates.FallingCrate;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Gift Drop system: packages fall near each player with red dropzone outlines. */
public class GiftDropManager {
    private final HungerGamesPlugin plugin;
    private final Set<Location> activeGiftDrops = new HashSet<>();
    private final Map<Location, GiftDropData> dropData = new HashMap<>();
    private final Map<UUID, BukkitRunnable> playerOutlines = new HashMap<>();

    public static class GiftDropData {
        public final CrateType type;
        public final String tier;
        public final String packageName;
        public GiftDropData(CrateType type, String tier, String packageName) {
            this.type = type; this.tier = tier; this.packageName = packageName;
        }
    }

    public GiftDropManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public Set<Location> getActiveDrops() { return Collections.unmodifiableSet(activeGiftDrops); }
    public GiftDropData getData(Location l) { return dropData.get(l); }
    public boolean removeDrop(Location l) {
        if (activeGiftDrops.remove(l)) {
            dropData.remove(l);
            Block b = l.getBlock();
            // Remove the block regardless of type (could be CHEST, CRAFTING_TABLE, SMOKER, SMITHING_TABLE, ENDER_CHEST, etc.)
            // Since it's in activeGiftDrops, we know it's a gift drop block
            b.setType(Material.AIR);
            return true;
        }
        return false;
    }

    public void spawnGiftDropsForPlayers(List<Player> players) {
        double radius = getYaml().getDouble("timing.giftDropRadiusFromPlayer", 5.0);
        
        for (Player p : players) {
            if (!p.isOnline() || p.getWorld() == null) continue;
            
            // Find ground level at player's location
            Location playerLoc = p.getLocation();
            Location groundLevel = findGroundLevel(playerLoc);
            
            // If player is underground, notify them
            boolean playerUnderground = playerLoc.getY() < groundLevel.getY() - 2;
            
            // Pick random location within radius at ground level
            int attempts = 20;
            Location dropLoc = null;
            while (attempts-- > 0 && dropLoc == null) {
                double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                double distance = ThreadLocalRandom.current().nextDouble() * radius;
                int x = groundLevel.getBlockX() + (int)(Math.cos(angle) * distance);
                int z = groundLevel.getBlockZ() + (int)(Math.sin(angle) * distance);
                
                Location test = new Location(p.getWorld(), x, groundLevel.getBlockY(), z);
                Location land = findGroundLevel(test);
                if (land != null && !activeGiftDrops.contains(land.getBlock().getLocation())) {
                    dropLoc = land;
                    break;
                }
            }
            
            if (dropLoc == null) continue;
            
            // Notify player if they're underground
            if (playerUnderground) {
                p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.YELLOW + "A package is waiting above ground for you at " +
                    dropLoc.getBlockX() + ", " + dropLoc.getBlockY() + ", " + dropLoc.getBlockZ() + "!"
                ));
            }
            
            CrateType type = pickTypeWithPriority();
            String tier = pickTier();
            String pkgName = pickPackageName(type, tier);
            
            // Show red dropzone outline
            showDropzoneOutline(p, dropLoc);
            
            // Spawn falling crate
            double fallSpeed = getYaml().getDouble("timing.fallSpeedBlocksPerTick", 0.08);
            FallingCrate fc = new FallingCrate(plugin, type, dropLoc.clone(), fallSpeed, landed -> {
                placeGiftBlock(landed, type, tier, pkgName);
            });
            fc.start();
        }
    }

    private void showDropzoneOutline(Player p, Location center) {
        // Red particle outline on the ground at landing spot
        BukkitRunnable outline = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline() || ticks++ > 200) { // 10 seconds
                    cancel();
                    playerOutlines.remove(p.getUniqueId());
                    return;
                }
                // Ground level at landing spot
                double groundY = center.getY();
                double r = 2.5; // Radius of the circle
                
                // Outer circle on the ground
                for (int i = 0; i < 64; i++) {
                    double a = i * (Math.PI * 2 / 64.0);
                    double x = center.getX() + r * Math.cos(a);
                    double z = center.getZ() + r * Math.sin(a);
                    // Spawn at ground level with slight offset for visibility
                    p.spawnParticle(Particle.DUST, x, groundY + 0.1, z, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.8f));
                }
                
                // Inner circle for better visibility
                double innerR = 1.5;
                for (int i = 0; i < 32; i++) {
                    double a = i * (Math.PI * 2 / 32.0);
                    double x = center.getX() + innerR * Math.cos(a);
                    double z = center.getZ() + innerR * Math.sin(a);
                    p.spawnParticle(Particle.DUST, x, groundY + 0.1, z, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 100, 100), 1.5f));
                }
                
                // Center marker (slightly elevated for visibility)
                p.spawnParticle(Particle.DUST, center.getX(), groundY + 0.2, center.getZ(), 3, 0.15, 0.1, 0.15, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0f));
            }
        };
        outline.runTaskTimer(plugin, 0L, 2L);
        playerOutlines.put(p.getUniqueId(), outline);
    }

    private Location findGroundLevel(Location start) {
        World w = start.getWorld();
        if (w == null) return null;
        
        int startY = start.getBlockY();
        int maxY = w.getMaxHeight() - 1;
        int minY = w.getMinHeight() + 1;
        
        // Search upward from start location to find ground level (surface)
        for (int yy = startY; yy <= maxY; yy++) {
            Block b = w.getBlockAt(start.getBlockX(), yy, start.getBlockZ());
            // Check if this is a solid block with air above it (ground level)
            if (b.getType().isSolid() && !b.getType().isAir()) {
                Block above = b.getRelative(org.bukkit.block.BlockFace.UP);
                Block above2 = above.getRelative(org.bukkit.block.BlockFace.UP);
                // Ground level: solid block with at least 2 blocks of air above (for falling crate)
                if (above.getType().isAir() && above2.getType().isAir()) {
                    return above.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        
        // If we didn't find ground level above, search downward from max height
        for (int yy = maxY; yy >= minY; yy--) {
            Block b = w.getBlockAt(start.getBlockX(), yy, start.getBlockZ());
            if (b.getType().isSolid() && !b.getType().isAir()) {
                Block above = b.getRelative(org.bukkit.block.BlockFace.UP);
                Block above2 = above.getRelative(org.bukkit.block.BlockFace.UP);
                if (above.getType().isAir() && above2.getType().isAir()) {
                    return above.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        
        return null;
    }

    private void placeGiftBlock(Location landed, CrateType type, String tier, String packageName) {
        Block b = landed.getBlock();
        if (!b.getType().isAir()) return;
        
        Material blockType = type.displayBlock;
        b.setType(blockType);
        
        // Make block unbreakable via metadata
        BlockState state = b.getState();
        state.setMetadata("hcil_giftdrop", new FixedMetadataValue(plugin, true));
        state.setMetadata("hcil_unbreakable", new FixedMetadataValue(plugin, true));
        state.update();
        
        activeGiftDrops.add(b.getLocation());
        dropData.put(b.getLocation(), new GiftDropData(type, tier, packageName));
        
        // Schedule beep
        scheduleGiftBeep(landed);
    }

    private Material getTypeBlock(CrateType type) {
        return type.displayBlock;
    }

    private CrateType pickTypeWithPriority() {
        FileConfiguration y = getYaml();
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
        
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return candidates.get(i);
        }
        return CrateType.BLOCKS;
    }

    private String pickTier() {
        FileConfiguration y = getYaml();
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

    private String pickPackageName(CrateType type, String tier) {
        return type.name() + " Gift Drop";
    }

    private void scheduleGiftBeep(Location l) {
        long interval = 40L; // 2 seconds
        double maxVolumeRadius = getYaml().getDouble("timing.audioFalloff.maxVolumeRadius", 5.0);
        double minVolumeRadius = getYaml().getDouble("timing.audioFalloff.minVolumeRadius", 50.0);
        double maxVolume = getYaml().getDouble("timing.audioFalloff.maxVolume", 1.0);
        final double finalMaxRadius = maxVolumeRadius;
        final double finalMinRadius = minVolumeRadius;
        final double finalMaxVol = Math.max(0.0, Math.min(1.0, maxVolume));
        final Location finalLoc = l.getBlock().getLocation().add(0.5, 0, 0.5);
        new BukkitRunnable() {
            @Override public void run() {
                if (!activeGiftDrops.contains(finalLoc.getBlock().getLocation())) {
                    cancel();
                    return;
                }
                if (finalLoc.getWorld() == null) { cancel(); return; }
                
                // Play 3 F# notes 0.2s apart
                for (int i = 0; i < 3; i++) {
                    final int delay = i * 4; // 0.2s = 4 ticks
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (finalLoc.getWorld() == null) return;
                            for (Player p : finalLoc.getWorld().getPlayers()) {
                                if (p.getWorld() != finalLoc.getWorld()) continue;
                                double distance = p.getLocation().distance(finalLoc);
                                
                                // Calculate volume based on distance
                                float volume = 0.0f;
                                if (distance <= finalMaxRadius) {
                                    volume = (float) finalMaxVol;
                                } else if (distance <= finalMinRadius) {
                                    double range = finalMinRadius - finalMaxRadius;
                                    double progress = (distance - finalMaxRadius) / range;
                                    volume = (float) (finalMaxVol * (1.0 - progress));
                                }
                                
                                if (volume > 0.01f) {
                                    // Play F# note at highest pitch with volume control
                                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 
                                            org.bukkit.SoundCategory.RECORDS, (float) volume, 2.0f);
                                }
                            }
                        }
                    }.runTaskLater(plugin, delay);
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    private FileConfiguration getYaml() {
        return com.hollowcrown.hungergames.util.Yaml.loadOrCreate(plugin, "envoys.yml");
    }

    public void clearAll() {
        for (Location l : new ArrayList<>(activeGiftDrops)) {
            Block b = l.getBlock();
            GiftDropData data = dropData.get(l);
            if (data != null && b.getType() == data.type.displayBlock) {
                b.setType(Material.AIR);
            }
        }
        activeGiftDrops.clear();
        dropData.clear();
        for (BukkitRunnable r : playerOutlines.values()) r.cancel();
        playerOutlines.clear();
    }
}


package com.hollowcrown.hungergames.crates;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Visual falling crate with a "parachute" banner and slowed descent. */
public class FallingCrate {
    private final HungerGamesPlugin plugin;
    private final CrateType type;
    private final Location target; // block center to land at
    private final double fallSpeed; // blocks per tick
    private final Consumer<Location> onLanded;
    private final FileConfiguration visualCfg;

    private ArmorStand stand; // carries the block as helmet
    private ArmorStand chute; // visual parachute (horizontal white banner)
    private List<LivingEntity> cornerEntities = new ArrayList<>(); // 4 entities at banner corners for leash attachment
    private List<LivingEntity> chestEntities = new ArrayList<>(); // 4 entities at chest center top, leashed to corners
    private static final double BANNER_HEIGHT = 2.5; // Height of banner above chest (1 block higher than before)
    private static final double CORNER_OFFSET = 0.8; // Distance from center to corners
    private Consumer<Location> beepCallback; // Callback to beep from current location
    private BukkitRunnable beepTask; // Task for beeping during fall

    public FallingCrate(HungerGamesPlugin plugin, CrateType type, Location target, double fallSpeed, Consumer<Location> onLanded) {
        this(plugin, type, target, fallSpeed, onLanded, null);
    }

    public FallingCrate(HungerGamesPlugin plugin, CrateType type, Location target, double fallSpeed, Consumer<Location> onLanded, FileConfiguration envoyVisualCfg) {
        this.plugin = plugin;
        this.type = type;
        this.target = target.clone();
        this.fallSpeed = Math.max(0.02, fallSpeed);
        this.onLanded = onLanded;
        this.visualCfg = envoyVisualCfg != null ? envoyVisualCfg : com.hollowcrown.hungergames.util.Yaml.loadOrCreate(plugin, "envoys.yml");
    }
    
    public void setBeepCallback(Consumer<Location> callback) {
        this.beepCallback = callback;
    }

    public void start() {
        World w = target.getWorld(); if (w == null) return;
        // Spawn configurable height above the target landing location (default 50)
        double spawnHeight = visualCfg.getDouble("timing.spawnHeightAboveTarget", 50.0);
        double startY = target.getY() + spawnHeight;
        // Ensure we don't exceed max height
        double maxY = w.getMaxHeight() - 2;
        if (startY > maxY) startY = maxY;
        Location spawn = new Location(w, target.getX(), startY, target.getZ());
        
        // Create chest armor stand (carries the block) - centered on spawn location
        Location chestCenter = spawn.clone();
        stand = w.spawn(chestCenter, ArmorStand.class, s -> {
            s.setInvisible(true); 
            s.setMarker(false); 
            s.setGravity(false);
            s.setSmall(false);
            s.getEquipment().setHelmet(new ItemStack(type.displayBlock));
            s.customName(LegacyComponentSerializer.legacySection().deserialize("§eLoot Drop")); 
            s.setCustomNameVisible(true);
        });
        
        // Check if parachute is enabled
        boolean parachuteEnabled = visualCfg.getBoolean("visual.parachuteEnabled", true);
        
        if (parachuteEnabled) {
            // Create horizontal banner parachute - centered over chest, 2.5 blocks higher
            Location bannerLoc = chestCenter.clone().add(0, BANNER_HEIGHT, 0);
            chute = w.spawn(bannerLoc, ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setGravity(false);
                s.setSmall(false);
                // Rotate to make banner horizontal (pitch -90 degrees)
                s.setHeadPose(new EulerAngle(Math.toRadians(-90), 0, 0));
                s.getEquipment().setHelmet(new ItemStack(Material.WHITE_BANNER));
            });
            
            // Create 4 small invisible slimes at banner corners (for lead attachment points)
            double[] offsets = {CORNER_OFFSET, -CORNER_OFFSET};
            for (double xOff : offsets) {
                for (double zOff : offsets) {
                    Location cornerLoc = bannerLoc.clone().add(xOff, 0, zOff);
                    Slime corner = (Slime) w.spawnEntity(cornerLoc, EntityType.SLIME);
                    corner.setSize(1); // Smallest slime
                    corner.setInvisible(true);
                    corner.setAI(false);
                    corner.setGravity(false);
                    corner.setInvulnerable(true);
                    corner.setSilent(true);
                    cornerEntities.add(corner);
                }
            }
            
            // Create 4 small invisible slimes at chest center top, each leashed to a corner
            Location chestTop = chestCenter.clone().add(0, 0.6, 0);
            for (int i = 0; i < cornerEntities.size(); i++) {
                Slime chestEntity = (Slime) w.spawnEntity(chestTop, EntityType.SLIME);
                chestEntity.setSize(1);
                chestEntity.setInvisible(true);
                chestEntity.setAI(false);
                chestEntity.setGravity(false);
                chestEntity.setInvulnerable(true);
                chestEntity.setSilent(true);
                chestEntities.add(chestEntity);
                
                // Leash chest entity to corner entity
                try {
                    if (cornerEntities.get(i) != null && !cornerEntities.get(i).isDead()) {
                        chestEntity.setLeashHolder(cornerEntities.get(i));
                    }
                } catch (Exception ignored) {}
            }
        }

        new BukkitRunnable() {
            @Override public void run() {
                if (stand == null || stand.isDead()) {
                    cleanup();
                    cancel();
                    return;
                }
                
                Location loc = stand.getLocation();
                double nextY = loc.getY() - fallSpeed;
                
                if (nextY <= target.getY() + 0.01) {
                    // Land
                    Location finalLoc = new Location(w, target.getX(), target.getY() + 0.5, target.getZ(), loc.getYaw(), loc.getPitch());
                    stand.teleport(finalLoc);
                    cleanup();
                    cancel();
                    onLanded.accept(target.getBlock().getLocation().add(0.5, 0, 0.5));
                    return;
                }
                
                // Update positions - centered on target X/Z coordinates
                Location newLoc = new Location(w, target.getX(), nextY, target.getZ(), loc.getYaw(), loc.getPitch());
                stand.teleport(newLoc);
                
                // Update parachute if enabled
                if (chute != null && !chute.isDead()) {
                    // Update banner position (2.5 blocks up from chest, centered)
                    chute.teleport(newLoc.clone().add(0, BANNER_HEIGHT, 0));
                    
                    // Update corner entity positions (at banner corners)
                    Location bannerPos = newLoc.clone().add(0, BANNER_HEIGHT, 0);
                    int idx = 0;
                    double[] offsets = {CORNER_OFFSET, -CORNER_OFFSET};
                    for (double xOff : offsets) {
                        for (double zOff : offsets) {
                            Location cornerPos = bannerPos.clone().add(xOff, 0, zOff);
                            if (idx < cornerEntities.size() && cornerEntities.get(idx) != null && !cornerEntities.get(idx).isDead()) {
                                cornerEntities.get(idx).teleport(cornerPos);
                            }
                            idx++;
                        }
                    }
                    
                    // Update chest entity positions (at chest center top)
                    Location chestTop = newLoc.clone().add(0, 0.6, 0);
                    for (LivingEntity chestEntity : chestEntities) {
                        if (chestEntity != null && !chestEntity.isDead()) {
                            chestEntity.teleport(chestTop);
                        }
                    }
                }
                
                // Call beep callback with current crate location (radius-based beeping)
                if (beepCallback != null) {
                    beepCallback.accept(newLoc);
                }
                
                // Particle trail - moved 1 block up from chest (at banner level, not in front)
                w.spawnParticle(Particle.CLOUD, newLoc.clone().add(0, 1.0, 0), 2, 0.1, 0.1, 0.1, 0.0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
    
    private void cleanup() {
        if (chute != null && !chute.isDead()) chute.remove();
        for (LivingEntity corner : cornerEntities) {
            if (corner != null && !corner.isDead()) corner.remove();
        }
        cornerEntities.clear();
        for (LivingEntity chestEntity : chestEntities) {
            if (chestEntity != null && !chestEntity.isDead()) chestEntity.remove();
        }
        chestEntities.clear();
        if (stand != null && !stand.isDead()) stand.remove();
    }
}



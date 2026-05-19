package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/** OP-only add-mode with special wand to place envoy spawn markers and see particles. */
public class AddChestModeManager implements Listener {
    private static final String WAND_NAME = "§6HCIronLegacy §7Spawn Wand";
    private static final Material WAND_MAT = Material.ENDER_CHEST;

    private final HungerGamesPlugin plugin;
    private final EnvoyManager envoys;
    private final Set<UUID> enabled = new HashSet<>();
    private final Set<UUID> showParticles = new HashSet<>();
    /** Template key for envoy YAML when OP uses scoped admin / wand flows. */
    private final Map<UUID, String> envoyTemplateByPlayer = new HashMap<>();
    private final Map<Location, Block> overlayChests = new HashMap<>(); // Spawn location -> overlay enderchest block
    private BukkitRunnable overlayTask;

    public AddChestModeManager(HungerGamesPlugin plugin, EnvoyManager envoys) {
        this.plugin = plugin; this.envoys = envoys;
        startParticles();
    }

    public void giveWand(Player p) {
        ItemStack it = new ItemStack(WAND_MAT);
        ItemMeta im = it.getItemMeta();
        im.displayName(LegacyComponentSerializer.legacySection().deserialize(WAND_NAME));
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(im);
        p.getInventory().addItem(it);
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aGiven spawn wand."));
    }

    public void setEnabled(Player p, boolean on) {
        setEnabled(p, on, null);
    }

    /** @param templateKey registry key for this map; if null, resolved from the player's current world. */
    public void setEnabled(Player p, boolean on, String templateKey) {
        if (on) {
            String tk = templateKey != null && !templateKey.isBlank()
                    ? templateKey
                    : plugin.templates().resolveTemplateKeyForWorld(p.getWorld().getName()).orElse(null);
            if (tk == null) {
                p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "\u00A7cStand in a registered template or match world to use envoy spawn tools."));
                return;
            }
            enabled.add(p.getUniqueId());
            showParticles.add(p.getUniqueId());
            envoyTemplateByPlayer.put(p.getUniqueId(), tk);
            startOverlay();
        } else {
            enabled.remove(p.getUniqueId());
            envoyTemplateByPlayer.remove(p.getUniqueId());
            stopOverlay();
        }
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(on ? "§aAdd-chests mode: ON" : "§cAdd-chests mode: OFF"));
    }

    private String resolveTemplateKey(Player p) {
        String tk = envoyTemplateByPlayer.get(p.getUniqueId());
        if (tk != null) return tk;
        return plugin.templates().resolveTemplateKeyForWorld(p.getWorld().getName()).orElse(null);
    }

    public boolean isEnabled(Player p) { return enabled.contains(p.getUniqueId()); }
    public void toggleParticles(Player p) {
        if (showParticles.contains(p.getUniqueId())) { showParticles.remove(p.getUniqueId()); p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eSpawn particles: OFF")); }
        else { showParticles.add(p.getUniqueId()); p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aSpawn particles: ON")); }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.isOp()) return;
        if (!isEnabled(p)) return;
        
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        
        // Check if clicking an overlay enderchest (left-click to remove)
        if (clicked.getType() == Material.ENDER_CHEST) {
            Location clickedLoc = clicked.getLocation().getBlock().getLocation();
            for (Map.Entry<Location, Block> entry : overlayChests.entrySet()) {
                if (entry.getValue().getLocation().equals(clickedLoc)) {
                    // This is an overlay chest - remove spawn point
                    if (e.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                        Location spawnLoc = entry.getKey();
                        String tkRm = resolveTemplateKey(p);
                        if (tkRm != null) envoys.removeNearest(spawnLoc, tkRm);
                        removeOverlayChest(spawnLoc);
                        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7eRemoved spawn point."));
                        p.playSound(clicked.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
                        e.setCancelled(true);
                    }
                    return;
                }
            }
        }
        
        // Handle wand interactions
        ItemStack it = e.getItem();
        if (it == null || it.getType() != WAND_MAT) return;
        if (it.getItemMeta() == null || it.getItemMeta().displayName() == null) return;
        String name = LegacyComponentSerializer.legacySection().serialize(it.getItemMeta().displayName());
        if (!name.contains("Spawn Wand")) return;
        
        // Right-click to place spawn point in air against clicked face
        if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            org.bukkit.block.BlockFace face = e.getBlockFace();
            if (face == null) return;
            
            // Place spawn point in air adjacent to clicked block
            Block targetBlock = clicked.getRelative(face);
            if (!targetBlock.getType().isAir()) {
                p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7cCannot place spawn point - location is not air."));
                e.setCancelled(true);
                return;
            }
            
            // Check if spawn point already exists at this block
            Location spawnLoc = targetBlock.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
            String tk = resolveTemplateKey(p);
            if (tk == null) {
                p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7cNo template context for envoy spawns."));
                e.setCancelled(true);
                return;
            }
            for (Location existing : envoys.getSpawnLocations(tk)) {
                if (existing.getBlock().getLocation().equals(targetBlock.getLocation())) {
                    p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7cSpawn point already exists at this location."));
                    e.setCancelled(true);
                    return;
                }
            }
            
            envoys.addLocation(spawnLoc, tk);
            updateOverlay();
            p.playSound(targetBlock.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aAdded spawn at §e" + spawnLoc.getBlockX() + "," + spawnLoc.getBlockY() + "," + spawnLoc.getBlockZ()));
            e.setCancelled(true);
        }
    }
    
    private void startOverlay() {
        if (overlayTask != null) return; // Already running
        updateOverlay();
        overlayTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Remove overlay if no OPs have mode enabled
                if (enabled.isEmpty()) {
                    stopOverlay();
                    return;
                }
                updateOverlay();
            }
        };
        overlayTask.runTaskTimer(plugin, 0L, 20L); // Update every second
    }
    
    private void stopOverlay() {
        if (overlayTask != null) {
            overlayTask.cancel();
            overlayTask = null;
        }
        // Remove all overlay chests
        for (Block chest : new ArrayList<>(overlayChests.values())) {
            if (chest.getType() == Material.ENDER_CHEST) {
                chest.setType(Material.AIR);
            }
        }
        overlayChests.clear();
    }
    
    private void updateOverlay() {
        // Remove overlay chests for spawn points that no longer exist
        UUID anyOp = enabled.stream().findAny().orElse(null);
        Player sample = anyOp != null ? Bukkit.getPlayer(anyOp) : null;
        String tk = sample != null ? resolveTemplateKey(sample) : null;
        List<Location> currentSpawns = tk != null ? envoys.getSpawnLocations(tk) : List.of();
        for (Map.Entry<Location, Block> entry : new ArrayList<>(overlayChests.entrySet())) {
            if (!currentSpawns.contains(entry.getKey())) {
                removeOverlayChest(entry.getKey());
            }
        }
        
        // Add overlay chests for spawn points that don't have them
        for (Location spawnLoc : currentSpawns) {
            if (!overlayChests.containsKey(spawnLoc)) {
                Block block = spawnLoc.getBlock();
                if (block.getType().isAir() || block.getType() == Material.ENDER_CHEST) {
                    block.setType(Material.ENDER_CHEST);
                    overlayChests.put(spawnLoc, block);
                }
            }
        }
    }
    
    private void removeOverlayChest(Location spawnLoc) {
        Block chest = overlayChests.remove(spawnLoc);
        if (chest != null && chest.getType() == Material.ENDER_CHEST) {
            chest.setType(Material.AIR);
        }
    }

    private void startParticles() {
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID id : enabled) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || !showParticles.contains(id)) continue;
                    String tkP = resolveTemplateKey(p);
                    if (tkP == null) continue;
                    for (Location l : envoys.getSpawnLocations(tkP)) {
                        if (!Objects.equals(l.getWorld(), p.getWorld())) continue;
                        // Green ring around spawn
                        double r = 0.6; double y = l.getY() + 0.2;
                        for (int i = 0; i < 24; i++) {
                            double a = i * (Math.PI * 2 / 24);
                            double x = l.getX() + r * Math.cos(a);
                            double z = l.getZ() + r * Math.sin(a);
                            p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0, 255, 85), 1.2f));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
}



package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/** OP-only mode to place tracked chests for the replenish system. */
public class ReplenishModeManager implements Listener {
    private static final String WAND_NAME = "§6HCIronLegacy §7Replenish Wand";
    private static final Material WAND_MAT = Material.CHEST;

    private final HungerGamesPlugin plugin;
    private final ReplenishManager repl;
    private final Set<UUID> enabled = new HashSet<>();
    private final Set<UUID> showParticles = new HashSet<>();
    private final Map<UUID, String> replenishTemplateByPlayer = new HashMap<>();

    public ReplenishModeManager(HungerGamesPlugin plugin, ReplenishManager repl) {
        this.plugin = plugin; this.repl = repl;
        startParticles();
    }

    public void giveWand(Player p) {
        ItemStack it = new ItemStack(WAND_MAT);
        ItemMeta im = it.getItemMeta();
        im.displayName(LegacyComponentSerializer.legacySection().deserialize(WAND_NAME));
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(im);
        p.getInventory().addItem(it);
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aGiven replenish wand."));
    }

    public void setEnabled(Player p, boolean on) {
        setEnabled(p, on, null);
    }

    public void setEnabled(Player p, boolean on, String templateKey) {
        if (on) {
            String tk = templateKey != null && !templateKey.isBlank()
                    ? templateKey
                    : plugin.templates().resolveTemplateKeyForWorld(p.getWorld().getName()).orElse(null);
            if (tk == null) {
                p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "\u00A7cStand in a registered template or match world to edit replenish chests."));
                return;
            }
            enabled.add(p.getUniqueId());
            replenishTemplateByPlayer.put(p.getUniqueId(), tk);
            showParticles.add(p.getUniqueId());
        } else {
            enabled.remove(p.getUniqueId());
            replenishTemplateByPlayer.remove(p.getUniqueId());
        }
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(on ? "§aReplenish mode: ON" : "§cReplenish mode: OFF"));
    }

    private String resolveTemplateKey(Player p) {
        String tk = replenishTemplateByPlayer.get(p.getUniqueId());
        if (tk != null) return tk;
        return plugin.templates().resolveTemplateKeyForWorld(p.getWorld().getName()).orElse(null);
    }
    public void toggleParticles(Player p) {
        if (showParticles.contains(p.getUniqueId())) { showParticles.remove(p.getUniqueId()); p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§eReplenish particles: OFF")); }
        else { showParticles.add(p.getUniqueId()); p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§aReplenish particles: ON")); }
    }

    public boolean isEnabled(Player p) { return enabled.contains(p.getUniqueId()); }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.isOp() || !isEnabled(p)) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != WAND_MAT) return;
        if (it.getItemMeta() == null || it.getItemMeta().displayName() == null) return;
        String name = LegacyComponentSerializer.legacySection().serialize(it.getItemMeta().displayName());
        if (!name.contains("Replenish Wand")) return;
        
        Block b = e.getClickedBlock();
        if (b == null) return;
        
        // Left-click to remove replenish chest
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && b.getType() == Material.CHEST) {
            Location chestLoc = b.getLocation().add(0.5, 0, 0.5);
            String tk = resolveTemplateKey(p);
            if (tk != null && repl.removeNearest(chestLoc, tk)) {
                p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7eRemoved replenish chest."));
                p.playSound(b.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
                e.setCancelled(true);
            }
            return;
        }
        
        // Right-click to place new chest
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!b.getType().isSolid()) return;
        
        Block above = b.getRelative(org.bukkit.block.BlockFace.UP);
        if (!above.getType().isAir()) return;
        above.setType(Material.CHEST);
        org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) above.getBlockData();
        // Face the player (180° - chest front faces player)
        org.bukkit.block.BlockFace face = getFacing(p.getLocation(), above.getLocation());
        if (face != null) chestData.setFacing(face);
        above.setBlockData(chestData);
        String tk = resolveTemplateKey(p);
        if (tk == null) {
            p.sendMessage(LegacyComponentSerializer.legacySection().deserialize("\u00A7cNo template context for replenish."));
            e.setCancelled(true);
            return;
        }
        repl.add(above.getLocation().add(0.5, 0, 0.5), tk);
        p.playSound(above.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1f, 1.2f);
        e.setCancelled(true);
    }

    private org.bukkit.block.BlockFace getFacing(Location playerLoc, Location blockLoc) {
        // Calculate direction FROM block TO player (chest faces player)
        org.bukkit.util.Vector dir = playerLoc.toVector().subtract(blockLoc.toVector()).normalize();
        double yaw = Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        if (yaw < 0) yaw += 360;
        // Return opposite direction so chest faces player
        if (yaw >= 315 || yaw < 45) return org.bukkit.block.BlockFace.NORTH; // Player south, chest faces north
        if (yaw >= 45 && yaw < 135) return org.bukkit.block.BlockFace.EAST; // Player west, chest faces east
        if (yaw >= 135 && yaw < 225) return org.bukkit.block.BlockFace.SOUTH; // Player north, chest faces south
        return org.bukkit.block.BlockFace.WEST; // Player east, chest faces west
    }

    private void startParticles() {
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID id : enabled) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || !showParticles.contains(id)) continue;
                    String tkP = resolveTemplateKey(p);
                    if (tkP == null) continue;
                    for (Location l : repl.list(tkP)) {
                        if (!Objects.equals(l.getWorld(), p.getWorld())) continue;
                        double y = l.getY() + 0.2; double r = 0.6;
                        for (int i = 0; i < 24; i++) {
                            double a = i * (Math.PI * 2 / 24);
                            double x = l.getX() + r * Math.cos(a);
                            double z = l.getZ() + r * Math.sin(a);
                            p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0, 180, 255), 1.2f));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
}



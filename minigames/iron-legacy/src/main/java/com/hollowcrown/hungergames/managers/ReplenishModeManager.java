package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
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

    /** True when the stack is the named replenish placement wand. */
    public static boolean isReplenishWand(ItemStack it) {
        if (it == null || it.getType() != WAND_MAT) return false;
        if (it.getItemMeta() == null || it.getItemMeta().displayName() == null) return false;
        String name = LegacyComponentSerializer.legacySection().serialize(it.getItemMeta().displayName());
        return name.contains("Replenish Wand");
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

    /**
     * Removes tracked spawn data for a replenish chest and optionally clears the block.
     *
     * @return true if spawn data was removed
     */
    public boolean removeTrackedChest(Player p, Block chestBlock, boolean destroyBlock) {
        if (chestBlock == null || chestBlock.getType() != Material.CHEST) return false;
        String tk = resolveTemplateKey(p);
        if (tk == null) {
            tk = plugin.templates().resolveTemplateKeyForWorld(chestBlock.getWorld().getName()).orElse(null);
        }
        if (tk == null) return false;
        Location chestLoc = chestBlock.getLocation().add(0.5, 0, 0.5);
        boolean removed = repl.removeNearest(chestLoc, tk);
        if (removed && destroyBlock) {
            chestBlock.setType(Material.AIR);
        }
        return removed;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.isOp()) return;
        ItemStack it = e.getItem();
        if (!isReplenishWand(it)) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        // Left-click with wand: remove spawn data + chest (mode not required)
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && b.getType() == Material.CHEST) {
            if (removeTrackedChest(p, b, true)) {
                p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                        "\u00A7eRemoved replenish chest and spawn data."));
                p.playSound(b.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.8f);
            }
            e.setCancelled(true);
            return;
        }

        // Right-click to place new chest (front faces the player, like vanilla)
        if (!isEnabled(p)) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!b.getType().isSolid()) return;

        Block above = b.getRelative(BlockFace.UP);
        if (!above.getType().isAir()) return;
        above.setType(Material.CHEST);
        if (above.getBlockData() instanceof Chest chestData) {
            chestData.setFacing(chestFaceTowardPlayer(p));
            above.setBlockData(chestData);
        }
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

    /**
     * Survival/creative block break while holding the wand: strip spawn data
     * (block removal is allowed by {@link com.hollowcrown.hungergames.listeners.ReplenishChestProtectionListener}).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWandBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!isReplenishWand(p.getInventory().getItemInMainHand())
                && !isReplenishWand(p.getInventory().getItemInOffHand())) {
            return;
        }
        Block b = e.getBlock();
        if (b.getType() != Material.CHEST) return;
        if (!plugin.replenish().isProtectedReplenishChest(b.getLocation())) return;
        if (removeTrackedChest(p, b, false)) {
            p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                    "\u00A7eRemoved replenish chest and spawn data."));
        }
    }

    /** Vanilla-style: chest front faces the player (opposite of look direction). */
    private static BlockFace chestFaceTowardPlayer(Player p) {
        BlockFace look = p.getFacing();
        return switch (look) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
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

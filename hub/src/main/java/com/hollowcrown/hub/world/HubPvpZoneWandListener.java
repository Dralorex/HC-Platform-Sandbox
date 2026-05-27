package com.hollowcrown.hub.world;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PvP zone selection wand (BLAZE_ROD). Only works inside hub protection.
 * Same selection system as the limbo wand — cyan outline preview, shift-right-click to confirm.
 */
public final class HubPvpZoneWandListener implements Listener {

    private static final String WAND_MARKER = "pvp_zone";

    private final HollowCrownHubPlugin plugin;
    private final HubWorldManager worldManager;
    private final Map<UUID, CornerState> selections = new HashMap<>();

    public HubPvpZoneWandListener(HollowCrownHubPlugin plugin, HubWorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        String tag = meta.getPersistentDataContainer().get(plugin.pvpZoneWandKey(), PersistentDataType.STRING);
        if (!WAND_MARKER.equals(tag)) return;

        event.setCancelled(true);

        if (!p.isOp()) {
            p.sendMessage(Component.text("OPs only.", NamedTextColor.RED));
            return;
        }
        if (!worldManager.isInHubProtection(p)) {
            p.sendMessage(Component.text("PvP zone wand only works inside hub protection.", NamedTextColor.RED));
            return;
        }

        CornerState state = selections.computeIfAbsent(p.getUniqueId(), k -> new CornerState());

        Location clicked = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : p.getLocation().getBlock().getLocation();

        if (p.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (state.pos1 == null || state.pos2 == null) {
                p.sendMessage(Component.text("Set both corners first.", NamedTextColor.RED));
                return;
            }
            worldManager.savePvpZone(state.pos1, state.pos2);
            stopOutlinePreview(state);
            p.sendMessage(Component.text("PvP zone saved.", NamedTextColor.GREEN));
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            state.pos1 = clicked;
            p.sendMessage(Component.text("PvP zone corner 1 set.", NamedTextColor.YELLOW));
            if (state.pos2 != null) startOutlinePreview(p, state);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            state.pos2 = clicked;
            p.sendMessage(Component.text("PvP zone corner 2 set.", NamedTextColor.YELLOW));
            if (state.pos1 != null) startOutlinePreview(p, state);
        }
    }

    static ItemStack createWand(HollowCrownHubPlugin plugin) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("\u00a76PvP Zone Wand"));
            meta.lore(List.of(
                    Component.text("Left-click block: first corner", NamedTextColor.GRAY),
                    Component.text("Right-click block: second corner", NamedTextColor.GRAY),
                    Component.text("Shift-right-click: save PvP zone", NamedTextColor.GRAY),
                    Component.text("Hub protection area only", NamedTextColor.DARK_GRAY)
            ));
            meta.getPersistentDataContainer().set(plugin.pvpZoneWandKey(), PersistentDataType.STRING, WAND_MARKER);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    private void startOutlinePreview(Player viewer, CornerState selection) {
        stopOutlinePreview(selection);
        selection.outlineTicksLeft = 40;
        selection.outlineTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!viewer.isOnline() || selection.pos1 == null || selection.pos2 == null) {
                    stopOutlinePreview(selection);
                    cancel();
                    return;
                }
                drawOutline(viewer, selection.pos1, selection.pos2);
                selection.outlineTicksLeft--;
                if (selection.outlineTicksLeft <= 0) {
                    stopOutlinePreview(selection);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void stopOutlinePreview(CornerState selection) {
        if (selection == null || selection.outlineTask == null) return;
        try { selection.outlineTask.cancel(); } catch (Exception ignored) {}
        selection.outlineTask = null;
    }

    private void drawOutline(Player viewer, Location pos1, Location pos2) {
        if (viewer == null || !viewer.isOnline() || pos1 == null || pos2 == null) return;
        World w = viewer.getWorld();
        if (w == null || pos1.getWorld() == null || pos2.getWorld() == null) return;
        if (!w.getName().equalsIgnoreCase(pos1.getWorld().getName())
                || !w.getName().equalsIgnoreCase(pos2.getWorld().getName())) return;

        double minX = Math.min(pos1.getBlockX(), pos2.getBlockX()) - 0.02;
        double maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) + 1.02;
        double minY = Math.min(pos1.getBlockY(), pos2.getBlockY()) - 0.02;
        double maxY = Math.max(pos1.getBlockY(), pos2.getBlockY()) + 1.02;
        double minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ()) - 0.02;
        double maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 1.02;

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1.2f);
        double step = 0.5;

        for (double x = minX; x <= maxX; x += step) {
            viewer.spawnParticle(Particle.DUST, x, minY, minZ, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, x, minY, maxZ, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, x, maxY, minZ, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, x, maxY, maxZ, 1, 0, 0, 0, 0, dust);
        }
        for (double y = minY; y <= maxY; y += step) {
            viewer.spawnParticle(Particle.DUST, minX, y, minZ, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, minX, y, maxZ, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, maxX, y, minZ, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, maxX, y, maxZ, 1, 0, 0, 0, 0, dust);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            viewer.spawnParticle(Particle.DUST, minX, minY, z, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, minX, maxY, z, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, maxX, minY, z, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, maxX, maxY, z, 1, 0, 0, 0, 0, dust);
        }
    }

    private static final class CornerState {
        Location pos1;
        Location pos2;
        BukkitTask outlineTask;
        int outlineTicksLeft;
    }
}

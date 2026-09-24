package com.hollowcrown.hub.world;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Circle lobby-zone wand: left-click center, right-click edge (radius + height from Y).
 * Hold Shift + scroll cycles Center/Edge and locks the hotbar slot while the wand is held.
 * Left Alt is not sent by vanilla clients, so Shift is the server-detectable modifier.
 * Particle border + bottom action-bar status only while holding the wand.
 */
public final class HubLobbyCircleWandListener implements Listener {

    static final String WAND_MARKER = "lobby_circle";

    /** Active tip shown in the action bar (cycled with Shift+scroll). */
    public enum SelectMode {
        CENTER,
        EDGE
    }

    private final HollowCrownHubPlugin plugin;
    private final HubWorldManager worldManager;
    private final Map<UUID, EditorState> editors = new HashMap<>();

    public HubLobbyCircleWandListener(HollowCrownHubPlugin plugin, HubWorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        startHudTask();
    }

    public static ItemStack createWand(HollowCrownHubPlugin plugin) {
        ItemStack wand = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Lobby Circle Wand", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Left-click: set center", NamedTextColor.GRAY),
                    Component.text("Right-click: set edge (radius + height)", NamedTextColor.GRAY),
                    Component.text("Hold Shift + scroll: cycle Center / Edge tip", NamedTextColor.AQUA),
                    Component.text("Shift-right-click: save circle lobby zone", NamedTextColor.YELLOW),
                    Component.text("Border + HUD only while holding this wand", NamedTextColor.DARK_GRAY),
                    Component.text("Shift+scroll stays on this slot; plain scroll changes hotbar", NamedTextColor.DARK_GRAY)
            ));
            meta.getPersistentDataContainer().set(
                    plugin.lobbyCircleWandKey(), PersistentDataType.STRING, WAND_MARKER);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    /**
     * Shift + mouse-wheel while the wand is selected: cycle Center/Edge and keep that hotbar slot.
     * Cancel plus a next-tick slot restore — cancel alone often leaves the client scrolled away.
     * Adjacent/wraparound moves only (scroll). Plain scroll and number keys still change the hotbar.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHotbarScroll(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (!p.isSneaking()) {
            return;
        }
        int prev = event.getPreviousSlot();
        ItemStack wandSlot = p.getInventory().getItem(prev);
        if (!isCircleWand(wandSlot)) {
            return;
        }
        int next = event.getNewSlot();
        int delta = Math.floorMod(next - prev, 9);
        if (delta != 1 && delta != 8) {
            return;
        }
        event.setCancelled(true);
        // Next-tick restore — client already scrolled locally; cancel packet alone is flaky.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (p.getInventory().getHeldItemSlot() != prev) {
                p.getInventory().setHeldItemSlot(prev);
            }
        });
        EditorState state = editors.computeIfAbsent(p.getUniqueId(), k -> new EditorState());
        state.mode = state.mode == SelectMode.CENTER ? SelectMode.EDGE : SelectMode.CENTER;
        sendHud(p, state);
        p.sendMessage(Component.text(
                "Selection tip: " + state.mode.name().charAt(0)
                        + state.mode.name().substring(1).toLowerCase(Locale.ROOT),
                NamedTextColor.AQUA));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !isCircleWand(item)) {
            return;
        }
        event.setCancelled(true);

        if (!p.isOp()) {
            p.sendMessage(Component.text("OPs only.", NamedTextColor.RED));
            return;
        }

        EditorState state = editors.computeIfAbsent(p.getUniqueId(), k -> new EditorState());
        Block clicked = event.getClickedBlock();
        Location blockLoc = clicked != null
                ? clicked.getLocation()
                : p.getLocation().getBlock().getLocation();

        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;

        if (p.isSneaking() && right) {
            if (state.center == null || state.edge == null) {
                p.sendMessage(Component.text("Set center (left-click) and edge (right-click) first.", NamedTextColor.RED));
                return;
            }
            if (!worldManager.saveLobbyCircleZone(state.center, state.edge)) {
                p.sendMessage(Component.text("Failed to save circle lobby zone.", NamedTextColor.RED));
                return;
            }
            p.sendMessage(Component.text(
                    "Circle lobby zone saved (radius "
                            + String.format(Locale.ROOT, "%.1f", horizontalRadius(state.center, state.edge))
                            + ", height "
                            + verticalSpan(state.center, state.edge)
                            + ").",
                    NamedTextColor.GREEN));
            return;
        }

        if (left) {
            state.center = blockLoc.clone();
            p.sendMessage(Component.text(
                    "Center set at " + fmt(blockLoc) + ".",
                    NamedTextColor.YELLOW));
            sendHud(p, state);
            return;
        }
        if (right) {
            state.edge = blockLoc.clone();
            if (state.center != null && state.edge.getWorld() != null
                    && state.center.getWorld() != null
                    && !state.edge.getWorld().equals(state.center.getWorld())) {
                p.sendMessage(Component.text("Edge must be in the same world as the center.", NamedTextColor.RED));
                state.edge = null;
                return;
            }
            p.sendMessage(Component.text(
                    "Edge set at " + fmt(blockLoc)
                            + " (radius "
                            + String.format(Locale.ROOT, "%.1f",
                            state.center == null ? 0.0 : horizontalRadius(state.center, state.edge))
                            + ").",
                    NamedTextColor.YELLOW));
            sendHud(p, state);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        EditorState state = editors.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            stopOutline(state);
        }
    }

    private void startHudTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    EditorState state = editors.computeIfAbsent(p.getUniqueId(), k -> new EditorState());
                    if (!isHoldingCircleWand(p)) {
                        stopOutline(state);
                        continue;
                    }
                    sendHud(p, state);
                    ensureOutline(p, state);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void ensureOutline(Player viewer, EditorState state) {
        if (state.outlineTask != null) {
            return;
        }
        state.outlineTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!viewer.isOnline() || !isHoldingCircleWand(viewer)) {
                    stopOutline(state);
                    cancel();
                    return;
                }
                Location c = state.center;
                Location e = state.edge;
                if (c == null) {
                    worldManager.lobbyCircleZone().ifPresent(zone ->
                            drawCylinder(viewer, zone.center(), zone.radius(), zone.minY(), zone.maxY()));
                    return;
                }
                if (e == null) {
                    drawMarker(viewer, c, Color.fromRGB(80, 255, 120));
                    return;
                }
                int minY = Math.min(c.getBlockY(), e.getBlockY());
                int maxY = Math.max(c.getBlockY(), e.getBlockY());
                if (minY == maxY) {
                    minY -= 8;
                    maxY += 24;
                }
                drawCylinder(viewer, c, horizontalRadius(c, e), minY, maxY);
                drawMarker(viewer, c, Color.fromRGB(80, 255, 120));
                drawMarker(viewer, e, Color.fromRGB(255, 200, 80));
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }

    private void stopOutline(EditorState state) {
        if (state == null || state.outlineTask == null) {
            return;
        }
        try {
            state.outlineTask.cancel();
        } catch (Exception ignored) {
        }
        state.outlineTask = null;
    }

    private void sendHud(Player p, EditorState state) {
        String mode = state.mode == SelectMode.CENTER ? "Center" : "Edge";
        String center = state.center == null ? "—" : fmt(state.center);
        String edge = state.edge == null ? "—" : fmt(state.edge);
        String radius = (state.center != null && state.edge != null)
                ? String.format(Locale.ROOT, "%.1f", horizontalRadius(state.center, state.edge))
                : "—";
        // Action bar reads as the bottom status line (closest to “bottom left” in vanilla HUD).
        p.sendActionBar(Component.text()
                .append(Component.text("Lobby Circle  ", NamedTextColor.GOLD))
                .append(Component.text("[" + mode + "] ", NamedTextColor.AQUA))
                .append(Component.text("C:" + center + " ", NamedTextColor.GREEN))
                .append(Component.text("E:" + edge + " ", NamedTextColor.YELLOW))
                .append(Component.text("R:" + radius, NamedTextColor.WHITE))
                .append(Component.text("  | Shift+scroll", NamedTextColor.DARK_GRAY))
                .build());
    }

    private static void drawMarker(Player viewer, Location loc, Color color) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        double x = loc.getBlockX() + 0.5;
        double y = loc.getBlockY() + 1.05;
        double z = loc.getBlockZ() + 0.5;
        viewer.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
    }

    private static void drawCylinder(Player viewer, Location center, double radius, int minY, int maxY) {
        if (viewer == null || center == null || center.getWorld() == null || radius <= 0) {
            return;
        }
        World w = center.getWorld();
        if (!viewer.getWorld().equals(w)) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 170, 40), 1.15f);
        double cx = center.getBlockX() + 0.5;
        double cz = center.getBlockZ() + 0.5;
        int steps = Math.max(24, (int) (radius * 6));
        for (int i = 0; i < steps; i++) {
            double a = (Math.PI * 2 * i) / steps;
            double x = cx + Math.cos(a) * radius;
            double z = cz + Math.sin(a) * radius;
            viewer.spawnParticle(Particle.DUST, x, minY + 0.05, z, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, x, maxY + 0.95, z, 1, 0, 0, 0, 0, dust);
        }
        // Vertical posts at N/E/S/W
        for (int yi = minY; yi <= maxY; yi += Math.max(1, (maxY - minY) / 8)) {
            viewer.spawnParticle(Particle.DUST, cx + radius, yi + 0.5, cz, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, cx - radius, yi + 0.5, cz, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, cx, yi + 0.5, cz + radius, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, cx, yi + 0.5, cz - radius, 1, 0, 0, 0, 0, dust);
        }
    }

    private boolean isHoldingCircleWand(Player p) {
        return isCircleWand(p.getInventory().getItemInMainHand())
                || isCircleWand(p.getInventory().getItemInOffHand());
    }

    private boolean isCircleWand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String tag = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.lobbyCircleWandKey(), PersistentDataType.STRING);
        return WAND_MARKER.equals(tag);
    }

    static double horizontalRadius(Location center, Location edge) {
        double dx = (edge.getBlockX() + 0.5) - (center.getBlockX() + 0.5);
        double dz = (edge.getBlockZ() + 0.5) - (center.getBlockZ() + 0.5);
        return Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
    }

    static int verticalSpan(Location center, Location edge) {
        int minY = Math.min(center.getBlockY(), edge.getBlockY());
        int maxY = Math.max(center.getBlockY(), edge.getBlockY());
        if (minY == maxY) {
            return 33;
        }
        return maxY - minY + 1;
    }

    private static String fmt(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static final class EditorState {
        SelectMode mode = SelectMode.CENTER;
        Location center;
        Location edge;
        BukkitTask outlineTask;
    }
}

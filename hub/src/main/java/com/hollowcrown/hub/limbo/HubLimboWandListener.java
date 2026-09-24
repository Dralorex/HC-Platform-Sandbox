package com.hollowcrown.hub.limbo;

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
import java.util.regex.Pattern;

/**
 * Template lobby circle wand. Writes HCIronLegacy {@code worlds.yml} limbo zone.
 * Left-click center, right-click edge (radius + height). Shift+scroll cycles the tip
 * and locks the hotbar slot. Shift-right-click saves. Spawn is {@code /hch template lobby spawn}.
 */
public final class HubLimboWandListener implements Listener {

    private static final String WAND_MARKER = "lobby_circle";
    private static final Pattern LOBBY_SUFFIX = Pattern.compile("-L\\d+$");
    private static final String MANAGED_PREFIX = "hch-worlds/templates/";

    public enum SelectMode {
        CENTER,
        EDGE
    }

    private final HollowCrownHubPlugin plugin;
    private final HubLimboWorldsYaml worldsYaml;
    private final Map<UUID, EditorState> editors = new HashMap<>();

    public HubLimboWandListener(HollowCrownHubPlugin plugin, HubLimboWorldsYaml worldsYaml) {
        this.plugin = plugin;
        this.worldsYaml = worldsYaml;
        startHudTask();
    }

    static ItemStack createWand(HollowCrownHubPlugin plugin) {
        ItemStack wand = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Lobby Circle Wand", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Left-click: set center", NamedTextColor.GRAY),
                    Component.text("Right-click: set edge (radius + height)", NamedTextColor.GRAY),
                    Component.text("Hold Shift + scroll: cycle Center / Edge tip", NamedTextColor.AQUA),
                    Component.text("Shift-right-click: save lobby circle", NamedTextColor.YELLOW),
                    Component.text("Spawn: /hch template lobby spawn (stand inside)", NamedTextColor.GRAY),
                    Component.text("Border + HUD only while holding this wand", NamedTextColor.DARK_GRAY)
            ));
            meta.getPersistentDataContainer().set(
                    plugin.limboWandTemplateKey(), PersistentDataType.STRING, WAND_MARKER);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHotbarScroll(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (!p.isSneaking()) {
            return;
        }
        int prev = event.getPreviousSlot();
        if (!isCircleWand(p.getInventory().getItem(prev))) {
            return;
        }
        int delta = Math.floorMod(event.getNewSlot() - prev, 9);
        if (delta != 1 && delta != 8) {
            return;
        }
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.getInventory().getHeldItemSlot() != prev) {
                p.getInventory().setHeldItemSlot(prev);
            }
        });
        EditorState state = editors.computeIfAbsent(p.getUniqueId(), k -> new EditorState());
        state.mode = state.mode == SelectMode.CENTER ? SelectMode.EDGE : SelectMode.CENTER;
        sendHud(p, state, resolveTemplateKey(p));
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
        if (!p.isOp() && !p.hasPermission("hollowcrownhub.admin")) {
            p.sendMessage(Component.text("Admins only.", NamedTextColor.RED));
            return;
        }

        String templateKey = resolveTemplateKey(p);
        if (templateKey == null) {
            p.sendMessage(Component.text(
                    "Could not resolve a template key for world '" + p.getWorld().getName()
                            + "'. Stand in a template world or lobby.",
                    NamedTextColor.RED));
            return;
        }

        EditorState state = editors.computeIfAbsent(p.getUniqueId(), k -> new EditorState());
        if (state.templateKey == null || !state.templateKey.equals(templateKey)) {
            state.templateKey = templateKey;
        }

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
            if (!worldsYaml.writeLimboCircle(templateKey, state.center, state.edge)) {
                p.sendMessage(Component.text("Failed to save lobby circle.", NamedTextColor.RED));
                return;
            }
            p.sendMessage(Component.text(
                    "Lobby circle saved for " + templateKey
                            + " (radius "
                            + String.format(Locale.ROOT, "%.1f", horizontalRadius(state.center, state.edge))
                            + "). Stand inside it and run /hch template lobby spawn.",
                    NamedTextColor.GREEN));
            return;
        }

        if (left) {
            state.center = blockLoc.clone();
            p.sendMessage(Component.text("Center set at " + fmt(blockLoc) + ".", NamedTextColor.YELLOW));
            sendHud(p, state, templateKey);
            return;
        }
        if (right) {
            state.edge = blockLoc.clone();
            if (state.center != null && state.center.getWorld() != null && state.edge.getWorld() != null
                    && !state.edge.getWorld().equals(state.center.getWorld())) {
                p.sendMessage(Component.text("Edge must be in the same world as the center.", NamedTextColor.RED));
                state.edge = null;
                return;
            }
            p.sendMessage(Component.text(
                    "Edge set at " + fmt(blockLoc) + " (radius "
                            + String.format(Locale.ROOT, "%.1f",
                            state.center == null ? 0.0 : horizontalRadius(state.center, state.edge))
                            + ").",
                    NamedTextColor.YELLOW));
            sendHud(p, state, templateKey);
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
                    if (!isHoldingCircleWand(p)) {
                        EditorState state = editors.get(p.getUniqueId());
                        if (state != null) {
                            stopOutline(state);
                        }
                        continue;
                    }
                    EditorState state = editors.computeIfAbsent(p.getUniqueId(), k -> new EditorState());
                    String key = resolveTemplateKey(p);
                    if (key != null) {
                        state.templateKey = key;
                    }
                    sendHud(p, state, key);
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
                if (c == null && state.templateKey != null) {
                    worldsYaml.limboZone(state.templateKey).ifPresent(zone -> {
                        if (zone.circle()) {
                            Location center = new Location(viewer.getWorld(), zone.circleCenterX(),
                                    Math.min(zone.pos1().getY(), zone.pos2().getY()), zone.circleCenterZ());
                            drawCylinder(viewer, center, zone.circleRadius(),
                                    (int) Math.min(zone.pos1().getY(), zone.pos2().getY()),
                                    (int) Math.max(zone.pos1().getY(), zone.pos2().getY()));
                        }
                    });
                    return;
                }
                if (c == null) {
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

    private void sendHud(Player p, EditorState state, String templateKey) {
        String mode = state.mode == SelectMode.CENTER ? "Center" : "Edge";
        String center = state.center == null ? "—" : fmt(state.center);
        String edge = state.edge == null ? "—" : fmt(state.edge);
        String radius = (state.center != null && state.edge != null)
                ? String.format(Locale.ROOT, "%.1f", horizontalRadius(state.center, state.edge))
                : "—";
        String key = templateKey == null ? "—" : templateKey;
        p.sendActionBar(Component.text()
                .append(Component.text("Lobby " + key + "  ", NamedTextColor.GOLD))
                .append(Component.text("[" + mode + "] ", NamedTextColor.AQUA))
                .append(Component.text("C:" + center + " ", NamedTextColor.GREEN))
                .append(Component.text("E:" + edge + " ", NamedTextColor.YELLOW))
                .append(Component.text("R:" + radius, NamedTextColor.WHITE))
                .append(Component.text("  | Shift+scroll", NamedTextColor.DARK_GRAY))
                .build());
    }

    private String resolveTemplateKey(Player p) {
        String worldName = p.getWorld().getName();
        var direct = worldsYaml.findTemplateKeyForWorldFolder(worldName);
        if (direct.isPresent()) {
            return direct.get();
        }
        String normalized = worldName.replace('\\', '/');
        if (normalized.toLowerCase(Locale.ROOT).startsWith(MANAGED_PREFIX.toLowerCase(Locale.ROOT))) {
            String tail = normalized.substring(MANAGED_PREFIX.length());
            String key = tail.toLowerCase(Locale.ROOT);
            if (HubLimboWorldsYaml.SAFE_TEMPLATE_KEY.matcher(key).matches()) {
                return key;
            }
        }
        String stripped = LOBBY_SUFFIX.matcher(worldName).replaceFirst("");
        if (!stripped.equals(worldName)) {
            var fromStripped = worldsYaml.findTemplateKeyForWorldFolder(stripped);
            if (fromStripped.isPresent()) {
                return fromStripped.get();
            }
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (HubLimboWorldsYaml.SAFE_TEMPLATE_KEY.matcher(lower).matches()) {
                return lower;
            }
        }
        for (var entry : plugin.templates().list()) {
            if (worldName.equalsIgnoreCase(entry.sourceWorldName())) {
                return entry.templateKey();
            }
        }
        String lower = worldName.toLowerCase(Locale.ROOT);
        if (HubLimboWorldsYaml.SAFE_TEMPLATE_KEY.matcher(lower).matches()) {
            return lower;
        }
        return null;
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
                .get(plugin.limboWandTemplateKey(), PersistentDataType.STRING);
        return WAND_MARKER.equals(tag);
    }

    static double horizontalRadius(Location center, Location edge) {
        double dx = (edge.getBlockX() + 0.5) - (center.getBlockX() + 0.5);
        double dz = (edge.getBlockZ() + 0.5) - (center.getBlockZ() + 0.5);
        return Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
    }

    private static void drawMarker(Player viewer, Location loc, Color color) {
        if (loc == null || loc.getWorld() == null || !viewer.getWorld().equals(loc.getWorld())) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        viewer.spawnParticle(Particle.DUST,
                loc.getBlockX() + 0.5, loc.getBlockY() + 1.05, loc.getBlockZ() + 0.5,
                1, 0, 0, 0, 0, dust);
    }

    private static void drawCylinder(Player viewer, Location center, double radius, int minY, int maxY) {
        if (viewer == null || center == null || radius <= 0) {
            return;
        }
        World w = viewer.getWorld();
        if (w == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 170, 40), 1.15f);
        double cx = center.getBlockX() + 0.5;
        double cz = center.getBlockZ() + 0.5;
        if (center.getWorld() != null && center.getWorld().equals(w)) {
            cx = center.getX();
            cz = center.getZ();
        }
        int steps = Math.max(24, (int) (radius * 6));
        for (int i = 0; i < steps; i++) {
            double a = (Math.PI * 2 * i) / steps;
            double x = cx + Math.cos(a) * radius;
            double z = cz + Math.sin(a) * radius;
            viewer.spawnParticle(Particle.DUST, x, minY + 0.05, z, 1, 0, 0, 0, 0, dust);
            viewer.spawnParticle(Particle.DUST, x, maxY + 0.95, z, 1, 0, 0, 0, 0, dust);
        }
    }

    private static String fmt(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static final class EditorState {
        SelectMode mode = SelectMode.CENTER;
        String templateKey;
        Location center;
        Location edge;
        BukkitTask outlineTask;
    }
}

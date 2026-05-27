package com.hollowcrown.hub.limbo;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
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
import java.util.Map;
import java.util.UUID;

/** Hollow Crown Hub limbo corner wand (writes HCIronLegacy {@code worlds.yml}). */
public final class HubLimboWandListener implements Listener {

    private static final String WAND_NAME = "§dLimbo Wand";
    private static final String WAND_MARKER = "*";

    private final HollowCrownHubPlugin plugin;
    private final HubLimboWorldsYaml worldsYaml;
    private final Map<UUID, CornerState> selections = new HashMap<>();

    public HubLimboWandListener(HollowCrownHubPlugin plugin, HubLimboWorldsYaml worldsYaml) {
        this.plugin = plugin;
        this.worldsYaml = worldsYaml;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        String wandTag = meta.getPersistentDataContainer().get(plugin.limboWandTemplateKey(), PersistentDataType.STRING);
        if (wandTag == null || wandTag.isBlank()) {
            return;
        }

        event.setCancelled(true);

        String templateKey = resolveTemplateKey(p);
        if (templateKey == null) {
            p.sendMessage(Component.text(
                    "Could not resolve a template key for world '" + p.getWorld().getName()
                            + "'. Stand in a template world or lobby.",
                    NamedTextColor.RED));
            return;
        }

        CornerState state = selections.get(p.getUniqueId());
        if (state == null || !state.templateKey.equals(templateKey)) {
            state = new CornerState(templateKey);
            selections.put(p.getUniqueId(), state);
        }

        Location clicked = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : p.getLocation().getBlock().getLocation();

        if (p.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (state.pos1 == null || state.pos2 == null) {
                p.sendMessage(Component.text("Set both corners first.", NamedTextColor.RED));
                return;
            }
            boolean ok = worldsYaml.writeLimboZone(templateKey, state.pos1, state.pos2);
            if (!ok) {
                p.sendMessage(Component.text("Failed to save limbo zone.", NamedTextColor.RED));
                return;
            }
            stopOutlinePreview(state);
            p.sendMessage(Component.text("Limbo zone confirmed for template: " + templateKey, NamedTextColor.GREEN));
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            state.pos1 = clicked;
            p.sendMessage(Component.text("First corner set.", NamedTextColor.YELLOW));
            if (state.pos2 != null) {
                startOutlinePreview(p, state);
            }
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            state.pos2 = clicked;
            p.sendMessage(Component.text("Second corner set.", NamedTextColor.YELLOW));
            if (state.pos1 != null) {
                startOutlinePreview(p, state);
            }
        }
    }

    static ItemStack createWand(HollowCrownHubPlugin plugin) {
        ItemStack wand = new ItemStack(org.bukkit.Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(WAND_NAME));
            meta.lore(java.util.List.of(
                    Component.text("Left-click block: first corner", NamedTextColor.GRAY),
                    Component.text("Right-click block: second corner", NamedTextColor.GRAY),
                    Component.text("Shift-right-click: save zone (HCIronLegacy worlds.yml)", NamedTextColor.GRAY),
                    Component.text("Works in any template world", NamedTextColor.DARK_GRAY)
            ));
            meta.getPersistentDataContainer().set(plugin.limboWandTemplateKey(), PersistentDataType.STRING, WAND_MARKER);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    private static final java.util.regex.Pattern LOBBY_SUFFIX = java.util.regex.Pattern.compile("-L\\d+$");
    private static final String MANAGED_PREFIX = "hch-worlds/templates/";

    private String resolveTemplateKey(Player p) {
        String worldName = p.getWorld().getName();

        // 1. Direct worlds.yml lookup by world folder name
        var direct = worldsYaml.findTemplateKeyForWorldFolder(worldName);
        if (direct.isPresent()) return direct.get();

        // 2. Managed template path (hch-worlds/templates/<key>)
        String normalized = worldName.replace('\\', '/');
        if (normalized.toLowerCase(java.util.Locale.ROOT).startsWith(MANAGED_PREFIX.toLowerCase(java.util.Locale.ROOT))) {
            String tail = normalized.substring(MANAGED_PREFIX.length());
            String key = tail.toLowerCase(java.util.Locale.ROOT);
            if (HubLimboWorldsYaml.SAFE_TEMPLATE_KEY.matcher(key).matches()) {
                return key;
            }
        }

        // 3. Lobby instance world (<key>-L#) — strip suffix and retry
        String stripped = LOBBY_SUFFIX.matcher(worldName).replaceFirst("");
        if (!stripped.equals(worldName)) {
            var fromStripped = worldsYaml.findTemplateKeyForWorldFolder(stripped);
            if (fromStripped.isPresent()) return fromStripped.get();
            String lower = stripped.toLowerCase(java.util.Locale.ROOT);
            if (HubLimboWorldsYaml.SAFE_TEMPLATE_KEY.matcher(lower).matches()) {
                return lower;
            }
        }

        // 4. Template registry lookup by source world
        for (var entry : plugin.templates().list()) {
            if (worldName.equalsIgnoreCase(entry.sourceWorldName())) {
                return entry.templateKey();
            }
        }

        // 5. Fallback: use world name if it's a safe key
        String lower = worldName.toLowerCase(java.util.Locale.ROOT);
        if (HubLimboWorldsYaml.SAFE_TEMPLATE_KEY.matcher(lower).matches()) {
            return lower;
        }
        return null;
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
        if (selection == null || selection.outlineTask == null) {
            return;
        }
        try {
            selection.outlineTask.cancel();
        } catch (Exception ignored) {
        }
        selection.outlineTask = null;
    }

    private void drawOutline(Player viewer, Location pos1, Location pos2) {
        if (viewer == null || !viewer.isOnline() || pos1 == null || pos2 == null) {
            return;
        }
        World w = viewer.getWorld();
        if (w == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return;
        }
        if (!w.getName().equalsIgnoreCase(pos1.getWorld().getName()) || !w.getName().equalsIgnoreCase(pos2.getWorld().getName())) {
            return;
        }

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
        final String templateKey;
        Location pos1;
        Location pos2;
        BukkitTask outlineTask;
        int outlineTicksLeft;

        CornerState(String templateKey) {
            this.templateKey = templateKey;
        }
    }
}

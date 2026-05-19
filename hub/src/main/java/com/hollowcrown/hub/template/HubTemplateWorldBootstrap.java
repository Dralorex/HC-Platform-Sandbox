package com.hollowcrown.hub.template;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Creates editable Iron Legacy template worlds on disk (managed folder under the server container)
 * and registers them in {@link HubTemplateRegistry}. Replaces legacy {@code /hg create} / {@code /il create}.
 */
public final class HubTemplateWorldBootstrap {

    /** Mechanic id registered by HCIronLegacy with Hollow Crown Hub. */
    public static final String IRON_LEGACY_MECHANIC_ID = "iron-legacy";

    private static final String MANAGED_ROOT = "hch-worlds/templates";

    private HubTemplateWorldBootstrap() {
    }

    public record Result(boolean ok, World world, String normalizedKey, String errorMessage) {
        public static Result success(World w, String nk) {
            return new Result(true, w, nk, null);
        }

        public static Result fail(String msg) {
            return new Result(false, null, null, msg);
        }
    }

    public static String managedFolderPath(String normalizedTemplateKey) {
        String safe = normalizedTemplateKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return MANAGED_ROOT + "/" + safe;
    }

    /**
     * Creates a new overworld map folder, registers {@link #IRON_LEGACY_MECHANIC_ID}, optionally teleports the executor.
     */
    public static Result createIronLegacyEditableWorld(
            HollowCrownHubPlugin plugin,
            HubTemplateRegistry registry,
            String rawTemplateKey,
            String displayNameJoined,
            Player teleportPlayerOrNull
    ) {
        final String nk;
        try {
            nk = HubTemplateRegistry.validateTemplateKeySyntax(rawTemplateKey);
        } catch (IllegalArgumentException ex) {
            return Result.fail(ex.getMessage());
        }
        if (registry.get(nk).isPresent()) {
            return Result.fail("Template '" + nk + "' already exists. Remove it first or pick another key.");
        }
        String folder = managedFolderPath(nk);
        try {
            HubTemplateRegistry.validateSourceFolder(folder);
        } catch (IllegalArgumentException ex) {
            return Result.fail(ex.getMessage());
        }

        Path container = Bukkit.getWorldContainer().toPath();
        Path worldDir = container;
        for (String segment : folder.split("/")) {
            worldDir = worldDir.resolve(segment);
        }

        if (Files.exists(worldDir)) {
            return Result.fail("World folder already exists on disk: " + folder);
        }
        if (Bukkit.getWorld(folder) != null) {
            return Result.fail("A world named '" + folder + "' is already loaded.");
        }

        World created = Bukkit.createWorld(new WorldCreator(folder));
        if (created == null) {
            return Result.fail("Bukkit.createWorld failed for path " + folder + ".");
        }

        applyTemplateDefaultsThenEditorRules(created);

        String display = displayNameJoined == null ? "" : displayNameJoined.trim();
        try {
            registry.upsert(nk, IRON_LEGACY_MECHANIC_ID, display, folder);
        } catch (IllegalArgumentException ex) {
            unloadAndDeleteBestEffort(created, worldDir);
            return Result.fail(ex.getMessage());
        }

        if (teleportPlayerOrNull != null) {
            tpIntoCreativeEditor(teleportPlayerOrNull, created);
        }

        plugin.getLogger().info("Created Iron Legacy editable template world templateKey=" + nk + " folder=" + folder);
        return Result.success(created, nk);
    }

    /** Mirrors Iron Legacy template create flow (defaults then editor snapshot rules). */
    private static void applyTemplateDefaultsThenEditorRules(World w) {
        w.setAutoSave(true);
        w.setStorm(false);
        w.setThundering(false);
        w.setDifficulty(Difficulty.NORMAL);
        w.setTime(6000L);
        try {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            w.setGameRule(GameRule.KEEP_INVENTORY, false);
        } catch (Throwable ignored) {
        }
        w.setTime(1000L);
        w.setStorm(false);
        w.setThundering(false);
        try {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        } catch (Throwable ignored) {
        }
    }

    private static void tpIntoCreativeEditor(Player p, World w) {
        var spawn = w.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        p.teleport(spawn);
        p.setGameMode(GameMode.CREATIVE);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(new ItemStack(org.bukkit.Material.AIR));
    }

    private static void unloadAndDeleteBestEffort(World w, Path worldDir) {
        try {
            if (!w.getPlayers().isEmpty()) {
                return;
            }
            Bukkit.unloadWorld(w, false);
        } catch (Throwable ignored) {
        }
        try {
            if (Files.exists(worldDir)) {
                try (var walk = Files.walk(worldDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (java.io.IOException ignored) {
                        }
                    });
                }
            }
        } catch (java.io.IOException ignored) {
        }
    }
}

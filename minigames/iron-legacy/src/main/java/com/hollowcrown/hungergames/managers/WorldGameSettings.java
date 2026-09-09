package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.List;

/**
 * Effective match settings for a template: overlays {@code world-game-settings.yml} on top of {@code config.yml}.
 */

public final class WorldGameSettings {
    private final HungerGamesPlugin plugin;
    private final ConfigurationSection overlay;

    public WorldGameSettings(HungerGamesPlugin plugin, ConfigurationSection overlay) {
        this.plugin = plugin;
        this.overlay = overlay;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private boolean ob(String key, boolean fallback) {
        return overlay != null && overlay.contains(key) ? overlay.getBoolean(key) : fallback;
    }

    private int oi(String key, int fallback) {
        return overlay != null && overlay.contains(key) ? overlay.getInt(key) : fallback;
    }

    private double od(String key, double fallback) {
        return overlay != null && overlay.contains(key) ? overlay.getDouble(key) : fallback;
    }

    private long ol(String key, long fallback) {
        return overlay != null && overlay.contains(key) ? overlay.getLong(key) : fallback;
    }

    private String os(String key, String fallback) {
        return overlay != null && overlay.contains(key) ? overlay.getString(key, fallback) : fallback;
    }

    public boolean autoAssignTeams() {
        return ob("autoAssignTeams", cfg().getBoolean("game.autoAssignTeams", true));
    }

    public BorderShrinkSchedule borderShrinkSchedule() {
    if (overlay != null && overlay.contains("borderShrinkPhases")) {
        return loadScheduleFromSection(overlay.getConfigurationSection("borderShrinkPhases"));
    }
    // backup reg border shrink (continuous)
    List<BorderShrinkPhase> legacy = new ArrayList<>();
    legacy.add(new BorderShrinkPhase(
            borderShrinkDelaySeconds(),
            borderStartSize(),
            borderEndSize(),
            borderShrinkDurationSeconds()
    ));
    return new BorderShrinkSchedule(legacy);
}

private static BorderShrinkSchedule loadScheduleFromSection(ConfigurationSection sec) {
    if (sec == null) return new BorderShrinkSchedule();
    List<BorderShrinkPhase> phases = new ArrayList<>();
    for (String key : sec.getKeys(false)) {
        if (!sec.isConfigurationSection(key)) continue;
        ConfigurationSection phaseSection = sec.getConfigurationSection(key);
        long delay = phaseSection.getLong("delaySeconds", 0);
        double start = phaseSection.getDouble("radiusStart", 400);
        double end = phaseSection.getDouble("radiusEnd", 50);
        long duration = phaseSection.getLong("shrinkDurationSeconds", 1800);
        phases.add(new BorderShrinkPhase(delay, start, end, duration));
    }
    return new BorderShrinkSchedule(phases);
}
    
    public int freezeCountdownSeconds() {
        return oi("freezeCountdownSeconds", cfg().getInt("game.countdownSeconds", 30));
    }

    /** 5..1 "DO NOT MOVE" phase before teleport to pads. */
    public boolean movementCountdownEnabled() {
        return ob("movementCountdownEnabled", cfg().getBoolean("game.movementCountdownEnabled", true));
    }

    /** After teleport: skip freeze/leash and go straight to RUNNING (parkour / survival style). */
    public boolean freeMovementAfterTeleport() {
        return ob("freeMovementAfterTeleport", cfg().getBoolean("game.freeMovementAfterTeleport", false));
    }

    public boolean launchOnMove() {
        return ob("launchOnMove", cfg().getBoolean("game.launchOnMove", false));
    }

    public boolean playCannonOnDeath() {
        return ob("playCannonOnDeath", cfg().getBoolean("game.playCannonOnDeath", true));
    }

    public boolean suppressDeathMessages() {
        return ob("suppressDeathMessages", cfg().getBoolean("game.suppressDeathMessages", true));
    }

    public boolean keepAdventureOnStart() {
        return ob("keepAdventureOnStart", cfg().getBoolean("game.keepAdventureOnStart", false));
    }

    public boolean includeOpsInGame() {
        return ob("includeOpsInGame", cfg().getBoolean("game.includeOpsInGame", false));
    }

    public boolean doDaylightCycle() {
        return ob("doDaylightCycle", cfg().getBoolean("game.doDaylightCycle", true));
    }

    public boolean doWeatherCycle() {
        return ob("doWeatherCycle", cfg().getBoolean("game.doWeatherCycle", false));
    }

    public boolean craftingEnabled() {
        return ob("craftingEnabled", HungerGamesPlugin.rules().isCraftingEnabled());
    }

    public boolean buildEnabled() {
        return ob("buildEnabled", HungerGamesPlugin.rules().isBuildEnabled());
    }

    public boolean digEnabled() {
        return ob("digEnabled", HungerGamesPlugin.rules().isDigEnabled());
    }

    public String buildMode() {
        return os("buildMode", HungerGamesPlugin.rules().getBuildMode());
    }

    public String digMode() {
        return os("digMode", HungerGamesPlugin.rules().getDigMode());
    }

    public double borderStartSize() {
        return od("borderStartSize", cfg().getDouble("worldborder.startSize"));
    }

    public double borderEndSize() {
        return od("borderEndSize", cfg().getDouble("worldborder.endSize"));
    }

    public long borderShrinkDelaySeconds() {
        return ol("borderShrinkDelaySeconds", cfg().getLong("worldborder.shrinkDelaySeconds"));
    }

    public long borderShrinkDurationSeconds() {
        return ol("borderShrinkDurationSeconds", cfg().getLong("worldborder.shrinkDurationSeconds"));
    }

    public int freezePenaltyHeight() {
        return oi("freezePenaltyHeight", cfg().getInt("game.freezePenaltyHeight", 100));
    }
}

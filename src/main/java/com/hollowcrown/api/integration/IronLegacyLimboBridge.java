package com.hollowcrown.api.integration;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hunger Games engine hooks for Hub-owned pre-match limbo (registered by HCIronLegacy).
 */
public interface IronLegacyLimboBridge {

    record TemplateRow(String key, String worldName, int maxPlayers, int teamSizeMax) {}

    record MatchInstanceRow(String worldName) {}

    record LimboZoneCorners(double x1, double y1, double z1, double x2, double y2, double z2) {}

    Optional<TemplateRow> findTemplate(String keyOrWorld);

    Optional<MatchInstanceRow> createMatchInstance(String templateKey);

    void destroyMatchInstanceIfEmpty(String worldName);

    int minimumFillPercent(String templateKey);

    Optional<LimboZoneCorners> limboZoneCorners(String templateKey);

    Location resolveLimboSpawn(TemplateRow info, String instanceWorldName);

    boolean startGameOnPreparedInstance(World instanceWorld, Set<UUID> players, boolean fromLimbo);

    String lastGameStartError();

    void markJoinIntentCounted(Player player);

    void clearJoinIntent(Player player);

    void assignTeamsForLimboLaunch(String templateKey, int teamSizeMax, Set<UUID> onlinePlayerIds);

    void returnToHubOrLobby(Player player, boolean wipeInv, boolean selectCompass);

    boolean velocityEnabled();

    boolean hubTargetIsThisServer();

    void recordKillCredit(UUID killerId);

    void sendToLobbyExact(Player player, boolean wipeInv, boolean selectCompass);

    boolean limboHostileMobSpawns();

    boolean limboPassiveMobSpawns();

    void setLimboHostileMobSpawns(boolean value);

    void setLimboPassiveMobSpawns(boolean value);

    String limboPvpSwordMaterialName();

    void migrateLimboLobbyYamlDefaultsIfNeeded();

    /** True when this backend should run hub-style limbo listeners (matches IL {@code ServerRoleManager#isHubLike}). */
    boolean isHubLikeServerRole();
}

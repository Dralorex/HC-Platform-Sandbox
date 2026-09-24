package com.hollowcrown.api.integration;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hunger Games engine hooks for Hub-owned pre-match lobby (registered by HCIronLegacy).
 */
public interface IronLegacyLobbyBridge {

    record TemplateRow(String key, String worldName, int maxPlayers, int teamSizeMax) {}

    record MatchInstanceRow(String worldName) {}

    record LobbyZoneCorners(double x1, double y1, double z1, double x2, double y2, double z2) {}

    Optional<TemplateRow> findTemplate(String keyOrWorld);

    Optional<MatchInstanceRow> createMatchInstance(String templateKey);

    void destroyMatchInstanceIfEmpty(String worldName);

    int minimumFillPercent(String templateKey);

    Optional<LobbyZoneCorners> lobbyZoneCorners(String templateKey);

    Location resolveLobbySpawn(TemplateRow info, String instanceWorldName);

    boolean startGameOnPreparedInstance(World instanceWorld, Set<UUID> players, boolean fromLobby);

    String lastGameStartError();

    void markJoinIntentCounted(Player player);

    void clearJoinIntent(Player player);

    void assignTeamsForLobbyLaunch(String templateKey, int teamSizeMax, Set<UUID> onlinePlayerIds);

    void returnToHubOrLobby(Player player, boolean wipeInv, boolean selectCompass);

    boolean velocityEnabled();

    boolean hubTargetIsThisServer();

    void recordKillCredit(UUID killerId);

    void sendToLobbyExact(Player player, boolean wipeInv, boolean selectCompass);

    boolean lobbyHostileMobSpawns();

    boolean lobbyPassiveMobSpawns();

    void setLobbyHostileMobSpawns(boolean value);

    void setLobbyPassiveMobSpawns(boolean value);

    String lobbyPvpSwordMaterialName();

    void migrateLobbyYamlDefaultsIfNeeded();

    /** True when this backend should run hub-style lobby listeners (matches IL {@code ServerRoleManager#isHubLike}). */
    boolean isHubLikeServerRole();

    /** True while the player is in an active Iron Legacy match (from launch until eliminated or match end). */
    boolean isActiveMatchParticipant(java.util.UUID playerId);
}

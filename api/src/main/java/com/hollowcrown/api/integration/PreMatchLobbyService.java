package com.hollowcrown.api.integration;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Pre-match lobby queues and arena UX (registered by HollowCrownHub).
 * Iron Legacy delegates here instead of owning session/lobby listeners.
 */
public interface PreMatchLobbyService {

    boolean sendToLobby(Player player, String templateKey);

    /**
     * Register a player in the lobby queue for an existing Hub lobby world (after teleport).
     * Session is keyed by {@code lobbyWorldName} so each {@code <template>-L#} instance has its own queue.
     */
    boolean registerHubLobbyJoin(Player player, String templateKey, String lobbyWorldName);

    /** End queue/countdown for a lobby world (e.g. when the Hub lobby instance is destroyed). */
    void endSessionForWorld(String lobbyWorldName);

    boolean isInLobby(Player player);

    String lobbyTemplateKey(Player player);

    Set<UUID> playersInSameTemplateSession(Player player);

    Location getSessionLobbySpawn(Player player);

    void leaveLobby(Player player);

    void abortAllQueuesAndKickToHub();

    boolean forceBeginTeleportCountdown(String worldOrTemplateKey);

    /**
     * After match start teleports players to spawn pads, clears the lobby arena volume in
     * {@code instanceWorldName} (air + entities). No-op when zone metadata is missing.
     */
    void clearLobbyZoneAfterSpawnPads(String instanceWorldName, String templateKey);

    boolean isInLobbyWorld(World world);

    Set<String> lobbyWorldNames();

    void prepareForLobbyJoin(Player player);

    void applyLobbyLoadout(Player player);

    boolean allowHostileMobSpawns();

    boolean allowPassiveMobSpawns();

    void setAllowHostileMobSpawns(boolean value);

    void setAllowPassiveMobSpawns(boolean value);

    /**
     * Per-template team size cap while the player is in pre-match lobby ({@code teamSizeMax} from worlds.yml),
     * or {@code -1} when not in lobby.
     */
    int teamSizeMaxFor(Player player);
}

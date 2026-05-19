package com.hollowcrown.api.integration;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Pre-match limbo queues and arena UX (registered by HollowCrownHub).
 * Iron Legacy delegates here instead of owning session/limbo listeners.
 */
public interface PreMatchLimboService {

    boolean sendToLimbo(Player player, String templateKey);

    boolean isInLimbo(Player player);

    String limboTemplateKey(Player player);

    Set<UUID> playersInSameTemplateSession(Player player);

    Location getSessionLimboSpawn(Player player);

    void leaveLimbo(Player player);

    void abortAllQueuesAndKickToHub();

    boolean forceBeginTeleportCountdown(String worldOrTemplateKey);

    boolean isInLimboWorld(World world);

    Set<String> limboWorldNames();

    void prepareForLimboJoin(Player player);

    void applyLimboLoadout(Player player);

    boolean allowHostileMobSpawns();

    boolean allowPassiveMobSpawns();

    void setAllowHostileMobSpawns(boolean value);

    void setAllowPassiveMobSpawns(boolean value);
}

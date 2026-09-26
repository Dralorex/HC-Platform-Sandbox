package com.hollowcrown.hungergames.integration;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.entity.Player;

/**
 * Post-game / lobby return routing: avoid a useless Velocity hop when the configured hub server id
 * matches this backend (same-server Hub + games).
 */
public final class HubIntegrationRouting {

    private HubIntegrationRouting() {
    }

    public static String configuredHubServerId(HungerGamesPlugin plugin) {
        return plugin.files().gameflow().getString("velocity.hubServerId", "hub-1");
    }

    /** True when {@code velocity.hubServerId} refers to this Paper server (no cross-server transfer needed). */
    public static boolean hubTargetIsThisServer(HungerGamesPlugin plugin) {
        String hub = configuredHubServerId(plugin);
        String here = plugin.serverRole().serverId();
        return hub != null && !hub.isBlank() && here != null && hub.equalsIgnoreCase(here);
    }

    /**
     * Sends the player to the configured hub: Velocity connect when proxy is enabled and the hub is a
     * different backend; otherwise {@link com.hollowcrown.hungergames.managers.LobbyManager#sendToLobbyExact}
     * (clears pre-match lobby via {@link com.hollowcrown.api.integration.PreMatchLobbyService}).
     */
    public static void returnToHubOrLobby(HungerGamesPlugin plugin, Player p, boolean wipeInv, boolean selectCompass) {
        if (p == null || !p.isOnline()) {
            return;
        }
        if (plugin.serverRole().velocityEnabled() && !hubTargetIsThisServer(plugin)) {
            String hubServer = configuredHubServerId(plugin);
            plugin.velocity().connectPlayer(p, hubServer);
        } else {
            plugin.lobby().sendToLobbyExact(p, wipeInv, selectCompass);
        }
    }
}

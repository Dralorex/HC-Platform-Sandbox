package com.hollowcrown.api.integration;

import org.bukkit.entity.Player;

/**
 * Callbacks from Hollow Crown Hub into Iron Legacy (kill tracking only).
 * Menus and parties are owned by HollowCrownHub — see {@link HubPlayerMenus}.
 * Registered by HCIronLegacy when both plugins run together.
 */
public interface IronLegacyHubBridge {

    void recordHubProtectedKill(Player killer, Player victim);
}

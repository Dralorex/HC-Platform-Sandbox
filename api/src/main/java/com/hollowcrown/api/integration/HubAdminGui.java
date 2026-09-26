package com.hollowcrown.api.integration;

import org.bukkit.entity.Player;

/**
 * Hollow Crown Hub multi-system admin GUIs. Registered by HollowCrownHub.
 */
public interface HubAdminGui {

    /** Home hub with tabs into Iron Legacy, native templates, queues, lobby tools. */
    void openShell(Player player);

    void openIronLegacyTemplates(Player player, int pageZero);

    void openHubTemplatesBrowser(Player player);

    void openQueueOverview(Player player);

    void openLobbyAdminShortcut(Player player);

    /** Hub / lobby / match server-plugin whitelist or blacklist. */
    void openPluginPolicyAdmin(Player player);
}

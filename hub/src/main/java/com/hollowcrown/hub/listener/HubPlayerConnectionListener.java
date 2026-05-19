package com.hollowcrown.hub.listener;

import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Connection lifecycle; extend for hub spawn routing, party sync, UUID-only state.
 */
public final class HubPlayerConnectionListener implements Listener {

    private final HollowCrownHubPlugin plugin;

    public HubPlayerConnectionListener(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        HubLog.info(plugin,
                "action=player_join",
                "playerUuid=" + event.getPlayer().getUniqueId(),
                "world=" + event.getPlayer().getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        HubLog.info(plugin,
                "action=player_quit",
                "playerUuid=" + event.getPlayer().getUniqueId());
    }
}

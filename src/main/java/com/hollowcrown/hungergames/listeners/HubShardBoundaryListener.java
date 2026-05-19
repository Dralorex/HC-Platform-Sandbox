package com.hollowcrown.hungergames.listeners;

import com.hollowcrown.api.integration.HubWorldApi;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;

/** Tracks shard assignment when crossing into/out of the Hollow Crown hub protection disk. */
public final class HubShardBoundaryListener implements Listener {

    private final HungerGamesPlugin plugin;

    public HubShardBoundaryListener(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Optional<HubWorldApi> apiOpt = plugin.hubWorldApi();
        if (apiOpt.isEmpty()) return;
        HubWorldApi h = apiOpt.get();
        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        boolean wasHub = h.isLocationInHubProtection(from);
        boolean isHub = h.isLocationInHubProtection(to);
        if (wasHub && !isHub) {
            plugin.lobbyShards().releasePlayer(p);
            plugin.game().clearJoinIntent(p);
        } else if (!wasHub && isHub) {
            plugin.lobbyShards().assignPlayer(p);
        }
    }
}

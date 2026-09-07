package com.hollowcrown.hub.nametag;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Drives {@link HubNametagService} from Bukkit lifecycle events. Refreshes are short delayed tasks so
 * the viewer's world/state has settled before we read it.
 */
public final class HubNametagListener implements Listener {

    private final HollowCrownHubPlugin plugin;
    private final HubNametagService nametags;

    public HubNametagListener(HollowCrownHubPlugin plugin, HubNametagService nametags) {
        this.plugin = plugin;
        this.nametags = nametags;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        refreshSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        refreshSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        refreshSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        nametags.onQuit(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                nametags.onSneakToggle(p);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void refreshSoon(Player p) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                nametags.onJoinOrWorldChange(p);
            }
        }.runTaskLater(plugin, 3L);
    }
}

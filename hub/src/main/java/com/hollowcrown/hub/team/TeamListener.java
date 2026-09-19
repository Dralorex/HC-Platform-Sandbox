package com.hollowcrown.hub.team;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class TeamListener implements Listener {

    @EventHandler
    public void playerJoin(PlayerJoinEvent event){
        //HollowCrownHubPlugin.getPlugin(HollowCrownHubPlugin.class).getLogger().info("Sending team data to " + event.getPlayer().getName());
        //HollowCrownHubPlugin plugin = HollowCrownHubPlugin.getPlugin(HollowCrownHubPlugin.class);
        //Bukkit.getScheduler().runTaskLater(plugin, () -> {
        //    TeamManager.sendFakeTeamPacketsTo(event.getPlayer());
        //    plugin.getLogger().info("Sending team data to " + event.getPlayer().getName() + ", loader: " + TeamManager.class.getClassLoader() + " hash: " + System.identityHashCode(TeamManager.instances) + ", all instances: " + TeamManager.instances);
        //}, 2);
        TeamManager.sendFakeTeamPacketsTo(event.getPlayer());
    }

    @EventHandler
    public void playerDamaged(EntityDamageEvent event){
        if(event.getEntity() instanceof Player player && event.getDamageSource().getCausingEntity() instanceof Player other){
            TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(player.getWorld());
            if(teamManagerInstance == null)
                return;
            TeamManager.Team team = teamManagerInstance.findPlayerTeam(player);
            TeamManager.Team otherTeam = teamManagerInstance.findPlayerTeam(other);
            if(team == null || otherTeam == null)
                return;
            if(team == otherTeam && !teamManagerInstance.friendlyFire) {
                event.setCancelled(true);
                return;
            }
            if(team.allies.contains(otherTeam) && !teamManagerInstance.allyFriendlyFire) {
                event.setCancelled(true);
                return;
            }
        }
    }
}

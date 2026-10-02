package com.hollowcrown.hub.team;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class TeamListener implements Listener {

    @EventHandler
    public void playerJoin(PlayerJoinEvent event){
        // Sync this viewer only — do not broadcast sendAllFakeTeamPackets on join.
        TeamManager.sendFakeTeamPacketsTo(event.getPlayer());
    }

    @EventHandler
    public void playerChangedWorld(PlayerChangedWorldEvent event){
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

package com.hollowcrown.hub.team;

import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Optional;
import java.util.UUID;

public final class NametagManager {

    private final TeamService teamService;

    public NametagManager(TeamService teamService) {
        this.teamService = teamService;
    }

    public void updateAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateViewer(viewer);
        }
    }

    public void updateViewer(Player viewer) {
        Scoreboard board = viewer.getScoreboard();

        for (Player target : Bukkit.getOnlinePlayers()) {

            String teamName = "nt_" + target.getUniqueId().toString().replace("-", "");

            Team team = board.getTeam(teamName);

            if (team == null) {
                team = board.registerNewTeam(teamName);
            }

            if (!team.hasEntry(target.getName())) {
                team.addEntry(target.getName());
            }

            if (isAlly(viewer.getUniqueId(), target.getUniqueId())) {
                team.color(org.bukkit.ChatColor.GREEN);
            } else {
                team.color(org.bukkit.ChatColor.RED);
            }
        }
    }

    private boolean isAlly(UUID viewer, UUID target) {

        Optional<TeamView> a = teamService.teamOf(viewer);
        Optional<TeamView> b = teamService.teamOf(target);

        return a.isPresent()
                && b.isPresent()
                && a.get().name().equalsIgnoreCase(b.get().name());
    }
}

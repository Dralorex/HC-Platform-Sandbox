package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

/** Renders a right-side sidebar leaderboard and refreshes every 10 seconds. */
public class KillLeaderboardSidebarManager implements Listener {
    private static final String OBJECTIVE_ID = "il_kills";

    private final HungerGamesPlugin plugin;

    public KillLeaderboardSidebarManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        startRefreshTask();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTo(event.getPlayer()), 20L);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyTo(player);
        }
    }

    private void startRefreshTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, 200L);
    }

    private void applyTo(Player player) {
        if (plugin.game().getState() == GameManager.State.FREEZE) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(OBJECTIVE_ID, Criteria.DUMMY, Component.text("§6Kills Leaderboard"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<KillLeaderboardManager.Entry> top = plugin.leaderboard().top(10);
        int score = 15;
        if (top.isEmpty()) {
            obj.getScore("§7No kills yet").setScore(score--);
        } else {
            int rank = 1;
            for (KillLeaderboardManager.Entry entry : top) {
                String name = trimName(entry.name());
                String line = "§e#" + rank + " §f" + name + " §7" + entry.kills();
                obj.getScore(line).setScore(score--);
                rank++;
                if (score <= 0) break;
            }
        }

        obj.getScore(" ").setScore(1);
        obj.getScore("§7Your kills: §c" + plugin.leaderboard().getKills(player.getUniqueId())).setScore(0);
        player.setScoreboard(board);
    }

    private String trimName(String input) {
        if (input == null || input.isBlank()) return "Unknown";
        return input.length() > 10 ? input.substring(0, 10) : input;
    }
}


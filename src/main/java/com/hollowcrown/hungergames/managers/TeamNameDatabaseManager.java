package com.hollowcrown.hungergames.managers;

import com.hollowcrown.hungergames.HungerGamesPlugin;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility adapter: Hub TeamService owns the team name database.
 */
public class TeamNameDatabaseManager {
    private final HungerGamesPlugin plugin;

    public TeamNameDatabaseManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // Hub TeamService owns persistence/state.
    }

    public void save() {
        // Hub TeamService owns persistence/state.
    }

    /** Sorted copy for GUIs. */
    public List<String> namesAlphabetical() {
        return Collections.unmodifiableList(new ArrayList<>(plugin.teams().teamNameDatabaseAlphabetical()));
    }

    /** False if blank, duplicate in DB, or name matches an existing team. */
    public boolean tryAddName(String raw) {
        return plugin.teams().tryAddTeamNameToDatabase(raw);
    }

    /** First alphabetical DB name not used by a team; null if none. */
    public String takeUnusedNameForNewTeam() {
        return plugin.teams().takeUnusedTeamNameForNewTeam();
    }

    /** Fallback when database exhausted. */
    public String generateSequentialTeamName() {
        return plugin.teams().takeUnusedTeamNameForNewTeam();
    }

    public ChatColor pickUnusedChatColor() {
        ChatColor[] candidates = {
                ChatColor.BLUE, ChatColor.GREEN, ChatColor.RED, ChatColor.GOLD,
                ChatColor.AQUA, ChatColor.YELLOW, ChatColor.LIGHT_PURPLE, ChatColor.WHITE,
                ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
                ChatColor.DARK_RED, ChatColor.DARK_PURPLE
        };
        var used = plugin.teams().getTeams().stream()
                .map(com.hollowcrown.hungergames.model.Team::getColor).collect(java.util.stream.Collectors.toSet());
        for (ChatColor c : candidates) {
            if (!used.contains(c) && c.isColor()) return c;
        }
        return ChatColor.WHITE;
    }
}

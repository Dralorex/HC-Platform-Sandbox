package com.hollowcrown.hungergames.integration;

import com.hollowcrown.api.chat.TeamChatProfile;
import com.hollowcrown.api.chat.TeamChatProvider;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.model.Team;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Exposes Iron Legacy team name + Bukkit {@link ChatColor} for external chat plugins via {@link TeamChatProvider}.
 */
public final class IronLegacyTeamChatProvider implements TeamChatProvider {

    private final HungerGamesPlugin plugin;

    public IronLegacyTeamChatProvider(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<TeamChatProfile> profile(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Team t = plugin.teams().getTeamOf(playerId);
        if (t == null) {
            return Optional.empty();
        }
        String prefix = "[" + t.getName() + "] ";
        String legacy = String.valueOf(t.getColor());
        return Optional.of(new TeamChatProfile(prefix, legacy));
    }

    /** Convenience for in-process chat mods that have a {@link Player}. */
    public Optional<TeamChatProfile> profile(Player player) {
        return player == null ? Optional.empty() : profile(player.getUniqueId());
    }
}

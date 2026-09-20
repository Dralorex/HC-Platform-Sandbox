package com.hollowcrown.hub.party;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;
import java.util.UUID;

/** Adventure components for party chat (player names use team color when set). */
public final class HubPartyText {

    private static final TextColor BRONZE = TextColor.color(0xCD7F32);

    private HubPartyText() {
    }

    public static Component coloredPlayerName(HollowCrownHubPlugin plugin, Player player) {
        if (player == null) {
            return Component.text("Unknown", BRONZE);
        }
        return coloredPlayerName(plugin, player.getName(), player.getUniqueId());
    }

    public static Component coloredPlayerName(HollowCrownHubPlugin plugin, String name, UUID playerId) {
        String safe = name == null || name.isBlank() ? "Unknown" : name;
        return Component.text(safe, resolveNameColor(playerId));
    }

    private static TextColor resolveNameColor(UUID playerId) {
        if (playerId == null)
            return BRONZE;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null)
            return BRONZE;
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(player.getWorld());
        if (teamManagerInstance == null)
            return BRONZE;
        TeamManager.Team team = teamManagerInstance.findPlayerTeam(playerId);
        if(team == null)
            return BRONZE;
        if (team.color != null) {
            return TextColor.color(team.color.asRGB());
        }
        return BRONZE;
    }

    private static TextColor legacyToTextColor(ChatColor legacy) {
        return switch (legacy) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            default -> NamedTextColor.WHITE;
        };
    }
}

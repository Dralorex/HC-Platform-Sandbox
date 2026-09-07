package com.hollowcrown.hub.party;

import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
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
        if (playerId == null) {
            return BRONZE;
        }
        RegisteredServiceProvider<TeamService> reg =
                Bukkit.getServicesManager().getRegistration(TeamService.class);
        if (reg == null) {
            return BRONZE;
        }
        Optional<TeamView> team = reg.getProvider().teamOf(playerId);
        if (team.isEmpty()) {
            return BRONZE;
        }
        TeamView t = team.get();
        if (t.displayRgb() != null) {
            return TextColor.color(t.displayRgb());
        }
        ChatColor legacy = t.legacyColor();
        if (legacy != null && legacy.isColor()) {
            return legacyToTextColor(legacy);
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

package com.hollowcrown.hub.team;

import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.policy.HubTemplatePolicyService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.EnumMap;
import java.util.Map;

/**
 * Fixed team prefix colors for {@link HubTemplatePolicyService.ColorMode#TEAM} templates.
 * Player name colors (green / red / blue / orange) are applied per-viewer in {@code HubNametagService}.
 */
public final class HubTeamAutoColors {

    /** Distinct prefix RGB values assigned in order (Team 1 → red, Team 2 → green, …). */
    private static final int[] PREFIX_RGB = {
            0xFF5555,
            0x55FF55,
            0x5555FF,
            0xFFFF55,
            0xFF55FF,
            0x55FFFF,
            0xFFAA00,
            0xAA00AA,
            0x00AAAA,
            0xFF5555,
    };

    private static final Map<ChatColor, int[]> LEGACY_RGB = new EnumMap<>(ChatColor.class);

    static {
        LEGACY_RGB.put(ChatColor.BLACK, new int[]{0, 0, 0});
        LEGACY_RGB.put(ChatColor.DARK_BLUE, new int[]{0, 0, 170});
        LEGACY_RGB.put(ChatColor.DARK_GREEN, new int[]{0, 170, 0});
        LEGACY_RGB.put(ChatColor.DARK_AQUA, new int[]{0, 170, 170});
        LEGACY_RGB.put(ChatColor.DARK_RED, new int[]{170, 0, 0});
        LEGACY_RGB.put(ChatColor.DARK_PURPLE, new int[]{170, 0, 170});
        LEGACY_RGB.put(ChatColor.GOLD, new int[]{255, 170, 0});
        LEGACY_RGB.put(ChatColor.GRAY, new int[]{170, 170, 170});
        LEGACY_RGB.put(ChatColor.DARK_GRAY, new int[]{85, 85, 85});
        LEGACY_RGB.put(ChatColor.BLUE, new int[]{85, 85, 255});
        LEGACY_RGB.put(ChatColor.GREEN, new int[]{85, 255, 85});
        LEGACY_RGB.put(ChatColor.AQUA, new int[]{85, 255, 255});
        LEGACY_RGB.put(ChatColor.RED, new int[]{255, 85, 85});
        LEGACY_RGB.put(ChatColor.LIGHT_PURPLE, new int[]{255, 85, 255});
        LEGACY_RGB.put(ChatColor.YELLOW, new int[]{255, 255, 85});
        LEGACY_RGB.put(ChatColor.WHITE, new int[]{255, 255, 255});
    }

    private HubTeamAutoColors() {
    }

    public record AutoColor(ChatColor legacyColor, Integer displayRgb) {
    }

    public static boolean isLegacyColorMode(HollowCrownHubPlugin plugin, Player player) {
        return isLegacyColorMode(plugin, limboTemplateKey(player));
    }

    public static boolean isLegacyColorMode(HollowCrownHubPlugin plugin, String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            return false;
        }
        return plugin.templatePolicies().configFor(templateKey).colorMode()
                == HubTemplatePolicyService.ColorMode.LEGACY;
    }

    public static String limboTemplateKey(Player player) {
        if (player == null) {
            return null;
        }
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        if (reg == null) {
            return null;
        }
        return reg.getProvider().limboTemplateKey(player);
    }

    public static AutoColor nextAvailable(TeamService teams) {
        if (teams == null) {
            return new AutoColor(ChatColor.RED, PREFIX_RGB[0]);
        }
        for (int rgb : PREFIX_RGB) {
            ChatColor legacy = nearestChatColor(rgb);
            if (!teams.colorTaken(legacy)) {
                return new AutoColor(legacy, rgb);
            }
        }
        int rgb = PREFIX_RGB[teams.teams().size() % PREFIX_RGB.length];
        return new AutoColor(nearestChatColor(rgb), rgb);
    }

    public static ChatColor nearestChatColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        ChatColor best = ChatColor.WHITE;
        int bestD = Integer.MAX_VALUE;
        for (Map.Entry<ChatColor, int[]> e : LEGACY_RGB.entrySet()) {
            int[] v = e.getValue();
            int dr = r - v[0];
            int dg = g - v[1];
            int db = b - v[2];
            int d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = e.getKey();
            }
        }
        return best;
    }
}

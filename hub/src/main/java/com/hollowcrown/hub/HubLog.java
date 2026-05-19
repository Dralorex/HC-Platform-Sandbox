package com.hollowcrown.hub;

import org.bukkit.plugin.Plugin;

import java.util.StringJoiner;

/**
 * Structured key=value logs; extend with templateKey, lobbyId, partyId as features land.
 */
public final class HubLog {

    private HubLog() {
    }

    public static void info(Plugin plugin, String... pairs) {
        plugin.getLogger().info(join(pairs));
    }

    public static void warn(Plugin plugin, String... pairs) {
        plugin.getLogger().warning(join(pairs));
    }

    public static void severe(Plugin plugin, String... pairs) {
        plugin.getLogger().severe(join(pairs));
    }

    private static String join(String... pairs) {
        StringJoiner j = new StringJoiner(" ");
        for (String p : pairs) {
            j.add(p);
        }
        return j.toString();
    }
}

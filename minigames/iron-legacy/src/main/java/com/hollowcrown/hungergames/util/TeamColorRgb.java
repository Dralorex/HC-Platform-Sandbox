package com.hollowcrown.hungergames.util;

import org.bukkit.ChatColor;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/** Map RGB picks to legacy {@link ChatColor} for scoreboard compatibility. */
public final class TeamColorRgb {
    private static final Map<ChatColor, int[]> RGB = new EnumMap<>(ChatColor.class);

    static {
        RGB.put(ChatColor.BLACK, new int[]{0, 0, 0});
        RGB.put(ChatColor.DARK_BLUE, new int[]{0, 0, 170});
        RGB.put(ChatColor.DARK_GREEN, new int[]{0, 170, 0});
        RGB.put(ChatColor.DARK_AQUA, new int[]{0, 170, 170});
        RGB.put(ChatColor.DARK_RED, new int[]{170, 0, 0});
        RGB.put(ChatColor.DARK_PURPLE, new int[]{170, 0, 170});
        RGB.put(ChatColor.GOLD, new int[]{255, 170, 0});
        RGB.put(ChatColor.GRAY, new int[]{170, 170, 170});
        RGB.put(ChatColor.DARK_GRAY, new int[]{85, 85, 85});
        RGB.put(ChatColor.BLUE, new int[]{85, 85, 255});
        RGB.put(ChatColor.GREEN, new int[]{85, 255, 85});
        RGB.put(ChatColor.AQUA, new int[]{85, 255, 255});
        RGB.put(ChatColor.RED, new int[]{255, 85, 85});
        RGB.put(ChatColor.LIGHT_PURPLE, new int[]{255, 85, 255});
        RGB.put(ChatColor.YELLOW, new int[]{255, 255, 85});
        RGB.put(ChatColor.WHITE, new int[]{255, 255, 255});
    }

    private TeamColorRgb() {
    }

    public static ChatColor nearestChatColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        ChatColor best = ChatColor.WHITE;
        int bestD = Integer.MAX_VALUE;
        for (Map.Entry<ChatColor, int[]> e : RGB.entrySet()) {
            int[] v = e.getValue();
            int d = dist2(r, g, b, v[0], v[1], v[2]);
            if (d < bestD) {
                bestD = d;
                best = e.getKey();
            }
        }
        return best;
    }

    private static int dist2(int r1, int g1, int b1, int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return dr * dr + dg * dg + db * db;
    }

    /**
     * Parses {@code #RRGGBB}, {@code RRGGBB}, or {@code #RGB} (expanded to doubled digits).
     *
     * @return 24-bit RGB or empty
     */
    public static OptionalInt parseHexRgb(String raw) {
        if (raw == null) return OptionalInt.empty();
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) {
            return OptionalInt.empty();
        }
        int rgb;
        try {
            rgb = Integer.parseInt(s, 16);
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
        if (rgb < 0 || rgb > 0xFFFFFF) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(rgb);
    }
}

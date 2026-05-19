package com.hollowcrown.hungergames.util;

import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.managers.TeamManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Limbo / lobby team display names: length, character set, profanity (config list), uniqueness.
 */
public final class TeamNamingRules {
    public static final int MAX_NAME_LENGTH = 20;

    private static final List<String> FALLBACK_BASES = List.of(
            "Squad", "Crew", "Unit", "Team", "Cell", "Fireteam");

    private TeamNamingRules() {
    }

    /** Strip legacy {@code §} / {@code &} style codes for validation and length. */
    public static String stripLegacyColorCodes(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        char[] ch = raw.toCharArray();
        for (int i = 0; i < ch.length; i++) {
            char c = ch[i];
            if (c == '§' || c == '&') {
                if (i + 1 < ch.length) {
                    i++;
                }
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * @return empty if valid, otherwise a short player-facing English reason
     */
    public static Optional<String> validateNewTeamName(String raw, HungerGamesPlugin plugin, Player player) {
        String stripped = stripLegacyColorCodes(raw).trim();
        if (stripped.isEmpty()) {
            return Optional.of("Team name cannot be empty.");
        }
        if (stripped.length() > MAX_NAME_LENGTH) {
            return Optional.of("Team name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (!allowedCharacters(stripped)) {
            return Optional.of("Use only letters, numbers, spaces, and _ - ' .");
        }
        if (containsProfanity(stripped, plugin)) {
            return Optional.of("That name is not allowed.");
        }
        TeamManager teams = plugin.teams();
        if (teams.nameTaken(stripped)) {
            return Optional.of("That name is already in use.");
        }
        if (player != null && teams.nameTakenByOtherTeamInSameLimbo(player, stripped)) {
            return Optional.of("Another team in this limbo already uses that name.");
        }
        return Optional.empty();
    }

    /**
     * Team name when binding a Hub party at match start if the leader never finished a custom naming flow.
     */
    public static String resolveBindTeamName(HungerGamesPlugin plugin, PartyView party, TeamManager teams) {
        String candidate = stripLegacyColorCodes(party.displayName()).trim();
        if (candidate.isEmpty()) {
            candidate = "Party";
        }
        if (candidate.length() > MAX_NAME_LENGTH) {
            candidate = candidate.substring(0, MAX_NAME_LENGTH);
        }
        if (!allowedCharacters(candidate) || containsProfanity(candidate, plugin)) {
            candidate = randomFallbackBase();
        }
        String name = candidate;
        int bump = 2;
        while (teams.nameTaken(name)) {
            String suffix = " " + bump++;
            int keep = Math.max(1, MAX_NAME_LENGTH - suffix.length());
            String base = candidate.length() > keep ? candidate.substring(0, keep) : candidate;
            name = (base + suffix);
            if (name.length() > MAX_NAME_LENGTH) {
                name = name.substring(0, MAX_NAME_LENGTH);
            }
        }
        return name;
    }

    private static String randomFallbackBase() {
        return FALLBACK_BASES.get(ThreadLocalRandom.current().nextInt(FALLBACK_BASES.size()));
    }

    private static boolean allowedCharacters(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '_' || c == '-' || c == '\'' || c == '.') {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean containsProfanity(String strippedDisplay, HungerGamesPlugin plugin) {
        List<String> words = plugin.getConfig().getStringList("limboTeamNaming.blockedWords");
        if (words == null || words.isEmpty()) {
            return false;
        }
        String hay = strippedDisplay.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (w == null || w.isBlank()) continue;
            String needle = w.toLowerCase(Locale.ROOT).trim();
            if (needle.isEmpty()) continue;
            if (hay.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}

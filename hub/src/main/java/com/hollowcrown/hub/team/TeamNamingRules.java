package com.hollowcrown.hub.team;

import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Limbo / lobby team display names: length, character set, profanity (config list), uniqueness.
 */
public final class TeamNamingRules {
    public static final int MAX_NAME_LENGTH = 20;

    private static final List<String> FALLBACK_BASES = List.of(
            "Squad", "Crew", "Unit", "Team", "Cell", "Fireteam");

    private static final String[] RANDOM_NAME_PRIMARIES = new String[]{
            "! Squad", "! Crew", "! Unit", "! Team", "! Collective", "! Fireteam", "! Army", "Voice of !", "! Guild", "! Syndicate"
    };

    //Assorted names for teams based on categories (currently 66, with 10 primaries that makes 660 unique names, making a ~0.6% chance of a joke name per team, or a ~26% chance of a joke name showing up among 50 randomly generated names. As most teams won't have randomly generated names, it's only around a 6% chance with 10 random names.
    private static final String[] RANDOM_NAME_SECONDARIES = new String[]{
            //Trees
            "Oak", "Birch", "Spruce", "Dark Oak", "Pale Oak", "Acacia", "Jungle", "Mangrove", "Warped", "Crimson", "Azalea",
            //Stones
            "Stone", "Granite", "Andesite", "Diorite", "Calcite", "Deepslate", "Basalt", "Netherrack", "Blackstone", "Bedrock",
            //Resources, including some cool sounding non-minecraft ones (placed at beginning)
            "Steel", "Bismuth", "Copper", "Iron", "Gold", "Diamond", "Emerald", "Lapis", "Redstone", "Coal", "Netherite", "Amethyst", "Quartz",
            //Planty things
            "Moss", "Leaf", "Bamboo", "Kelp", "Coral", "Mushroom", "Fungus", "Spore", "Blossom", "Vine", "Berry", "Flower",
            //Turf
            "Grass", "Dirt", "Sand", "Nylium", "Podzol", "Mycelium", "Terracotta",
            //Equipment
            "Sword", "Shield", "Pickaxe", "Shovel", "Axe", "Hoe", "Helmet", "Chestplate", "Leggings", "Boots", "Bow",
            //Joke stand ins (C Team, Team Rocket, Team Fortress, Team Avatar)
            "Sea", "Firework", "Bastion", "Armor Stand"

    };

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
    public static Optional<String> validateNewTeamName(String raw, HollowCrownHubPlugin plugin, Player player) {
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
        //TeamManager teams = plugin.teams();
        if (TeamManager.getOrCreateWorldInstance(player.getWorld()).teams.containsKey(stripped.toLowerCase(Locale.ROOT))) {
            return Optional.of("That name is already in use.");
        }
        //if (player != null && teams.nameTakenByOtherTeamInSameLimbo(player, stripped)) {
        if(player != null && TeamManager.getOrCreateWorldInstance(player.getWorld()).findPlayerTeam(player) != TeamManager.getOrCreateWorldInstance(player.getWorld()).teams.get(stripped.toLowerCase(Locale.ROOT))) {
            return Optional.of("You are already on a team!");
        }
        return Optional.empty();
    }

    public static Optional<String> validateNewTeamNameForRenaming(String raw, HollowCrownHubPlugin plugin, Player player) {
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
        //TeamManager teams = plugin.teams();
        if (TeamManager.getOrCreateWorldInstance(player.getWorld()).teams.containsKey(stripped.toLowerCase(Locale.ROOT))) {
            return Optional.of("That name is already in use.");
        }
        //if (player != null && teams.nameTakenByOtherTeamInSameLimbo(player, stripped)) {
        if(TeamManager.getOrCreateWorldInstance(player.getWorld()).findPlayerTeam(player) == null) {
            return Optional.of("You do not have a team!");
        }
        return Optional.empty();
    }

    /**
     * Team name when binding a Hub party at match start if the leader never finished a custom naming flow.
     */
    public static String resolveBindTeamName(HollowCrownHubPlugin plugin, PartyView party, TeamManager.TeamManagerInstance teams) {
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
        while (teams.teams.containsKey(name.toLowerCase(Locale.ROOT))) {
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

    //[Hydraulic] generate a valid "random" name
    public static String randomTeamName(TeamManager.TeamManagerInstance teams) {
        //if(!teams.teams.containsKey("C Team".toLowerCase()))
        //    return "C Team";
        Random random = new Random();
        int tries = 0;
        String name = RANDOM_NAME_PRIMARIES[random.nextInt(0, RANDOM_NAME_PRIMARIES.length)];
        name = name.replace("!", RANDOM_NAME_SECONDARIES[random.nextInt(0, RANDOM_NAME_SECONDARIES.length)]);
        if(name.equals("Team Sea"))
            name = "C Team";
        else if(name.equals("Team Firework"))
            name = "Team Rocket";
        else if(name.equals("Team Bastion"))
            name = "Team Fortress";
        else if(name.equals("Team Armor Stand"))
            name = "Team Avatar";
        while (teams.teams.containsKey(name.toLowerCase())) {
            if(tries > 1000) { //Look, at this point something has gone wrong. Switch to a numerical approach instead of processing for forever.
                int i = 1;
                while (teams.teams.containsKey(("Team " + i).toLowerCase()))
                    i++;
                return "Team " + i;
            }
            name = RANDOM_NAME_PRIMARIES[random.nextInt(0, RANDOM_NAME_PRIMARIES.length)];
            name = name.replace("!", RANDOM_NAME_SECONDARIES[random.nextInt(0, RANDOM_NAME_SECONDARIES.length)]);
            if(name.equals("Team Sea"))
                name = "C Team";
            else if(name.equals("Team Firework"))
                name = "Team Rocket";
            else if(name.equals("Team Bastion"))
                name = "Team Fortress";
            else if(name.equals("Team Armor Stand"))
                name = "Team Avatar";
            tries++;
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

    private static boolean containsProfanity(String strippedDisplay, HollowCrownHubPlugin plugin) {
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

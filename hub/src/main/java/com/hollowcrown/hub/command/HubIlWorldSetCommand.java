package com.hollowcrown.hub.command;

import com.hollowcrown.api.integration.IronLegacyTemplateAdmin;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;

/**
 * Iron Legacy {@code worlds.yml} field updates via {@code /hch template set …} when the map token resolves.
 */
public final class HubIlWorldSetCommand {

    private HubIlWorldSetCommand() {
    }

    /** Reserved world-field names (not Hub mechanic ids). */
    public static boolean isIlWorldField(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "display", "enabled", "maxplayers", "teamsize", "icon", "minimumpercentage", "gamerule" -> true;
            default -> false;
        };
    }

    /** {@code /hch template set limbo spawn} — player must stand in template limbo zone. */
    public static boolean applyLimboSpawn(CommandSender sender, HollowCrownHubPlugin plugin) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (plugin.limboLayout().setLimboSpawnHere(p)) {
            p.sendMessage(Component.text("Limbo spawn set.", NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text(
                    "Failed — stand in the template world inside the saved limbo zone (/hch template limbo wand first).",
                    NamedTextColor.RED));
        }
        return true;
    }

    /**
     * {@code /hch template set <worldFolder|yamlKey> <field> …} for IL worlds.yml rows.
     * Preconditions: caller verified IL loaded and {@link HubIlWorldInput#matchesAnyTemplate} and {@link #isIlWorldField}.
     */
    public static boolean applyWorldFields(CommandSender sender, IronLegacyTemplateAdmin admin, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(Component.text(
                    "Usage: /hch template set <worldFolder> <display|enabled|maxplayers|teamsize|icon|minimumpercentage|gamerule> …",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(
                    "<worldFolder> = folder name of the map on disk (worldName in worlds.yml).",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                    "Or: /hch template limbo spawn  (same as set limbo spawn)",
                    NamedTextColor.GRAY));
            return true;
        }
        Optional<String> keyOpt = HubIlWorldInput.resolveCanonicalKey(admin, args[2]);
        if (keyOpt.isEmpty() && admin.ensureWorldTemplateRow(args[2])) {
            keyOpt = HubIlWorldInput.resolveCanonicalKey(admin, args[2]);
        }
        if (keyOpt.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Unknown Iron Legacy map \"" + args[2]
                            + "\". Drop into hch-worlds/templates/ then /hch template worlds discover, "
                            + "or use a key from /hch template worlds list.",
                    NamedTextColor.RED));
            return true;
        }
        String key = keyOpt.get();
        String field = args[3].toLowerCase(Locale.ROOT);
        boolean ok = false;
        switch (field) {
            case "display" -> {
                String value = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                if (value.isBlank()) {
                    sender.sendMessage(Component.text("Display name cannot be empty.", NamedTextColor.RED));
                    return true;
                }
                ok = admin.setDisplayName(key, value);
            }
            case "enabled" -> {
                Boolean on = parseBool(args[4]);
                if (on == null) {
                    sender.sendMessage(Component.text("Use true/false.", NamedTextColor.RED));
                    return true;
                }
                ok = admin.setEnabled(key, on);
            }
            case "maxplayers" -> {
                int v = parseInt(args[4]);
                if (v < 1) {
                    sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
                    return true;
                }
                ok = admin.setMaxPlayers(key, v);
            }
            case "teamsize" -> {
                int v = parseInt(args[4]);
                if (v < 1) {
                    sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
                    return true;
                }
                ok = admin.setTeamSizeMax(key, v);
            }
            case "icon" -> ok = admin.setIconKey(key, args[4]);
            case "minimumpercentage" -> {
                int v = parseInt(args[4]);
                ok = admin.setMinimumPercentage(key, v);
            }
            case "gamerule" -> {
                if (args.length < 6) {
                    sender.sendMessage(Component.text(
                            "Usage: /hch template set <worldFolder> gamerule <doDaylightCycle|doWeatherCycle> <true|false>",
                            NamedTextColor.YELLOW));
                    return true;
                }
                Boolean on = parseBool(args[5]);
                if (on == null) {
                    sender.sendMessage(Component.text("Use true/false.", NamedTextColor.RED));
                    return true;
                }
                ok = admin.setPublishGameRule(key, args[4], on);
            }
            default -> {
                sender.sendMessage(Component.text("Unknown field.", NamedTextColor.RED));
                return true;
            }
        }
        if (ok) {
            sender.sendMessage(Component.text("Updated (Iron Legacy worlds.yml).", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                    "Update failed — check values (e.g. gamerule: doDaylightCycle / doWeatherCycle, icon key exists).",
                    NamedTextColor.RED));
        }
        return true;
    }

    private static Boolean parseBool(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "t", "on", "yes" -> true;
            case "false", "f", "off", "no" -> false;
            default -> null;
        };
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

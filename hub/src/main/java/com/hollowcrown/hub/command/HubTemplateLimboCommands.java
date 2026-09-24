package com.hollowcrown.hub.command;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * {@code /hch template limbo …} — Hub-owned limbo zone wand + spawn (writes HCIronLegacy {@code worlds.yml}).
 */
public final class HubTemplateLimboCommands {

    private HubTemplateLimboCommands() {
    }

    /** {@code args[0]} {@code template}, {@code args[1]} {@code limbo}. */
    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            help(sender);
            return true;
        }
        String verb = args[2].toLowerCase(Locale.ROOT);
        return switch (verb) {
            case "help", "?" -> {
                help(sender);
                yield true;
            }
            case "wand" -> {
                sender.sendMessage(Component.text(
                        "The corner wand is gone. Use /hch template lobby wand",
                        NamedTextColor.YELLOW));
                yield true;
            }
            case "spawn" -> HubIlWorldSetCommand.applyLimboSpawn(sender, plugin);
            default -> {
                sender.sendMessage(Component.text("Unknown limbo subcommand. Try /hch template limbo help", NamedTextColor.RED));
                yield true;
            }
        };
    }

    /** {@code /hch template lobby wand} */
    public static boolean handleLobbyWand(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("You need hollowcrownhub.admin.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("wand")) {
            sender.sendMessage(Component.text("Usage: /hch template lobby wand", NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only (wand goes into inventory).", NamedTextColor.RED));
            return true;
        }
        if (plugin.limboLayout().giveLimboWand(p)) {
            p.sendMessage(Component.text(
                    "Lobby circle wand given. Left=center, Right=edge, Shift+scroll cycles tip, Shift-right saves. Spawn: /hch template limbo spawn",
                    NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Could not give wand (inventory full?).", NamedTextColor.RED));
        }
        return true;
    }

    private static void help(CommandSender sender) {
        sender.sendMessage(Component.text("Limbo setup", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                " /hch template lobby wand — circle zone (center, edge, Shift+scroll, shift-right save)",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " spawn — save limbo spawn at your feet (inside the saved circle, template world)",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Alias: /hch template set limbo spawn  — same as spawn",
                NamedTextColor.DARK_GRAY));
    }
}

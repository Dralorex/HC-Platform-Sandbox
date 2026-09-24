package com.hollowcrown.hub.command;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * {@code /hch template lobby …} — circle zone wand + spawn (writes HCIronLegacy {@code worlds.yml}).
 */
public final class HubTemplateLobbyCommands {

    private HubTemplateLobbyCommands() {
    }

    /** {@code args[0]} {@code template}, {@code args[1]} {@code lobby}. */
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
            case "wand" -> giveWand(plugin, sender);
            case "spawn" -> HubIlWorldSetCommand.applyLobbySpawn(sender, plugin);
            default -> {
                sender.sendMessage(Component.text("Unknown lobby subcommand. Try /hch template lobby help", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private static boolean giveWand(HollowCrownHubPlugin plugin, CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only (wand goes into inventory).", NamedTextColor.RED));
            return true;
        }
        if (plugin.lobbyLayout().giveLobbyWand(p)) {
            p.sendMessage(Component.text(
                    "Lobby circle wand given. Left=center, Right=edge, Shift+scroll cycles tip, Shift-right saves. Spawn: /hch template lobby spawn",
                    NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Could not give wand (inventory full?).", NamedTextColor.RED));
        }
        return true;
    }

    private static void help(CommandSender sender) {
        sender.sendMessage(Component.text("Lobby zone (/hch template lobby)", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                " wand — circle zone (center, edge, Shift+scroll, shift-right save)",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " spawn — save lobby spawn at your feet (inside the saved circle, template world)",
                NamedTextColor.GRAY));
    }
}

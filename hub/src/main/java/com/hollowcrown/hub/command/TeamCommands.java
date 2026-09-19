package com.hollowcrown.hub.command;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.HubLog;
import com.hollowcrown.hub.party.HubPartyListener;
import com.hollowcrown.hub.party.HubPartyService;
import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.*;

import static com.hollowcrown.hub.team.TeamManager.allianceAddCommand;

/**
 * {@code /hch team …} — Hub-owned teams (UUID ids).
 */
public final class TeamCommands {

    //TO/DO: rewrite this copy of HubPartyCommands for use in the teams system, as seen in team.TeamManager

    private TeamCommands() {
    }

    public static boolean handle(HollowCrownHubPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("hollowcrownhub.team")) {
            sender.sendMessage(Component.text("You cannot use team commands here.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            usage(sender);
            return true;
        }
        HubPartyService parties = plugin.partyService();
        String sub = args[1].toLowerCase(Locale.ROOT);
        if(sender instanceof Player player) {
            List<String> arrayList = Arrays.stream(args).sequential().skip(3).toList();
            String teamName = String.join(" ", arrayList);
            return switch (sub) {
                case "ally" -> {
                    String sub2 = args[2].toLowerCase(Locale.ROOT);
                    yield switch (sub2) {
                        case "add" -> TeamManager.allianceAddCommand(player, teamName);
                        case "remove" -> TeamManager.allianceRemoveCommand(player, teamName);
                        default -> {
                            sender.sendMessage(Component.text("Unknown party subcommand.", NamedTextColor.RED));
                            usage(sender);
                            yield true;
                        }
                    };
                }
                case "rival" -> {
                    String sub2 = args[2].toLowerCase(Locale.ROOT);
                    yield switch (sub2) {
                        case "add" -> TeamManager.rivalAddCommand(player, teamName);
                        case "remove" -> TeamManager.rivalRemoveCommand(player, teamName);
                        default -> {
                            sender.sendMessage(Component.text("Unknown party subcommand.", NamedTextColor.RED));
                            usage(sender);
                            yield true;
                        }
                    };
                }
                default -> {
                    sender.sendMessage(Component.text("Unknown party subcommand.", NamedTextColor.RED));
                    usage(sender);
                    yield true;
                }
            };
        }
        return true;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Team: /hch team ally <add|remove> <team> | /hch team rival <add|remove> <team>", NamedTextColor.YELLOW));
    }
}

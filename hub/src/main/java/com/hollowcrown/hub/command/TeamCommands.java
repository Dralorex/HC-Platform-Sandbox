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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
            return switch (sub) {
                case "ally" -> {
                    String sub2 = args[2].toLowerCase(Locale.ROOT);
                    yield switch (sub2) {
                        case "add" -> TeamManager.allianceAddCommand(player, args[3]);
                        case "remove" -> TeamManager.allianceRemoveCommand(player, args[3]);
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
                        case "add" -> TeamManager.rivalAddCommand(player, args[3]);
                        case "remove" -> TeamManager.rivalRemoveCommand(player, args[3]);
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

    private static boolean create(HollowCrownHubPlugin plugin, CommandSender sender, HubPartyService parties) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        Optional<String> id = parties.createParty(player.getUniqueId());
        if (id.isEmpty()) {
            player.sendMessage(Component.text("You are already in a party. Leave or disband first.", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("Party created. Id: " + id.get(), NamedTextColor.GREEN));
        HubLog.info(plugin, "action=party_cmd_create", "player=" + player.getName());
        return true;
    }

    private static boolean invite(HollowCrownHubPlugin plugin, CommandSender sender, HubPartyService parties, String[] args) {
        if (!(sender instanceof Player leader)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch party invite <player>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or not online.", NamedTextColor.RED));
            return true;
        }
        if (!parties.invite(leader.getUniqueId(), target.getUniqueId())) {
            leader.sendMessage(Component.text("Could not invite (must be leader, target must not be in a party).", NamedTextColor.RED));
            return true;
        }
        String partyId = parties.partyForMember(leader.getUniqueId()).map(v -> v.partyId()).orElse("");
        var leftover = target.getInventory().addItem(HubPartyListener.createInvitePaper(plugin, partyId));
        for (ItemStack drop : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
        leader.sendMessage(Component.text("Invite sent to " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text(leader.getName() + " invited you to a party. Check your inventory for paper.", NamedTextColor.AQUA));
        HubLog.info(plugin, "action=party_cmd_invite", "leader=" + leader.getName(), "target=" + target.getName());
        return true;
    }

    private static boolean leave(CommandSender sender, HubPartyService parties) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!parties.leave(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("You left the party.", NamedTextColor.YELLOW));
        return true;
    }

    private static boolean disband(CommandSender sender, HubPartyService parties) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!parties.disband(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not the party leader.", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("Party disbanded.", NamedTextColor.YELLOW));
        return true;
    }

    private static boolean kick(CommandSender sender, HubPartyService parties, String[] args) {
        if (!(sender instanceof Player leader)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch party kick <player>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or not online.", NamedTextColor.RED));
            return true;
        }
        if (!parties.kick(leader.getUniqueId(), target.getUniqueId())) {
            leader.sendMessage(Component.text("Could not kick (must be leader, target must be in your party).", NamedTextColor.RED));
            return true;
        }
        leader.sendMessage(Component.text("Kicked " + target.getName() + ".", NamedTextColor.YELLOW));
        target.sendMessage(Component.text("You were removed from the party.", NamedTextColor.RED));
        return true;
    }

    private static boolean accept(CommandSender sender, HubPartyService parties, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch party accept <partyId>", NamedTextColor.YELLOW));
            return true;
        }
        if (!parties.acceptInvite(player.getUniqueId(), args[2])) {
            player.sendMessage(Component.text("No valid invite for that party.", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("Joined party.", NamedTextColor.GREEN));
        parties.partyForMember(player.getUniqueId()).ifPresent(v -> {
            Player leader = Bukkit.getPlayer(v.leaderId());
            if (leader != null && leader.isOnline() && !leader.getUniqueId().equals(player.getUniqueId())) {
                leader.showTitle(Title.title(
                        Component.text("Party", NamedTextColor.GREEN),
                        Component.text(player.getName() + " joined", NamedTextColor.GREEN),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400))));
            }
        });
        return true;
    }

    private static boolean decline(CommandSender sender, HubPartyService parties, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hch party decline <partyId>", NamedTextColor.YELLOW));
            return true;
        }
        var view = parties.partyById(args[2]);
        if (!parties.declineInvite(player.getUniqueId(), args[2])) {
            player.sendMessage(Component.text("No invite for that party.", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("Invite declined.", NamedTextColor.YELLOW));
        view.ifPresent(v -> {
            Player leader = Bukkit.getPlayer(v.leaderId());
            if (leader != null && leader.isOnline()) {
                leader.showTitle(Title.title(
                        Component.empty(),
                        Component.text(player.getName() + " declined your invite", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(400))));
            }
        });
        return true;
    }

    private static boolean list(HollowCrownHubPlugin plugin, CommandSender sender, HubPartyService parties) {
        if (!sender.hasPermission("hollowcrownhub.admin")) {
            sender.sendMessage(Component.text("Admin only.", NamedTextColor.RED));
            return true;
        }
        var all = parties.allPartiesSnapshot();
        sender.sendMessage(Component.text("Hub parties (" + all.size() + "):", NamedTextColor.GOLD));
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("  (none)", NamedTextColor.DARK_GRAY));
            return true;
        }
        for (var v : all) {
            sender.sendMessage(Component.text(
                    "  " + v.partyId() + " | leader=" + v.leaderId() + " | members=" + v.memberCount(),
                    NamedTextColor.GRAY));
        }
        HubLog.info(plugin, "action=party_cmd_list", "sender=" + sender.getName());
        return true;
    }

    /** Tab-complete online player names for invite/kick. */
    public static List<String> tabPlayers(String partial) {
        String p = partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(pl.getName());
            }
        }
        return out;
    }
}

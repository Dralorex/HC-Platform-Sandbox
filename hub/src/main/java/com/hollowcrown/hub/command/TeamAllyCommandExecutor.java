package com.hollowcrown.hub.command;

import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.integration.HubIronLegacySupport;
import com.hollowcrown.hub.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class TeamAllyCommandExecutor implements CommandExecutor, TabCompleter {

    private final HollowCrownHubPlugin plugin;

    public TeamAllyCommandExecutor(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Component.text("Team Ally — /ally", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  /ally help — this help", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /ally add <team> — offer an alliance to a team or accept one from them.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /ally remove <team> — break an alliance with this team.", NamedTextColor.GRAY));
            return true;
        }
        if(sender instanceof Player player && args.length >= 2) {
            //simulate a greedy string argument
            List<String> arrayList = Arrays.stream(args).sequential().skip(1).toList();
            String teamName = String.join(" ", arrayList);
            if (args[0].equalsIgnoreCase("add")) {
                return TeamManager.allianceAddCommand(player, teamName);
            }
            if (args[0].equalsIgnoreCase("remove")) {
                return TeamManager.allianceRemoveCommand(player, teamName);
            }
        }
        sender.sendMessage(Component.text("Unknown subcommand. Try /ally help", NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String p = args[0].toLowerCase();
            for (String c : List.of("help", "add", "remove")) {
                if (c.startsWith(p)) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("add") && sender instanceof Player player) {
            return tabTeam(args, player, false);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("remove") && sender instanceof Player player) {
            return tabTeam(args, player, true);
        }
        return List.of();
    }

    private static List<String> tabTeam(String[] args, Player player, boolean showAlliances) {
        if (args.length >= 2) {
            TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(player.getWorld());
            if (teamManagerInstance == null)
                return List.of("No Teams Found!");
            TeamManager.Team team = teamManagerInstance.findPlayerTeam(player);
            if (team == null)
                return List.of();
            ArrayList<String> allies = new ArrayList<>(team.allies.size());
            for (TeamManager.Team other : team.allies)
                allies.add(other.getName().toLowerCase(Locale.ROOT));
            if (!showAlliances) {
                return partial(args[1].toLowerCase(Locale.ROOT), teamManagerInstance.teams.keySet().stream().filter((e) -> !(e.toLowerCase(Locale.ROOT).equals(team.getName().toLowerCase(Locale.ROOT)) || allies.contains(e.toLowerCase(Locale.ROOT)))).sorted().toList());
            } else {
                return partial(args[1].toLowerCase(Locale.ROOT), allies);
            }
        }
        return List.of();
    }

    private static List<String> partial(String arg, List<String> options) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(arg.toLowerCase())).collect(Collectors.toList());
    }
}

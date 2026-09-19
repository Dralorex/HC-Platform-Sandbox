package com.hollowcrown.hub.command;

import com.hollowcrown.hub.HollowCrownHubPlugin;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TeamRivalCommandExecutor implements CommandExecutor, TabCompleter {

    private final HollowCrownHubPlugin plugin;

    public TeamRivalCommandExecutor(HollowCrownHubPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Component.text("Team rival — /rival", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  /rival help — this help", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /rival add <team> — declare a team your rival.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /rival remove <team> — dismiss a previous team rival.", NamedTextColor.GRAY));
            return true;
        }
        if(sender instanceof Player player && args.length >= 2) {
            //simulate a greedy string argument
            List<String> arrayList = Arrays.stream(args).sequential().skip(1).toList();
            String teamName = String.join(" ", arrayList);
            if (args[0].equalsIgnoreCase("add")) {
                return TeamManager.rivalAddCommand(player, teamName);
            }
            if (args[0].equalsIgnoreCase("remove")) {
                return TeamManager.rivalRemoveCommand(player, teamName);
            }
        }
        sender.sendMessage(Component.text("Unknown subcommand. Try /rival help", NamedTextColor.RED));
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

    private static List<String> tabTeam(String[] args, Player player, boolean showRivalries) {
        if (args.length == 2) {
            TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(player.getWorld());
            if (teamManagerInstance == null)
                return List.of();
            TeamManager.Team team = teamManagerInstance.findPlayerTeam(player);
            if (team == null)
                return List.of();
            ArrayList<String> rivals = new ArrayList<>(team.rivals.size());
            for (TeamManager.Team other : team.rivals)
                rivals.add(other.getName().toLowerCase(Locale.ROOT));
            if (!showRivalries) {
                return partial(args[1].toLowerCase(Locale.ROOT), teamManagerInstance.teams.keySet().stream().filter((e) -> !(e.toLowerCase(Locale.ROOT).equals(team.getName().toLowerCase(Locale.ROOT)) || rivals.contains(e.toLowerCase(Locale.ROOT)))).sorted().toList());
            } else {
                return partial(args[1].toLowerCase(Locale.ROOT), rivals);
            }
        }
        return List.of();
    }

    private static List<String> partial(String arg, List<String> options) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(arg.toLowerCase())).collect(Collectors.toList());
    }
}

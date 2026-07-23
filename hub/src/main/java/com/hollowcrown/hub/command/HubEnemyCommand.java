package com.hollowcrown.hub.command;

import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import com.hollowcrown.hub.alliance.HubAllianceService;
import com.hollowcrown.hub.policy.HubTemplatePolicyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Player command: {@code /enemy <team>} — leader-only. Marks {@code <team>} as a designated enemy of
 * the caller's team (asymmetric, orange in nametags + TAB).
 */
public final class HubEnemyCommand implements CommandExecutor, TabCompleter {

    private final HollowCrownHubPlugin plugin;
    private final HubAllianceService alliances;

    public HubEnemyCommand(HollowCrownHubPlugin plugin, HubAllianceService alliances) {
        this.plugin = plugin;
        this.alliances = alliances;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("/enemy is a player-only command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].isBlank()) {
            p.sendMessage(Component.text("Usage: /enemy <team>", NamedTextColor.YELLOW));
            return true;
        }
        Optional<HubTemplatePolicyService.TemplateConfig> cfgOpt = resolveConfigFor(p);
        if (cfgOpt.isEmpty()) {
            p.sendMessage(Component.text("You are not in a match.", NamedTextColor.RED));
            return true;
        }
        TeamService teams = teamsOrNull();
        if (teams == null) {
            p.sendMessage(Component.text("Team service unavailable.", NamedTextColor.RED));
            return true;
        }
        TeamView myTeam = teams.teamOf(p.getUniqueId()).orElse(null);
        if (myTeam == null) {
            p.sendMessage(Component.text("You are not on a team.", NamedTextColor.RED));
            return true;
        }
        if (!myTeam.isLeader(p.getUniqueId())) {
            p.sendMessage(Component.text("Only team leaders can use /enemy.", NamedTextColor.RED));
            return true;
        }
        TeamView other = teams.teamByName(args[0]).orElse(null);
        if (other == null) {
            p.sendMessage(Component.text("No team named '" + args[0] + "'.", NamedTextColor.RED));
            return true;
        }
        if (other.name().equalsIgnoreCase(myTeam.name())) {
            p.sendMessage(Component.text("You cannot mark your own team as an enemy.", NamedTextColor.RED));
            return true;
        }

        alliances.markDesignatedEnemy(myTeam.name(), other.name());
        alliances.broadcastToTeam(myTeam.name(), Component.text(
                other.name() + " has been deemed an enemy!", NamedTextColor.GOLD));
        plugin.nametags().refreshWorld(p.getWorld());
        return true;
    }

    private Optional<HubTemplatePolicyService.TemplateConfig> resolveConfigFor(Player p) {
        if (p == null || p.getWorld() == null) return Optional.empty();
        String tk = plugin.limboLayout().worldsYaml().resolveTemplateKeyForWorld(p.getWorld().getName()).orElse(null);
        if (tk == null) return Optional.empty();
        return Optional.of(plugin.templatePolicies().configFor(tk));
    }

    private TeamService teamsOrNull() {
        RegisteredServiceProvider<TeamService> reg = Bukkit.getServicesManager().getRegistration(TeamService.class);
        return reg == null ? null : reg.getProvider();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            TeamService teams = teamsOrNull();
            if (teams != null) {
                for (TeamView t : teams.teams()) out.add(t.name());
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
            return out;
        }
        return List.of();
    }
}

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
import java.util.UUID;

/**
 * Player command: {@code /ally <team>} (leader-only invite) and {@code /ally accept <team>} (accept).
 *
 * <p>Both arms gate on the per-template "allies enabled" toggle resolved from the actor's world.
 * Designated-enemy marking is handled by {@link HubEnemyCommand}.</p>
 */
public final class HubAllyCommand implements CommandExecutor, TabCompleter {

    private final HollowCrownHubPlugin plugin;
    private final HubAllianceService alliances;

    public HubAllyCommand(HollowCrownHubPlugin plugin, HubAllianceService alliances) {
        this.plugin = plugin;
        this.alliances = alliances;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("/ally is a player-only command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(Component.text("Usage: /ally <team>  |  /ally accept <team>", NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            if (args.length < 2 || args[1].isBlank()) {
                p.sendMessage(Component.text("Usage: /ally accept <team>", NamedTextColor.YELLOW));
                return true;
            }
            handleAccept(p, args[1]);
            return true;
        }

        handleInvite(p, args[0]);
        return true;
    }

    private void handleInvite(Player actor, String targetTeamRaw) {
        Optional<HubTemplatePolicyService.TemplateConfig> cfgOpt = resolveConfigFor(actor);
        if (cfgOpt.isEmpty() || !cfgOpt.get().alliesEnabled()) {
            actor.sendMessage(Component.text(
                    "Alliances are not enabled in this match.", NamedTextColor.RED));
            return;
        }
        if (alliances.areFurtherAlliancesDisabled()) {
            actor.sendMessage(Component.text(
                    "Only one team will make it out alive.", NamedTextColor.RED));
            return;
        }

        TeamService teams = teamsOrNull();
        if (teams == null) {
            actor.sendMessage(Component.text("Team service unavailable.", NamedTextColor.RED));
            return;
        }
        TeamView myTeam = teams.teamOf(actor.getUniqueId()).orElse(null);
        if (myTeam == null) {
            actor.sendMessage(Component.text("You are not on a team.", NamedTextColor.RED));
            return;
        }
        if (!myTeam.isLeader(actor.getUniqueId())) {
            actor.sendMessage(Component.text("Only team leaders can issue /ally.", NamedTextColor.RED));
            return;
        }
        TeamView other = teams.teamByName(targetTeamRaw).orElse(null);
        if (other == null) {
            actor.sendMessage(Component.text(
                    "No team named '" + targetTeamRaw + "'.", NamedTextColor.RED));
            return;
        }
        if (other.name().equalsIgnoreCase(myTeam.name())) {
            actor.sendMessage(Component.text("You cannot ally with yourself.", NamedTextColor.RED));
            return;
        }
        if (alliances.areAllied(myTeam.name(), other.name())) {
            actor.sendMessage(Component.text(
                    "You are already allied with " + other.name() + ".", NamedTextColor.YELLOW));
            return;
        }

        alliances.invite(myTeam.name(), other.name());
        actor.sendMessage(Component.text("Alliance invite sent to " + other.name() + ".",
                NamedTextColor.GREEN));

        UUID leaderId = other.ownerId();
        if (leaderId != null) {
            Player leader = Bukkit.getPlayer(leaderId);
            if (leader != null && leader.isOnline()) {
                leader.sendMessage(Component.text(
                        myTeam.name() + " wants to form an alliance with you.", NamedTextColor.AQUA));
                leader.sendMessage(Component.text(
                        "Accept with /ally accept " + myTeam.name(), NamedTextColor.GRAY));
            }
        }
    }

    private void handleAccept(Player actor, String inviterTeamRaw) {
        Optional<HubTemplatePolicyService.TemplateConfig> cfgOpt = resolveConfigFor(actor);
        if (cfgOpt.isEmpty() || !cfgOpt.get().alliesEnabled()) {
            actor.sendMessage(Component.text(
                    "Alliances are not enabled in this match.", NamedTextColor.RED));
            return;
        }
        if (alliances.areFurtherAlliancesDisabled()) {
            actor.sendMessage(Component.text(
                    "Only one team will make it out alive.", NamedTextColor.RED));
            return;
        }

        TeamService teams = teamsOrNull();
        if (teams == null) {
            actor.sendMessage(Component.text("Team service unavailable.", NamedTextColor.RED));
            return;
        }
        TeamView myTeam = teams.teamOf(actor.getUniqueId()).orElse(null);
        if (myTeam == null) {
            actor.sendMessage(Component.text("You are not on a team.", NamedTextColor.RED));
            return;
        }
        if (!myTeam.isLeader(actor.getUniqueId())) {
            actor.sendMessage(Component.text(
                    "Only team leaders can accept alliances.", NamedTextColor.RED));
            return;
        }
        TeamView inviter = teams.teamByName(inviterTeamRaw).orElse(null);
        if (inviter == null) {
            actor.sendMessage(Component.text(
                    "No team named '" + inviterTeamRaw + "'.", NamedTextColor.RED));
            return;
        }
        if (!alliances.hasPendingInviteFrom(inviter.name(), myTeam.name())) {
            actor.sendMessage(Component.text(
                    "No pending alliance invite from " + inviter.name() + ".", NamedTextColor.RED));
            return;
        }

        int max = cfgOpt.get().maxTeamsPerAlliance();
        boolean ok = alliances.acceptInvite(inviter.name(), myTeam.name(), max);
        if (!ok) {
            actor.sendMessage(Component.text(
                    "Could not form the alliance (group too large?).", NamedTextColor.RED));
            return;
        }

        Component msg = Component.text(
                "An ally has been formed with " + inviter.name(), NamedTextColor.AQUA);
        alliances.broadcastToTeam(myTeam.name(), msg);
        alliances.broadcastToTeam(inviter.name(), Component.text(
                "An ally has been formed with " + myTeam.name(), NamedTextColor.AQUA));
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
            out.add("accept");
            TeamService teams = teamsOrNull();
            if (teams != null) {
                for (TeamView t : teams.teams()) out.add(t.name());
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            List<String> out = new ArrayList<>();
            TeamService teams = teamsOrNull();
            if (teams != null) {
                for (TeamView t : teams.teams()) out.add(t.name());
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
            return out;
        }
        return List.of();
    }
}

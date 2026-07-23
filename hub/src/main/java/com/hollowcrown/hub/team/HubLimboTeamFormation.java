package com.hollowcrown.hub.team;

import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.api.integration.PreMatchLimboService;
import com.hollowcrown.hub.HollowCrownHubPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Match-scoped team helpers for pre-match limbo (independent from parties). */
public final class HubLimboTeamFormation {

    private HubLimboTeamFormation() {
    }

    /** Clears stale team membership when entering a new lobby limbo session. */
    public static void resetTeamForLimboEntry(HollowCrownHubPlugin plugin, Player player) {
        if (plugin == null || player == null) {
            return;
        }
        TeamService teams = teams();
        if (teams == null) {
            return;
        }
        teams.clearTeamsFor(java.util.List.of(player.getUniqueId()));
        refreshNametags(plugin, player.getWorld());
    }

    public static void resetTeamsForLimboEntry(HollowCrownHubPlugin plugin, Collection<UUID> playerIds) {
        if (plugin == null || playerIds == null || playerIds.isEmpty()) {
            return;
        }
        TeamService teams = teams();
        if (teams == null) {
            return;
        }
        teams.clearTeamsFor(playerIds);
        for (UUID id : playerIds) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                refreshNametags(plugin, p.getWorld());
            }
        }
    }

    /**
     * Creates a numbered team for the leader only. Other players join via team invites.
     */
    public static boolean createTeam(
            HollowCrownHubPlugin plugin,
            Player leader,
            TeamService teams,
            HubTeamAutoColors.AutoColor color,
            boolean legacyManualPick
    ) {
        if (leader == null || teams == null || color == null) {
            return false;
        }
        if (teams.teamOf(leader.getUniqueId()).isPresent()) {
            return false;
        }

        String teamName = HubTeamNamingRules.allocateNumberedTeamName(teams);
        teams.createTeam(teamName, color.legacyColor(), leader.getUniqueId(), color.displayRgb());

        if (legacyManualPick) {
            leader.sendMessage(Component.text(
                    "You created " + teamName + " (" + prettyColorName(color.legacyColor()) + "). Invite players from the team menu.",
                    NamedTextColor.GREEN));
        } else {
            leader.sendMessage(Component.text(
                    "You created " + teamName + ". Invite teammates from the Match Team menu.",
                    NamedTextColor.GREEN));
        }
        refreshNametags(plugin, leader.getWorld());
        return true;
    }

    /**
     * When a party joins lobby limbo, place members on one numbered team up to {@code teamSizeMax}.
     * Solo players are unchanged — they use the Match Team menu.
     */
    public static Optional<String> bindPartyTeamOnLobbyJoin(
            HollowCrownHubPlugin plugin,
            Player player,
            int teamSizeMax
    ) {
        if (plugin == null || player == null || teamSizeMax < 1) {
            return Optional.empty();
        }
        PartyService parties = parties();
        TeamService teams = teams();
        if (parties == null || teams == null) {
            return Optional.empty();
        }
        Optional<PartyView> partyOpt = parties.partyForMember(player.getUniqueId());
        if (partyOpt.isEmpty()) {
            return Optional.empty();
        }
        PartyView party = partyOpt.get();
        UUID leaderId = party.leaderId();

        Optional<TeamView> leaderTeam = teams.teamOf(leaderId);
        if (leaderTeam.isPresent()) {
            TeamView team = leaderTeam.get();
            if (party.isMember(player.getUniqueId()) && teams.teamOf(player.getUniqueId()).isEmpty()) {
                if (team.memberCount() < teamSizeMax) {
                    try {
                        teams.addMember(team.name(), player.getUniqueId());
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            refreshNametags(plugin, player.getWorld());
            return teams.teamOf(player.getUniqueId()).map(TeamView::name);
        }

        if (!party.isLeader(player.getUniqueId())) {
            return Optional.empty();
        }

        HubTeamAutoColors.AutoColor color = HubTeamAutoColors.nextAvailable(teams);
        String teamName = HubTeamNamingRules.allocateNumberedTeamName(teams);
        teams.createTeam(teamName, color.legacyColor(), leaderId, color.displayRgb());

        Set<UUID> session = sessionPlayerIds(player);
        for (UUID memberId : party.membersJoinOrder()) {
            if (memberId.equals(leaderId)) {
                continue;
            }
            if (!session.contains(memberId)) {
                continue;
            }
            if (teams.teamOf(memberId).isPresent()) {
                continue;
            }
            TeamView current = teams.teamOf(leaderId).orElse(null);
            if (current == null || current.memberCount() >= teamSizeMax) {
                break;
            }
            try {
                teams.addMember(current.name(), memberId);
            } catch (RuntimeException ignored) {
            }
        }
        refreshNametags(plugin, player.getWorld());
        return teams.teamOf(player.getUniqueId()).map(TeamView::name);
    }

    public static String previewNextTeamName(TeamService teams) {
        return HubTeamNamingRules.previewNextTeamName(teams);
    }

    public static int memberCap(HollowCrownHubPlugin plugin, Player player) {
        if (plugin == null) {
            return 2;
        }
        TeamService teams = teams();
        if (teams instanceof HubTeamService hubTeams) {
            return hubTeams.resolveMemberCap(player);
        }
        return Math.max(1, plugin.getConfig().getInt("teams.memberCap", 2));
    }

    private static TeamService teams() {
        RegisteredServiceProvider<TeamService> reg =
                Bukkit.getServicesManager().getRegistration(TeamService.class);
        return reg == null ? null : reg.getProvider();
    }

    private static PartyService parties() {
        RegisteredServiceProvider<PartyService> reg =
                Bukkit.getServicesManager().getRegistration(PartyService.class);
        return reg == null ? null : reg.getProvider();
    }

    private static Set<UUID> sessionPlayerIds(Player player) {
        RegisteredServiceProvider<PreMatchLimboService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLimboService.class);
        if (reg == null) {
            return Set.of();
        }
        return reg.getProvider().playersInSameTemplateSession(player);
    }

    private static void refreshNametags(HollowCrownHubPlugin plugin, org.bukkit.World world) {
        if (plugin == null || world == null) {
            return;
        }
        plugin.nametags().onTeamRosterChanged(world, java.util.Set.of());
    }

    private static String prettyColorName(ChatColor c) {
        if (c == null) {
            return "White";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : c.name().toLowerCase().split("_")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}

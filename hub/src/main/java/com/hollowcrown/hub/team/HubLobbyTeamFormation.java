package com.hollowcrown.hub.team;

import com.hollowcrown.api.party.PartyService;
import com.hollowcrown.api.party.PartyView;
//import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.api.integration.PreMatchLobbyService;
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

/** Match-scoped team helpers for pre-match lobby (independent from parties). */
public final class HubLobbyTeamFormation {

    private HubLobbyTeamFormation() {
    }

    /** Clears stale team membership when entering a new lobby session. */
    public static void resetTeamForLobbyEntry(HollowCrownHubPlugin plugin, Player player) {
        if (plugin == null || player == null) {
            return;
        }
        //Reset player team here, or too lazy ig
        refreshNametags(plugin, player.getWorld());
    }

    public static void resetTeamsForLobbyEntry(HollowCrownHubPlugin plugin, Collection<UUID> playerIds) {
        if (plugin == null || playerIds == null || playerIds.isEmpty()) {
            return;
        }
        //Reset player team here, or too lazy ig
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
    /*public static boolean createTeam(
            HollowCrownHubPlugin plugin,
            Player leader,
            TeamManager.TeamManagerInstance teamManagerInstance,
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
    }*/

    /**
     * When a party joins a lobby, place members on one numbered team up to {@code teamSizeMax}.
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
        //TeamService teams = teams();
        TeamManager.TeamManagerInstance teamManagerInstance = TeamManager.getInstance(player.getWorld());
        if (parties == null || teamManagerInstance == null) {
            return Optional.empty();
        }
        Optional<PartyView> partyOpt = parties.partyForMember(player.getUniqueId());
        if (partyOpt.isEmpty()) {
            return Optional.empty();
        }
        PartyView party = partyOpt.get();
        UUID leaderId = party.leaderId();

        TeamManager.Team leaderTeam = TeamManager.findPlayerTeam(leaderId);
        if(leaderTeam != null || party.memberCount() > teamSizeMax || !party.isLeader(player.getUniqueId()))
            return Optional.empty();

        TeamManager.Team team = new TeamManager.Team(TeamNamingRules.randomTeamName(teamManagerInstance), teamManagerInstance.getRandomUnusedColor(), teamManagerInstance);
        team.members.add(leaderId);
        for(UUID uuid : party.membersJoinOrder())
            if(!team.members.contains(uuid) && team.members.size() < teamSizeMax)
                team.members.add(uuid);

        teamManagerInstance.markDirtyAndScheduleFlush();
        return Optional.of(team.getName());
    }

    //public static String previewNextTeamName(TeamService teams) {
    //    return HubTeamNamingRules.previewNextTeamName(teams);
    //}

    public static int memberCap(HollowCrownHubPlugin plugin, Player player) {
        if (plugin == null) {
            return 2;
        }
        //TeamService teams = teams();
        //if (teams instanceof HubTeamService hubTeams) {
        //    return hubTeams.resolveMemberCap(player);
        //}
        return Math.max(1, plugin.getConfig().getInt("teams.memberCap", 2));
    }

    private static PartyService parties() {
        RegisteredServiceProvider<PartyService> reg =
                Bukkit.getServicesManager().getRegistration(PartyService.class);
        return reg == null ? null : reg.getProvider();
    }

    private static Set<UUID> sessionPlayerIds(Player player) {
        RegisteredServiceProvider<PreMatchLobbyService> reg =
                Bukkit.getServicesManager().getRegistration(PreMatchLobbyService.class);
        if (reg == null) {
            return Set.of();
        }
        return reg.getProvider().playersInSameTemplateSession(player);
    }

    private static void refreshNametags(HollowCrownHubPlugin plugin, org.bukkit.World world) {
        if (plugin == null || world == null) {
            return;
        }
        TeamManager.getOrCreateWorldInstance(world).markDirtyAndScheduleFlush();
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

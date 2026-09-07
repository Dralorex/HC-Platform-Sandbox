package com.hollowcrown.hungergames.managers;

import com.hollowcrown.api.team.TeamService;
import com.hollowcrown.api.team.TeamView;
import com.hollowcrown.hungergames.HungerGamesPlugin;
import com.hollowcrown.hungergames.model.Team;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.entity.Player;

import java.util.*;

/** Compatibility wrapper: Iron Legacy call sites now read/write Hub-owned TeamService state. */
public class TeamManager {
    private final HungerGamesPlugin plugin;

    public TeamManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<Team> getTeams() {
        List<Team> out = new ArrayList<>();
        for (TeamView tv : service().teams()) out.add(toLegacy(tv));
        return out;
    }

    public Optional<Team> getTeamByName(String name) {
        return service().teamByName(name).map(this::toLegacy);
    }

    public Team getTeamOf(UUID id) {
        return service().teamOf(id).map(this::toLegacy).orElse(null);
    }

    public boolean nameTaken(String name) { return service().nameTaken(name); }

    /**
     * True if another team with the same display name has any member in the same limbo template session
     * as {@code prospectiveCreator} (excludes the team the player is already on).
     */
    public boolean nameTakenByOtherTeamInSameLimbo(Player prospectiveCreator, String name) {
        if (prospectiveCreator == null || name == null || name.isBlank()) {
            return true;
        }
        String needle = name.toLowerCase(Locale.ROOT);
        Set<UUID> session = plugin.limbo().playersInSameTemplateSession(prospectiveCreator);
        if (session.isEmpty()) {
            return false;
        }
        Team mine = getTeamOf(prospectiveCreator.getUniqueId());
        for (Team t : getTeams()) {
            if (!t.getName().toLowerCase(Locale.ROOT).equals(needle)) {
                continue;
            }
            if (mine != null && t.getName().equalsIgnoreCase(mine.getName())) {
                continue;
            }
            for (UUID m : t.getMembers()) {
                if (session.contains(m)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean colorTaken(ChatColor color) {
        return service().colorTaken(color);
    }

    public Team createTeam(String name, ChatColor color, UUID owner) {
        return createTeam(name, color, owner, null);
    }

    /**
     * @param displayRgb 24-bit RGB for tab/display name, or null for legacy {@link ChatColor} only
     */
    public Team createTeam(String name, ChatColor color, UUID owner, Integer displayRgb) {
        return toLegacy(service().createTeam(name, color, owner, displayRgb));
    }

    public void invite(UUID to, String teamName) {
        service().invite(to, teamName);
    }

    public Set<String> getInvites(UUID id) { return service().invites(id); }

    public void clearInvite(UUID id, String teamName) {
        // Hub service clears accepted/declined invites internally.
    }

    /** Resolve stored invite key (case-insensitive). */
    private String resolveInviteKey(UUID playerId, String teamName) {
        if (teamName == null) return null;
        Set<String> s = service().invites(playerId);
        if (s == null) return null;
        for (String x : s) {
            if (x.equalsIgnoreCase(teamName)) return x;
        }
        return null;
    }

    /**
     * Declines a pending team invite and notifies the team owner via subtitle (not chat).
     * @return false if the player had no matching invite
     */
    public boolean declineTeamInvite(Player player, String teamName, boolean choseDifferentTeam) {
        return player != null && service().declineInvite(player, teamName, choseDifferentTeam);
    }

    /**
     * Accepts one team invite, auto-declines all others (owners get subtitle feedback), joins the team,
     * and shows the team owner a short title (not chat) when someone joins.
     * @param memberCap max members allowed on the team (use {@code <= 0} for config default {@code game.teamSize})
     */
    public boolean acceptTeamInvite(Player player, Team team, int memberCap) {
        if (player == null || team == null) return false;
        return service().acceptInvite(player, team.getName(), memberCap);
    }

    public void addMember(Team t, UUID id) {
        service().addMember(t.getName(), id);
    }

    /** Remove a player from whichever team they are in. Deletes empty teams. */
    public boolean removeMember(UUID id) {
        return service().removeMember(id);
    }

    /** NEW: wipe all teams and all invites, then persist. Prefer {@link #clearTeamsFor(Collection)} per session. */
    public void clearAllTeams() {
        service().clearAllTeams();
    }

    /** Clears teams for specific players only (lobby/match session teardown). */
    public void clearTeamsFor(Collection<UUID> playerIds) {
        service().clearTeamsFor(playerIds);
    }

    public void load() {
        // Hub TeamService owns persistence.
    }

    public void save() {
        // Hub TeamService owns persistence.
    }

    public boolean autoCreateTeamsEnabled() {
        return service().autoCreateTeamsEnabled();
    }

    public void setAutoCreateTeamsEnabled(boolean enabled) {
        service().setAutoCreateTeamsEnabled(enabled);
    }

    public boolean tryAddTeamNameToDatabase(String raw) {
        return service().tryAddTeamNameToDatabase(raw);
    }

    public List<String> teamNameDatabaseAlphabetical() {
        return List.copyOf(service().teamNameDatabaseAlphabetical());
    }

    public String takeUnusedTeamNameForNewTeam() {
        return service().takeUnusedTeamNameForNewTeam();
    }

    private Team toLegacy(TeamView tv) {
        Team out = new Team(tv.name(), tv.legacyColor(), tv.ownerId(), tv.displayRgb());
        out.getMembers().clear();
        out.getMembers().addAll(tv.members());
        return out;
    }

    private TeamService service() {
        RegisteredServiceProvider<TeamService> reg =
                org.bukkit.Bukkit.getServicesManager().getRegistration(TeamService.class);
        if (reg == null) {
            throw new IllegalStateException("TeamService not registered. Is HollowCrownHub enabled?");
        }
        return reg.getProvider();
    }

}

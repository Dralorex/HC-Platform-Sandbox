package com.hollowcrown.api.team;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hub-owned team system (party-like) used by minigames.
 * <p>
 * This is intentionally not tied to Iron Legacy internals; Iron Legacy should consume this service
 * instead of owning team state.
 */
public interface TeamService {

    Collection<TeamView> teams();

    Optional<TeamView> teamByName(String name);

    Optional<TeamView> teamOf(UUID playerId);

    boolean nameTaken(String name);

    boolean colorTaken(ChatColor color);

    /** Creates a team and persists it. */
    TeamView createTeam(String name, ChatColor legacyColor, UUID ownerId, Integer displayRgb);

    /** Adds a member to an existing team. */
    void addMember(String teamName, UUID memberId);

    /** Removes a member from their team (if any). Deletes empty teams. */
    boolean removeMember(UUID memberId);

    /** Clears all teams and invites. */
    void clearAllTeams();

    /** Invite player to a team name (case preserved). */
    void invite(UUID toPlayerId, String teamName);

    /** Pending invites by team name (case-preserved keys). */
    Set<String> invites(UUID playerId);

    /** Decline a pending invite (notifies owner). */
    boolean declineInvite(Player player, String teamName, boolean choseDifferentTeam);

    /**
     * Accepts a pending invite, auto-declines all other invites (owners notified), and joins the team.
     * @param memberCapOverride member cap; use {@code <= 0} to use service default
     */
    boolean acceptInvite(Player player, String teamName, int memberCapOverride);

    /** Auto-create teams toggle (default ON). */
    boolean autoCreateTeamsEnabled();

    void setAutoCreateTeamsEnabled(boolean enabled);

    /** Team name database management. */
    Collection<String> teamNameDatabaseAlphabetical();

    boolean tryAddTeamNameToDatabase(String raw);

    /** Returns a name for a new auto-created team. */
    String takeUnusedTeamNameForNewTeam();
}


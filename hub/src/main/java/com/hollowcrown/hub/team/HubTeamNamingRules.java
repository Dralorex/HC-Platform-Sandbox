package com.hollowcrown.hub.team;

import com.hollowcrown.api.team.TeamService;

/** Allocates {@code Team 1}, {@code Team 2}, … display names for match teams. */
public final class HubTeamNamingRules {

    private HubTeamNamingRules() {
    }

    public static String allocateNumberedTeamName(TeamService teams) {
        if (teams instanceof HubTeamService hubTeams) {
            return hubTeams.allocateNumberedTeamName();
        }
        return "Team 1";
    }

    /** Preview the name the next new team would receive (same logic as allocate). */
    public static String previewNextTeamName(TeamService teams) {
        return allocateNumberedTeamName(teams);
    }
}

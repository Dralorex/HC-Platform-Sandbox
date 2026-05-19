package com.hollowcrown.api.team;

import org.bukkit.ChatColor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable snapshot of a team.
 */
public record TeamView(
        String name,
        ChatColor legacyColor,
        UUID ownerId,
        Integer displayRgb,
        Set<UUID> members,
        List<UUID> memberJoinOrder
) {
    public boolean isLeader(UUID playerId) {
        return ownerId != null && ownerId.equals(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members != null && members.contains(playerId);
    }

    public int memberCount() {
        return members == null ? 0 : members.size();
    }
}


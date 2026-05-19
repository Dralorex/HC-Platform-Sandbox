package com.hollowcrown.api.party;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a Hub party for minigames and queue policy (no Bukkit types).
 */
public final class PartyView {

    private final String partyId;
    private final UUID leaderId;
    private final String displayName;
    private final List<UUID> membersJoinOrder;

    public PartyView(String partyId, UUID leaderId, String displayName, List<UUID> membersJoinOrder) {
        this.partyId = Objects.requireNonNull(partyId, "partyId").trim();
        this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        this.displayName = displayName == null ? "" : displayName.trim();
        this.membersJoinOrder = List.copyOf(Objects.requireNonNull(membersJoinOrder, "membersJoinOrder"));
        if (this.partyId.isEmpty()) {
            throw new IllegalArgumentException("partyId empty");
        }
    }

    public String partyId() {
        return partyId;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public String displayName() {
        return displayName;
    }

    public List<UUID> membersJoinOrder() {
        return membersJoinOrder;
    }

    public int memberCount() {
        return membersJoinOrder.size();
    }

    public boolean isMember(UUID playerId) {
        return playerId != null && membersJoinOrder.contains(playerId);
    }

    public boolean isLeader(UUID playerId) {
        return playerId != null && leaderId.equals(playerId);
    }
}

package com.hollowcrown.hub.party;

import com.hollowcrown.api.party.PartyView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Mutable party row (Hub process only). */
final class HubParty {

    final String id;
    String displayName;
    UUID leader;
    final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    final List<UUID> memberJoinOrder = new ArrayList<>();

    HubParty(String id, String displayName, UUID leader) {
        this.id = Objects.requireNonNull(id);
        this.displayName = displayName == null ? "" : displayName;
        this.leader = Objects.requireNonNull(leader);
    }

    PartyView toView() {
        List<UUID> order = new ArrayList<>();
        for (UUID u : memberJoinOrder) {
            if (members.contains(u)) {
                order.add(u);
            }
        }
        return new PartyView(id, leader, displayName, order);
    }

    UUID nextLeader() {
        for (UUID id : memberJoinOrder) {
            if (members.contains(id)) {
                return id;
            }
        }
        return members.iterator().next();
    }
}

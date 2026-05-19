package com.hollowcrown.api.party;

import java.util.List;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hub-owned party graph (UUID-first). HollowCrownHub registers the implementation.
 */
public interface PartyService {

    Optional<PartyView> partyForMember(UUID memberId);

    Optional<PartyView> partyById(String partyId);

    /**
     * Creates a solo party for {@code leaderId}, or empty if they already belong to a party.
     *
     * @return new party id string (UUID)
     */
    Optional<String> createParty(UUID leaderId);

    boolean leave(UUID memberId);

    /** Only the current leader may disband. */
    boolean disband(UUID leaderId);

    /** Only the leader may invite; target must be online on the same policy server (Hub). */
    boolean invite(UUID leaderId, UUID targetId);

    /** Leader removes a member (cannot kick self). */
    boolean kick(UUID leaderId, UUID targetMemberId);

    boolean acceptInvite(UUID playerId, String partyId);


    /** Pending invite party ids for menus (newest first where applicable). */
    List<String> pendingInvitePartyIds(UUID playerId);
    boolean declineInvite(UUID playerId, String partyId);
}

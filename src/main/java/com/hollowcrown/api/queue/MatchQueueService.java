package com.hollowcrown.api.queue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hub-owned FIFO match queue per template. HollowCrownHub registers the implementation.
 */
public interface MatchQueueService {

    /**
     * Enqueues the acting player: if they lead a {@link com.hollowcrown.api.party.PartyService party},
     * the whole party is one queue unit (all must be online on this server).
     */
    MatchEnqueueResult enqueue(UUID actingPlayerId, String templateKey);

    /** Removes this player from any queue unit (whole party leaves if party unit). */
    boolean leaveQueue(UUID playerId);

    Optional<String> queuedTemplateFor(UUID playerId);

    /** Human-readable lines for admin/debug (e.g. {@code party:uuid 3p}, {@code solo+2}). */
    List<String> describeQueue(String templateKey);

    int queuedUnitCount(String templateKey);
}

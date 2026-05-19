package com.hollowcrown.hub.command;

import com.hollowcrown.api.queue.MatchEnqueueResult;

/**
 * Shared player-facing text for match queue denials (GUI + /hch queue).
 */
public final class MatchEnqueueMessages {

    private MatchEnqueueMessages() {
    }

    public static String playerDenyMessage(MatchEnqueueResult.Status s) {
        return switch (s) {
            case OK -> "Queued.";
            case ALREADY_QUEUED -> "You (or a party member) are already in a queue. Leave first.";
            case UNKNOWN_TEMPLATE -> "Unknown template.";
            case OFFLINE_PARTY_MEMBER -> "All party members must be online on this server.";
            case PARTY_TOO_LARGE -> "Party is too large for this template's queue cap (or use bypass permission).";
            case SOLO_ONLY -> "This template's queue is solo-only (no multi-player parties).";
            case NOT_PARTY_LEADER -> "Only the party leader can queue the whole party.";
            case OTHER -> "Could not join queue.";
        };
    }
}

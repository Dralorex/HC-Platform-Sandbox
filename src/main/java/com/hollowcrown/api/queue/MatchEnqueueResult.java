package com.hollowcrown.api.queue;

import java.util.Objects;

/**
 * Result of attempting to join a template's match queue on the Hub.
 */
public final class MatchEnqueueResult {

    public enum Status {
        /** Added (new unit or merged into solo aggregate). */
        OK,
        /** Player (or party member) already in a queue entry. */
        ALREADY_QUEUED,
        UNKNOWN_TEMPLATE,
        /** One or more party members are not on this server. */
        OFFLINE_PARTY_MEMBER,
        /** Party larger than template {@code queueMaxUnitSize} (unless bypass). */
        PARTY_TOO_LARGE,
        /** Template {@code queueSoloOnly} — multi-member parties blocked (unless bypass). */
        SOLO_ONLY,
        /** Only party leader may enqueue the whole party. */
        NOT_PARTY_LEADER,
        OTHER
    }

    private final Status status;
    private final int positionOneBased;

    public MatchEnqueueResult(Status status, int positionOneBased) {
        this.status = Objects.requireNonNull(status, "status");
        this.positionOneBased = Math.max(0, positionOneBased);
    }

    public static MatchEnqueueResult ok(int positionOneBased) {
        return new MatchEnqueueResult(Status.OK, positionOneBased);
    }

    public static MatchEnqueueResult denied(Status reason) {
        return new MatchEnqueueResult(reason, 0);
    }

    public Status status() {
        return status;
    }

    public boolean success() {
        return status == Status.OK;
    }

    /** Queue position after enqueue (1 = front), or 0 if denied. */
    public int positionOneBased() {
        return positionOneBased;
    }
}

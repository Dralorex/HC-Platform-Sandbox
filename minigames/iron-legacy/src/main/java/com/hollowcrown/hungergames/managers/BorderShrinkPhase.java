package com.hollowcrown.hungergames.managers;

/**
 *  phase for a world border shrink event.
 * fields are in-game time units starting at match start
 */
public class BorderShrinkPhase {
    private final long delaySeconds;         // When to start shrinking
    private final double radiusStart;        // Starting border radius
    private final double radiusEnd;          // Ending border radius
    private final long shrinkDurationSeconds; // How long to shrink over

    public BorderShrinkPhase(long delaySeconds, double radiusStart, double radiusEnd, long shrinkDurationSeconds) {
        this.delaySeconds = delaySeconds;
        this.radiusStart = radiusStart;
        this.radiusEnd = radiusEnd;
        this.shrinkDurationSeconds = shrinkDurationSeconds;
    }

    public long delaySeconds() { return delaySeconds; }
    public double radiusStart() { return radiusStart; }
    public double radiusEnd() { return radiusEnd; }
    public long shrinkDurationSeconds() { return shrinkDurationSeconds; }

    @Override
    public String toString() {
        return String.format("BorderPhase[delay=%ds, %s→%s over %ds]",
                delaySeconds, radiusStart, radiusEnd, shrinkDurationSeconds);
    }
}

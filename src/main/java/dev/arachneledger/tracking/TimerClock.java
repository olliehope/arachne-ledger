package dev.arachneledger.tracking;

import java.util.function.LongSupplier;

/**
 * Anchors display time once, then advances with a monotonic clock without changing journal dates.
 */
final class TimerClock {
    private final LongSupplier nanoseconds;
    private long anchorMillis, anchorNanos, lastMillis;
    private boolean anchored;

    TimerClock(LongSupplier nanoseconds) {
        this.nanoseconds = nanoseconds;
    }

    long now(long wallMillis) {
        if (nanoseconds == null) return wallMillis;
        long nanos = nanoseconds.getAsLong();
        if (!anchored) {
            anchorMillis = lastMillis = wallMillis;
            anchorNanos = nanos;
            anchored = true;
        }
        // Subtracting the two readings also handles nanoTime's signed wraparound.
        long elapsedMillis = Math.max(0, (nanos - anchorNanos) / 1_000_000);
        long current =
                elapsedMillis > Long.MAX_VALUE - anchorMillis
                        ? Long.MAX_VALUE
                        : anchorMillis + elapsedMillis;
        lastMillis = Math.max(lastMillis, current);
        return lastMillis;
    }

    void reset() {
        anchored = false;
        anchorMillis = anchorNanos = lastMillis = 0;
    }
}

package dev.arachneledger.tracking;

import dev.arachneledger.ledger.Ledger;

/** The active clock and idle grace are independent of receipts, screens and persistence. */
final class TrackingClock {
    static final long AFK_GRACE_MILLIS = 60_000;
    private static final long MAX_TICK_GAP_MILLIS = 5_000;
    private static final long SUMMONING_TIMEOUT_MILLIS = 60_000;

    long lastTickAt;
    long activeGraceUntil;
    long summoningUntil;

    boolean advance(
            Ledger ledger,
            boolean ready,
            boolean paused,
            boolean inside,
            boolean fighting,
            long now) {
        boolean changed = false;
        if (ready
                && !paused
                && inside
                && lastTickAt > 0
                && now > lastTickAt
                && now - lastTickAt <= MAX_TICK_GAP_MILLIS) {
            long end = fighting ? now : Math.min(now, activeGraceUntil);
            if (end > lastTickAt) {
                ledger.tick(end - lastTickAt);
                changed = true;
            }
        }
        lastTickAt = inside && !paused ? now : 0;
        return changed;
    }

    void summon(long now) {
        summoningUntil = now + SUMMONING_TIMEOUT_MILLIS;
    }

    void spawned() {
        activeGraceUntil = 0;
        summoningUntil = 0;
    }

    void died(long now) {
        activeGraceUntil = now + AFK_GRACE_MILLIS;
        summoningUntil = 0;
    }

    void reset() {
        lastTickAt = 0;
        activeGraceUntil = 0;
        summoningUntil = 0;
    }

    boolean summoning(boolean inside, boolean paused, boolean fighting) {
        return inside && !paused && !fighting && summoningUntil > lastTickAt;
    }

    boolean afk(boolean inside, boolean paused, boolean fighting) {
        return inside
                && !paused
                && !fighting
                && !summoning(inside, paused, fighting)
                && activeGraceUntil > 0
                && lastTickAt >= activeGraceUntil;
    }

    boolean waiting(boolean inside, boolean paused, boolean fighting) {
        return inside
                && !paused
                && !fighting
                && !summoning(inside, paused, fighting)
                && activeGraceUntil == 0;
    }
}

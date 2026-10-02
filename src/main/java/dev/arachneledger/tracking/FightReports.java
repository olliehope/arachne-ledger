package dev.arachneledger.tracking;

import dev.arachneledger.ledger.Ledger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Delays local kill reports until rewards settle; never changes accounting or fight eligibility.
 */
final class FightReports {
    private static final long AFTER_DEATH_MILLIS = 3_000;
    private static final long AFTER_DAMAGE_MILLIS = 1_000;
    private static final long REWARD_QUIET_MILLIS = 750;
    private static final long MAX_DELAY_MILLIS = 10_000;
    private final Deque<TrackedFight> pending = new ArrayDeque<>();
    private final Deque<Tracker.KillSummary> ready = new ArrayDeque<>();

    void add(TrackedFight fight, long now, long killNumber) {
        fight.sessionKillNumber = killNumber;
        fight.reportAfterAt =
                Math.max(fight.deathAt + AFTER_DEATH_MILLIS, now + AFTER_DAMAGE_MILLIS);
        pending.addLast(fight);
    }

    void flush(Ledger ledger, boolean enabled, long now) {
        for (Iterator<TrackedFight> iterator = pending.iterator(); iterator.hasNext(); ) {
            TrackedFight fight = iterator.next();
            if (now < fight.reportAfterAt
                    || (now - fight.lastRewardAt < REWARD_QUIET_MILLIS
                            && now - fight.deathAt < MAX_DELAY_MILLIS)) continue;
            Ledger.Stats stats = ledger.fightStats(fight.history.id);
            if (enabled && stats.kills() > 0) {
                ready.addLast(
                        new Tracker.KillSummary(
                                fight.history.duration(),
                                fight.damage,
                                stats.revenue(),
                                stats.costs(),
                                stats.unpriced(),
                                fight.sessionKillNumber,
                                ledger.fightScavengerCoins(fight.history.id)));
            }
            iterator.remove();
        }
    }

    List<Tracker.KillSummary> drain() {
        List<Tracker.KillSummary> result = List.copyOf(ready);
        ready.clear();
        return result;
    }

    void clear() {
        pending.clear();
        ready.clear();
    }
}

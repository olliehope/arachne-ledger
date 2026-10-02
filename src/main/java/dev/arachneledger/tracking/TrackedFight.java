package dev.arachneledger.tracking;

import dev.arachneledger.ledger.FightRecord;

/** Transient reward/report timing; the saved fight remains the authority on its outcome. */
final class TrackedFight {
    final FightRecord history;
    long deathAt;
    long damage;
    long lastRewardAt;
    long reportAfterAt;
    long sessionKillNumber;

    TrackedFight(FightRecord history) {
        this.history = history;
    }
}

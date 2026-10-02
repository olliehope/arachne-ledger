package dev.arachneledger.tracking;

import static dev.arachneledger.diagnostics.TrackingDiagnostics.Kind.*;
import static dev.arachneledger.diagnostics.TrackingDiagnostics.Result.*;

import dev.arachneledger.config.Config;
import dev.arachneledger.diagnostics.TrackingDiagnostics;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.Messages;

import java.util.ArrayList;
import java.util.List;

/** Owns fight transitions and damage qualification; it never prices loot or writes files. */
final class FightLifecycle {
    private static final long DAMAGE_SUMMARY_WINDOW_MILLIS = 5_000;
    private final TrackingClock clock;
    private final FightReports reports;
    private final TrackingDiagnostics diagnostics;
    final List<Long> pendingSummonEntryIds = new ArrayList<>();
    TrackedFight activeFight, rewardFight, awaitingDamageFight;
    long awaitingDamageSince, lastDeathAt;
    private boolean changed;

    FightLifecycle(TrackingClock clock, FightReports reports, TrackingDiagnostics diagnostics) {
        this.clock = clock;
        this.reports = reports;
        this.diagnostics = diagnostics;
    }

    boolean consumeChanges() {
        boolean result = changed;
        changed = false;
        return result;
    }

    void clearRuntime() {
        activeFight = null;
        rewardFight = null;
        awaitingDamageFight = null;
        awaitingDamageSince = 0;
        lastDeathAt = 0;
        pendingSummonEntryIds.clear();
    }

    void interrupt(Ledger ledger) {
        if (activeFight != null) {
            activeFight.history.outcome = FightRecord.Outcome.INTERRUPTED;
            activeFight.history.activeEnd = ledger.activeMillis;
            changed = true;
        }
        if (awaitingDamageFight != null
                && awaitingDamageFight.history.outcome == FightRecord.Outcome.WAITING_DAMAGE) {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.MISSING_DAMAGE;
            changed = true;
        }
    }

    void expireDamageSummary(long now) {
        if (awaitingDamageFight != null
                && now - awaitingDamageSince > DAMAGE_SUMMARY_WINDOW_MILLIS) {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.MISSING_DAMAGE;
            diagnostics.record(
                    now,
                    DAMAGE,
                    IGNORED,
                    "Fight " + awaitingDamageFight.history.id,
                    "No personal damage summary within 5 seconds");
            awaitingDamageFight = null;
            awaitingDamageSince = 0;
            changed = true;
        }
    }

    void observeBossActivity(Ledger ledger, Config config, Messages.Event event, long now) {
        boolean confirmedSpawn = event == Messages.Event.SPAWN;
        // Generic activity can recover a missed welcome. After a death it needs a fresh summon
        // cue, so old dialogue cannot restart the fight or extend the AFK grace.
        if (activeFight == null
                && (confirmedSpawn || clock.activeGraceUntil == 0 || now < clock.summoningUntil)) {
            activeFight = beginFight(ledger, confirmedSpawn ? now : 0, config.minimumDamage);
            diagnostics.record(
                    now,
                    SPAWN,
                    ACCEPTED,
                    "Fight " + activeFight.history.id,
                    confirmedSpawn
                            ? "Confirmed boss spawn"
                            : "Recovered boss activity; spawn time unknown");
            clock.spawned();
        } else if (activeFight != null && confirmedSpawn) {
            // A Crystal ritual may emit activity before its welcome. Confirm the existing fight
            // rather than losing its spawn marker or creating a second fight.
            if (ledger.confirmFightSpawn(activeFight.history.id, now)) {
                changed = true;
                diagnostics.record(
                        now,
                        SPAWN,
                        ACCEPTED,
                        "Fight " + activeFight.history.id,
                        "Confirmed the recovered fight's spawn");
            } else {
                diagnostics.record(
                        now, SPAWN, IGNORED, "Repeated spawn cue", "A fight is already active");
            }
        }
        if (activeFight == null) {
            diagnostics.record(
                    now,
                    SPAWN,
                    IGNORED,
                    "Boss activity",
                    "Needs a fresh summon or confirmed spawn after a death");
        }
    }

    boolean observeBossDeath(Ledger ledger, Config config, long now) {
        // A relayed results block cannot become another death without an intervening fight.
        if (lastDeathAt != 0 && activeFight == null) {
            diagnostics.record(
                    now, DEATH, IGNORED, "Repeated boss death", "No intervening active fight");
            return false;
        }
        lastDeathAt = now;
        clock.summoningUntil = 0;
        if (awaitingDamageFight != null
                && awaitingDamageFight.history.outcome == FightRecord.Outcome.WAITING_DAMAGE) {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.MISSING_DAMAGE;
        }
        rewardFight =
                activeFight != null ? activeFight : beginFight(ledger, 0, config.minimumDamage);
        rewardFight.deathAt = now;
        awaitingDamageFight = rewardFight;
        activeFight = null;

        FightRecord history = rewardFight.history;
        history.died = now;
        history.activeEnd = ledger.activeMillis;
        history.minimumDamage = Math.max(1, config.minimumDamage);
        history.outcome = FightRecord.Outcome.WAITING_DAMAGE;
        diagnostics.record(
                now,
                DEATH,
                ACCEPTED,
                "Fight " + history.id,
                "Waiting for your personal damage summary");
        changed = true;

        clock.died(now);
        awaitingDamageSince = now;
        return true;
    }

    void observeDamageSummary(Ledger ledger, String message, long now) {
        var damage = Messages.damage(message);
        if (awaitingDamageSince <= 0
                || now < awaitingDamageSince
                || now - awaitingDamageSince > DAMAGE_SUMMARY_WINDOW_MILLIS
                || damage.isEmpty()) {
            if (damage.isPresent())
                diagnostics.record(
                        now, DAMAGE, IGNORED, message, "No recent death awaiting damage");
            return;
        }
        long dealtDamage = damage.getAsLong();
        awaitingDamageFight.damage = dealtDamage;
        awaitingDamageFight.history.damage = dealtDamage;
        changed = true;
        if (dealtDamage >= awaitingDamageFight.history.minimumDamage) {
            countQualifiedKill(ledger, awaitingDamageFight, now);
        } else if (dealtDamage == 0) {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.ZERO_DAMAGE;
        } else {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.LOW_DAMAGE;
        }
        diagnostics.record(
                now,
                DAMAGE,
                dealtDamage >= awaitingDamageFight.history.minimumDamage ? ACCEPTED : IGNORED,
                Long.toString(dealtDamage),
                "Fight "
                        + awaitingDamageFight.history.id
                        + " / "
                        + awaitingDamageFight.history.outcome
                        + " / minimum "
                        + awaitingDamageFight.history.minimumDamage);
        awaitingDamageSince = 0;
        awaitingDamageFight = null;
    }

    private void countQualifiedKill(Ledger ledger, TrackedFight completed, long now) {
        completed.history.outcome = FightRecord.Outcome.COUNTED;
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", now, completed.history.id);
        changed = true;
        reports.add(completed, now, ledger.stats(false).kills());
    }

    private TrackedFight beginFight(Ledger ledger, long spawnedAt, long minimumDamage) {
        FightRecord history = ledger.beginFight(spawnedAt, Math.max(1, minimumDamage));
        ledger.associateSummons(pendingSummonEntryIds, history.id);
        pendingSummonEntryIds.clear();
        changed = true;
        return new TrackedFight(history);
    }
}

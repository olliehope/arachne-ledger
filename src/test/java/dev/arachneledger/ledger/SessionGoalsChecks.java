package dev.arachneledger.ledger;

import com.google.gson.Gson;

import dev.arachneledger.config.SessionGoals;
import dev.arachneledger.config.Store;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.screen.GoalsScreen;

import java.nio.file.Files;

/** Personal goals are session-only and completion alerts never replay when settings are loaded. */
public final class SessionGoalsChecks {
    private static int checks;

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static void eq(double expected, double actual, String why) {
        yes(Math.abs(expected - actual) < .00001, why + ": " + actual);
    }

    public static void main(String[] args) throws Exception {
        durableAcknowledgement();
        SessionGoals goals = new SessionGoals();
        Ledger ledger = new Ledger();
        SessionGoalMonitor monitor = new SessionGoalMonitor();
        yes(
                !monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "Default target is disabled");
        goals.kind = SessionGoals.Kind.KILLS;
        goals.kills = 2;
        yes(
                !monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "Selecting target primes notification");
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", 1);
        eq(.5, GoalProgress.calculate(ledger, goals).fraction(), "Kill progress is partial");
        yes(
                !monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "Partial goal has no alert");
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", 2);
        yes(
                monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "Completion alerts once");
        yes(
                !monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "Further ticks do not replay");
        ledger.undo();
        monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals));
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", 3);
        yes(
                !monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "Corrections do not repeat an earned alert");
        SessionGoalMonitor loaded = new SessionGoalMonitor();
        yes(
                !loaded.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "Loaded completed target stays silent");
        ledger.newSession();
        eq(
                0,
                GoalProgress.calculate(ledger, goals).current(),
                "Lifetime kills do not carry into session goal");
        monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals));
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 2, 0, "server", 4);
        yes(
                monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "New session can finish same goal");
        goals.kind = SessionGoals.Kind.PROFIT;
        goals.profit = 100;
        goals.notify = false;
        monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals));
        ledger.add(Ledger.Kind.LOOT, "STRING", 1, 150, "pickup", 5);
        ledger.add(Ledger.Kind.EXPENSE, "MANUAL", 1, 100, "manual", 6);
        eq(.5, GoalProgress.calculate(ledger, goals).fraction(), "Profit goal deducts costs");
        ledger.add(Ledger.Kind.INCOME, "SCAVENGER_COINS", 1, 100, "scoreboard", 7);
        yes(
                !monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "Muted completion is silent");
        eq(
                1,
                GoalProgress.calculate(ledger, goals).fraction(),
                "Over-complete progress caps at full");
        goals.kind = SessionGoals.Kind.ACTIVE_TIME;
        goals.activeMillis = 60_000;
        ledger.tick(5_000);
        eq(
                5_000,
                GoalProgress.calculate(ledger, goals).current(),
                "Active-time target follows recorded active clock");
        SessionGoals copy = goals.copy();
        copy.kills = 500;
        yes(goals.kills == 2, "Editing goal draft does not mutate saved target");
        SessionGoals malformed =
                new Gson()
                        .fromJson(
                                "{\"kind\":null,\"kills\":-3,\"activeMillis\":9223372036854775807,\"profit\":-1}",
                                SessionGoals.class);
        malformed.validate();
        yes(
                malformed.kind == SessionGoals.Kind.NONE
                        && malformed.kills == 1
                        && malformed.activeMillis == 3_600_000_000L
                        && malformed.profit == 1,
                "Malformed settings migrate to bounded values");
        yes(Format.time(0).equals("00:00:00"), "Zero uses hours minutes seconds");
        yes(Format.time(-1).equals("00:00:00"), "Negative clock is clamped");
        yes(Format.time(90_061_000).equals("25:01:01"), "Hours never turn into days");
        yes(Format.time(3_600_000_000L).equals("1000:00:00"), "Long sessions preserve full hours");
        eq(90_061_000, GoalsScreen.parseTime("25:01:01"), "Time input accepts extended hours");
        for (String invalid :
                new String[] {"00:00:59", "01:60:00", "01:00:60", "1h", "1001:00:00"}) {
            boolean rejected = false;
            try {
                GoalsScreen.parseTime(invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            yes(rejected, "Reject invalid time " + invalid);
        }
        System.out.println("PASS: " + checks + " session goal checks.");
    }

    private static void durableAcknowledgement() throws Exception {
        SessionGoals goals = new SessionGoals();
        goals.kind = SessionGoals.Kind.KILLS;
        goals.kills = 2;
        Ledger ledger = new Ledger();
        SessionGoalMonitor monitor = new SessionGoalMonitor();
        yes(
                !monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "A fresh target starts incomplete");
        yes(!monitor.consumeChanged(), "An incomplete baseline does not request a save");
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 2, 0, "server", 1);
        long historyRevision = ledger.revision();
        yes(
                monitor.update(ledger, goals, GoalProgress.calculate(ledger, goals)),
                "A live completion is delivered");
        yes(monitor.consumeChanged(), "Completion requests acknowledgement persistence");
        yes(!monitor.consumeChanged(), "Acknowledgement dirty state drains once");
        eq(
                historyRevision,
                ledger.revision(),
                "Display acknowledgement does not invalidate financial caches");
        yes(
                ledger.sessionGoalAcknowledgement.target() == 2,
                "Only the selected target is acknowledged");

        ledger.undo();
        var file = Files.createTempDirectory("arachne-goal-acknowledgement").resolve("ledger.json");
        Store.write(file, ledger);
        Ledger restored = Store.read(file, Ledger.class, Ledger::new, Ledger::validate);
        yes(
                ledger.sessionGoalAcknowledgement.equals(restored.sessionGoalAcknowledgement),
                "Acknowledgement survives correction and JSON reload");
        SessionGoalMonitor reloadedMonitor = new SessionGoalMonitor();
        yes(
                !reloadedMonitor.update(restored, goals, GoalProgress.calculate(restored, goals)),
                "Corrected loaded goal retains its acknowledgement");
        yes(!reloadedMonitor.consumeChanged(), "A loaded acknowledgement never rewrites itself");
        restored.add(Ledger.Kind.KILL, "ARACHNE", 2, 0, "server", 2);
        yes(
                !reloadedMonitor.update(restored, goals, GoalProgress.calculate(restored, goals)),
                "Correction then reload then recross never repeats a completed target");
        goals.notify = false;
        goals.profit = 500;
        goals.activeMillis = 120_000;
        yes(
                !reloadedMonitor.update(restored, goals, GoalProgress.calculate(restored, goals)),
                "Muting and inactive targets cannot reopen a selected target");
        goals.notify = true;
        yes(
                !reloadedMonitor.update(restored, goals, GoalProgress.calculate(restored, goals)),
                "Unmuting keeps the selected target acknowledged");
        goals.kind = SessionGoals.Kind.NONE;
        reloadedMonitor.update(restored, goals, GoalProgress.calculate(restored, goals));
        goals.kind = SessionGoals.Kind.KILLS;
        yes(
                !reloadedMonitor.update(restored, goals, GoalProgress.calculate(restored, goals)),
                "Turning the same goal off and on does not replay completion");

        restored.newSession();
        reloadedMonitor.update(restored, goals, GoalProgress.calculate(restored, goals));
        restored.add(Ledger.Kind.KILL, "ARACHNE", 2, 0, "server", 3);
        yes(
                reloadedMonitor.update(restored, goals, GoalProgress.calculate(restored, goals)),
                "New session can complete the same target");
        yes(reloadedMonitor.consumeChanged(), "New session acknowledgement requires a save");

        Ledger imported = new Ledger();
        imported.add(Ledger.Kind.KILL, "ARACHNE", 2, 0, "server", 4);
        SessionGoalMonitor baseline = new SessionGoalMonitor();
        yes(
                !baseline.update(imported, goals, GoalProgress.calculate(imported, goals)),
                "An already complete imported goal stays silent");
        yes(baseline.consumeChanged(), "Already complete imported goals are durably acknowledged");
        imported.sessionGoalAcknowledgement = new SessionGoalMonitor.Acknowledgement(1, null, -1);
        imported.validate();
        yes(
                imported.sessionGoalAcknowledgement == null,
                "Malformed optional metadata cannot block financial history");
        eq(
                2,
                imported.stats(false).kills(),
                "Discarding invalid acknowledgement preserves counted data");
        Ledger legacy = new Gson().fromJson("{}", Ledger.class);
        legacy.validate();
        yes(
                legacy.sessionGoalAcknowledgement == null,
                "Old ledgers migrate without invented acknowledgement");

        var directory = Files.createTempDirectory("arachne-goal-metadata-save");
        Tracker tracker = new Tracker(directory);
        tracker.account("Goal");
        SessionGoalMonitor clientMonitor = new SessionGoalMonitor();
        clientMonitor.update(tracker.ledger, goals, GoalProgress.calculate(tracker.ledger, goals));
        tracker.record(Ledger.Kind.KILL, "ARACHNE", 2, 0, "server", 5);
        tracker.save();
        yes(
                clientMonitor.update(
                        tracker.ledger, goals, GoalProgress.calculate(tracker.ledger, goals)),
                "A clean saved journal can still produce a new goal acknowledgement");
        yes(
                clientMonitor.consumeChanged(),
                "Client metadata must be saved even without dirty receipts");
        tracker.saveJournalMetadata();
        yes(
                tracker.ready() && tracker.error.isEmpty(),
                "Display metadata uses the normal durable journal save");
        Ledger saved =
                Store.read(
                        directory.resolve("Goal-default.json"),
                        Ledger.class,
                        Ledger::new,
                        Ledger::validate);
        yes(
                saved.sessionGoalAcknowledgement.equals(tracker.ledger.sessionGoalAcknowledgement),
                "Metadata save persists acknowledgement before delivery");
    }

    private SessionGoalsChecks() {}
}

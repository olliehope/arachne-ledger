package dev.arachneledger;

import java.nio.file.Files;
import java.util.UUID;

/** Crystal fights can receive ordinary boss dialogue before their known welcome. */
public final class T2SpawnChecks {
    private static int checks;
    private static final long BASE = 1_000_000;
    private static final String PLAYER = "realpoopy123";
    // The bracketed head is the user's textual representation of the inline player head.
    private static final String OWN_CRYSTAL =
            "☄ [realpoopy123 head]realpoopy123 placed an Arachne Crystal! Something is awakening!";
    private static final String OTHER_CRYSTAL =
            "☄ [OtherPlayer head]OtherPlayer placed an Arachne Crystal! Something is awakening!";
    private static final String T2_WELCOME = "[BOSS] Arachne: With your sacrifice.";
    private static final String T1_WELCOME = "[BOSS] Arachne: A befitting welcome!";
    // Synthetic ordinary dialogue exercises recovery without claiming a new server phrase.
    private static final String ACTIVITY = "[BOSS] Arachne: Your sacrifice is accepted.";

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) {
            throw new AssertionError(why);
        }
    }

    private static void eq(long expected, long actual, String why) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void coins(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static Tracker tracker() throws Exception {
        Tracker tracker = new Tracker(Files.createTempDirectory("arachne-t2-spawns-"));
        tracker.account("account");
        tracker.config.crystalConfigured = true;
        tracker.config.crystalCost = 100_000;
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE);
        tick(tracker, 0);
        return tracker;
    }

    private static void message(Tracker tracker, String text, long elapsed) {
        tracker.message(text, PLAYER, BASE + elapsed);
    }

    private static void tick(Tracker tracker, long elapsed) {
        tracker.tick(BASE + elapsed, true);
    }

    private static GraphData.Snapshot graph(Tracker tracker) {
        return GraphData.build(tracker.ledger, false, tracker.config.graph);
    }

    private static void down(Tracker tracker, long elapsed, long damage) {
        message(tracker, "ARACHNE DOWN!", elapsed);
        message(tracker, "Your Damage: " + damage + " (Position #1)", elapsed + 10);
    }

    public static void main(String[] args) throws Exception {
        yes(
                Messages.isSummoning(OWN_CRYSTAL),
                "Player-head representation retains the Crystal awakening cue");
        yes(Messages.isSummoning(OTHER_CRYSTAL), "Other player's head does not prevent awakening");
        yes(
                Messages.parse(OWN_CRYSTAL, PLAYER) == Messages.Event.CRYSTAL,
                "Exact own placement identifies Crystal cost ownership");
        yes(
                Messages.parse(OTHER_CRYSTAL, PLAYER) == Messages.Event.NONE,
                "Other player's Crystal is not charged");
        yes(
                Messages.parse("☄ YOU placed an Arachne Crystal! Something is awakening!", PLAYER)
                        == Messages.Event.CRYSTAL,
                "YOU remains a valid Crystal cost owner");
        yes(
                Messages.parse(ACTIVITY, PLAYER) == Messages.Event.ACTIVITY,
                "Synthetic earlier dialogue is ordinary boss activity");
        yes(
                Messages.parse(T2_WELCOME, PLAYER) == Messages.Event.SPAWN,
                "Verified T2 welcome confirms spawn");
        yes(
                !Messages.isSummoning("Party > realpoopy123: " + OWN_CRYSTAL),
                "Quoted head placement cannot awaken a boss");

        Tracker t2 = tracker();
        message(t2, OWN_CRYSTAL, 1000);
        yes(t2.isSummoning(), "Crystal placement waits for a confirmed spawn");
        eq(0, t2.ledger.fights.size(), "Placement alone creates no fight");
        coins(100_000, t2.ledger.stats(false).costs(), "Own Crystal is charged once");
        tick(t2, 2000);
        eq(
                0,
                t2.ledger.activeMillis,
                "Crystal ritual does not add active time before boss dialogue");
        message(t2, ACTIVITY, 3000);
        FightRecord provisional = t2.ledger.fights.getFirst();
        long fightId = provisional.id;
        eq(0, provisional.spawned, "Earlier ordinary dialogue creates an unknown-duration fight");
        yes(t2.timerState().equals("Fighting"), "Ordinary boss activity recovers the active fight");
        GraphData.Snapshot beforeWelcome = graph(t2);
        eq(0, beforeWelcome.spawnCount(), "An unknown start has no confirmed spawn marker");
        eq(
                fightId,
                t2.ledger.entries.getFirst().fightId(),
                "Own Crystal is associated with the provisional fight");
        tick(t2, 5000);
        eq(2000, t2.ledger.activeMillis, "Recovered fight advances active time once");
        message(t2, T2_WELCOME, 6000);
        eq(
                BASE + 6000,
                provisional.spawned,
                "Later known Crystal welcome upgrades the provisional spawn timestamp");
        eq(
                fightId,
                t2.ledger.fights.getFirst().id,
                "Confirming spawn preserves the fight identity");
        eq(1, t2.ledger.fights.size(), "Confirming spawn does not create a second fight");
        eq(
                3000,
                t2.ledger.activeMillis,
                "Confirming spawn neither resets nor duplicates active time");
        GraphData.Snapshot afterWelcome = graph(t2);
        eq(1, afterWelcome.spawnCount(), "Confirmed Crystal spawn appears in the graph count");
        eq(1, afterWelcome.spawns().size(), "Confirmed Crystal spawn creates one graph marker");
        eq(
                fightId,
                afterWelcome.spawns().getFirst().fightId(),
                "Graph marker references the original fight");
        eq(
                3000,
                afterWelcome.spawns().getFirst().elapsed(),
                "Marker uses welcome confirmation on the active-time axis");
        eq(
                3000,
                provisional.spawnActiveMillis,
                "Confirmed marker position is recorded independently of provisional start");
        eq(
                0,
                provisional.activeStart,
                "Confirmation preserves the original fight accounting origin");
        yes(
                beforeWelcome != afterWelcome,
                "Spawn confirmation invalidates the cached graph without a journal change");
        eq(1, t2.ledger.entries.size(), "Spawn confirmation adds no monetary entry");
        coins(
                100_000,
                t2.ledger.fightStats(fightId).costs(),
                "Original Crystal charge stays attached after confirmation");

        message(t2, T2_WELCOME, 6500);
        message(t2, T1_WELCOME, 7000);
        message(t2, "[BOSS] Arachne: The Era of Spiders begins now.", 8000);
        eq(
                BASE + 6000,
                provisional.spawned,
                "Repeated or alternate known spawn cues retain the first confirmed timestamp");
        eq(1, t2.ledger.fights.size(), "Repeated known cues do not split a fight");
        eq(1, graph(t2).spawnCount(), "Repeated cues do not add graph spawns");
        eq(
                3000,
                graph(t2).spawns().getFirst().elapsed(),
                "Repeated cues do not move the confirmed marker");
        tick(t2, 11000);
        eq(8000, t2.ledger.activeMillis, "Dialogue and known cues do not count active time twice");
        down(t2, 12000, 10_000);
        eq(
                1,
                t2.ledger.stats(false).kills(),
                "Confirmed T2 with exactly minimum damage counts once");
        yes(
                provisional.outcome == FightRecord.Outcome.COUNTED,
                "Qualified result belongs to the confirmed original fight");
        eq(6000, provisional.duration(), "History measures confirmed welcome to death");
        t2.observeLootStand(UUID.randomUUID(), "Soul String x44", BASE + 12100);
        tick(t2, 15500);
        var reports = t2.drainKillSummaries();
        eq(1, reports.size(), "Crystal fight emits one kill summary");
        eq(
                6000,
                reports.getFirst().fightMillis(),
                "Runtime summary uses the upgraded spawn timestamp");
        eq(10_000, reports.getFirst().damage(), "Summary keeps server damage");
        coins(100_000, reports.getFirst().costs(), "Summary includes original Crystal expense");
        coins(220_000, reports.getFirst().income(), "Summary includes the reward labels");
        coins(
                120_000,
                reports.getFirst().profit(),
                "Confirming a spawn preserves reward accounting");
        yes(t2.drainKillSummaries().isEmpty(), "Summary is drained exactly once");
        t2.save();
        java.nio.file.Path savedPath = Files.createTempFile("arachne-t2-confirmed-", ".json");
        Store.write(savedPath, t2.ledger);
        Ledger restored = Store.read(savedPath, Ledger.class, Ledger::new, Ledger::validate);
        eq(
                BASE + 6000,
                restored.fight(fightId).spawned,
                "Confirmed timestamp survives storage round-trip");
        eq(
                3000,
                restored.fight(fightId).spawnActiveMillis,
                "Confirmed active marker survives storage round-trip");
        eq(
                1,
                GraphData.build(restored, false, t2.config.graph).spawnCount(),
                "Restored Crystal history retains its graph marker");
        eq(
                3000,
                GraphData.build(restored, false, t2.config.graph).spawns().getFirst().elapsed(),
                "Restored marker stays at confirmation rather than earlier activity");

        for (long at = 20500; at <= 70500; at += 5000) {
            tick(t2, at);
        }
        tick(t2, 72000);
        yes(t2.isAfk(), "Completed T2 becomes AFK at sixty seconds after death");
        eq(
                69000,
                t2.ledger.activeMillis,
                "AFK boundary retains recovered active time without duplication");
        message(t2, OTHER_CRYSTAL, 75000);
        yes(
                t2.isSummoning() && !t2.isAfk(),
                "Another player's Crystal starts the second awakening from AFK");
        tick(t2, 80000);
        eq(
                69000,
                t2.ledger.activeMillis,
                "Second Crystal ritual does not revive active time before dialogue");
        message(t2, ACTIVITY, 81000);
        FightRecord second = t2.ledger.fights.getLast();
        eq(0, second.spawned, "Second early dialogue also starts provisional history");
        eq(1, graph(t2).spawnCount(), "Second provisional fight has no marker yet");
        tick(t2, 86000);
        message(t2, T2_WELCOME, 87000);
        eq(BASE + 87000, second.spawned, "Second Crystal welcome upgrades its own fight");
        eq(2, graph(t2).spawnCount(), "Both confirmed Crystal spawns are visible after AFK");
        eq(
                75000,
                graph(t2).spawns().getLast().elapsed(),
                "Second confirmed marker excludes the AFK and ritual gaps");
        yes(
                !t2.isAfk() && !t2.isSummoning() && t2.timerState().equals("Fighting"),
                "Confirmed second Crystal is actively tracked");
        coins(
                100_000,
                t2.ledger.stats(false).costs(),
                "Another player's summon does not add personal expense");
        down(t2, 90000, 9999);
        tick(t2, 93500);
        eq(
                1,
                t2.ledger.stats(false).kills(),
                "Damage below ten thousand still excludes the second T2 kill");
        yes(
                second.outcome == FightRecord.Outcome.LOW_DAMAGE,
                "Low participation remains recorded in fight history");
        eq(3000, second.duration(), "Skipped T2 retains its confirmed duration");
        eq(
                2,
                graph(t2).spawnCount(),
                "Skipped participation does not erase a genuine confirmed spawn");
        yes(
                t2.drainKillSummaries().isEmpty(),
                "Low damage cannot produce a successful kill chat summary");

        Tracker zero = tracker();
        message(zero, OWN_CRYSTAL, 1000);
        message(zero, ACTIVITY, 2000);
        message(zero, T2_WELCOME, 3000);
        down(zero, 5000, 0);
        tick(zero, 8500);
        eq(
                0,
                zero.ledger.stats(false).kills(),
                "Zero damage cannot become a kill after spawn confirmation");
        yes(
                zero.ledger.fights.getFirst().outcome == FightRecord.Outcome.ZERO_DAMAGE,
                "Zero-damage T2 remains visible as skipped history");
        eq(1, graph(zero).spawnCount(), "Zero-damage T2 retains its genuine spawn marker");
        coins(
                100_000,
                zero.ledger.stats(false).costs(),
                "Zero-damage T2 retains money actually spent");
        yes(
                zero.drainKillSummaries().isEmpty(),
                "Zero-damage T2 prints no successful kill summary");

        Tracker join = tracker();
        message(join, ACTIVITY, 1000);
        FightRecord joined = join.ledger.fights.getFirst();
        eq(0, joined.spawned, "Mid-fight recovery initially has unknown spawn time");
        GraphData.Snapshot unknownGraph = graph(join);
        tick(join, 6000);
        message(join, T2_WELCOME, 7000);
        eq(
                BASE + 7000,
                joined.spawned,
                "A known welcome also upgrades a fight recovered without a placement");
        eq(
                1,
                graph(join).spawnCount(),
                "Recovered join gains a graph marker after explicit spawn evidence");
        eq(
                6000,
                graph(join).spawns().getFirst().elapsed(),
                "Recovered join marker uses confirmation rather than join time");
        yes(
                unknownGraph != graph(join),
                "No-cost recovered join still invalidates graph metadata cache");
        eq(6000, join.ledger.activeMillis, "Recovering a join preserves accumulated active time");
        down(join, 9000, 20_000);
        tick(join, 12500);
        eq(
                2000,
                join.drainKillSummaries().getFirst().fightMillis(),
                "Recovered join reports welcome-to-death duration");

        for (String firstWelcome : new String[] {T1_WELCOME, T2_WELCOME}) {
            Tracker known = tracker();
            message(known, firstWelcome, 1000);
            message(known, ACTIVITY, 2000);
            message(known, T2_WELCOME, 3000);
            message(known, T1_WELCOME, 4000);
            eq(
                    BASE + 1000,
                    known.ledger.fights.getFirst().spawned,
                    "An already known T1/T2 start is never overwritten");
            eq(1, known.ledger.fights.size(), "An already known T1/T2 stays one fight");
            eq(1, graph(known).spawnCount(), "An already known T1/T2 stays one graph spawn");
            eq(
                    0,
                    graph(known).spawns().getFirst().elapsed(),
                    "An already known T1/T2 retains its original graph position");
            down(known, 6000, 10_000);
            tick(known, 9500);
            eq(
                    5000,
                    known.drainKillSummaries().getFirst().fightMillis(),
                    "Known T1/T2 duration keeps its original start");
        }

        Tracker unconfirmed = tracker();
        message(unconfirmed, OTHER_CRYSTAL, 1000);
        message(unconfirmed, ACTIVITY, 2000);
        down(unconfirmed, 4000, 10_000);
        tick(unconfirmed, 7500);
        eq(
                0,
                unconfirmed.ledger.fights.getFirst().spawned,
                "Ordinary dialogue alone does not invent a confirmed spawn timestamp");
        eq(
                -1,
                unconfirmed.drainKillSummaries().getFirst().fightMillis(),
                "Missing known welcome retains an unknown kill duration");
        eq(0, graph(unconfirmed).spawnCount(), "Missing welcome retains no confirmed graph marker");

        // Schema-1 histories from 1.8 lack the new marker field; their old positions remain valid.
        java.nio.file.Path oldPath = Files.createTempFile("arachne-t2-legacy-", ".json");
        Files.writeString(
                oldPath,
                """
            {"schema":1,"activeMillis":9000,"sessionMillis":0,"sessionStart":0,"entries":[],
             "sessionId":1,"fights":[
               {"id":1,"session":1,"spawned":1001000,"died":1006000,"activeStart":2000,"activeEnd":7000,
                "damage":10000,"minimumDamage":10000,"outcome":"COUNTED"},
               {"id":2,"session":1,"spawned":0,"died":0,"activeStart":7000,"activeEnd":9000,
                "damage":-1,"minimumDamage":10000,"outcome":"INTERRUPTED"}]}
            """);
        Ledger legacy = Store.read(oldPath, Ledger.class, Ledger::new, Ledger::validate);
        eq(
                -1,
                legacy.fight(1).spawnActiveMillis,
                "Older confirmed history receives the legacy marker fallback");
        eq(
                -1,
                legacy.fight(2).spawnActiveMillis,
                "Older unknown history retains an unknown marker position");
        var legacyGraph = GraphData.build(legacy, false, null);
        eq(
                1,
                legacyGraph.spawnCount(),
                "Older known history keeps one spawn while unknown history remains excluded");
        eq(
                2000,
                legacyGraph.spawns().getFirst().elapsed(),
                "Older known history keeps its original activeStart marker");
        long oldRevision = legacy.revision();
        yes(
                !legacy.confirmFightSpawn(1, BASE + 15000),
                "Existing legacy spawn cannot be overwritten");
        eq(
                oldRevision,
                legacy.revision(),
                "Ignoring an already known spawn does not invalidate cached history");
        eq(
                2000,
                GraphData.build(legacy, false, null).spawns().getFirst().elapsed(),
                "Ignored legacy confirmation leaves the marker unchanged");

        for (FightRecord.Outcome outcome : FightRecord.Outcome.values()) {
            if (outcome == FightRecord.Outcome.FIGHTING) {
                continue;
            }
            Ledger ended = new Ledger();
            FightRecord history = ended.beginFight(0, 10_000);
            history.outcome = outcome;
            if (outcome != FightRecord.Outcome.INTERRUPTED) {
                history.died = BASE + 5000;
            }
            long revision = ended.revision();
            yes(
                    !ended.confirmFightSpawn(history.id, BASE + 6000),
                    "Completed or interrupted history cannot acquire a late spawn");
            eq(0, history.spawned, "Rejected late confirmation keeps unknown history unknown");
            eq(
                    revision,
                    ended.revision(),
                    "Rejected late confirmation preserves graph cache revision");
            eq(
                    0,
                    GraphData.build(ended, false, null).spawnCount(),
                    "Rejected late confirmation invents no graph marker");
        }
        System.out.println(
                "PASS: "
                        + checks
                        + " T2 Crystal recovery, spawn confirmation, graph, timing and ownership checks.");
    }

    private T2SpawnChecks() {}
}

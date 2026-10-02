package dev.arachneledger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Pure regression scenarios for selectable graph series, spawn markers and future projections. */
public final class GraphChecks {
    private static int checks;
    private static final GraphPreferences.Metric PROFIT = GraphPreferences.Metric.PROFIT;
    private static final GraphPreferences.Metric LOOT = GraphPreferences.Metric.LOOT;
    private static final GraphPreferences.Metric COSTS = GraphPreferences.Metric.COSTS;

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) {
            throw new AssertionError(why);
        }
    }

    private static void advance(Ledger ledger, long millis) {
        while (millis > 0) {
            long delta = Math.min(5_000, millis);
            ledger.tick(delta);
            millis -= delta;
        }
    }

    private static void add(Ledger ledger, Ledger.Kind kind, String item, long count, double unit) {
        ledger.add(kind, item, count, unit, "test", 1_000_000 + ledger.activeMillis);
    }

    private static GraphPreferences allSeries() {
        GraphPreferences prefs = new GraphPreferences();
        prefs.series = EnumSet.allOf(GraphPreferences.Metric.class);
        return prefs;
    }

    private static void immutable(Runnable mutation, String why) {
        checks++;
        try {
            mutation.run();
            throw new AssertionError(why);
        } catch (UnsupportedOperationException expected) {
        }
    }

    public static void main(String[] args) throws Exception {
        Ledger ledger = new Ledger();
        GraphPreferences prefs = allSeries();
        GraphData.Snapshot empty = GraphData.build(ledger, false, prefs);
        eq(0, empty.elapsed(), "Empty active elapsed");
        eq(0, empty.duration(), "Empty axis has no invented future");
        eq(0, empty.value(PROFIT), "Empty profit");
        eq(0, empty.hourly(LOOT), "No division by zero");
        yes(!empty.projectionReady(), "Empty projection is unavailable");
        eq(3, empty.series().size(), "Each selected series exists before the first entry");
        eq(1, empty.series().get(PROFIT).size(), "Actual graphs begin at zero");
        eq(0, empty.series().get(PROFIT).getFirst().profit(), "Initial zero value");
        yes(GraphData.build(ledger, false, prefs) == empty, "Unchanged render reuses snapshot");

        add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 100_000);
        FightRecord counted = ledger.beginFight(1_000_000, 10_000);
        advance(ledger, 50_000);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 44, 5_000);
        add(ledger, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 2_317);
        add(ledger, Ledger.Kind.INCOME, "MANUAL", 1, 5_000);
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        counted.outcome = FightRecord.Outcome.COUNTED;
        counted.activeEnd = ledger.activeMillis;
        prefs.showProjection = true;
        GraphData.Snapshot beforeWarmup = GraphData.build(ledger, false, prefs);
        eq(127_317, beforeWarmup.value(PROFIT), "Profit includes loot and other income less cost");
        eq(220_000, beforeWarmup.value(LOOT), "Loot excludes Scavenger and manual income");
        eq(100_000, beforeWarmup.value(COSTS), "Cost curve stays positive");
        eq(
                -100_000,
                beforeWarmup.series().get(PROFIT).get(1).profit(),
                "Crystal creates a negative profit point");
        eq(0, beforeWarmup.series().get(LOOT).get(1).profit(), "Crystal does not lower loot");
        eq(100_000, beforeWarmup.series().get(COSTS).get(1).profit(), "Crystal raises costs");
        eq(50_000, beforeWarmup.elapsed(), "Axis uses active time");
        eq(50_000, beforeWarmup.duration(), "Warming projection does not extend axis");
        eq(0, beforeWarmup.projections().size(), "No speculative line during warmup");
        eq(1, beforeWarmup.spawnCount(), "Known spawn counted separately from a kill");
        eq(counted.id, beforeWarmup.spawns().getFirst().fightId(), "Marker identifies its fight");
        eq(0, beforeWarmup.spawns().getFirst().elapsed(), "First spawn at session origin");

        advance(ledger, 10_000);
        GraphData.Snapshot minute = GraphData.build(ledger, false, prefs);
        yes(minute.projectionReady(), "One minute and a qualifying kill enable projected pace");
        eq(60_000, minute.windowMillis(), "Initial projection uses observed window length");
        eq(1, minute.windowKills(), "Projection requires a qualifying kill entry");
        eq(360_000, minute.duration(), "Future line extends five active minutes");
        eq(7_639_020, minute.hourly(PROFIT), "Measured hourly profit");
        eq(13_200_000, minute.hourly(LOOT), "Measured hourly loot");
        eq(6_000_000, minute.hourly(COSTS), "Measured hourly costs");
        eq(
                minute.hourly(LOOT),
                minute.projectedHourly(LOOT),
                "Initial rolling loot pace matches initial session");
        eq(
                763_902,
                minute.projectedTotal(PROFIT),
                "Future value anchors to current profit then adds five minutes");
        List<Ledger.Point> forecast = minute.projections().get(LOOT);
        eq(2, forecast.size(), "Projection is a future segment, not another historical curve");
        eq(60_000, forecast.getFirst().elapsed(), "Projection starts now");
        eq(220_000, forecast.getFirst().profit(), "Projection starts at selected scope value");
        eq(360_000, forecast.getLast().elapsed(), "Projection ends five active minutes from now");
        eq(1_320_000, forecast.getLast().profit(), "Loot future adds observed loot pace");
        immutable(() -> minute.series().clear(), "Series map is immutable");
        immutable(() -> minute.series().get(PROFIT).clear(), "Point list is immutable");
        immutable(() -> minute.values().clear(), "Totals map is immutable");
        immutable(() -> minute.spawns().clear(), "Marker list is immutable");

        List<Ledger.Point> cachedPoints = minute.series().get(PROFIT);
        advance(ledger, 1_000);
        GraphData.Snapshot ticking = GraphData.build(ledger, false, prefs);
        yes(ticking != minute, "Active time invalidates the hourly snapshot");
        yes(
                ticking.series().get(PROFIT) == cachedPoints,
                "Active time reuses expensive actual point lists");
        eq(61_000, ticking.elapsed(), "Active elapsed refreshes without a journal entry");
        eq(
                127_317 * 3_600_000.0 / 61_000,
                ticking.hourly(PROFIT),
                "Quiet time changes displayed hourly rate");

        prefs.primary = LOOT;
        GraphData.Snapshot selected = GraphData.build(ledger, false, prefs);
        yes(selected.primary() == LOOT, "Metric choice updates displayed primary");
        yes(selected.series().get(PROFIT) == cachedPoints, "Metric choice reuses recorded points");
        prefs.setVisible(PROFIT, false);
        selected = GraphData.build(ledger, false, prefs);
        yes(!selected.series().containsKey(PROFIT), "Disabled series is absent from graph");
        yes(!selected.projections().containsKey(PROFIT), "Disabled series has no projection");
        eq(127_317, selected.value(PROFIT), "Hiding profit does not change accounting");
        prefs.setVisible(LOOT, false);
        yes(
                GraphData.build(ledger, false, prefs).primary() == COSTS,
                "Hiding primary selects another enabled metric");
        prefs.setVisible(COSTS, false);
        GraphData.Snapshot hidden = GraphData.build(ledger, false, prefs);
        eq(0, hidden.series().size(), "All series can be disabled");
        eq(0, hidden.projections().size(), "No projected curves when all series are disabled");
        eq(hidden.elapsed(), hidden.duration(), "Empty chart does not reserve future space");
        yes(hidden.projectionReady(), "Text projection readiness survives line visibility");
        prefs.setVisible(LOOT, true);
        prefs.showProjection = false;
        GraphData.Snapshot noProjection = GraphData.build(ledger, false, prefs);
        eq(
                noProjection.elapsed(),
                noProjection.duration(),
                "Projection toggle restores actual duration");
        eq(0, noProjection.projections().size(), "Projection toggle hides future segment");
        yes(
                noProjection.projectedHourly(LOOT) > 0,
                "Hourly projected text remains available without the future line");

        add(ledger, Ledger.Kind.CALLING, "ARACHNE_KEEPER_FRAGMENT", 2, 25_000);
        add(ledger, Ledger.Kind.EXPENSE, "MANUAL", 1, 200_000);
        GraphData.Snapshot loss = GraphData.build(ledger, false, prefs);
        eq(-122_683, loss.value(PROFIT), "All costs participate in profit");
        eq(350_000, loss.value(COSTS), "Cost curve sums crystal, calling and expenses");
        yes(loss.projectedHourly(PROFIT) < 0, "Negative pace is retained");
        ledger.undo();
        eq(
                77_317,
                GraphData.build(ledger, false, prefs).value(PROFIT),
                "Undo invalidates graph cache");
        ledger.reprice("SOUL_STRING", 4_000);
        GraphData.Snapshot repriced = GraphData.build(ledger, false, prefs);
        eq(176_000, repriced.value(LOOT), "Explicit session repricing updates recorded loot");
        eq(33_317, repriced.value(PROFIT), "Repricing updates profit curve");

        FightRecord unknown = ledger.beginFight(0, 10_000);
        unknown.outcome = FightRecord.Outcome.MISSING_DAMAGE;
        FightRecord skipped = ledger.beginFight(1_061_000, 10_000);
        skipped.outcome = FightRecord.Outcome.LOW_DAMAGE;
        advance(ledger, 5_000);
        FightRecord interrupted = ledger.beginFight(1_066_000, 10_000);
        interrupted.outcome = FightRecord.Outcome.INTERRUPTED;
        GraphData.Snapshot markers = GraphData.build(ledger, false, prefs);
        eq(3, markers.spawnCount(), "Skipped and interrupted known spawns remain markers");
        yes(
                markers.spawns().stream().noneMatch(marker -> marker.fightId() == unknown.id),
                "Unknown spawn time is never invented");
        eq(61_000, markers.spawns().get(1).elapsed(), "Marker uses recorded active start");
        eq(66_000, markers.spawns().get(2).elapsed(), "Later marker remains on active axis");

        ledger.newSession();
        GraphData.Snapshot reset = GraphData.build(ledger, false, prefs);
        eq(0, reset.value(LOOT), "Session scope resets loot");
        eq(0, reset.spawnCount(), "Old session spawns are excluded even at same active instant");
        yes(!reset.projectionReady(), "Session reset clears recent pace");
        GraphData.Snapshot lifetime = GraphData.build(ledger, true, prefs);
        eq(176_000, lifetime.value(LOOT), "Total scope preserves historical values");
        eq(3, lifetime.spawnCount(), "Total scope preserves all known spawns");
        yes(!lifetime.projectionReady(), "Lifetime totals do not borrow old session pace");
        eq(66_000, lifetime.elapsed(), "Total axis retains lifetime active elapsed");
        FightRecord next = ledger.beginFight(2_000_000, 10_000);
        add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 50_000);
        advance(ledger, 60_000);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 10, 3_000);
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        next.activeEnd = ledger.activeMillis;
        next.outcome = FightRecord.Outcome.COUNTED;
        prefs.showProjection = true;
        GraphData.Snapshot newSession = GraphData.build(ledger, false, prefs);
        lifetime = GraphData.build(ledger, true, prefs);
        eq(30_000, newSession.value(LOOT), "New session uses its own recorded unit price");
        eq(206_000, lifetime.value(LOOT), "Total adds values recorded at different prices");
        eq(1_800_000, newSession.projectedHourly(LOOT), "New session projected loot pace");
        eq(
                newSession.projectedHourly(LOOT),
                lifetime.projectedHourly(LOOT),
                "Total scope uses current-session recent pace");
        eq(180_000, newSession.projectedTotal(LOOT), "Session projection anchors to session loot");
        eq(356_000, lifetime.projectedTotal(LOOT), "Total projection anchors to lifetime loot");
        eq(1, newSession.spawnCount(), "New session marker count");
        eq(
                0,
                newSession.spawns().getFirst().elapsed(),
                "New session marker subtracts session origin");
        eq(4, lifetime.spawnCount(), "Lifetime marker count");
        eq(
                66_000,
                lifetime.spawns().getLast().elapsed(),
                "Lifetime marker does not subtract session origin");
        ledger.reprice("SOUL_STRING", 2_000);
        eq(
                196_000,
                GraphData.build(ledger, true, prefs).value(LOOT),
                "Session repricing leaves prior session prices intact");

        // Five-minute windows are exclusive on the left once they roll, exactly like analytics.
        advance(ledger, 300_000);
        GraphData.Snapshot rolled = GraphData.build(ledger, false, prefs);
        eq(300_000, rolled.windowMillis(), "Recent window caps at five active minutes");
        eq(0, rolled.windowKills(), "Kill exactly at rolling left boundary has expired");
        yes(!rolled.projectionReady(), "No recent qualifying kill means no forecast");
        eq(0, rolled.projections().size(), "Expired pace hides future segment");
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        add(ledger, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 100);
        rolled = GraphData.build(ledger, false, prefs);
        yes(rolled.projectionReady(), "Fresh qualifying kill restores recent forecast");
        eq(
                1_200,
                rolled.projectedHourly(PROFIT),
                "Recent manual coin income affects profit forecast");
        eq(0, rolled.projectedHourly(LOOT), "Coin income never affects loot forecast");
        eq(
                rolled.value(LOOT),
                rolled.projectedTotal(LOOT),
                "Zero recent loot yields a flat future line");
        GraphData.Snapshot beforeValidation = rolled;
        ledger.validate();
        GraphData.Snapshot validated = GraphData.build(ledger, false, prefs);
        yes(validated != beforeValidation, "Validation invalidates external caches");
        eq(
                beforeValidation.value(LOOT),
                validated.value(LOOT),
                "Validation preserves graph values");
        Ledger independent = new Ledger();
        yes(
                !GraphData.build(independent, true, prefs).projectionReady(),
                "Another ledger cannot inherit cached projections");
        eq(
                0,
                GraphData.build(independent, true, prefs).spawnCount(),
                "Another profile has separate spawn history");

        Path folder =
                Files.createTempDirectory(
                        Path.of(System.getProperty("test.root", "build")), "arachne-graphs-");
        Path settingsFile = folder.resolve("settings.json");
        Files.writeString(settingsFile, "{}");
        Config migrated = Store.read(settingsFile, Config.class, Config::new, Config::validate);
        yes(
                migrated.graph.enabledSeries().equals(Set.of(PROFIT)),
                "Old settings receive the established net-profit graph");
        yes(
                migrated.graph.showActiveTime
                        && migrated.graph.showHourly
                        && migrated.graph.showScope,
                "Old settings retain useful graph text defaults");
        yes(
                !migrated.graph.showProjection
                        && !migrated.graph.showSpawns
                        && !migrated.graph.showProjectedTotal,
                "New optional curves, markers and future-total text are initially off");
        migrated.graph.series = EnumSet.of(LOOT, COSTS);
        migrated.graph.primary = LOOT;
        migrated.graph.showProjection = true;
        migrated.graph.showSpawns = true;
        migrated.graph.showActiveTime = false;
        migrated.graph.showTotal = false;
        migrated.graph.showProjectedTotal = true;
        migrated.graph.showSpawnCount = true;
        Store.write(settingsFile, migrated);
        Config reopened = Store.read(settingsFile, Config.class, Config::new, Config::validate);
        yes(
                reopened.graph.enabledSeries().equals(Set.of(LOOT, COSTS)),
                "Enabled series persist across restart");
        yes(reopened.graph.effectiveMetric() == LOOT, "Primary metric persists across restart");
        yes(
                reopened.graph.showProjection
                        && reopened.graph.showSpawns
                        && reopened.graph.showSpawnCount,
                "Future curve and spawn display preferences persist");
        yes(
                !reopened.graph.showActiveTime
                        && !reopened.graph.showTotal
                        && reopened.graph.showProjectedTotal,
                "Independent graph text preferences persist");
        reopened.graph.series = EnumSet.noneOf(GraphPreferences.Metric.class);
        Store.write(settingsFile, reopened);
        reopened = Store.read(settingsFile, Config.class, Config::new, Config::validate);
        yes(
                reopened.graph.enabledSeries().isEmpty(),
                "Intentionally disabling all lines survives validation and restart");
        Files.writeString(settingsFile, "{\"graph\":null}");
        Config explicitNull = Store.read(settingsFile, Config.class, Config::new, Config::validate);
        yes(
                explicitNull.graph.enabledSeries().equals(Set.of(PROFIT)),
                "Null graph settings recover to safe defaults");
        System.out.println(
                "PASS: "
                        + checks
                        + " selectable graph, spawn, projection, scope and cache checks.");
    }
}

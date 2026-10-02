package dev.arachneledger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** End-to-end fight history accounting, migration, corrections and reward attribution. */
public final class HistoryChecks {
    private static int checks;
    private static final long BASE = 1_000_000;
    private static final String SPAWN = "[BOSS] Arachne: Ahhhh...A Calling...";

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static void eq(long expected, long actual, String why) {
        checks++;
        if (expected != actual) throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }
    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001)
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }
    private static void same(Object expected, Object actual, String why) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }
    private static void reject(Runnable action, String why) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException ex) { return; }
        throw new AssertionError(why);
    }
    private static Tracker tracker() throws Exception {
        return tracker(Files.createTempDirectory("arachne-history-"));
    }
    private static Tracker tracker(Path directory) {
        Tracker result = new Tracker(directory);
        result.account("owner");
        result.config.crystalConfigured = true;
        result.config.crystalCost = 100_000;
        result.config.callingCost = 700;
        result.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE);
        result.tick(BASE, true);
        return result;
    }
    private static void message(Tracker tracker, String text, long offset) {
        tracker.message(text, "Player", BASE + offset);
    }
    private static void spawn(Tracker tracker, long offset) { message(tracker, SPAWN, offset); }
    private static void down(Tracker tracker, long offset, long damage) {
        message(tracker, "ARACHNE DOWN!", offset);
        message(tracker, "Your Damage: " + damage + " (Position #1)", offset + 10);
    }
    private static void stand(Tracker tracker, String name, long offset) {
        tracker.observeLootStand(UUID.randomUUID(), name, BASE + offset);
    }
    private static FightRecord latest(Tracker tracker) { return tracker.ledger.recentFights(true).getFirst(); }
    private static void graphs(Ledger.Stats stats, String why) {
        eq(stats.profit(), stats.graph().getLast().profit(), why + " graph endpoint equals profit");
        long previous = 0;
        for (var point : stats.graph()) {
            yes(point.elapsed() >= previous, why + " graph remains chronological");
            previous = point.elapsed();
        }
    }

    public static void main(String[] args) throws Exception {
        migrationAndPersistence();
        qualificationAndThresholds();
        stableSummonsAndLateRewards();
        correctionsAndPricing();
        oldSessionCorrections();
        contextAndIsolation();
        unfinishedAndRecentLimit();
        automaticRngOnly();
        System.out.println("PASS: " + checks + " persistent fight history, correction and attribution checks.");
    }

    private static void migrationAndPersistence() throws Exception {
        Path directory = Files.createTempDirectory("arachne-history-migration-");
        Path file = directory.resolve("ledger.json");
        // A real pre-history ledger has neither IDs nor fight/session metadata.
        Files.writeString(file, """
            {"schema":1,"activeMillis":1000,"sessionMillis":0,"sessionStart":0,"entries":[
              {"at":1,"elapsed":0,"kind":"CRYSTAL","item":"ARACHNE_CRYSTAL","count":1,"unit":100000,"source":"server"},
              {"at":2,"elapsed":1000,"kind":"LOOT","item":"SOUL_STRING","count":44,"unit":5000,"source":"armor_stand"}
            ]}
            """);
        Ledger migrated = Store.read(file, Ledger.class, Ledger::new, Ledger::validate);
        eq(120_000, migrated.stats(true).profit(), "Legacy migration preserves financial totals");
        eq(0, migrated.fights.size(), "Legacy migration does not fabricate fights");
        eq(1, migrated.sessionId, "Legacy session receives a usable session identity");
        yes(migrated.entries.getFirst().id() > 0 && migrated.entries.getLast().id() > 0, "Old entries receive stable IDs");
        yes(migrated.entries.getFirst().id() != migrated.entries.getLast().id(), "Migrated IDs are unique");
        eq(0, migrated.entries.getFirst().fightId(), "Unattributed legacy entry remains outside fight history");
        List<Long> migratedIds = migrated.entries.stream().map(Ledger.Entry::id).toList();
        FightRecord fight = migrated.beginFight(BASE, 12_345);
        fight.died = BASE + 2000; fight.damage = 25_000; fight.activeEnd = migrated.activeMillis;
        fight.outcome = FightRecord.Outcome.COUNTED;
        var kill = migrated.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", BASE + 2000, fight.id);
        Store.write(file, migrated);
        Ledger saved = Store.read(file, Ledger.class, Ledger::new, Ledger::validate);
        same(migratedIds, saved.entries.subList(0, 2).stream().map(Ledger.Entry::id).toList(), "Assigned legacy IDs persist");
        eq(kill.id(), saved.entries.getLast().id(), "New entry ID persists");
        eq(fight.id, saved.entries.getLast().fightId(), "New entry association persists");
        FightRecord restored = saved.fight(fight.id);
        eq(BASE, restored.spawned, "Spawn timestamp persists");
        eq(BASE + 2000, restored.died, "Death timestamp persists");
        eq(25_000, restored.damage, "Damage persists");
        eq(12_345, restored.minimumDamage, "Recorded threshold persists");
        same(FightRecord.Outcome.COUNTED, restored.outcome, "Outcome persists");
        eq(2000, restored.duration(), "Recorded fight duration uses spawn and death");
        var next = saved.add(Ledger.Kind.INCOME, "MANUAL", 1, 1, "test", BASE + 3000);
        yes(next.id() > kill.id(), "Reload cannot reuse an existing entry ID");
        yes(saved.beginFight(BASE + 4000, 10_000).id > fight.id, "Reload cannot reuse an existing fight ID");
    }

    private static void qualificationAndThresholds() throws Exception {
        Tracker counted = tracker();
        spawn(counted, 1000); down(counted, 5000, 10_000);
        FightRecord good = latest(counted);
        same(FightRecord.Outcome.COUNTED, good.outcome, "Exact threshold creates counted history");
        eq(1, counted.ledger.fightStats(good.id).kills(), "Qualified history has its own kill journal entry");
        eq(4000, good.duration(), "History has actual fight duration");
        same("Counted", good.reason(true), "Counted history explains qualification");
        yes(counted.undo(), "A kill can be undone");
        eq(0, counted.ledger.fightStats(good.id).kills(), "Undo changes journal-derived history kills");
        same("Kill entry removed", good.reason(false), "Undo does not falsely display a counted kill");
        counted.tick(BASE + 9000, true);
        yes(counted.drainKillSummaries().isEmpty(), "Undo suppresses a pending successful chat report");

        Tracker zero = tracker(); spawn(zero, 1000); down(zero, 5000, 0);
        same(FightRecord.Outcome.ZERO_DAMAGE, latest(zero).outcome, "Zero damage has its own skip reason");
        eq(0, zero.ledger.stats(true).kills(), "Zero damage history cannot create a kill");
        Tracker low = tracker();
        message(low, "☄ You placed an Arachne Crystal!", 100); spawn(low, 1000); down(low, 5000, 9999);
        stand(low, "Soul String x2", 5100);
        FightRecord skipped = latest(low);
        same(FightRecord.Outcome.LOW_DAMAGE, skipped.outcome, "Low damage has its own skip reason");
        eq(9999, skipped.damage, "Rejected damage is available for inspection");
        eq(-90_000, low.ledger.fightStats(skipped.id).profit(), "Rejected participation keeps actual costs and rewards");
        eq(0, low.ledger.fightStats(skipped.id).kills(), "Skipped fight has no kill journal entry");
        yes(skipped.reason(false).contains("10,000"), "Skip explanation includes the recorded threshold");

        Tracker missing = tracker(); spawn(missing, 1000); message(missing, "ARACHNE DOWN!", 5000);
        FightRecord absent = latest(missing);
        same(FightRecord.Outcome.WAITING_DAMAGE, absent.outcome, "Death initially waits for damage");
        message(missing, "Your Damage: -1 (Position #1)", 5010);
        message(missing, "Party > Player: Your Damage: 999999 (Position #1)", 5020);
        message(missing, "Your Damage: 999999999999999999999999 (Position #1)", 5030);
        same(FightRecord.Outcome.WAITING_DAMAGE, absent.outcome, "Invalid and spoofed damage do not qualify history");
        missing.tick(BASE + 10_001, true);
        same(FightRecord.Outcome.MISSING_DAMAGE, absent.outcome, "Expired damage window finalizes missing damage");
        message(missing, "Your Damage: 100000 (Position #1)", 10_010);
        eq(0, missing.ledger.stats(true).kills(), "Late damage cannot turn missing history into a kill");
        eq(-1, absent.damage, "Absent damage remains explicitly unknown");

        Tracker frozen = tracker(); spawn(frozen, 1000); message(frozen, "ARACHNE DOWN!", 5000);
        frozen.config.minimumDamage = 20_000;
        message(frozen, "Your Damage: 15000 (Position #1)", 5010);
        FightRecord first = latest(frozen);
        same(FightRecord.Outcome.COUNTED, first.outcome, "Threshold is frozen before a delayed damage line");
        eq(10_000, first.minimumDamage, "Old fight retains its decision threshold");
        spawn(frozen, 7000); down(frozen, 9000, 15000);
        FightRecord second = latest(frozen);
        same(FightRecord.Outcome.LOW_DAMAGE, second.outcome, "Later fight uses the newly configured threshold");
        eq(20_000, second.minimumDamage, "Later history records its own threshold");
        eq(10_000, first.minimumDamage, "New settings do not rewrite earlier history");
        Tracker unknown = tracker(); message(unknown, "[BOSS] Arachne: A tough fight!", 1000); down(unknown, 3000, 50000);
        eq(-1, latest(unknown).duration(), "Joining during a fight does not fabricate its spawn time");
    }

    private static void stableSummonsAndLateRewards() throws Exception {
        Tracker stable = tracker();
        message(stable, "☄ You placed an Arachne's Calling! (1/4)", 100);
        long retainedId = stable.ledger.entries.getLast().id();
        message(stable, "☄ You placed an Arachne's Calling! (2/4)", 200);
        long removedId = stable.ledger.entries.getLast().id();
        yes(stable.undo(), "Undo removes a pending placement");
        stable.record(Ledger.Kind.INCOME, "MANUAL", 1, 77, "manual", BASE + 250);
        long manualId = stable.ledger.entries.getLast().id();
        stable.reprice("ARACHNE_KEEPER_FRAGMENT", 900);
        spawn(stable, 1000); down(stable, 4000, 100000);
        FightRecord fight = latest(stable);
        eq(900, stable.ledger.fightStats(fight.id).costs(), "Removed placement cannot remain in a fight's costs");
        var retained = stable.ledger.entries.stream().filter(e -> e.id() == retainedId).findFirst().orElseThrow();
        eq(fight.id, retained.fightId(), "Repriced pending placement keeps its stable association");
        eq(900, retained.unit(), "Summon association preserves the repriced unit cost");
        yes(stable.ledger.entries.stream().noneMatch(e -> e.id() == removedId), "Removed placement ID remains absent");
        eq(0, stable.ledger.entries.stream().filter(e -> e.id() == manualId).findFirst().orElseThrow().fightId(),
            "Unrelated entry reusing a removed index is not mistaken for a summon");

        Tracker late = tracker();
        message(late, "☄ You placed an Arachne Crystal!", 100); spawn(late, 1000); down(late, 4000, 100000);
        FightRecord first = latest(late);
        message(late, "☄ You placed an Arachne's Calling! (1/4)", 4100);
        spawn(late, 5000);
        FightRecord second = latest(late);
        stand(late, "Soul String x44", 6000);
        late.tick(BASE + 7000, true);
        eq(220_000, late.ledger.fightStats(first.id).revenue(), "Prior fight keeps late rewards after the next spawn");
        eq(100_000, late.ledger.fightStats(first.id).costs(), "Prior fight keeps only its own summon cost");
        eq(700, late.ledger.fightStats(second.id).costs(), "Calling placed after death funds the next fight");
        eq(0, late.ledger.fightStats(second.id).revenue(), "New spawn does not steal prior reward labels");
        stand(late, "Arachne Fragment x2", 12_000);
        eq(221_000, late.ledger.fightStats(first.id).revenue(), "History continues receiving labels after the chat report delay");
        down(late, 15_000, 150000); stand(late, "Soul String x10", 15_100);
        eq(50_000, late.ledger.fightStats(second.id).revenue(), "Next death opens rewards for its own fight");
        eq(late.ledger.stats(true).profit(), late.ledger.fightStats(first.id).profit() + late.ledger.fightStats(second.id).profit(),
            "Attributed fights reconcile with overall journal profit");
    }

    private static void correctionsAndPricing() throws Exception {
        Tracker tracker = tracker();
        message(tracker, "☄ You placed an Arachne Crystal!", 100); spawn(tracker, 1000); down(tracker, 4000, 100000);
        stand(tracker, "Soul String x44", 4100); stand(tracker, "Arachne Shard", 4200);
        FightRecord fight = latest(tracker);
        eq(120_000, tracker.ledger.fightStats(fight.id).profit(), "Initial fight profit uses recorded entries");
        eq(1, tracker.ledger.fightStats(fight.id).unpriced(), "Fight flags actual unpriced reward quantity");
        List<Long> soulIds = tracker.ledger.entries.stream().filter(e -> e.item().equals("SOUL_STRING")).map(Ledger.Entry::id).toList();
        tracker.reprice("SOUL_STRING", 6000);
        eq(164_000, tracker.ledger.fightStats(fight.id).profit(), "Repricing updates fight profit from the journal");
        same(soulIds, tracker.ledger.entries.stream().filter(e -> e.item().equals("SOUL_STRING")).map(Ledger.Entry::id).toList(),
            "Repricing retains entry identity");
        tracker.editFightLoot(fight.id, "SOUL_STRING", 40);
        eq(40, tracker.ledger.fightStats(fight.id).loot().get("SOUL_STRING").longValue(), "Fight quantity correction updates its breakdown");
        eq(140_000, tracker.ledger.stats(false).profit(), "Fight correction updates session profit");
        eq(tracker.ledger.stats(true).profit(), tracker.ledger.fightStats(fight.id).profit(), "Corrected single fight matches lifetime totals");
        tracker.reprice("ARACHNE_SHARD", 2000);
        eq(0, tracker.ledger.fightStats(fight.id).unpriced(), "Explicit repricing resolves fight's unpriced warning");
        eq(142_000, tracker.ledger.fightStats(fight.id).profit(), "Repriced unpriced reward updates history profit");
        graphs(tracker.ledger.stats(false), "Corrected session");
        graphs(tracker.ledger.stats(true), "Corrected lifetime");
        graphs(tracker.ledger.fightStats(fight.id), "Corrected fight");
        eq(tracker.ledger.stats(false).revenue(), tracker.ledger.fightLootValues(fight.id).values().stream().mapToDouble(Double::doubleValue).sum(),
            "Drop row values reconcile with journal revenue");
        eq(100_000, tracker.ledger.fightSpend(fight.id, Ledger.Kind.CRYSTAL), "Detailed crystal row uses the same journal costs");
        tracker.ledger.validate();

        Tracker weighted = tracker(); spawn(weighted, 1000); down(weighted, 4000, 100000);
        weighted.config.prices.put("ARACHNE_FRAGMENT", 100.0); stand(weighted, "Arachne Fragment x2", 4100);
        weighted.config.prices.put("ARACHNE_FRAGMENT", 400.0); stand(weighted, "Arachne Fragment x3", 4200);
        FightRecord mixed = latest(weighted);
        eq(1400, weighted.ledger.fightStats(mixed.id).revenue(), "Multiple receipts keep different historical unit values");
        weighted.config.prices.put("ARACHNE_FRAGMENT", 9999.0);
        weighted.editFightLoot(mixed.id, "ARACHNE_FRAGMENT", 10);
        eq(2800, weighted.ledger.fightStats(mixed.id).revenue(), "Quantity increase uses weighted recorded price instead of current price");
        weighted.editFightLoot(mixed.id, "ARACHNE_FRAGMENT", 1);
        eq(280, weighted.ledger.fightStats(mixed.id).revenue(), "Quantity decrease keeps the same historical weighted price");
        weighted.editFightLoot(mixed.id, "ARACHNE_FRAGMENT", 0);
        eq(0, weighted.ledger.fightStats(mixed.id).loot().getOrDefault("ARACHNE_FRAGMENT", 0L).longValue(), "Zero quantity removes a reward");
        eq(0, weighted.ledger.fightStats(mixed.id).revenue(), "Deleted reward no longer contributes profit");
        weighted.ledger.validate();
        reject(() -> weighted.editFightLoot(mixed.id, "UNKNOWN", 1), "Unknown correction item rejected");
        reject(() -> weighted.editFightLoot(mixed.id, "STRING", -1), "Negative correction quantity rejected");
        reject(() -> weighted.editFightLoot(mixed.id, "STRING", 1_000_000_001L), "Unbounded correction quantity rejected");
        Tracker live = tracker(); spawn(live, 1000);
        reject(() -> live.editFightLoot(latest(live).id, "STRING", 1), "Active fight cannot be edited while automatic rewards are pending");
        message(live, "ARACHNE DOWN!", 4000);
        reject(() -> live.editFightLoot(latest(live).id, "STRING", 1), "Waiting for damage cannot be edited");
    }

    private static void oldSessionCorrections() throws Exception {
        Tracker tracker = tracker(); spawn(tracker, 1000); down(tracker, 4000, 100000); stand(tracker, "Soul String x4", 4100);
        FightRecord old = latest(tracker);
        tracker.newSession();
        tracker.record(Ledger.Kind.INCOME, "MANUAL", 1, 777, "manual", BASE + 5000);
        long currentEntryId = tracker.ledger.entries.getLast().id();
        int initialBoundary = tracker.ledger.sessionStart;
        tracker.editFightLoot(old.id, "SOUL_STRING", 0);
        eq(initialBoundary - 1, tracker.ledger.sessionStart, "Removing old reward adjusts session boundary");
        eq(777, tracker.ledger.stats(false).profit(), "Removing old reward does not reduce current session profit");
        eq(777, tracker.ledger.stats(true).profit(), "Removing old reward updates lifetime profit");
        tracker.config.prices.put("ARACHNE_FANG", 5000.0);
        tracker.editFightLoot(old.id, "ARACHNE_FANG", 2);
        eq(10_000, tracker.ledger.fightStats(old.id).profit(), "Previously missing reward can be inserted into old fight");
        eq(10_777, tracker.ledger.stats(true).profit(), "Historical insertion updates lifetime totals");
        eq(777, tracker.ledger.stats(false).profit(), "Historical insertion remains outside current session totals");
        eq(currentEntryId, tracker.ledger.entries.get(tracker.ledger.sessionStart).id(), "Historical insertion preserves actual current-session origin");
        tracker.reprice("ARACHNE_FANG", 8000);
        eq(10_000, tracker.ledger.fightStats(old.id).profit(), "Current-session repricing does not rewrite earlier session prices");
        same(List.of(), tracker.ledger.recentFights(false), "Old fight disappears from current session history");
        graphs(tracker.ledger.stats(false), "Old-fight correction current session");
        graphs(tracker.ledger.stats(true), "Old-fight correction lifetime");
        tracker.ledger.validate();

        // A skipped fight without entries still needs correct insertion when all journal entries are current.
        Tracker emptyOld = tracker(); spawn(emptyOld, 1000); down(emptyOld, 4000, 0);
        FightRecord empty = latest(emptyOld); emptyOld.newSession();
        emptyOld.record(Ledger.Kind.INCOME, "MANUAL", 1, 50, "manual", BASE + 5000);
        emptyOld.editFightLoot(empty.id, "STRING", 10);
        eq(30, emptyOld.ledger.fightStats(empty.id).profit(), "No-entry old fight accepts its first missing reward");
        eq(50, emptyOld.ledger.stats(false).profit(), "First historical insertion cannot capture today's entries");
        eq(80, emptyOld.ledger.stats(true).profit(), "First historical insertion changes lifetime only");
        emptyOld.ledger.validate();
    }

    private static void contextAndIsolation() throws Exception {
        Tracker paused = tracker(); spawn(paused, 1000); paused.tick(BASE + 2000, true);
        FightRecord pausedFight = latest(paused); paused.togglePause();
        same(FightRecord.Outcome.INTERRUPTED, pausedFight.outcome, "Manual pause finalizes an active fight as interrupted");
        eq(1000, pausedFight.activeEnd - pausedFight.activeStart, "Interrupted history retains recorded active time");
        Tracker reset = tracker(); spawn(reset, 1000); reset.resetContext();
        same(FightRecord.Outcome.INTERRUPTED, latest(reset).outcome, "World reset finalizes in-progress history");
        Tracker waiting = tracker(); spawn(waiting, 1000); message(waiting, "ARACHNE DOWN!", 4000); waiting.resetContext();
        same(FightRecord.Outcome.MISSING_DAMAGE, latest(waiting).outcome, "Context loss while waiting finalizes missing damage");
        Tracker session = tracker(); spawn(session, 1000); FightRecord interrupted = latest(session); session.newSession();
        same(FightRecord.Outcome.INTERRUPTED, interrupted.outcome, "New session finalizes old active fight");
        eq(2, session.ledger.sessionId, "New session advances persistent identity");
        yes(session.ledger.recentFights(false).isEmpty(), "Interrupted old fight is excluded from new-session history");
        eq(1, session.ledger.recentFights(true).size(), "Interrupted old fight remains in total history");

        Path directory = Files.createTempDirectory("arachne-history-profiles-");
        Tracker profile = tracker(directory); spawn(profile, 1000); profile.profile("second");
        yes(profile.ledger.fights.isEmpty(), "New profile has independent history");
        profile.profile("default");
        same(FightRecord.Outcome.INTERRUPTED, latest(profile).outcome, "Profile switch saves interrupted history to the old profile");
        profile.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE + 5000);
        spawn(profile, 6000); profile.account("other-owner");
        yes(profile.ledger.fights.isEmpty(), "Other account has independent history");
        profile.account("owner");
        eq(2, profile.ledger.fights.size(), "Original account restores its own history only");
        yes(profile.ledger.fights.stream().allMatch(f -> f.outcome == FightRecord.Outcome.INTERRUPTED),
            "Account switch finalizes and persists original active fight");
    }

    private static void unfinishedAndRecentLimit() throws Exception {
        Path directory = Files.createTempDirectory("arachne-history-crash-");
        Tracker original = tracker(directory); spawn(original, 1000); original.save();
        Tracker reopened = tracker(directory);
        same(FightRecord.Outcome.INTERRUPTED, latest(reopened).outcome, "Reopening cannot leave a persisted active fight permanently live");
        Ledger disk = Store.read(directory.resolve("owner-default.json"), Ledger.class, Ledger::new, Ledger::validate);
        same(FightRecord.Outcome.INTERRUPTED, disk.fights.getFirst().outcome, "Recovered interrupted outcome is saved back to disk");
        spawn(reopened, 3000); message(reopened, "ARACHNE DOWN!", 5000); reopened.save();
        Tracker reopenedAgain = tracker(directory);
        same(FightRecord.Outcome.MISSING_DAMAGE, latest(reopenedAgain).outcome, "Reopening a pending result finalizes missing damage");
        eq(0, reopenedAgain.ledger.stats(true).kills(), "Restoring unfinished history does not invent a kill");

        Ledger ledger = new Ledger();
        for (int i = 0; i < 70; i++) {
            FightRecord fight = ledger.beginFight(BASE + i, 10_000);
            fight.outcome = FightRecord.Outcome.INTERRUPTED;
        }
        eq(50, ledger.recentFights(true).size(), "Total history displays the newest 50 fights");
        eq(70, ledger.recentFights(true).getFirst().id, "History begins with newest fight");
        eq(21, ledger.recentFights(true).getLast().id, "Total history limit discards oldest visible rows");
        ledger.newSession();
        for (int i = 0; i < 3; i++) {
            FightRecord fight = ledger.beginFight(BASE + 100 + i, 10_000);
            fight.outcome = FightRecord.Outcome.INTERRUPTED;
        }
        eq(3, ledger.recentFights(false).size(), "Session history filters before applying its 50-row limit");
        yes(ledger.recentFights(false).stream().allMatch(f -> f.session == ledger.sessionId), "Session history never leaks older fights");
        eq(73, ledger.recentFights(false).getFirst().id, "Current session ordering is newest first");
        eq(50, ledger.recentFights(true).size(), "New session does not remove older total history");
        same(73, new HashSet<>(ledger.fights.stream().map(f -> f.id).toList()).size(), "History identity remains unique across sessions");
        ledger.validate();
    }

    private static void automaticRngOnly() throws Exception {
        Tracker tracker = tracker(); tracker.config.rngTitles = true; tracker.config.prices.put("ARACHNE_FANG", 2000.0);
        spawn(tracker, 1000); down(tracker, 4000, 100000);
        UUID fang = UUID.randomUUID();
        tracker.observeLootStand(fang, "§aArachne's Fang", BASE + 4100);
        tracker.observeLootStand(fang, "§aArachne's Fang", BASE + 4150);
        tracker.pickup("ARACHNE_FANG", 1, BASE + 4200);
        eq(1, tracker.rng.queued(), "Stand scans and matching pickup produce exactly one title");
        same("+2.0k coins", tracker.rng.current(BASE + 4300).valueText(), "Title shows the actual recorded reward value");
        long fightId = latest(tracker).id;
        tracker.rng.clear();
        tracker.editFightLoot(fightId, "ARACHNE_FANG", 2);
        tracker.reprice("ARACHNE_FANG", 3000);
        tracker.record(Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 1_000_000, "manual", BASE + 4300);
        eq(0, tracker.rng.queued(), "Corrections, repricing and manual entries never replay automatic titles");
        tracker.pickup("TARANTULA_EPIC", 1, BASE + 4400);
        tracker.observeLootStand(UUID.randomUUID(), "§5[Lvl 1] Tarantula", BASE + 4450);
        eq(1, tracker.rng.queued(), "Pickup-first rare reward also produces exactly one title");
        eq(1, tracker.ledger.fightStats(fightId).loot().get("TARANTULA_EPIC").longValue(), "Pickup-first pet is recorded once in fight history");
        tracker.rng.clear(); tracker.config.rngTitles = false;
        tracker.observeLootStand(UUID.randomUUID(), "§6[Lvl 1] Tarantula", BASE + 4500);
        eq(0, tracker.rng.queued(), "Disabled titles leave the queue empty");
        eq(1, tracker.ledger.fightStats(fightId).loot().get("TARANTULA_LEGENDARY").longValue(), "Disabled titles preserve automatic rare-drop accounting");
    }

    private HistoryChecks() {}
}

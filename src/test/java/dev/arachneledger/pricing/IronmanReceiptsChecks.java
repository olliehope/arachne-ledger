package dev.arachneledger.pricing;

import dev.arachneledger.config.Store;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.SessionSummary;
import dev.arachneledger.tracking.Tracker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Replays known-zero rewards through receipts, edits, saved history and fight reporting. */
public final class IronmanReceiptsChecks {
    private static int checks;
    private static final String ESSENCE = "ESSENCE_SPIDER";
    private static final long BASE = 1_000_000;

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void reject(Runnable action, String why) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(why);
    }

    public static void main(String[] args) throws Exception {
        derivedWarnings();
        receiptValidationAndPersistence();
        scopedRepricing();
        historicalCorrections();
        automaticAndManualRecording();
        System.out.println("PASS: " + checks + " Ironman receipt, history and reporting checks.");
    }

    private static void derivedWarnings() {
        Ledger ledger = new Ledger();
        Ledger.Entry ignored =
                ledger.add(Ledger.Kind.LOOT, ESSENCE, 8, 0, "armor_stand", 100, true);
        Ledger.Entry missing = ledger.add(Ledger.Kind.LOOT, ESSENCE, 2, 0, "manual", 200);
        ledger.add(Ledger.Kind.LOOT, "SOUL_STRING", 3, 5000, "pickup", 300);
        ledger.add(Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 1000, "server", 400);
        ledger.tick(1000);
        yes(ignored.intentionalZero() && !ignored.unpriced(), "Known zero is a complete valuation");
        yes(missing.unpriced() && !missing.intentionalZero(), "An unset zero remains missing");
        Ledger.Stats stats = ledger.stats(false);
        eq(10, stats.loot().get(ESSENCE), "Known zero rewards still contribute loot quantities");
        eq(2, stats.unpriced(), "Only missing receipt quantities contribute warnings");
        eq(
                2,
                stats.unpricedLoot().get(ESSENCE),
                "Per-item warnings share the aggregate receipt rules");
        eq(15_000, stats.revenue(), "Zero receipts do not invent revenue");
        eq(14_000, stats.profit(), "Actual loot and costs retain their full value");
        eq(
                stats.profit(),
                stats.graph().getLast().profit(),
                "Graph reconciles with mixed valuations");
        reject(
                () -> stats.unpricedLoot().put("ARACK", 1L),
                "Warning maps cannot be mutated by screens");
        ledger.tick(1000);
        eq(
                2,
                ledger.stats(false).unpricedLoot().get(ESSENCE),
                "Live-clock cache retains per-item warnings");
        eq(2, SessionSummary.capture(ledger).unpriced(), "Recaps use the same warning definition");
        Ledger.Stats legacyApi = new Ledger.Stats(0, 0, 0, 0, 0, 0, Map.of(), List.of(), 0);
        yes(legacyApi.unpricedLoot().isEmpty(), "The previous Stats constructor remains available");
        ledger.validate();
    }

    private static void receiptValidationAndPersistence() throws Exception {
        Ledger ledger = new Ledger();
        reject(
                () -> ledger.add(Ledger.Kind.LOOT, ESSENCE, 1, 5, "manual", 100, true),
                "An intentional zero cannot retain positive income");
        reject(
                () -> ledger.add(Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 0, "server", 100, true),
                "A zero-valued cost cannot acquire a loot valuation marker");
        ledger.entries.add(
                new Ledger.Entry(
                        100, 0, Ledger.Kind.LOOT, ESSENCE, 7, 0, "armor_stand", 0, 0, true));
        ledger.entries.add(
                new Ledger.Entry(200, 0, Ledger.Kind.LOOT, "ARACHNE_SHARD", 2, 0, "armor_stand"));
        ledger.validate();
        yes(ledger.entries.getFirst().id() > 0, "Entry-ID migration assigns a stable identity");
        yes(
                ledger.entries.getFirst().intentionalZero(),
                "Entry-ID migration preserves zero intent");
        Path root = Files.createTempDirectory("arachne-ironman-receipts-");
        Path file = root.resolve("ledger.json");
        Store.write(file, ledger);
        Ledger reopened = Store.read(file, Ledger.class, Ledger::new, Ledger::validate);
        eq(
                2,
                reopened.stats(true).unpriced(),
                "Saved known-zero and missing receipts remain distinct");
        yes(
                reopened.entries.getFirst().intentionalZero(),
                "Known-zero marker survives JSON persistence");
        eq(
                ledger.entries.getFirst().id(),
                reopened.entries.getFirst().id(),
                "Reload preserves entry identity");
        Path legacy = root.resolve("legacy.json");
        Files.writeString(
                legacy,
                """
                {"entries":[{"at":100,"elapsed":0,"kind":"LOOT","item":"ESSENCE_SPIDER", "count":8,"unit":0,"source":"armor_stand"}]}
                """);
        Ledger migrated = Store.read(legacy, Ledger.class, Ledger::new, Ledger::validate);
        yes(
                !migrated.entries.getFirst().intentionalZero(),
                "A legacy zero is not silently reclassified");
        eq(
                8,
                migrated.stats(true).unpriced(),
                "Legacy missing-price quantities keep their original meaning");
        Ledger malformed = new Ledger();
        malformed.entries.add(
                new Ledger.Entry(100, 0, Ledger.Kind.LOOT, ESSENCE, 1, 5, "manual", 0, 1, true));
        reject(malformed::validate, "A malformed saved zero marker is rejected");
    }

    private static void scopedRepricing() {
        Ledger ledger = new Ledger();
        Ledger.Entry previous = ledger.add(Ledger.Kind.LOOT, ESSENCE, 8, 100, "armor_stand", 100);
        ledger.add(Ledger.Kind.LOOT, "ARACHNE_SHARD", 1, 0, "armor_stand", 200);
        ledger.newSession();
        SessionSummary.Snapshot frozen = ledger.sessionRecaps.getFirst();
        Ledger.Entry current = ledger.add(Ledger.Kind.LOOT, ESSENCE, 4, 50, "pickup", 300);
        ledger.reprice(ESSENCE, 0, true);
        Ledger.Entry changed = ledger.entries.getLast();
        eq(current.id(), changed.id(), "Explicit repricing preserves receipt identity");
        yes(
                changed.intentionalZero() && !changed.unpriced(),
                "Session repricing can set a known-zero basis");
        eq(
                previous.unit(),
                ledger.entries.getFirst().unit(),
                "Earlier recorded market value stays unchanged");
        eq(
                0,
                ledger.stats(false).unpriced(),
                "Current session no longer has a missing essence value");
        eq(1, ledger.stats(true).unpriced(), "Unrelated old missing values still warn");
        eq(1, frozen.unpriced(), "Captured recap warning counts remain frozen");
        eq(800, frozen.profit().revenue(), "Captured recap financial values remain frozen");
        ledger.reprice(ESSENCE, 250);
        yes(
                !ledger.entries.getLast().intentionalZero(),
                "Returning to a priced basis clears zero intent");
        eq(
                1000,
                ledger.stats(false).revenue(),
                "Repricing applies the requested positive unit value");
        ledger.reprice(ESSENCE, 0);
        eq(
                4,
                ledger.stats(false).unpriced(),
                "The legacy zero-price API continues to mean missing price");
        ledger.validate();
    }

    private static FightRecord finishedFight(Ledger ledger) {
        FightRecord fight = ledger.beginFight(1000, 10_000);
        ledger.tick(1000);
        fight.died = 2000;
        fight.damage = 20_000;
        fight.activeEnd = ledger.activeMillis;
        fight.outcome = FightRecord.Outcome.COUNTED;
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", 2000, fight.id);
        return fight;
    }

    private static void historicalCorrections() {
        Ledger ledger = new Ledger();
        FightRecord fight = finishedFight(ledger);
        ledger.add(Ledger.Kind.LOOT, ESSENCE, 2, 0, "armor_stand", 2100, fight.id, true);
        ledger.add(Ledger.Kind.LOOT, ESSENCE, 3, 0, "pickup", 2200, fight.id, true);
        ledger.setFightLootCount(fight.id, ESSENCE, 7, 999, 2300);
        Ledger.Entry merged = ledger.entries.getLast();
        yes(
                merged.intentionalZero(),
                "Quantity corrections preserve the previous known-zero basis");
        eq(
                0,
                ledger.fightStats(fight.id).unpriced(),
                "Corrected known-zero reward has no fight warning");
        eq(
                7,
                ledger.fightStats(fight.id).loot().get(ESSENCE),
                "Quantity correction retains all requested drops");
        ledger.add(Ledger.Kind.LOOT, ESSENCE, 1, 0, "manual", 2400, fight.id);
        ledger.setFightLootCount(fight.id, ESSENCE, 9, 0, 2500, true);
        yes(
                !ledger.entries.getLast().intentionalZero(),
                "Mixed known and missing zero receipts remain missing");
        eq(
                9,
                ledger.fightStats(fight.id).unpricedLoot().get(ESSENCE),
                "Mixed correction does not hide missing prices");
        ledger.setFightLootCount(fight.id, "ARACK", 2, 0, 2600, true);
        yes(
                ledger.entries.getLast().intentionalZero(),
                "A previously absent item uses the supplied zero basis");
        ledger.newSession();
        ledger.setFightLootCount(fight.id, "ARACK", 3, 5000, 2700);
        eq(
                0,
                ledger.stats(false).loot().getOrDefault("ARACK", 0L),
                "Old-fight corrections stay outside the current session");
        eq(
                3,
                ledger.fightStats(fight.id).loot().get("ARACK"),
                "Old-fight corrections update the historical quantity");
        yes(
                ledger.entries.getLast().intentionalZero(),
                "Historical corrections retain the recorded zero basis");
        eq(
                9,
                ledger.stats(true).unpriced(),
                "Only truly missing merged receipts warn after historical edits");
        ledger.validate();
    }

    private static void automaticAndManualRecording() throws Exception {
        Path root = Files.createTempDirectory("arachne-ironman-tracker-");
        Tracker tracker = new Tracker(root);
        tracker.account("ironman-owner");
        tracker.config.ironman = true;
        tracker.config.salvageArmor = true;
        tracker.config.salvageWeapons = true;
        tracker.config.manualSet(ESSENCE, 900);
        tracker.config.manualSet("SOUL_STRING", 5000);
        tracker.config.crystalConfigured = true;
        tracker.config.crystalCost = 1000;
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE);
        tracker.tick(BASE, true);
        tracker.message("☄ You placed an Arachne Crystal!", "Owner", BASE + 100);
        tracker.message("[BOSS] Arachne: Ahhhh...A Calling...", "Owner", BASE + 200);
        tracker.message("ARACHNE DOWN!", "Owner", BASE + 1000);
        tracker.message("Your Damage: 20,000 (Position #1)", "Owner", BASE + 1010);
        tracker.observeLootStand(new UUID(17, 1), "Spider Essence x8", BASE + 1100);
        tracker.observeLootStand(new UUID(17, 2), "Arachne's Helmet", BASE + 1150);
        tracker.pickup("ARACK", 1, BASE + 1200);
        tracker.pickup("SOUL_STRING", 1, BASE + 1250);
        tracker.record(
                Ledger.Kind.LOOT,
                ESSENCE,
                4,
                tracker.config.lootPrice(ESSENCE),
                "manual",
                BASE + 1300);
        eq(
                0,
                tracker.ledger.stats(false).unpriced(),
                "All automatic and manual unsellable rewards have a known-zero basis");
        eq(
                12,
                tracker.ledger.stats(false).loot().get(ESSENCE),
                "Direct essence rewards retain their recorded quantities");
        eq(
                1,
                tracker.ledger.stats(false).loot().get("ARACHNE_HELMET"),
                "Salvage armor remains an armor receipt");
        eq(
                1,
                tracker.ledger.stats(false).loot().get("ARACK"),
                "Salvage weapons remain weapon receipts");
        eq(
                5000,
                tracker.ledger.stats(false).revenue(),
                "Ironman excludes essence and salvage value while retaining priced income");
        eq(1000, tracker.ledger.stats(false).costs(), "Ironman does not erase actual summon costs");
        tracker.tick(BASE + 5000, true);
        List<Tracker.KillSummary> reports = tracker.drainKillSummaries();
        eq(1, reports.size(), "Qualified fights still produce a summary");
        eq(0, reports.getFirst().unpriced(), "Known-zero rewards do not warn in kill reports");
        eq(
                4000,
                reports.getFirst().profit(),
                "Kill profit includes summon cost and only attributed priced rewards");
        long fightId = tracker.ledger.recentFights(true).getFirst().id;
        tracker.editFightLoot(fightId, "ARACHNE_BOOTS", 1);
        yes(
                tracker.ledger.entries.stream()
                        .filter(e -> e.item().equals("ARACHNE_BOOTS"))
                        .findFirst()
                        .orElseThrow()
                        .intentionalZero(),
                "Missing-item manual corrections use the active Ironman salvage basis");
        tracker.config.ironman = false;
        eq(
                0,
                tracker.ledger.stats(false).unpriced(),
                "Toggling Ironman does not reclassify existing receipts");
        eq(
                5000,
                tracker.ledger.stats(false).revenue(),
                "Toggling Ironman does not revalue recorded history");
        tracker.reprice(ESSENCE, tracker.config.lootPrice(ESSENCE));
        eq(
                15_800,
                tracker.ledger.stats(false).revenue(),
                "Explicit repricing restores the selected essence basis across session receipts");
        eq(
                7200,
                tracker.ledger.fightLootValues(fightId).get(ESSENCE),
                "Repricing keeps automatic reward attribution");
        yes(
                tracker.ledger.entries.stream()
                        .filter(e -> e.item().equals(ESSENCE))
                        .noneMatch(Ledger.Entry::intentionalZero),
                "Positive repricing clears known-zero markers");
        tracker.config.ironman = true;
        tracker.reprice(ESSENCE, tracker.config.lootPrice(ESSENCE));
        eq(
                0,
                tracker.ledger.stats(false).unpriced(),
                "Repricing back to Ironman restores known zero without warnings");
        tracker.saveConfig();
        tracker.save();
        Tracker restored = new Tracker(root);
        restored.account("ironman-owner");
        yes(restored.config.ironman, "Ironman preference survives tracker restart");
        eq(
                0,
                restored.ledger.stats(true).unpriced(),
                "Known-zero loot survives complete tracker persistence");
        eq(
                5000,
                restored.ledger.stats(true).revenue(),
                "Restored totals use stored receipts without repricing");
        restored.ledger.validate();
    }

    private IronmanReceiptsChecks() {}
}

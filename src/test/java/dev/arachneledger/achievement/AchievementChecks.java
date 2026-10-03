package dev.arachneledger.achievement;

import dev.arachneledger.achievement.AchievementDefinition.Metric;
import dev.arachneledger.config.Store;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Progression scenarios use recorded observations rather than live Minecraft state. */
public final class AchievementChecks {
    private static final long BASE = 1_000_000;
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) {
            throw new AssertionError(why);
        }
    }

    private static void eq(long expected, long actual, String why) {
        yes(expected == actual, why + ": expected " + expected + ", got " + actual);
    }

    private static void close(double expected, double actual, String why) {
        yes(
                Double.isFinite(actual) && Math.abs(expected - actual) < 0.000001,
                why + ": expected " + expected + ", got " + actual);
    }

    private static Ledger.Entry add(
            Ledger ledger,
            Ledger.Kind kind,
            String item,
            long count,
            double unit,
            String source,
            long fightId) {
        return ledger.add(kind, item, count, unit, source, BASE + ledger.activeMillis, fightId);
    }

    private static FightRecord fight(
            Ledger ledger,
            long duration,
            long damage,
            FightRecord.Outcome outcome,
            String killSource) {
        long started = BASE + ledger.activeMillis;
        FightRecord fight = ledger.beginFight(started, 10_000);
        ledger.tick(1);
        fight.died = started + duration;
        fight.activeEnd = ledger.activeMillis;
        fight.damage = damage;
        fight.outcome = outcome;
        if (killSource != null) {
            add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0, killSource, fight.id);
        }
        return fight;
    }

    private static AchievementState fresh(Ledger ledger) {
        AchievementState state = new AchievementState();
        var update = Achievements.evaluate(ledger, state, BASE, true);
        yes(update.changed(), "Initial baseline is persisted");
        eq(0, update.unlocks().size(), "Initial baseline stays silent");
        return state;
    }

    private static Achievements.Progress progress(
            Ledger ledger, AchievementState state, String id) {
        return Achievements.snapshot(ledger, state).progress().stream()
                .filter(row -> row.definition().id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static void registryAndEmptyState() {
        Set<String> ids = new HashSet<>();
        eq(23, Achievements.DEFINITIONS.size(), "Registry contains all planned milestones");
        for (var definition : Achievements.DEFINITIONS) {
            yes(ids.add(definition.id()), "Every definition has a stable unique ID");
            yes(definition.target() > 0, "Every milestone has a positive target");
            close(0, definition.fraction(-1), "Unknown progress has no filled bar");
            close(0, definition.fraction(0), "Empty progress has no filled bar");
            close(1, definition.fraction(definition.target()), "Reached target fills the bar");
            yes(definition.reached(definition.target()), "Exact target unlocks each tier");
        }
        Ledger empty = new Ledger();
        AchievementState state = fresh(empty);
        var snapshot = Achievements.snapshot(empty, state);
        eq(0, snapshot.earned(), "Empty ledger has no earned milestones");
        eq(23, snapshot.total(), "Total includes hidden achievements");
        yes(
                !progress(empty, state, "pet_legendary").revealed(),
                "Hidden milestone starts concealed");
        eq(-1, AchievementFacts.from(empty).fastestKill(), "No fabricated kill timing");
        yes(
                !Achievements.evaluate(empty, state, BASE + 1, true).changed(),
                "Repeated evaluation is inert");
        try {
            snapshot.progress().clear();
            throw new AssertionError("Mutable achievement snapshot");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
    }

    private static void trustedFactsAndSpeed() {
        Ledger ledger = new Ledger();
        AchievementState state = fresh(ledger);
        for (String source : List.of("manual", "fight_edit", "command", "unknown")) {
            add(ledger, Ledger.Kind.KILL, "ARACHNE", 100, 0, source, 0);
            add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 100, 1, source, 0);
            add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 100_000, 1, source, 0);
            add(ledger, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 1, source, 0);
            add(ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 100, 1, source, 0);
        }
        var ignored = AchievementFacts.from(ledger);
        eq(0, ignored.countedKills(), "Manual kills never count toward achievements");
        eq(0, ignored.crystalPlacements(), "Manual crystal receipts never prove placements");
        eq(0, ignored.soulString(), "Manual loot cannot manufacture collection progress");
        eq(0, ignored.pets(), "Manual pets cannot manufacture rare milestones");
        eq(0, ignored.fangs(), "Edited fangs cannot manufacture milestones");
        eq(
                0,
                Achievements.evaluate(ledger, state, BASE + 1, true).unlocks().size(),
                "Manual adjustments never notify achievements");

        FightRecord known = fight(ledger, 45_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", known.id);
        fight(ledger, 1_000, 9_999, FightRecord.Outcome.LOW_DAMAGE, "server");
        fight(ledger, 1_000, 0, FightRecord.Outcome.ZERO_DAMAGE, "server");
        fight(ledger, 1_000, 9_999, FightRecord.Outcome.COUNTED, "server");
        fight(ledger, 1_000, 10_000, FightRecord.Outcome.COUNTED, "manual");
        fight(ledger, 1_000, 10_000, FightRecord.Outcome.WAITING_DAMAGE, "server");
        fight(ledger, 1_000, 10_000, FightRecord.Outcome.COUNTED, null);
        FightRecord legacy = fight(ledger, 1_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        legacy.spawnActiveMillis = -1;
        FightRecord unknown = fight(ledger, 1_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        unknown.spawned = 0;
        unknown.spawnActiveMillis = -1;
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 7, 0, "server", 0);
        add(ledger, Ledger.Kind.KILL, "OTHER_BOSS", 100, 0, "server", 0);
        add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 10, 30_000, "server", 0);
        add(ledger, Ledger.Kind.CALLING, "ARACHNE_KEEPER_FRAGMENT", 100, 1, "server", 0);
        add(ledger, Ledger.Kind.CRYSTAL, "WRONG_ITEM", 100, 1, "server", 0);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 300, 1, "armor_stand", known.id);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 400, 1, "pickup", 0);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 300, 1, "server", 0);
        add(ledger, Ledger.Kind.LOOT, "TARANTULA_EPIC", 1, 0, "pet_claim", known.id);
        add(ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 0, "armor_stand", known.id);
        var facts = AchievementFacts.from(ledger);
        eq(
                10,
                facts.countedKills(),
                "Qualifying fights plus legacy kills, without duplicate fight credit");
        eq(10, facts.crystalPlacements(), "Only own automatic crystal placements count");
        eq(1_000, facts.soulString(), "Automatic quantity paths contribute collection progress");
        eq(1, facts.pets(), "Unpriced automatic pet still counts");
        eq(1, facts.fangs(), "Unpriced automatic fang still counts");
        eq(45_000, facts.fastestKill(), "Legacy or unknown spawn never invents a faster time");
        var update = Achievements.evaluate(ledger, state, BASE + 2, true);
        eq(7, update.unlocks().size(), "One live evaluation unlocks earned tiers once");
        yes(state.earnedAt.containsKey("speed_60"), "First speed tier reached");
        yes(state.earnedAt.containsKey("speed_45"), "Exact speed threshold reached");
        yes(!state.earnedAt.containsKey("speed_30"), "Unreached speed tier stays locked");
        yes(
                !progress(ledger, state, "pet_legendary").revealed(),
                "Epic pet does not reveal legendary secret");
        long entries = ledger.entries.size(), revision = ledger.revision();
        double profit = ledger.stats(true).profit();
        Achievements.snapshot(ledger, state);
        eq(
                entries,
                ledger.entries.size(),
                "Reading achievement book never adds accounting entries");
        eq(revision, ledger.revision(), "Reading achievement book never mutates ledger revision");
        close(
                profit,
                ledger.stats(true).profit(),
                "Achievement evaluation preserves financial totals");
        ledger.reprice("SOUL_STRING", 999);
        eq(
                0,
                Achievements.evaluate(ledger, state, BASE + 3, true).unlocks().size(),
                "Repricing cannot replay achievement notifications");
        ledger.newSession();
        eq(
                10,
                AchievementFacts.from(ledger).countedKills(),
                "Achievements use lifetime progress across sessions");
        add(ledger, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 0, "armor_stand", known.id);
        var legendary = Achievements.evaluate(ledger, state, BASE + 4, true);
        eq(1, legendary.unlocks().size(), "Legendary pet unlocks its hidden milestone once");
        yes(
                progress(ledger, state, "pet_legendary").revealed(),
                "Earned secret reveals its actual details");
        ledger.undo();
        yes(
                progress(ledger, state, "pet_legendary").earned(),
                "Earned achievement survives a later correction");
        eq(
                0,
                Achievements.evaluate(ledger, state, BASE + 5, true).unlocks().size(),
                "Undo cannot replay an unlock");
    }

    private static void backfillDeliveryAndPersistence() throws Exception {
        Ledger ledger = new Ledger();
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1000, 0, "server", 0);
        add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 500, 1, "server", 0);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 100_000, 0, "pickup", 0);
        add(ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 100, 0, "armor_stand", 0);
        add(ledger, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 0, "pet_claim", 0);
        AchievementState imported = new AchievementState();
        var update = Achievements.evaluate(ledger, imported, BASE, true);
        eq(0, update.unlocks().size(), "Existing history backfills silently");
        eq(14, imported.earnedAt.size(), "Backfill applies all factual non-speed tiers");
        eq(14, imported.notified.size(), "Backfill acknowledges all imported unlocks");
        yes(
                imported.earnedAt.values().stream().allMatch(value -> value == 0),
                "Backfill does not invent historical unlock dates");
        var root = Files.createTempDirectory("arachne-achievements-");
        var file = root.resolve("achievement-state.json");
        Store.write(file, imported);
        AchievementState loaded =
                Store.read(
                        file,
                        AchievementState.class,
                        AchievementState::new,
                        AchievementState::validate);
        eq(14, loaded.earnedAt.size(), "Unlock IDs survive JSON round trip");
        eq(14, loaded.notified.size(), "Notification acknowledgments survive JSON round trip");
        eq(
                0,
                Achievements.evaluate(ledger, loaded, BASE + 1, true).unlocks().size(),
                "Reload never replays notifications");
        fight(ledger, 30_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        var speed = Achievements.evaluate(ledger, loaded, BASE + 2, false);
        eq(0, speed.unlocks().size(), "Disabled notifications stay silent for new unlocks");
        yes(speed.changed(), "Silent live unlocks still require persistence");
        eq(17, loaded.notified.size(), "Disabled notification unlocks are acknowledged");
        eq(BASE + 2, loaded.earnedAt.get("speed_30"), "Live unlock records its observed date");
        eq(
                0,
                Achievements.evaluate(ledger, loaded, BASE + 3, true).unlocks().size(),
                "Enabling notifications never replays silent live unlocks");
        loaded.catalogVersion = 0;
        loaded.earnedAt.remove("speed_60");
        loaded.notified.remove("speed_60");
        var migration = Achievements.evaluate(ledger, loaded, BASE + 4, true);
        yes(migration.changed(), "Registry revision backfills new definitions");
        eq(0, migration.unlocks().size(), "Registry revision backfill stays silent");
        eq(
                0,
                loaded.earnedAt.get("speed_60"),
                "Registry backfill preserves unknown achievement date");
    }

    private static void malformedAndFutureState() {
        AchievementState state = new AchievementState();
        state.earnedAt = null;
        state.notified = null;
        state.catalogVersion = -1;
        state.validate();
        eq(0, state.catalogVersion, "Missing state defaults migrate safely");
        yes(
                state.earnedAt.isEmpty() && state.notified.isEmpty(),
                "Null state collections migrate safely");
        state.earnedAt = new LinkedHashMap<>();
        state.earnedAt.put("future_achievement", 123L);
        state.earnedAt.put(null, 123L);
        state.earnedAt.put("", 123L);
        state.earnedAt.put("bad_date", -1L);
        state.earnedAt.put("missing_date", null);
        state.notified = new LinkedHashSet<>();
        state.notified.add("future_achievement");
        state.notified.add("orphan");
        state.notified.add(null);
        state.validate();
        eq(
                1,
                state.earnedAt.size(),
                "Malformed state entries are discarded without breaking financial history");
        yes(
                state.earnedAt.containsKey("future_achievement"),
                "Unknown valid IDs remain for forward compatibility");
        eq(1, state.notified.size(), "Only acknowledgments of earned IDs are retained");
        var definition =
                Achievements.DEFINITIONS.stream()
                        .filter(row -> row.metric() == Metric.FASTEST_KILL)
                        .findFirst()
                        .orElseThrow();
        close(
                0.5,
                definition.fraction(definition.target() * 2),
                "Speed progress uses best known duration");
        close(
                1,
                definition.fraction(definition.target() / 2),
                "Faster-than-target speed bar clamps to full");
        yes(!definition.reached(-1), "Unknown speed never qualifies");
        yes(!definition.reached(0), "Zero duration never qualifies");
    }

    private static void dryStreakMilestones() {
        Ledger ledger = new Ledger();
        AchievementState state = fresh(ledger);
        for (int index = 0; index < 100; index++) {
            fight(ledger, 90_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        }
        FightRecord current = ledger.fights.getLast();
        add(ledger, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 0, "manual", current.id);
        add(ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 0, "fight_edit", current.id);
        FightRecord ignored =
                fight(ledger, 90_000, 9_999, FightRecord.Outcome.LOW_DAMAGE, "server");
        add(ledger, Ledger.Kind.LOOT, "TARANTULA_EPIC", 1, 0, "pet_claim", ignored.id);
        add(ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 0, "armor_stand", ignored.id);
        var facts = AchievementFacts.from(ledger);
        eq(100, facts.petDryStreak(), "Manual pets and low-damage rewards do not reset a dry run");
        eq(100, facts.fangDryStreak(), "Edited fangs and skipped fights do not reset a dry run");
        var unlocked = Achievements.evaluate(ledger, state, BASE + 1, true);
        yes(
                unlocked.unlocks().stream()
                        .anyMatch(row -> row.definition().id().equals("pet_dry_100")),
                "The first pet dry milestone unlocks live");
        yes(state.earnedAt.containsKey("fang_dry_50"), "The first fang dry milestone unlocks");
        yes(state.earnedAt.containsKey("fang_dry_100"), "Exact dry threshold unlocks its tier");
        yes(!state.earnedAt.containsKey("fang_dry_250"), "A later dry tier remains locked");

        FightRecord reward = fight(ledger, 90_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        add(ledger, Ledger.Kind.LOOT, "TARANTULA_EPIC", 1, 0, "pet_claim", reward.id);
        add(ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 0, "armor_stand", reward.id);
        eq(
                100,
                AchievementFacts.from(ledger).petDryStreak(),
                "A later pet preserves best historical dry progress");
        eq(
                100,
                AchievementFacts.from(ledger).fangDryStreak(),
                "A later fang preserves best historical dry progress");
        for (int index = 0; index < 250; index++) {
            fight(ledger, 90_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        }
        eq(
                250,
                AchievementFacts.from(ledger).petDryStreak(),
                "Dry streak restarts after either pet rarity");
        eq(
                250,
                AchievementFacts.from(ledger).fangDryStreak(),
                "The longest later run becomes progression");
        Achievements.evaluate(ledger, state, BASE + 2, true);
        yes(state.earnedAt.containsKey("fang_dry_250"), "The highest fang dry tier unlocks");
        yes(!state.earnedAt.containsKey("pet_dry_500"), "Pet dry tiers use their own thresholds");
        eq(
                0,
                Achievements.evaluate(ledger, state, BASE + 3, true).unlocks().size(),
                "Dry achievements notify only once");

        AchievementState oldCatalog = new AchievementState();
        oldCatalog.initialized = true;
        oldCatalog.catalogVersion = 1;
        var migrated = Achievements.evaluate(ledger, oldCatalog, BASE + 4, true);
        eq(0, migrated.unlocks().size(), "New dry milestones backfill without upgrade chat spam");
        yes(
                oldCatalog.earnedAt.containsKey("pet_dry_100"),
                "Catalog migration retains factual dry progress");
        eq(
                0,
                oldCatalog.earnedAt.get("fang_dry_250"),
                "Imported dry progress does not invent an unlock date");
        eq(2, oldCatalog.catalogVersion, "The dry milestone catalog is acknowledged");

        Ledger boundary = new Ledger();
        for (int index = 0; index < 99; index++) {
            fight(boundary, 90_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        }
        FightRecord hundredth =
                fight(boundary, 90_000, 10_000, FightRecord.Outcome.COUNTED, "server");
        add(boundary, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 0, "armor_stand", hundredth.id);
        add(boundary, Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 0, "armor_stand", hundredth.id);
        eq(
                99,
                AchievementFacts.from(boundary).petDryStreak(),
                "The rewarded fight is not counted as a dry kill");
        eq(
                99,
                AchievementFacts.from(boundary).fangDryStreak(),
                "A drop on the target fight does not manufacture a dry milestone");
        AchievementState imported = fresh(boundary);
        yes(
                !imported.earnedAt.containsKey("pet_dry_100"),
                "A reward before 100 dry fights keeps pet milestone locked");
        yes(
                !imported.earnedAt.containsKey("fang_dry_100"),
                "Fang dry thresholds exclude the reward fight");

        Ledger legacy = new Ledger();
        add(legacy, Ledger.Kind.KILL, "ARACHNE", 2_000, 0, "server", 0);
        eq(
                0,
                AchievementFacts.from(legacy).petDryStreak(),
                "Legacy aggregate kills do not invent an ordered pet dry run");
        var compatible = new AchievementFacts(1, 2, 3, 4, 5, 6, 7);
        eq(
                0,
                compatible.petDryStreak(),
                "The previous facts constructor defaults new dry metrics safely");
        eq(0, compatible.fangDryStreak(), "Existing callers have no fabricated fang dry progress");
    }

    public static void main(String[] args) throws Exception {
        registryAndEmptyState();
        trustedFactsAndSpeed();
        backfillDeliveryAndPersistence();
        malformedAndFutureState();
        dryStreakMilestones();
        System.out.println("Achievement checks passed: " + checks);
    }
}

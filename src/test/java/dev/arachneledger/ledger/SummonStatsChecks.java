package dev.arachneledger.ledger;

import com.google.gson.Gson;

import dev.arachneledger.skyblock.PurseCoins;

/** Tier comparisons keep actual costs, qualification, corrections and unknown legacy timing. */
public final class SummonStatsChecks {
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001)
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }

    private static void advance(Ledger ledger, long millis) {
        while (millis > 0) {
            long step = Math.min(5_000, millis);
            ledger.tick(step);
            millis -= step;
        }
    }

    private static FightRecord counted(Ledger ledger, FightRecord.SummonTier tier, long duration) {
        FightRecord fight = ledger.beginFight(duration > 0 ? 1_000_000 : 0, 10_000, tier);
        advance(ledger, duration);
        fight.died = 1_000_000 + duration;
        fight.activeEnd = ledger.activeMillis;
        fight.damage = 10_000;
        fight.outcome = FightRecord.Outcome.COUNTED;
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", fight.died, fight.id);
        return fight;
    }

    private static void add(
            Ledger ledger,
            FightRecord fight,
            Ledger.Kind kind,
            String item,
            long count,
            double unit,
            String source) {
        ledger.add(kind, item, count, unit, source, fight.died, fight.id);
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        FightRecord calling = counted(ledger, FightRecord.SummonTier.CALLING, 10_000);
        add(ledger, calling, Ledger.Kind.LOOT, "SOUL_STRING", 4, 5_000, "armor_stand");
        add(ledger, calling, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 317, "scoreboard");
        add(ledger, calling, Ledger.Kind.CALLING, "ARACHNE_KEEPER_FRAGMENT", 1, 2_000, "server");
        add(ledger, calling, Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 500, "armor_stand");
        add(ledger, calling, Ledger.Kind.LOOT, "SOUL_STRING", 10, 5_000, "manual");
        FightRecord crystal = counted(ledger, FightRecord.SummonTier.CRYSTAL, 20_000);
        add(ledger, crystal, Ledger.Kind.LOOT, "SOUL_STRING", 12, 5_000, "pickup");
        add(ledger, crystal, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 40_000, "server");
        add(ledger, crystal, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 100_000, "pet_claim");
        add(ledger, crystal, Ledger.Kind.LOOT, "SPIDER_ESSENCE", 8, 0, "armor_stand");
        FightRecord unknown = counted(ledger, FightRecord.SummonTier.UNKNOWN, 0);
        add(ledger, unknown, Ledger.Kind.LOOT, "STRING", 10, 3, "pickup");
        FightRecord low = ledger.beginFight(1_000_001, 10_000, FightRecord.SummonTier.CRYSTAL);
        low.damage = 9_999;
        low.outcome = FightRecord.Outcome.LOW_DAMAGE;
        low.died = 1_000_010;
        add(ledger, low, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 40_000, "server");
        add(ledger, low, Ledger.Kind.LOOT, "SOUL_STRING", 1, 5_000, "armor_stand");
        Ledger.Entry unlinked =
                ledger.add(Ledger.Kind.EXPENSE, "MANUAL", 1, 700, "manual", 1_000_010);
        SummonStats.Snapshot selected = ledger.summonStats(false);
        var t1 = selected.row(FightRecord.SummonTier.CALLING);
        var t2 = selected.row(FightRecord.SummonTier.CRYSTAL);
        eq(1, t1.qualifiedKills(), "Calling counts retained qualifying kills");
        eq(20_500, t1.detectedLootRevenue(), "Detected loot excludes manual correction income");
        eq(317, t1.scavengerCoins(), "Scavenger coins remain separate detected revenue");
        eq(2_000, t1.costs(), "Calling retains actual paid costs");
        eq(18_817, t1.profit(), "Calling net reconciles loot, scavenger and cost");
        eq(18_317, t1.ordinaryProfit(), "Ordinary net excludes only rare drop receipts");
        eq(10_000, t1.averageFightMillis(), "Known time is actual spawn to death");
        eq(18_817 * 360, t1.fightHourly(), "Tier hourly is explicitly fight-only");
        eq(1, t2.qualifiedKills(), "Crystal excludes below-threshold fight");
        eq(160_000, t2.detectedLootRevenue(), "Crystal pet and normal rewards both count");
        eq(40_000, t2.costs(), "Crystal only includes qualifying attributed costs");
        eq(120_000, t2.profit(), "Crystal net includes pet value");
        eq(20_000, t2.ordinaryProfit(), "Crystal ordinary net excludes pet value");
        eq(55_000, selected.unassignedRevenue(), "Manual and ineligible income stays unassigned");
        eq(40_700, selected.unassignedCosts(), "Ignored and unlinked costs remain visible");
        yes(
                !selected.row(FightRecord.SummonTier.UNKNOWN).hasTiming(),
                "Unknown legacy spawn cannot create a rate");
        eq(
                0,
                selected.row(FightRecord.SummonTier.UNKNOWN).fightHourly(),
                "Unknown timing returns finite unavailable rate");
        yes(selected == ledger.summonStats(false), "Unchanged comparison reuses snapshot");
        var total = ledger.summonStats(true);
        yes(
                selected == ledger.summonStats(false) && total == ledger.summonStats(true),
                "Alternating scopes preserves both comparison caches");
        ledger.reprice("SOUL_STRING", 4_000);
        eq(
                16_500,
                ledger.summonStats(false).row(FightRecord.SummonTier.CALLING).detectedLootRevenue(),
                "Explicit repricing updates tier revenue");
        yes(selected != ledger.summonStats(false), "Correction invalidates comparison snapshot");
        eq(20_500, t1.detectedLootRevenue(), "Prior comparison remains immutable");
        yes(ledger.undo(), "Unlinked last cost can be removed");
        eq(40_000, ledger.summonStats(false).unassignedCosts(), "Undo removes only retained cost");
        ledger.newSession();
        eq(
                0,
                ledger.summonStats(false).row(FightRecord.SummonTier.CALLING).qualifiedKills(),
                "New session has no inherited tier kills");
        eq(
                1,
                ledger.summonStats(true).row(FightRecord.SummonTier.CALLING).qualifiedKills(),
                "Lifetime comparison keeps earlier qualified fights");
        Ledger saved = new Gson().fromJson(new Gson().toJson(ledger), Ledger.class);
        saved.validate();
        yes(
                saved.fights.getFirst().summonTier == FightRecord.SummonTier.CALLING,
                "Observed tier survives saved history");
        FightRecord legacy = new Gson().fromJson("{\"id\":1,\"session\":1}", FightRecord.class);
        legacy.validate();
        yes(legacy.summonTier == FightRecord.SummonTier.UNKNOWN, "Legacy fight has unknown tier");
        legacy.summonTier = null;
        legacy.validate();
        yes(
                legacy.summonTier == FightRecord.SummonTier.UNKNOWN,
                "Explicit null tier migrates safely");
        cacheAndRollingBoundaries();
        System.out.println("PASS: " + checks + " summon comparison and rolling cache checks.");
    }

    private static void cacheAndRollingBoundaries() {
        Ledger ledger = new Ledger();
        advance(ledger, 500);
        ledger.newSession();
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", 1);
        ledger.add(Ledger.Kind.LOOT, "SOUL_STRING", 1, 5_000, "pickup", 1);
        advance(ledger, 59_999);
        yes(
                !ledger.analytics(false).projectionReady(),
                "Warmup does not complete one millisecond early");
        advance(ledger, 1);
        yes(
                ledger.analytics(false).projectionReady(),
                "Warmup boundary is exact between global seconds");
        var analytics = ledger.analytics(false);
        var profit = ledger.profitBreakdown(false);
        var lifetime = ledger.analytics(true);
        var lifetimeProfit = ledger.profitBreakdown(true);
        yes(
                analytics == ledger.analytics(false) && lifetime == ledger.analytics(true),
                "Alternating analytics scopes does not rebuild financial history");
        yes(
                profit == ledger.profitBreakdown(false)
                        && lifetimeProfit == ledger.profitBreakdown(true),
                "Alternating profit scopes does not rebuild financial history");
        advance(ledger, 100);
        yes(analytics == ledger.analytics(false), "Rates reuse current second's display snapshot");
        eq(
                60_100,
                ledger.stats(false).elapsed(),
                "Accounting clock remains exact within display second");
        advance(ledger, 239_900);
        eq(
                5_000,
                ledger.analytics(false).windowNet(),
                "Session-origin receipt remains at exactly five minutes");
        advance(ledger, 1);
        eq(
                0,
                ledger.analytics(false).windowNet(),
                "Exact left boundary expires despite same display second");
        eq(
                0,
                ledger.profitBreakdown(false).windowNet(),
                "Profit projections share exact window expiry");
        yes(
                !ledger.analytics(false).projectionReady(),
                "Expired only kill disables projection immediately");
    }

    private SummonStatsChecks() {}
}

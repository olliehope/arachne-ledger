package dev.arachneledger.ledger;

import dev.arachneledger.skyblock.PurseCoins;

import java.util.AbstractList;
import java.util.List;

/** Reconciles regular profit with the recorded journal, including scope and rolling boundaries. */
public final class ProfitBreakdownChecks {
    private static int checks;

    private static final class CountedEntries extends AbstractList<Ledger.Entry> {
        private final List<Ledger.Entry> entries;
        private int reads;

        CountedEntries(List<Ledger.Entry> entries) {
            this.entries = List.copyOf(entries);
        }

        @Override
        public Ledger.Entry get(int index) {
            reads++;
            return entries.get(index);
        }

        @Override
        public int size() {
            return entries.size();
        }
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static void advance(Ledger ledger, long millis) {
        while (millis > 0) {
            long step = Math.min(5_000, millis);
            ledger.tick(step);
            millis -= step;
        }
    }

    private static void add(Ledger ledger, Ledger.Kind kind, String item, long count, double unit) {
        ledger.add(kind, item, count, unit, "server", 1_000_000 + ledger.activeMillis);
    }

    private static void reconciles(Ledger ledger, boolean total) {
        var snapshot = ProfitBreakdown.calculate(ledger, total);
        var stats = ledger.stats(total);
        eq(stats.revenue(), snapshot.revenue(), "Revenue reconciles to existing accounting");
        eq(stats.costs(), snapshot.costs(), "Costs reconcile to existing accounting");
        eq(stats.profit(), snapshot.net(), "Net reconciles to existing accounting");
        eq(stats.hourly(), snapshot.hourly(), "Rate uses the same active clock");
        eq(
                snapshot.net(),
                snapshot.ordinaryNet() + snapshot.rngRevenue(),
                "Ordinary plus RNG reconciles");
        eq(
                ledger.analytics(total).projectedHourly(),
                snapshot.projectedHourly(),
                "Projection preserves existing boundary rules");
        snapshot.validate();
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        var empty = ProfitBreakdown.calculate(ledger, false);
        eq(0, empty.ordinaryNet(), "Empty normal profit");
        eq(0, empty.ordinaryHourly(), "Empty rate is finite");
        eq(0, empty.projectedOrdinaryHourly(), "Empty projection is finite");
        yes(!empty.projectionReady(), "Empty projection unavailable");
        for (String item : List.of("TARANTULA_EPIC", "TARANTULA_LEGENDARY", "ARACHNE_FANG")) {
            yes(ProfitBreakdown.isRngItem(item), "Recognizes RNG item " + item);
        }
        for (String item :
                List.of(
                        "SOUL_STRING",
                        "ARACHNE_FRAGMENT",
                        "LUXURIOUS_SPOOL",
                        "ARACHNE_HELMET",
                        "STRING",
                        "TARANTULA",
                        "MANUAL")) {
            yes(!ProfitBreakdown.isRngItem(item), "Does not infer RNG from unrelated item " + item);
        }
        yes(!ProfitBreakdown.isRngItem(null), "Null classification is safe");
        add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 25_000);
        add(ledger, Ledger.Kind.CALLING, "ARACHNE_KEEPER_FRAGMENT", 2, 2_500);
        add(ledger, Ledger.Kind.EXPENSE, "MANUAL", 1, 100);
        advance(ledger, 60_000);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 2, 5_000);
        add(ledger, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 1_000_000);
        add(ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 150_000);
        add(ledger, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 2_317);
        add(ledger, Ledger.Kind.INCOME, "MANUAL", 1, 2_000);
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        var captured = ProfitBreakdown.calculate(ledger, false);
        eq(1_164_317, captured.revenue(), "All receipt income included");
        eq(1_150_000, captured.rngRevenue(), "Only pet and Fang income excluded");
        eq(14_317, captured.ordinaryRevenue(), "Coins and ordinary items retained");
        eq(30_100, captured.costs(), "Both summon kinds and other cost retained");
        eq(-15_783, captured.ordinaryNet(), "Negative baseline remains negative despite jackpot");
        eq(1_134_217, captured.net(), "Jackpot-inclusive total unchanged");
        eq(-946_980, captured.ordinaryHourly(), "Normal rate uses actual active minute");
        eq(
                -946_980,
                captured.projectedOrdinaryHourly(),
                "Origin costs remain in initial projection");
        yes(captured.projectionReady(), "One minute and recent kill enable both projections");
        reconciles(ledger, false);
        ledger.reprice("TARANTULA_LEGENDARY", 2_000_000);
        var repriced = ProfitBreakdown.calculate(ledger, false);
        eq(2_150_000, repriced.rngRevenue(), "Explicit repricing changes RNG receipt value");
        eq(
                captured.ordinaryNet(),
                repriced.ordinaryNet(),
                "RNG repricing never changes normal profit");
        eq(1_150_000, captured.rngRevenue(), "Captured result is immutable after repricing");
        ledger.reprice("SOUL_STRING", 4_000);
        eq(
                -17_783,
                ProfitBreakdown.calculate(ledger, false).ordinaryNet(),
                "Ordinary repricing affects regular value");
        List<Ledger.Entry> before = List.copyOf(ledger.entries);
        ProfitBreakdown.calculate(ledger, true);
        yes(before.equals(ledger.entries), "Calculation never mutates receipts");
        advance(ledger, 300_000);
        var boundary = ProfitBreakdown.calculate(ledger, false);
        eq(0, boundary.windowNet(), "Left boundary excludes old kill and rewards");
        yes(!boundary.projectionReady(), "No recent kill clears projection readiness");
        add(ledger, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 500);
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        eq(
                6_000,
                ProfitBreakdown.calculate(ledger, false).projectedOrdinaryHourly(),
                "Fresh coin rate uses five-minute window");
        reconciles(ledger, false);
        ledger.newSession();
        reconciles(ledger, false);
        reconciles(ledger, true);
        eq(
                0,
                ProfitBreakdown.calculate(ledger, false).rngRevenue(),
                "New session has no inherited jackpot");
        eq(
                2_150_000,
                ProfitBreakdown.calculate(ledger, true).rngRevenue(),
                "Lifetime retains recorded RNG history");
        add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 10_000);
        advance(ledger, 60_000);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 3, 5_000);
        add(ledger, Ledger.Kind.LOOT, "TARANTULA_EPIC", 1, 0);
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        eq(
                5_000,
                ProfitBreakdown.calculate(ledger, false).ordinaryNet(),
                "Unpriced pet contributes no invented coins");
        eq(
                300_000,
                ProfitBreakdown.calculate(ledger, true).projectedOrdinaryHourly(),
                "Lifetime projection uses current-session normal pace");
        eq(
                ProfitBreakdown.calculate(ledger, false).projectedOrdinaryHourly(),
                ProfitBreakdown.calculate(ledger, true).projectedOrdinaryHourly(),
                "Scope does not change current pace");
        reconciles(ledger, false);
        reconciles(ledger, true);
        ledger.undo();
        yes(
                !ProfitBreakdown.calculate(ledger, false).projectionReady(),
                "Deleted recent kill disables projection");
        reconciles(ledger, false);
        ledger.validate();
        reconciles(ledger, true);
        cacheAndRetiming();
        System.out.println(
                "PASS: " + checks + " ordinary-profit, RNG, recorded-price and projection checks.");
    }

    private static void cacheAndRetiming() {
        Ledger cached = new Ledger();
        add(cached, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 10_000);
        advance(cached, 60_000);
        add(cached, Ledger.Kind.LOOT, "SOUL_STRING", 3, 5_000);
        add(cached, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 1_000_000);
        add(cached, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        var cachedMinute = cached.profitBreakdown(false);
        yes(
                cachedMinute == cached.profitBreakdown(false),
                "Same journal and active clock reuse cached snapshot");
        advance(cached, 60_000);
        var retimed = cached.profitBreakdown(false);
        eq(5_000, retimed.ordinaryNet(), "Active clock update retains receipt subtotals");
        eq(150_000, retimed.ordinaryHourly(), "Active clock update retimes normal hourly rate");
        eq(
                150_000,
                retimed.projectedOrdinaryHourly(),
                "Clock-only update retimes normal projection");
        eq(5_000, cachedMinute.ordinaryNet(), "Prior cached snapshot remains immutable");
        cached.reprice("SOUL_STRING", 4_000);
        eq(
                2_000,
                cached.profitBreakdown(false).ordinaryNet(),
                "Revision change rebuilds cached financial totals");
        cached.newSession();
        eq(
                0,
                cached.profitBreakdown(false).net(),
                "New session invalidates cached receipt boundary");
        eq(
                1_002_000,
                cached.profitBreakdown(true).net(),
                "Scope change rebuilds lifetime financial totals");
        eq(
                0,
                cached.profitBreakdown(false).net(),
                "Returning to current scope does not inherit lifetime cache");
        yes(
                !cached.profitBreakdown(true).projectionReady(),
                "Lifetime financial cache does not inherit old session projection");
        add(cached, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 20_000);
        advance(cached, 60_000);
        add(cached, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        eq(
                -1_200_000,
                cached.profitBreakdown(true).projectedOrdinaryHourly(),
                "Lifetime cache uses current-session origin cost for projection");
        advance(cached, 300_000);
        yes(
                !cached.profitBreakdown(true).projectionReady(),
                "Cached retiming expires kill at exclusive rolling boundary");
        eq(
                0,
                cached.profitBreakdown(true).windowNet(),
                "Cached retiming drops expired window receipts");

        Ledger longHistory = new Ledger();
        for (int i = 0; i < 10_000; i++) add(longHistory, Ledger.Kind.LOOT, "STRING", 1, 3);
        advance(longHistory, 360_000);
        add(longHistory, Ledger.Kind.LOOT, "SOUL_STRING", 1, 5_000);
        add(longHistory, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        var financials = ProfitBreakdown.calculate(longHistory, true);
        CountedEntries counted = new CountedEntries(longHistory.entries);
        longHistory.entries = counted;
        advance(longHistory, 1_000);
        var recent = ProfitBreakdown.retime(longHistory, true, financials);
        eq(35_000, recent.revenue(), "Retiming retains all lifetime receipt revenue");
        eq(60_000, recent.projectedOrdinaryHourly(), "Retiming uses only current recent receipts");
        yes(
                counted.reads <= 10,
                "Retiming never scans ten thousand out-of-window lifetime receipts");
        recent.validate();
    }
}

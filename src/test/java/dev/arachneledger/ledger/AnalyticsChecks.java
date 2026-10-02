package dev.arachneledger.ledger;

/** Fixed active-time accounting scenarios; no game, network, or wall clock required. */
public final class AnalyticsChecks {
    private static int checks;

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

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        var empty = ledger.analytics(false);
        yes(!empty.projectionReady(), "Empty history has no projection");
        eq(0, empty.projectedHourly(), "Unavailable projection stays finite");
        eq(0, empty.averageNetPerKill(), "Empty per-kill average stays finite");
        eq(0, empty.killsPerHour(), "Empty kill rate stays finite");
        eq(0, empty.averageMillisPerKill(), "Empty per-kill time stays finite");

        add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 100_000);
        advance(ledger, 59_000);
        add(ledger, Ledger.Kind.LOOT, "SOUL_STRING", 44, 5_000);
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        yes(
                !ledger.analytics(false).projectionReady(),
                "A completed kill before one minute is still warming up");
        advance(ledger, 1_000);
        var minute = ledger.analytics(false);
        yes(minute.projectionReady(), "One minute and one kill enable projection");
        eq(
                7_200_000,
                minute.projectedHourly(),
                "Projection subtracts crystal placed at session origin");
        eq(120_000, minute.windowNet(), "Window net includes observed loot and cost");
        eq(60_000, minute.windowMillis(), "Initial window uses actual elapsed time");
        eq(1, minute.windowKills(), "One sampled kill");
        eq(120_000, minute.averageNetPerKill(), "Net per kill includes crystal cost");
        eq(60, minute.killsPerHour(), "Kill rate uses active time");
        eq(60_000, minute.averageMillisPerKill(), "Time per kill includes waiting");
        eq(100_000, minute.crystalSpend(), "Crystal spending follows recorded price");
        eq(220_000, minute.lootRevenue().get("SOUL_STRING"), "Loot totals use recorded prices");

        advance(ledger, 60_000);
        eq(
                3_600_000,
                ledger.analytics(false).projectedHourly(),
                "Quiet active time reduces projected rate");
        add(ledger, Ledger.Kind.CALLING, "ARACHNE_KEEPER_FRAGMENT", 2, 25_000);
        add(ledger, Ledger.Kind.EXPENSE, "MANUAL", 1, 90_000);
        var loss = ledger.analytics(false);
        eq(-600_000, loss.projectedHourly(), "Losses produce a negative projection");
        eq(50_000, loss.callingSpend(), "Calling cost is quantity times price");
        eq(90_000, loss.otherSpend(), "Other expenses shown separately");
        eq(
                ledger.stats(false).costs(),
                loss.crystalSpend() + loss.callingSpend() + loss.otherSpend(),
                "Spending reconciles to total costs");
        ledger.undo();
        eq(
                2_100_000,
                ledger.analytics(false).projectedHourly(),
                "Undo invalidates projection cache");
        ledger.reprice("SOUL_STRING", 4_000);
        eq(780_000, ledger.analytics(false).projectedHourly(), "Repricing recalculates projection");
        eq(
                176_000,
                ledger.analytics(false).lootRevenue().get("SOUL_STRING"),
                "Repricing recalculates item revenue");

        advance(ledger, 240_000);
        var old = ledger.analytics(false);
        eq(300_000, old.windowMillis(), "Window caps at five active minutes");
        eq(-50_000, old.windowNet(), "Old revenue and crystal cost roll out independently");
        yes(!old.projectionReady(), "A window without recent kills has no current pace");
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        eq(
                -600_000,
                ledger.analytics(false).projectedHourly(),
                "Recent kill uses all costs in rolling window");
        advance(ledger, 60_000);
        eq(
                0,
                ledger.analytics(false).windowNet(),
                "Rolled window excludes events exactly at left endpoint");

        ledger.newSession();
        var session = ledger.analytics(false);
        var total = ledger.analytics(true);
        yes(
                !session.projectionReady() && !total.projectionReady(),
                "New session clears pace in both views");
        eq(0, session.windowNet(), "No old-session amounts leak across a session boundary");
        eq(0, session.crystalSpend(), "Session spending resets");
        eq(100_000, total.crystalSpend(), "Lifetime spending preserved");
        eq(0, session.lootRevenue().size(), "Session loot breakdown resets");
        eq(176_000, total.lootRevenue().get("SOUL_STRING"), "Lifetime loot breakdown preserved");
        add(ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 10_000);
        advance(ledger, 60_000);
        yes(
                !ledger.analytics(true).projectionReady(),
                "Elapsed time without a current-session kill is insufficient");
        add(ledger, Ledger.Kind.INCOME, "MANUAL", 1, 30_000);
        add(ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        var current = ledger.analytics(false);
        var lifetime = ledger.analytics(true);
        eq(1_200_000, current.projectedHourly(), "Session projection uses only new session events");
        eq(
                current.projectedHourly(),
                lifetime.projectedHourly(),
                "Lifetime view uses the same current pace");
        eq(20_000, current.averageNetPerKill(), "Session per-kill average uses session net");
        eq(
                46_000.0 / 3,
                lifetime.averageNetPerKill(),
                "Lifetime per-kill average follows selected scope");
        eq(
                110_000,
                lifetime.crystalSpend(),
                "Lifetime spending combines sessions at recorded prices");
        eq(0, current.lootRevenue().size(), "Manual income is not mislabelled as loot");
        ledger.validate();
        eq(
                1_200_000,
                ledger.analytics(false).projectedHourly(),
                "Validation rebuilds transient caches");
        Ledger otherProfile = new Ledger();
        advance(otherProfile, 60_000);
        yes(
                !otherProfile.analytics(true).projectionReady(),
                "Independent profile cannot inherit projection samples");
        eq(
                0,
                otherProfile.analytics(true).crystalSpend(),
                "Independent profile has its own spending");
        System.out.println(
                "PASS: " + checks + " projection, scope, spending and recorded-value checks.");
    }
}

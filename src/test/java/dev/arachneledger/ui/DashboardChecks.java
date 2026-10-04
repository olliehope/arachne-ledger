package dev.arachneledger.ui;

import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.PurseCoins;

/** Dashboard snapshots keep scope, ignored-fight explanations and clipboard figures consistent. */
public final class DashboardChecks {
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static void eq(double expected, double actual, String why) {
        yes(
                Double.isFinite(actual) && Math.abs(expected - actual) < .00001,
                why + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        DashboardData dashboard = new DashboardData();
        var empty = dashboard.get(ledger, false);
        yes(empty.lastFinished() == null, "No invented fight before tracking");
        yes(dashboard.get(ledger, false) == empty, "Unchanged dashboard reuses its snapshot");
        FightRecord fight = ledger.beginFight(1_000_000, 10_000);
        ledger.add(Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 100, "server", 1_000_000, fight.id);
        ledger.tick(1_000);
        ledger.add(Ledger.Kind.LOOT, "TARANTULA_EPIC", 1, 500, "armor_stand", 1_010_000, fight.id);
        ledger.add(Ledger.Kind.LOOT, "STRING", 20, 5, "pickup", 1_010_001, fight.id);
        ledger.add(Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 20, "scoreboard", 1_010_002, fight.id);
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", 1_010_003, fight.id);
        fight.outcome = FightRecord.Outcome.COUNTED;
        fight.damage = 12_000;
        fight.died = 1_010_000;
        ledger.historyChanged();
        var counted = dashboard.get(ledger, false);
        eq(520, counted.stats().profit(), "Recorded scope net includes loot, coins and cost");
        eq(20, counted.profit().ordinaryNet(), "Ordinary net removes pet value and keeps costs");
        eq(fight.id, counted.lastFinished().id(), "Last fight links its original identity");
        eq(12_000, counted.lastFinished().damage(), "Last-fight damage is retained");
        eq(10_000, counted.lastFinished().duration(), "Confirmed fight duration is retained");
        yes(counted.lastFinished().reason().equals("Counted"), "Qualified fight reports counted");
        ledger.tick(20);
        yes(
                dashboard.get(ledger, false) == counted,
                "Subsecond frames do not repeat receipt joins");
        ledger.tick(980);
        var ticking = dashboard.get(ledger, false);
        yes(
                ticking != counted && ticking.fights() == counted.fights(),
                "A clock second refreshes rates while reusing unchanged fight rows");
        eq(2_000, ticking.stats().elapsed(), "Displayed active clock advances");

        FightRecord skipped = ledger.beginFight(1_030_000, 10_000);
        ledger.add(Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 200, "server", 1_030_000, skipped.id);
        skipped.damage = 1;
        skipped.died = 1_040_000;
        skipped.outcome = FightRecord.Outcome.LOW_DAMAGE;
        ledger.historyChanged();
        var low = dashboard.get(ledger, false);
        eq(
                skipped.id,
                low.lastFinished().id(),
                "An ignored fight still appears as the latest result");
        eq(-200, low.lastFinished().profit(), "Ignored costs remain visible and recorded");
        yes(
                low.lastFinished().reason().contains("below 10,000"),
                "Ignored fight explains its damage threshold");
        eq(320, low.stats().profit(), "Scope net retains costs from ignored fights");
        ledger.undo();
        var undo = dashboard.get(ledger, false);
        eq(0, undo.lastFinished().profit(), "Undo refreshes last-fight financial data");
        eq(520, undo.stats().profit(), "Undo restores scope net");
        String copied = DashboardData.recap(undo, false);
        yes(copied.startsWith("Arachne Ledger · Session"), "Clipboard identifies its scope");
        yes(
                copied.contains("Profit: 520 coins")
                        && copied.contains("Profit without RNG: 20 coins"),
                "Clipboard includes matching full and ordinary profit");
        yes(
                copied.contains("Loot: 600 coins") && copied.contains("Costs: 100 coins"),
                "Clipboard excludes coins from loot and retains summoning costs");
        yes(copied.contains("Active time: 00:00:02"), "Clipboard uses hours, minutes and seconds");
        ledger.newSession();
        yes(dashboard.get(ledger, false).fights().isEmpty(), "Session reset excludes old fights");
        eq(2, dashboard.get(ledger, true).fights().size(), "Lifetime view retains old fights");
        eq(
                520,
                dashboard.get(ledger, true).stats().profit(),
                "Lifetime scope retains historical values");
        Ledger other = new Ledger();
        yes(
                dashboard.get(other, true).fights().isEmpty(),
                "An account change cannot inherit fight rows");
        eq(
                0,
                dashboard.get(other, true).stats().profit(),
                "An account change cannot inherit money");
        System.out.println(
                "PASS: " + checks + " dashboard cache, ignored fight, scope and clipboard checks.");
    }
}

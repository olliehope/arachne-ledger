package dev.arachneledger.ledger;

import com.google.gson.Gson;

import dev.arachneledger.skyblock.PurseCoins;

import java.util.List;

/** Records must require genuine qualifying kills and remain responsive to journal corrections. */
public final class SessionRecordsChecks {
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void advance(Ledger ledger, long millis) {
        while (millis > 0) {
            long step = Math.min(5_000, millis);
            ledger.tick(step);
            millis -= step;
        }
    }

    private static void add(
            Ledger ledger,
            FightRecord fight,
            Ledger.Kind kind,
            String item,
            long count,
            double value,
            String source) {
        ledger.add(kind, item, count, value, source, 1_000_000 + ledger.activeMillis, fight.id);
    }

    private static FightRecord fight(
            Ledger ledger, long duration, long damage, boolean confirmed, String killSource) {
        FightRecord fight =
                ledger.beginFight(confirmed ? 1_000_000 + ledger.activeMillis : 0, 10_000);
        advance(ledger, duration);
        fight.died = 1_000_000 + ledger.activeMillis;
        fight.activeEnd = ledger.activeMillis;
        fight.damage = damage;
        fight.outcome =
                damage >= 10_000 ? FightRecord.Outcome.COUNTED : FightRecord.Outcome.LOW_DAMAGE;
        if (killSource != null) add(ledger, fight, Ledger.Kind.KILL, "ARACHNE", 1, 0, killSource);
        return fight;
    }

    private static void rejected(Runnable action, String why) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        yes(rejected, why);
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        var empty = SessionSummary.capture(ledger, false, 123);
        yes(!empty.hasActivity(), "Empty session has no recap activity");
        eq(123, empty.capturedAt(), "Capture uses supplied timestamp");
        eq(0, empty.qualifiedKills(), "Empty kills");
        eq(-1, empty.fastestKillMillis(), "Missing timed kill remains unknown");
        empty.validate();
        var noRecords = PersonalRecords.calculate(ledger, true);
        yes(
                noRecords.fastestKill() == null
                        && noRecords.bestFight() == null
                        && noRecords.bestSession() == null,
                "Empty history never invents records");

        FightRecord first = fight(ledger, 12_000, 100_000, true, "server");
        add(ledger, first, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 20_000, "server");
        add(ledger, first, Ledger.Kind.LOOT, "SOUL_STRING", 44, 5_000, "armor_stand");
        add(ledger, first, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 2_317, "scoreboard");
        add(ledger, first, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 100_000_000, "manual");
        add(ledger, first, Ledger.Kind.INCOME, "MANUAL", 1, 100_000_000, "manual");
        var initial = PersonalRecords.calculate(ledger, true);
        eq(
                12_000,
                initial.fastestKill().durationMillis(),
                "Confirmed duration creates speed record");
        eq(
                202_317,
                initial.bestFight().net(),
                "Automatic reward and Scav coins minus summon spend");
        eq(202_317, initial.bestSession().net(), "Manual rewards cannot inflate session record");
        eq(1, initial.qualifiedKills(), "One qualifying genuine server kill");

        FightRecord low = fight(ledger, 1_000, 9_999, true, "server");
        add(ledger, low, Ledger.Kind.CALLING, "ARACHNE_KEEPER_FRAGMENT", 1, 50_000, "server");
        add(ledger, low, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 500_000_000, "pet_claim");
        eq(
                first.id,
                PersonalRecords.calculate(ledger, true).fastestKill().fightId(),
                "Low-damage attempt cannot create speed record");
        eq(
                152_317,
                PersonalRecords.calculate(ledger, true).bestSession().net(),
                "Failed attempt's cost subtracts; its rewards excluded from record");

        FightRecord unknown = fight(ledger, 10_000, 10_000, false, "server");
        add(ledger, unknown, Ledger.Kind.LOOT, "SOUL_STRING", 1, 5_000, "pickup");
        FightRecord fakeKill = fight(ledger, 500, 100_000, true, "manual");
        add(ledger, fakeKill, Ledger.Kind.LOOT, "SOUL_STRING", 1, 50_000_000, "pickup");
        FightRecord noKill = fight(ledger, 500, 100_000, true, null);
        add(ledger, noKill, Ledger.Kind.LOOT, "SOUL_STRING", 1, 50_000_000, "armor_stand");
        var summary = SessionSummary.capture(ledger, false, 456);
        summary.validate();
        eq(2, summary.qualifiedKills(), "Unknown spawn still counts genuine qualifying kill");
        eq(1, summary.timedKills(), "Unknown spawn and manual kills excluded from timed average");
        eq(12_000, summary.averageKillMillis(), "Average includes reliable kill durations only");
        eq(12_000, summary.fastestKillMillis(), "Unknown spawn never produces zero-second record");
        eq(20_000, summary.crystalSpend(), "Recap separates Crystal spend");
        eq(50_000, summary.callingSpend(), "Recap includes failed Calling spend");
        eq(2_317, summary.scavengerCoins(), "Recap Scav subtotal is included once");
        eq(
                ledger.stats(false).profit(),
                summary.profit().net(),
                "Displayed recap retains all journal adjustments");
        eq(
                157_317,
                PersonalRecords.calculate(ledger, true).bestSession().net(),
                "Unconfirmed damage/kill cannot inflate tracked session record");
        var capturedIndex = HistoryIndex.build(ledger);
        var frozen = capturedIndex.fights().getFirst();
        boolean immutable = false;
        try {
            frozen.detectedLoot().put("STRING", 1L);
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        yes(immutable, "Index quantities immutable");
        immutable = false;
        try {
            capturedIndex.fights().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        yes(immutable, "Index fight selection immutable");

        add(ledger, first, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 1_000_000, "pet_claim");
        eq(
                1_202_317,
                PersonalRecords.calculate(ledger, true).bestFight().net(),
                "Late reward joins original fight");
        eq(
                202_317,
                PersonalRecords.calculate(ledger, true).bestFight().ordinaryNet(),
                "Record without RNG separates pet value");
        eq(202_317, frozen.net(), "Captured index remains unchanged by late rewards");
        ledger.reprice("SOUL_STRING", 4_000);
        eq(
                1_158_317,
                PersonalRecords.calculate(ledger, true).bestFight().net(),
                "Repricing updates records using recorded receipt values");
        eq(202_317, initial.bestFight().net(), "Previously calculated records are immutable");
        ledger.setFightLootCount(first.id, "SOUL_STRING", 100, 4_000, 1_001_000);
        eq(
                982_317,
                PersonalRecords.calculate(ledger, true).bestFight().net(),
                "Manual replacement cannot create reward record");
        eq(
                2,
                PersonalRecords.calculate(ledger, true).qualifiedKills(),
                "Editing rewards does not alter genuine kill qualification");

        var captured = SessionSummary.capture(ledger, false, 789);
        List<Ledger.Entry> before = List.copyOf(ledger.entries);
        ledger.newSession();
        yes(ledger.entries.equals(before), "New recap never rewrites receipt journal");
        eq(1, ledger.sessionRecaps.size(), "New session retains previous recap");
        eq(
                captured.profit().net(),
                ledger.sessionRecaps.getLast().profit().net(),
                "Saved recap captures exact previous-session financials");
        eq(
                0,
                SessionSummary.capture(ledger, false).qualifiedKills(),
                "Current recap resets qualifying kills");
        yes(
                PersonalRecords.calculate(ledger, false).bestFight() == null,
                "Current-session records do not leak old fights");
        eq(
                first.id,
                PersonalRecords.calculate(ledger, true).bestFight().fightId(),
                "Lifetime records preserved");
        var oldSummary = ledger.sessionRecaps.getLast();
        FightRecord second = fight(ledger, 8_000, 100_000, true, "server");
        add(ledger, second, Ledger.Kind.LOOT, "SOUL_STRING", 2, 5_000, "pickup");
        add(ledger, second, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 20_000, "server");
        var current = PersonalRecords.calculate(ledger, false);
        eq(-10_000, current.bestFight().net(), "Losing fight remains valid record without clamp");
        eq(-10_000, current.bestSession().net(), "Losing session remains valid record");
        eq(
                8_000,
                PersonalRecords.calculate(ledger, true).fastestKill().durationMillis(),
                "New faster timed fight wins lifetime record");
        eq(
                oldSummary.profit().net(),
                ledger.sessionRecaps.getLast().profit().net(),
                "New-session events cannot mutate prior recap");
        second.spawnActiveMillis = -1;
        eq(
                first.id,
                PersonalRecords.calculate(ledger, true).fastestKill().fightId(),
                "Legacy unconfirmed timing cannot create speed record");
        add(ledger, second, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 10_000_000, "manual");
        eq(
                -10_000,
                PersonalRecords.calculate(ledger, false).bestFight().net(),
                "Manual Scav-labelled coins excluded from records");
        ledger.validate();
        Gson gson = new Gson();
        Ledger restored = gson.fromJson(gson.toJson(ledger), Ledger.class);
        restored.validate();
        eq(
                oldSummary.profit().net(),
                restored.sessionRecaps.getLast().profit().net(),
                "Recap survives Gson save and reload");
        eq(
                first.id,
                PersonalRecords.calculate(restored, true).bestFight().fightId(),
                "Records recompute correctly after reload");
        eq(
                3,
                SessionSummary.capture(restored, true).qualifiedKills(),
                "Total summary sees all three legitimate kills");
        restored.sessionRecaps = null;
        restored.validate();
        yes(restored.sessionRecaps.isEmpty(), "Legacy missing recap field migrates empty");
        SessionSummary.Snapshot invalid =
                new SessionSummary.Snapshot(
                        1, false, 1, oldSummary.profit(), 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, true);
        rejected(invalid::validate, "Timed kills cannot exceed qualifying kills");
        var invalidProfit = new ProfitBreakdown.Snapshot(1, 2, 0, 0, false, 0, 0, 0);
        rejected(invalidProfit::validate, "RNG revenue cannot exceed all revenue");
        invalidProfit = new ProfitBreakdown.Snapshot(0, 0, Double.NaN, 0, false, 0, 0, 0);
        rejected(invalidProfit::validate, "Nonfinite saved costs rejected");
        invalidProfit = new ProfitBreakdown.Snapshot(0, 0, 0, 100, false, 101, 0, 0);
        rejected(invalidProfit::validate, "Projection window cannot exceed captured active time");
        var roundedProfit = new ProfitBreakdown.Snapshot(1, 0, 0.3, 100, false, 100, 0, 0);
        var roundedSummary =
                new SessionSummary.Snapshot(
                        1,
                        false,
                        1,
                        roundedProfit,
                        1,
                        1,
                        0,
                        0,
                        0,
                        -1,
                        0.1,
                        0.2,
                        0,
                        1.0000000001,
                        0,
                        true);
        roundedSummary.validate();
        yes(true, "Floating-point subtotal noise remains a valid saved recap");
        var wrongSpend =
                new SessionSummary.Snapshot(
                        1, false, 1, roundedProfit, 1, 1, 0, 0, 0, -1, 0.1, 0.2, 50, 0, 0, true);
        rejected(wrongSpend::validate, "Spend subtotals must reconcile to captured costs");
        var wrongScavenger =
                new SessionSummary.Snapshot(
                        1, false, 1, roundedProfit, 1, 1, 0, 0, 0, -1, 0.1, 0.2, 0, 50, 0, true);
        rejected(wrongScavenger::validate, "Scavenger subtotal cannot exceed all income");
        Ledger futureRecap = gson.fromJson(gson.toJson(ledger), Ledger.class);
        var enormousProfit =
                new ProfitBreakdown.Snapshot(1, 0, 0, ledger.activeMillis + 1, false, 0, 0, 0);
        futureRecap.sessionRecaps =
                new java.util.ArrayList<>(
                        List.of(
                                new SessionSummary.Snapshot(
                                        1,
                                        false,
                                        1,
                                        enormousProfit,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        -1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        true)));
        rejected(
                futureRecap::validate,
                "Saved recap active time cannot exceed whole ledger history");
        System.out.println(
                "PASS: "
                        + checks
                        + " recap, reliable-time, trusted-reward, correction and record checks.");
    }
}

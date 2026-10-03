package dev.arachneledger.ledger;

import com.google.gson.Gson;

import dev.arachneledger.ledger.RngSince.Reward;

/** RNG history must follow genuine fights, retained receipts and the selected session boundary. */
public final class RngSinceChecks {
    private static int checks;
    private static final long WALL_ORIGIN = 1_000_000;

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

    private static FightRecord fight(Ledger ledger, long damage, String killSource) {
        FightRecord fight = ledger.beginFight(WALL_ORIGIN + ledger.activeMillis, 10_000);
        advance(ledger, 10_000);
        fight.died = WALL_ORIGIN + ledger.activeMillis;
        fight.activeEnd = ledger.activeMillis;
        fight.damage = damage;
        fight.outcome =
                damage >= 10_000 ? FightRecord.Outcome.COUNTED : FightRecord.Outcome.LOW_DAMAGE;
        if (killSource != null) {
            ledger.add(
                    Ledger.Kind.KILL,
                    "ARACHNE",
                    1,
                    0,
                    killSource,
                    WALL_ORIGIN + ledger.activeMillis,
                    fight.id);
        }
        return fight;
    }

    private static FightRecord fight(Ledger ledger) {
        return fight(ledger, 100_000, "server");
    }

    private static Ledger.Entry drop(
            Ledger ledger, FightRecord fight, String item, long count, String source) {
        return ledger.add(
                Ledger.Kind.LOOT,
                item,
                count,
                0,
                source,
                WALL_ORIGIN + ledger.activeMillis,
                fight.id);
    }

    public static void main(String[] args) {
        emptyAndTrustedHistory();
        scopesLateRewardsAndCorrections();
        boundaryWithoutFutureQualification();
        notificationIntervals();
        System.out.println("PASS: " + checks + " RNG history and scope checks.");
    }

    private static void emptyAndTrustedHistory() {
        Ledger ledger = new Ledger();
        var empty = RngSince.calculate(ledger, false);
        eq(4, empty.rows().size(), "Every reward group exists before any tracked kill");
        eq(0, empty.qualifiedKills(), "Empty history has no qualifying kills");
        eq(0, empty.row(Reward.ANY_PET).dropsPer100Kills(), "No kills never divide by zero");
        yes(!empty.row(Reward.ANY_PET).hasDrop(), "Empty history does not invent a last drop");
        advance(ledger, 2_000);
        eq(2_000, RngSince.calculate(ledger, false).elapsed(), "Empty scope still has active time");

        fight(ledger);
        fight(ledger);
        FightRecord epicFight = fight(ledger);
        advance(ledger, 500);
        Ledger.Entry epic = drop(ledger, epicFight, "TARANTULA_EPIC", 2, "pet_claim");
        fight(ledger);
        FightRecord legendaryFight = fight(ledger);
        advance(ledger, 500);
        Ledger.Entry legendary =
                drop(ledger, legendaryFight, "TARANTULA_LEGENDARY", 1, "armor_stand");
        FightRecord lastDry = fight(ledger);
        advance(ledger, 2_000);
        drop(ledger, lastDry, "TARANTULA_LEGENDARY", 100, "manual");
        drop(ledger, lastDry, "ARACHNE_FANG", 100, "fight_edit");

        var snapshot = RngSince.calculate(ledger, true);
        var pets = snapshot.row(Reward.ANY_PET);
        eq(6, snapshot.qualifiedKills(), "One qualifying kill per genuine fight");
        eq(3, pets.drops(), "Combined pets retain detected quantities rather than event count");
        eq(50, pets.dropsPer100Kills(), "Observed quantities are normalized by qualifying kills");
        eq(1, pets.killsSinceDrop(), "Reward fight itself is excluded from kills since drop");
        eq(
                12_500,
                pets.activeMillisSinceDrop(),
                "Active clock advances after the original reward fight");
        eq(2, pets.longestDryStreak(), "Longest pre-drop dry run is retained");
        eq(legendaryFight.id, pets.lastDrop().fightId(), "Latest pet fight determines last drop");
        eq(1, pets.lastDrop().count(), "Last drop quantity is per latest reward fight");
        eq(
                legendaryFight.activeEnd,
                pets.lastDrop().activeMillis(),
                "Last drop active clock is anchored to original fight ending");
        eq(
                legendary.at(),
                pets.lastDrop().at(),
                "Saved receipt wall time determines last drop date");
        eq(3, snapshot.row(Reward.EPIC_PET).killsSinceDrop(), "Rarities have separate dry runs");
        eq(3, snapshot.row(Reward.EPIC_PET).longestDryStreak(), "Current dry run can set longest");
        eq(4, snapshot.row(Reward.LEGENDARY_PET).longestDryStreak(), "Legendary prefix is tracked");
        eq(6, snapshot.row(Reward.FANG).killsSinceDrop(), "Manual Fangs do not reset dry kills");
        yes(!snapshot.row(Reward.FANG).hasDrop(), "Manual drops never create a detected last drop");
        eq(
                snapshot.elapsed(),
                snapshot.row(Reward.FANG).activeMillisSinceDrop(),
                "No drop uses scope time");
        eq(
                pets.longestDryStreak(),
                RngSince.longestDryStreak(HistoryIndex.build(ledger), Reward.ANY_PET),
                "Achievement helper reuses trusted index with the same dry-run rule");

        var atEpic = RngSince.calculateAt(ledger, true, epic.id());
        eq(3, atEpic.qualifiedKills(), "Receipt boundary excludes later kills");
        eq(2, atEpic.row(Reward.ANY_PET).drops(), "Receipt boundary excludes later pets");
        eq(0, atEpic.row(Reward.ANY_PET).killsSinceDrop(), "Inclusive boundary includes its drop");
        eq(epic.elapsed(), atEpic.elapsed(), "Boundary uses receipt active clock");
        eq(
                500,
                atEpic.row(Reward.ANY_PET).activeMillisSinceDrop(),
                "Post-fight pickup delay remains tracked since the original drop fight");
        var beforeLegendary = RngSince.calculateBefore(ledger, true, legendary.id());
        eq(
                5,
                beforeLegendary.qualifiedKills(),
                "Before reward still includes its earlier kill receipt");
        eq(2, beforeLegendary.row(Reward.ANY_PET).drops(), "Before reward omits incoming quantity");
        eq(
                2,
                beforeLegendary.row(Reward.ANY_PET).killsSinceDrop(),
                "Before reward retains preceding pet interval");
        eq(
                legendary.elapsed() - epicFight.activeEnd,
                beforeLegendary.row(Reward.ANY_PET).activeMillisSinceDrop(),
                "Before reward measures interval at incoming receipt time");

        FightRecord low = fight(ledger, 9_999, "server");
        drop(ledger, low, "ARACHNE_FANG", 5, "pickup");
        drop(ledger, low, "TARANTULA_LEGENDARY", 10, "pet_claim");
        FightRecord fakeKill = fight(ledger, 100_000, "manual");
        drop(ledger, fakeKill, "ARACHNE_FANG", 5, "armor_stand");
        FightRecord noKill = fight(ledger, 100_000, null);
        drop(ledger, noKill, "ARACHNE_FANG", 5, "server");
        ledger.add(Ledger.Kind.LOOT, "TARANTULA_EPIC", 100, 0, "pet_claim", 2_000_000);
        var trusted = RngSince.calculate(ledger, true);
        eq(
                6,
                trusted.qualifiedKills(),
                "Damage threshold and retained server kill are both required");
        eq(
                3,
                trusted.row(Reward.ANY_PET).drops(),
                "Unqualified and unassociated pets are excluded");
        eq(0, trusted.row(Reward.FANG).drops(), "Unqualified rewards cannot reset Fang history");
        eq(
                6,
                trusted.row(Reward.FANG).longestDryStreak(),
                "Low-damage attempts do not add dry kills");
        eq(
                3,
                snapshot.row(Reward.ANY_PET).drops(),
                "Previously captured snapshot remains immutable");
        var clockOnly = RngSince.advanceClock(snapshot, ledger.activeMillis, 0);
        eq(ledger.activeMillis, clockOnly.elapsed(), "Clock-only refresh uses live active time");
        eq(3, clockOnly.row(Reward.ANY_PET).drops(), "Clock-only refresh retains quantity");
        eq(
                1,
                clockOnly.row(Reward.ANY_PET).killsSinceDrop(),
                "Clock-only refresh retains dry kills");
        eq(
                2,
                clockOnly.row(Reward.ANY_PET).longestDryStreak(),
                "Clock-only refresh retains longest streak");
        eq(
                ledger.activeMillis - legendaryFight.activeEnd,
                clockOnly.row(Reward.ANY_PET).activeMillisSinceDrop(),
                "Clock-only refresh advances from saved drop clock");
        eq(
                clockOnly.elapsed(),
                clockOnly.row(Reward.FANG).activeMillisSinceDrop(),
                "No-drop clock-only refresh uses scope elapsed");
        yes(
                clockOnly.row(Reward.ANY_PET).lastDrop().equals(pets.lastDrop()),
                "Clock-only refresh preserves last drop identity");
        boolean immutable = false;
        try {
            snapshot.rows().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        yes(immutable, "Snapshot row selection is immutable");
        boolean rejected = false;
        try {
            RngSince.calculateAt(ledger, true, Long.MAX_VALUE);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        yes(rejected, "Deleted or missing receipt boundaries are rejected");
    }

    private static void scopesLateRewardsAndCorrections() {
        Ledger ledger = new Ledger();
        FightRecord first = fight(ledger);
        drop(ledger, first, "TARANTULA_EPIC", 1, "pickup");
        FightRecord oldDry = fight(ledger);
        FightRecord recentDrop = fight(ledger);
        drop(ledger, recentDrop, "TARANTULA_LEGENDARY", 1, "pet_claim");
        fight(ledger);
        ledger.newSession();
        advance(ledger, 4_000);
        var currentEmpty = RngSince.calculate(ledger, false);
        eq(0, currentEmpty.qualifiedKills(), "Session reset starts a fresh kill scope");
        yes(!currentEmpty.row(Reward.ANY_PET).hasDrop(), "Session cannot inherit previous pet");
        eq(
                4_000,
                currentEmpty.row(Reward.ANY_PET).activeMillisSinceDrop(),
                "Fresh session has its own time origin");
        eq(
                1,
                RngSince.calculate(ledger, true).row(Reward.ANY_PET).killsSinceDrop(),
                "Lifetime last drop remains available");

        FightRecord current = fight(ledger);
        Ledger.Entry lateOld = drop(ledger, oldDry, "TARANTULA_EPIC", 1, "pet_claim");
        var lifetimeLate = RngSince.calculate(ledger, true);
        eq(3, lifetimeLate.row(Reward.ANY_PET).drops(), "Late receipt joins its original fight");
        eq(
                recentDrop.id,
                lifetimeLate.row(Reward.ANY_PET).lastDrop().fightId(),
                "Late older drop does not replace newer drop fight");
        eq(
                2,
                lifetimeLate.row(Reward.ANY_PET).killsSinceDrop(),
                "Late older drop cannot erase newer dry kills");
        eq(
                34_000,
                lifetimeLate.row(Reward.EPIC_PET).activeMillisSinceDrop(),
                "Late older pickup cannot erase active time from subsequent dry fights");
        eq(
                3,
                lifetimeLate.row(Reward.EPIC_PET).killsSinceDrop(),
                "Late older pickup retains corresponding dry kills");
        eq(
                oldDry.activeEnd,
                lifetimeLate.row(Reward.EPIC_PET).lastDrop().activeMillis(),
                "Late pickup is anchored to original fight end");
        eq(
                lateOld.at(),
                lifetimeLate.row(Reward.EPIC_PET).lastDrop().at(),
                "Late pickup still displays its actual receipt date");
        var currentLate = RngSince.calculate(ledger, false);
        eq(1, currentLate.qualifiedKills(), "Current session has one genuine kill");
        eq(
                0,
                currentLate.row(Reward.ANY_PET).drops(),
                "Late previous-session reward stays outside session scope");
        yes(
                !currentLate.row(Reward.ANY_PET).hasDrop(),
                "Late old reward cannot invent session last drop");
        eq(
                1,
                currentLate.row(Reward.ANY_PET).killsSinceDrop(),
                "Late old reward leaves current dry run intact");
        yes(ledger.undo(), "Late receipt can be undone");
        eq(
                2,
                RngSince.calculate(ledger, true).row(Reward.ANY_PET).drops(),
                "Undo removes late detected quantity");
        eq(
                0,
                RngSince.calculate(ledger, false).row(Reward.ANY_PET).drops(),
                "Undo preserves current scope");

        Ledger.Entry currentDrop = drop(ledger, current, "TARANTULA_EPIC", 1, "armor_stand");
        advance(ledger, 1_000);
        var sessionDrop = RngSince.calculate(ledger, false).row(Reward.ANY_PET);
        eq(1, sessionDrop.drops(), "Current detected pet resets only current scope");
        eq(0, sessionDrop.killsSinceDrop(), "Reward kill is not a dry kill");
        eq(
                1_000,
                sessionDrop.activeMillisSinceDrop(),
                "Current reward uses its recorded active time");
        eq(0, sessionDrop.longestDryStreak(), "A one-kill session with a drop has no dry kills");
        eq(
                100,
                sessionDrop.dropsPer100Kills(),
                "Observed one drop in one kill is represented without prediction");
        ledger.reprice("TARANTULA_EPIC", 100_000);
        eq(
                1,
                RngSince.calculate(ledger, false).row(Reward.ANY_PET).drops(),
                "Repricing preserves detected history");
        eq(
                currentDrop.id(),
                ledger.entries.getLast().id(),
                "Repricing preserves receipt boundary identity");
        ledger.setFightLootCount(
                current.id, "TARANTULA_EPIC", 5, 100_000, WALL_ORIGIN + ledger.activeMillis);
        var edited = RngSince.calculate(ledger, false).row(Reward.ANY_PET);
        eq(0, edited.drops(), "Manual replacement cannot fabricate observed RNG quantity");
        eq(1, edited.killsSinceDrop(), "Manual replacement restores dry kill");
        yes(!edited.hasDrop(), "Manual replacement removes last detected session drop");
        ledger.validate();
        Gson gson = new Gson();
        Ledger restored = gson.fromJson(gson.toJson(ledger), Ledger.class);
        restored.validate();
        eq(
                0,
                RngSince.calculate(restored, false).row(Reward.ANY_PET).drops(),
                "Save and reload preserves correction semantics");
        eq(
                2,
                RngSince.calculate(restored, true).row(Reward.ANY_PET).drops(),
                "Lifetime detected quantities survive reload");
        restored.entries.removeIf(
                entry -> entry.fightId() == recentDrop.id && entry.kind() == Ledger.Kind.KILL);
        eq(
                1,
                RngSince.calculate(restored, true).row(Reward.ANY_PET).drops(),
                "Removing server kill removes its RNG eligibility");
        eq(
                3,
                RngSince.calculate(restored, true).row(Reward.ANY_PET).killsSinceDrop(),
                "Dry history recomputes after removed qualifying kill");
        boolean rejected = false;
        try {
            RngSince.calculateAt(ledger, false, lateOld.id());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        yes(rejected, "Undone receipt boundary cannot silently calculate a different interval");
    }

    private static void boundaryWithoutFutureQualification() {
        Ledger ledger = new Ledger();
        FightRecord fight = ledger.beginFight(WALL_ORIGIN, 10_000);
        advance(ledger, 10_000);
        fight.died = WALL_ORIGIN + ledger.activeMillis;
        fight.activeEnd = ledger.activeMillis;
        fight.damage = 100_000;
        fight.outcome = FightRecord.Outcome.COUNTED;
        Ledger.Entry reward = drop(ledger, fight, "ARACHNE_FANG", 1, "armor_stand");
        ledger.add(
                Ledger.Kind.KILL,
                "ARACHNE",
                1,
                0,
                "server",
                WALL_ORIGIN + ledger.activeMillis,
                fight.id);
        eq(
                1,
                RngSince.calculate(ledger, true).row(Reward.FANG).drops(),
                "Reward becomes eligible once server kill is retained");
        var atReward = RngSince.calculateAt(ledger, true, reward.id());
        eq(
                0,
                atReward.qualifiedKills(),
                "A future kill receipt cannot qualify an earlier boundary");
        eq(
                0,
                atReward.row(Reward.FANG).drops(),
                "Future kill cannot expose reward before qualification");
    }

    private static void notificationIntervals() {
        Ledger ledger = new Ledger();
        FightRecord first = fight(ledger);
        Ledger.Entry firstPet = drop(ledger, first, "TARANTULA_LEGENDARY", 1, "pet_claim");
        var firstInterval = RngSince.intervalForDrop(ledger, true, firstPet.id());
        eq(1, firstInterval.kills(), "First drop includes its qualifying fight");
        eq(10_000, firstInterval.activeMillis(), "First drop time ends at its original fight");
        yes(!firstInterval.hasPrevious(), "First drop does not invent an earlier pet");
        fight(ledger);
        FightRecord third = fight(ledger);
        FightRecord fourth = fight(ledger);
        drop(ledger, fourth, "TARANTULA_LEGENDARY", 1, "armor_stand");
        fight(ledger);
        Ledger.Entry delayed = drop(ledger, third, "TARANTULA_LEGENDARY", 1, "pickup");
        var interval = RngSince.intervalForDrop(ledger, true, delayed.id());
        eq(2, interval.kills(), "Late reward excludes later fights and later pet resets");
        eq(20_000, interval.activeMillis(), "Late reward uses original fight endings");
        yes(interval.hasPrevious(), "Late reward finds an earlier original-fight pet");
        Ledger.Entry sameFight = drop(ledger, third, "TARANTULA_LEGENDARY", 1, "armor_stand");
        eq(
                0,
                RngSince.intervalForDrop(ledger, true, sameFight.id()).kills(),
                "A second genuine receipt in the same fight does not repeat dry kills");
        Ledger.Entry epic = drop(ledger, third, "TARANTULA_EPIC", 1, "pet_claim");
        eq(
                3,
                RngSince.intervalForDrop(ledger, true, epic.id()).kills(),
                "Notification intervals distinguish pet rarities");
        Ledger.Entry manual = drop(ledger, third, "ARACHNE_FANG", 1, "manual");
        yes(
                RngSince.intervalForDrop(ledger, true, manual.id()) == null,
                "Manual loot cannot establish a rare notification interval");
        FightRecord low = fight(ledger, 9_999, null);
        Ledger.Entry lowDrop = drop(ledger, low, "ARACHNE_FANG", 1, "armor_stand");
        yes(
                RngSince.intervalForDrop(ledger, true, lowDrop.id()) == null,
                "Low participation keeps a real reward's interval unknown");
        ledger.newSession();
        yes(
                RngSince.intervalForDrop(ledger, false, delayed.id()) == null,
                "Current-session chat cannot borrow an older session's interval");
        FightRecord current = fight(ledger);
        Ledger.Entry currentDrop = drop(ledger, current, "ARACHNE_FANG", 1, "armor_stand");
        eq(
                1,
                RngSince.intervalForDrop(ledger, false, currentDrop.id()).kills(),
                "New session starts its own counted interval");
        eq(
                10_000,
                RngSince.intervalForDrop(ledger, false, currentDrop.id()).activeMillis(),
                "New session active interval starts at its saved origin");
        ledger.undo();
        yes(
                RngSince.intervalForDrop(ledger, true, currentDrop.id()) == null,
                "An undone reward cannot produce stale notification data");

        Ledger pending = new Ledger();
        FightRecord pendingFight = fight(pending, 100_000, null);
        Ledger.Entry early = drop(pending, pendingFight, "ARACHNE_FANG", 1, "armor_stand");
        yes(
                RngSince.intervalForDrop(pending, true, early.id()) == null,
                "Qualification requires a retained server kill");
        pending.add(
                Ledger.Kind.KILL,
                "ARACHNE",
                1,
                0,
                "server",
                WALL_ORIGIN + pending.activeMillis,
                pendingFight.id);
        eq(
                1,
                RngSince.intervalForDrop(pending, true, early.id()).kills(),
                "Damage confirmed after loot still produces a one-kill interval");
        pending.undo();
        yes(
                RngSince.intervalForDrop(pending, true, early.id()) == null,
                "Removing the qualifying kill removes the notification interval");
    }

    private RngSinceChecks() {}
}

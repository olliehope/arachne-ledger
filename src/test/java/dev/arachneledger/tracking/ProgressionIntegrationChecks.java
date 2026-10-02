package dev.arachneledger.tracking;

import dev.arachneledger.achievement.AchievementFacts;
import dev.arachneledger.achievement.Achievements;
import dev.arachneledger.config.Store;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.SessionSummary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Actual tracking, persistence and account transitions exercise progression delivery boundaries.
 */
public final class ProgressionIntegrationChecks {
    private static final long BASE = 1_000_000;
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static void eq(long expected, long actual, String why) {
        yes(expected == actual, why + ": expected " + expected + ", got " + actual);
    }

    private static void close(double expected, double actual, String why) {
        yes(
                Double.isFinite(actual) && Math.abs(expected - actual) < 0.00001,
                why + ": expected " + expected + ", got " + actual);
    }

    private static Path data() throws Exception {
        return Files.createTempDirectory("arachne-progression-");
    }

    private static Tracker load(Path path, String account) {
        Tracker tracker = new Tracker(path);
        tracker.account(account);
        yes(tracker.ready(), "Tracker loads a healthy account ledger");
        enter(tracker, BASE);
        return tracker;
    }

    private static void enter(Tracker tracker, long now) {
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", now);
        tracker.tick(now, true);
    }

    private static void crystal(Tracker tracker, long now) {
        tracker.message(
                "☄ Player placed an Arachne Crystal! Something is awakening!", "Player", now);
        tracker.tick(now, true);
    }

    private static void tenCrystals(Tracker tracker, long start) {
        for (int index = 1; index <= 10; index++) crystal(tracker, start + index * 3_000);
    }

    private static boolean contains(List<Achievements.Unlock> notices, String id) {
        return notices.stream().anyMatch(unlock -> unlock.definition().id().equals(id));
    }

    private static void silentBackfillAndLiveDelivery() throws Exception {
        Path path = data();
        Ledger old = new Ledger();
        old.add(Ledger.Kind.KILL, "ARACHNE", 100, 0, "server", BASE);
        old.add(Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 9, 10_000, "server", BASE);
        old.add(Ledger.Kind.LOOT, "SOUL_STRING", 1_000, 5_000, "armor_stand", BASE);
        old.add(Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 0, "pet_claim", BASE);
        old.achievements = null;
        old.sessionRecaps = null;
        Store.write(path.resolve("Player-default.json"), old);
        Tracker tracker = load(path, "Player");
        eq(
                5,
                tracker.ledger.achievements.earnedAt.size(),
                "Old history backfills factual lifetime tiers");
        eq(
                5,
                tracker.ledger.achievements.notified.size(),
                "Backfilled achievements are acknowledged");
        eq(
                0,
                tracker.drainAchievements().size(),
                "Loading existing history produces no achievement spam");
        eq(
                0,
                tracker.ledger.sessionRecaps.size(),
                "Pre-recap history initializes an empty recap list");
        yes(
                !tracker.ledger.achievements.earnedAt.containsKey("speed_60"),
                "Legacy kill totals never fabricate speed achievements");
        crystal(tracker, BASE + 3_000);
        List<Achievements.Unlock> notices = tracker.drainAchievements();
        eq(
                1,
                notices.size(),
                "First live placement beyond imported progress unlocks one milestone");
        yes(contains(notices, "crystals_10"), "Own Crystal placement unlocks Summoner I");
        eq(0, tracker.drainAchievements().size(), "Achievement notice drains once");
        tracker.save();
        tracker.reprice("SOUL_STRING", 5_500);
        eq(0, tracker.drainAchievements().size(), "Repricing never replays notices");
        tracker.resetContext();
        enter(tracker, BASE + 10_000);
        eq(
                0,
                tracker.drainAchievements().size(),
                "World reset preserves earned state without replay");
        Tracker restored = load(path, "Player");
        eq(
                6,
                restored.ledger.achievements.earnedAt.size(),
                "Earned progress survives account reload");
        eq(
                0,
                restored.drainAchievements().size(),
                "Reloaded acknowledgments prevent duplicate notices");
        yes(
                restored.ledger.achievements.notified.contains("crystals_10"),
                "Live delivery acknowledgment was saved with its account ledger");
    }

    private static void disabledNotificationsAndScopeIsolation() throws Exception {
        Path path = data();
        Tracker tracker = load(path, "A");
        tracker.config.achievementNotifications = false;
        tracker.saveConfig();
        tenCrystals(tracker, BASE);
        yes(
                tracker.ledger.achievements.earnedAt.containsKey("crystals_10"),
                "Disabling notifications still records unlocks");
        yes(
                tracker.ledger.achievements.notified.contains("crystals_10"),
                "Disabled notifications still persist delivery acknowledgment");
        eq(0, tracker.drainAchievements().size(), "Disabled notifications remain silent");
        tracker.config.achievementNotifications = true;
        tracker.saveConfig();
        tracker.tick(BASE + 31_000, true);
        eq(
                0,
                tracker.drainAchievements().size(),
                "Enabling notices does not replay silent unlocks");
        var originalState = tracker.ledger.achievements;
        tracker.newSession();
        eq(
                1,
                tracker.ledger.achievements.earnedAt.size(),
                "New session retains lifetime achievement progress");
        yes(
                tracker.ledger.achievements == originalState,
                "Session change keeps the same account progression state");
        eq(0, tracker.drainAchievements().size(), "New session does not replay achievements");
        tracker.drainSessionRecaps();
        tracker.profile("second");
        yes(
                tracker.ledger.achievements != originalState,
                "Separate profiles own independent progression objects");
        eq(
                0,
                tracker.ledger.achievements.earnedAt.size(),
                "New profile starts with no earned achievements");
        eq(0, tracker.drainAchievements().size(), "Profile transition clears old notices");
        eq(0, tracker.drainSessionRecaps().size(), "Profile transition clears old recap notices");
        enter(tracker, BASE + 40_000);
        tenCrystals(tracker, BASE + 40_000);
        yes(
                tracker.ledger.achievements.earnedAt.containsKey("crystals_10"),
                "Separate profile can independently earn the same achievement");
        tracker.profile("default");
        eq(
                0,
                tracker.drainAchievements().size(),
                "Undrained profile notices cannot leak into another profile");
        eq(
                1,
                tracker.ledger.achievements.earnedAt.size(),
                "Returning profile loads original unlocks");
        eq(2, tracker.ledger.sessionId, "Returning profile retains its own session boundary");
        tracker.account("B");
        eq(0, tracker.ledger.achievements.earnedAt.size(), "New account owns fresh progression");
        eq(
                0,
                tracker.ledger.sessionRecaps.size(),
                "New account cannot inherit another account's recaps");
        enter(tracker, BASE + 80_000);
        tenCrystals(tracker, BASE + 80_000);
        tracker.account("A");
        eq(
                0,
                tracker.drainAchievements().size(),
                "Undrained account notices cannot leak across accounts");
        eq(
                1,
                tracker.ledger.achievements.earnedAt.size(),
                "Original account progression survives switching away");
        eq(1, tracker.ledger.sessionRecaps.size(), "Original account retains its captured recap");
        Tracker restored = load(path, "B");
        eq(
                1,
                restored.ledger.achievements.earnedAt.size(),
                "Second account progress persists independently");
        eq(0, restored.drainAchievements().size(), "Second account reload remains silent");
    }

    private static void capturedRecapSurvivesReload() throws Exception {
        Path path = data();
        Tracker tracker = load(path, "Player");
        tracker.config.crystalConfigured = true;
        tracker.config.crystalCost = 100_000;
        tracker.config.sessionRecapChat = true;
        tracker.saveConfig();
        tracker.message(
                "☄ YOU placed an Arachne Crystal! Something is awakening!", "Player", BASE + 1_000);
        tracker.message("[BOSS] Arachne: With your sacrifice.", "Player", BASE + 1_100);
        tracker.tick(BASE + 2_100, true);
        tracker.observePurse(List.of("Purse: 1,000"), false, BASE + 2_100);
        tracker.message("ARACHNE DOWN!", "Player", BASE + 3_100);
        tracker.message("Your Damage: 100,000 (Position #1)", "Player", BASE + 3_110);
        tracker.observeLootStand(UUID.randomUUID(), "Soul String x44", BASE + 3_120);
        tracker.observePurse(List.of("Purse: 3,317 (+2,317)"), false, BASE + 3_130);
        tracker.tick(BASE + 3_140, true);
        eq(
                1,
                tracker.ledger.stats(false).kills(),
                "Automatic damage summary records a qualified kill");
        eq(
                3,
                tracker.drainAchievements().size(),
                "Confirmed short kill unlocks its speed tiers once");
        tracker.newSession();
        List<SessionSummary.Snapshot> notices = tracker.drainSessionRecaps();
        eq(1, notices.size(), "Finishing an active session queues one recap");
        eq(0, tracker.drainSessionRecaps().size(), "Session recap notice drains once");
        SessionSummary.Snapshot recap = notices.getFirst();
        eq(1, recap.sessionId(), "Recap keeps the completed session identity");
        eq(1, recap.crystals(), "Recap retains own Crystal placement count");
        eq(1, recap.qualifiedKills(), "Recap counts qualifying automatic fights");
        eq(1, recap.timedKills(), "Recap distinguishes reliably timed kills");
        eq(2_000, recap.fastestKillMillis(), "Recap uses confirmed spawn and death duration");
        close(2_000, recap.averageKillMillis(), "Recap average uses confirmed duration");
        close(100_000, recap.crystalSpend(), "Recap preserves recorded summon spending");
        close(2_317, recap.scavengerCoins(), "Recap preserves detected Scavenger coins");
        close(122_317, recap.profit().net(), "Recap preserves captured reward value minus costs");
        eq(0, tracker.ledger.stats(false).kills(), "New session starts with fresh selected totals");
        eq(
                3,
                tracker.ledger.achievements.earnedAt.size(),
                "New session retains earned speed achievements");
        tracker.record(Ledger.Kind.INCOME, "MANUAL", 1, 500, "manual", BASE + 5_000);
        tracker.save();
        close(
                122_317,
                recap.profit().net(),
                "Later accounting changes cannot mutate captured recap");
        Tracker restored = load(path, "Player");
        eq(1, restored.ledger.sessionRecaps.size(), "Saved recap survives JSON ledger reload");
        SessionSummary.Snapshot saved = restored.ledger.sessionRecaps.getFirst();
        eq(1, saved.sessionId(), "Reloaded recap retains old session identity");
        close(122_317, saved.profit().net(), "Reloaded recap retains captured profit");
        close(2_317, saved.scavengerCoins(), "Reloaded recap retains coin income");
        eq(
                0,
                restored.drainSessionRecaps().size(),
                "Opening an existing ledger never replays old recaps");
        eq(
                0,
                restored.drainAchievements().size(),
                "Opening an existing ledger never replays speed milestones");
        restored.config.sessionRecapChat = false;
        restored.newSession();
        eq(
                2,
                restored.ledger.sessionRecaps.size(),
                "Disabling recap chat still captures financial history");
        eq(0, restored.drainSessionRecaps().size(), "Disabled recap notifications stay silent");
    }

    private static void lowDamageAndBlockedDelivery() throws Exception {
        Tracker tracker = load(data(), "Player");
        long[] damages = {0, 9_999};
        for (int index = 0; index < damages.length; index++) {
            long at = BASE + index * 10_000;
            tracker.message("[BOSS] Arachne: With your sacrifice.", "Player", at + 1_000);
            tracker.message("ARACHNE DOWN!", "Player", at + 2_000);
            tracker.message(
                    "Your Damage: " + damages[index] + " (Position #1)", "Player", at + 2_010);
            tracker.tick(at + 2_020, true);
        }
        eq(
                0,
                tracker.ledger.stats(true).kills(),
                "Zero and sub-threshold damage cannot record kills");
        eq(
                0,
                AchievementFacts.from(tracker.ledger).countedKills(),
                "Skipped fights cannot advance counted-kill milestones");
        eq(
                -1,
                AchievementFacts.from(tracker.ledger).fastestKill(),
                "Skipped short fights cannot advance speed milestones");
        eq(0, tracker.drainAchievements().size(), "Skipped damage cannot produce unlock notices");
        yes(
                tracker.ledger.fights.getFirst().outcome == FightRecord.Outcome.ZERO_DAMAGE,
                "Zero damage outcome remains explicit in saved history");
        yes(
                tracker.ledger.fights.getLast().outcome == FightRecord.Outcome.LOW_DAMAGE,
                "Low damage outcome remains explicit in saved history");
        tracker.record(Ledger.Kind.KILL, "ARACHNE", 1_000, 0, "manual", BASE + 30_000);
        tracker.record(Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 0, "manual", BASE + 30_000);
        tracker.save();
        eq(
                0,
                tracker.drainAchievements().size(),
                "Manual kill and pet adjustments cannot trigger achievements");

        Path path = data();
        Tracker blocked = load(path, "Player");
        for (int index = 1; index <= 9; index++) crystal(blocked, BASE + index * 3_000);
        Path ledgerPath = path.resolve("Player-default.json");
        Files.delete(ledgerPath);
        Files.createDirectory(ledgerPath);
        Path sentinel = ledgerPath.resolve("keep.txt");
        Files.writeString(sentinel, "original fixture");
        crystal(blocked, BASE + 30_000);
        yes(!blocked.ready(), "Failed achievement persistence blocks tracking");
        yes(!blocked.error.isEmpty(), "Failed achievement persistence reports an error");
        eq(0, blocked.drainAchievements().size(), "Unsaved unlock notices never reach the user");
        eq(
                0,
                blocked.drainSessionRecaps().size(),
                "Storage-blocked tracker never delivers recap notices");
        yes(Files.exists(sentinel), "Failed save preserves the obstructing original path");
    }

    public static void main(String[] args) throws Exception {
        silentBackfillAndLiveDelivery();
        disabledNotificationsAndScopeIsolation();
        capturedRecapSurvivesReload();
        lowDamageAndBlockedDelivery();
        System.out.println("Progression integration checks passed: " + checks);
    }
}

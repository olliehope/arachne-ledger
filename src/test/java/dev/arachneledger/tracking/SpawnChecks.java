package dev.arachneledger.tracking;

import dev.arachneledger.skyblock.Messages;

import java.nio.file.Files;

/** Actual server spawn dialogue and final summon cues, without trusting ordinary chat. */
public final class SpawnChecks {
    private static int checks;
    private static final long BASE = 1_000_000;
    private static final String FINAL_CALLING =
            "☄ OtherPlayer placed an Arachne's Calling! Something is awakening! (4/4)";

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) {
            throw new AssertionError(why);
        }
    }

    private static void event(Messages.Event expected, String text, String why) {
        checks++;
        Messages.Event actual = Messages.parse(text, "Player");
        if (expected != actual) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void eq(long expected, long actual, String why) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void message(Tracker tracker, String text, long elapsed) {
        tracker.message(text, "Player", BASE + elapsed);
    }

    private static void tick(Tracker tracker, long elapsed) {
        tracker.tick(BASE + elapsed, true);
    }

    private static Tracker tracker() throws Exception {
        Tracker tracker = new Tracker(Files.createTempDirectory("arachne-spawns-"));
        tracker.account("account");
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE);
        tick(tracker, 0);
        return tracker;
    }

    private static Tracker afkTracker() throws Exception {
        Tracker tracker = tracker();
        message(tracker, "[BOSS] Arachne: Ahhhh...A Calling...", 1000);
        tick(tracker, 6000);
        message(tracker, "ARACHNE DOWN!", 6000);
        message(tracker, "Your Damage: 100,000 (Position #1)", 6010);
        for (long time = 11000; time <= 71000; time += 5000) {
            tick(tracker, time);
        }
        yes(tracker.isAfk(), "Completed fight reaches AFK after its 60 second grace");
        eq(65000, tracker.ledger.activeMillis, "AFK boundary excludes the extra five second gap");
        tracker.drainKillSummaries();
        return tracker;
    }

    public static void main(String[] args) throws Exception {
        event(
                Messages.Event.SPAWN,
                "§c[BOSS] Arachne§r§f: A befitting welcome!",
                "Calling welcome starts a fight");
        event(
                Messages.Event.SPAWN,
                "§c[BOSS] Arachne§r§f: With your sacrifice.",
                "Crystal welcome starts a fight");
        event(
                Messages.Event.SPAWN,
                " [BOSS] Arachne: The Era of Spiders begins now. ",
                "Existing Crystal spawn stays supported");
        event(
                Messages.Event.SPAWN,
                "[BOSS] Arachne: Ahhhh...A Calling...",
                "Existing Calling spawn stays supported");
        event(
                Messages.Event.SPAWN,
                "[BOSS] Arachne: Ahhhh... A Calling...",
                "Calling ellipsis allows a following space");
        event(
                Messages.Event.SPAWN,
                "[BOSS] Arachne: Ahhhh ... A Calling ...",
                "Calling ellipsis allows surrounding spaces");
        event(
                Messages.Event.SPAWN,
                "[BOSS] Arachne: Ahhhh… A Calling…",
                "Unicode ellipsis is normalized");
        event(
                Messages.Event.SPAWN,
                "\u200B[BOSS] Arachne:\u00a0With your sacrifice.\u200B",
                "Invisible formatting and nonbreaking spaces are removed");
        event(
                Messages.Event.ACTIVITY,
                "[BOSS] Arachne: Spiders in my Den, can you count to ten?",
                "Other boss dialogue is not a spawn");
        event(
                Messages.Event.NONE,
                "[BOSS] Arachne: No, this is impossible...",
                "Death dialogue is not activity");
        event(
                Messages.Event.NONE,
                "[BOSS] Arachne: I will be back, even stronger!",
                "Final death dialogue is not activity");
        for (String phrase :
                new String[] {
                    "A befitting welcome!", "With your sacrifice.", "Ahhhh...A Calling..."
                }) {
            event(
                    Messages.Event.NONE,
                    "Player: [BOSS] Arachne: " + phrase,
                    "Public chat cannot spoof a spawn");
            event(
                    Messages.Event.NONE,
                    "Party > Player: [BOSS] Arachne: " + phrase,
                    "Party chat cannot spoof a spawn");
            event(
                    Messages.Event.NONE,
                    "Guild > [MVP+] Player: [BOSS] Arachne: " + phrase,
                    "Guild chat cannot spoof a spawn");
        }
        event(
                Messages.Event.ACTIVITY,
                "[BOSS] Arachne: With your sacrifice. Something else",
                "Extra dialogue does not match a known spawn");
        event(
                Messages.Event.NONE,
                "[BOSS] Arachne Keeper: A befitting welcome!",
                "Keeper text is excluded");
        yes(
                Messages.isSummoning("☄ Player placed an Arachne Crystal! Something is awakening!"),
                "Crystal awakening starts summoning");
        yes(
                Messages.isSummoning(
                        "§5☄ §b[MVP++] OtherPlayer §eplaced an Arachne's Calling! §r§eSomething is awakening! (4/4)"),
                "Another player's final Calling starts summoning");
        yes(
                Messages.isSummoning(
                        "☄ YOU placed an Arachne’s Calling! Something is awakening! (4/4)"),
                "YOU and smart apostrophes are supported");
        event(
                Messages.Event.CRYSTAL,
                "☄ YOU placed an Arachne Crystal! Something is awakening!",
                "YOU is charged for its Crystal");
        event(
                Messages.Event.CALLING,
                "☄ You placed an Arachne's Calling! Something is awakening! (4/4)",
                "You is charged for its Calling");
        event(
                Messages.Event.NONE,
                "☄ OtherPlayer placed an Arachne Crystal! Something is awakening!",
                "Another player's Crystal is not charged");
        for (int count = 1; count <= 3; count++) {
            yes(
                    !Messages.isSummoning(
                            "☄ Player placed an Arachne's Calling! (" + count + "/4)"),
                    "Partial Calling does not start summoning");
            yes(
                    !Messages.isSummoning(
                            "☄ Player placed an Arachne's Calling! Something is awakening! ("
                                    + count
                                    + "/4)"),
                    "Malformed partial awakening does not start summoning");
        }
        yes(
                !Messages.isSummoning("☄ Player placed an Arachne's Calling! (4/4)"),
                "Final Calling without awakening is not a summon cue");
        yes(
                !Messages.isSummoning(
                        "☄ Player placed an Arachne's Calling! Something is awakening!"),
                "Calling awakening requires the final count");
        yes(
                !Messages.isSummoning("☄ Player placed an Arachne Crystal!"),
                "A plain Crystal placement does not prove awakening");
        yes(
                !Messages.isSummoning(
                        "Party > Player: ☄ YOU placed an Arachne Crystal! Something is awakening!"),
                "Quoted summon cue is excluded");
        yes(
                !Messages.isSummoning("[BOSS] Arachne: Something is awakening!"),
                "Generic dialogue does not restart summoning");
        yes(
                Messages.isArachneCue(
                        "☄ OtherPlayer placed an Arachne's Calling! Something is awakening! (4/4)"),
                "Final summon remains a location cue");

        Tracker calling = afkTracker();
        message(calling, "[BOSS] Arachne: A befitting welcome!", 72000);
        yes(
                !calling.isAfk() && calling.timerState().equals("Fighting"),
                "Actual Calling welcome resumes a tracker that was AFK");
        tick(calling, 77000);
        eq(70000, calling.ledger.activeMillis, "Calling welcome starts active time at its receipt");
        message(calling, "[BOSS] Arachne: A befitting welcome!", 78000);
        tick(calling, 82000);
        message(calling, "ARACHNE DOWN!", 83000);
        message(calling, "Your Damage: 100,000 (Position #1)", 83010);
        tick(calling, 87000);
        var summaries = calling.drainKillSummaries();
        eq(1, summaries.size(), "Second fight produces exactly one summary");
        eq(
                11000,
                summaries.getFirst().fightMillis(),
                "Repeated actual welcome does not reset kill duration");
        eq(
                2,
                calling.ledger.stats(false).kills(),
                "Actual Calling welcome creates the next qualified kill");

        Tracker crystal = afkTracker();
        message(crystal, "[BOSS] Arachne: With your sacrifice.", 72000);
        tick(crystal, 77000);
        yes(
                !crystal.isAfk() && crystal.timerState().equals("Fighting"),
                "Actual Crystal welcome resumes a tracker that was AFK");
        eq(70000, crystal.ledger.activeMillis, "Crystal welcome starts active timing");

        Tracker summoning = afkTracker();
        message(summoning, "☄ OtherPlayer placed an Arachne's Calling! (3/4)", 72000);
        yes(summoning.isAfk() && !summoning.isSummoning(), "Partial Calling leaves AFK intact");
        message(summoning, FINAL_CALLING, 73000);
        yes(
                summoning.isSummoning() && !summoning.isAfk(),
                "Completed summon replaces AFK with awakening");
        yes(summoning.timerState().equals("Summoning"), "HUD timer identifies summoning");
        tick(summoning, 77000);
        eq(65000, summoning.ledger.activeMillis, "Awakening does not start the fight clock");
        eq(
                0,
                (long) summoning.ledger.stats(false).costs(),
                "Other player's final Calling is not charged");
        message(summoning, "[BOSS] Arachne: A befitting welcome!", 78000);
        tick(summoning, 83000);
        yes(
                !summoning.isSummoning() && !summoning.isAfk(),
                "Actual spawn ends the awakening state");
        eq(70000, summoning.ledger.activeMillis, "Actual spawn starts timing after awakening");

        Tracker recovery = afkTracker();
        message(recovery, FINAL_CALLING, 72000);
        message(recovery, "[BOSS] Arachne: A tough fight!", 73000);
        tick(recovery, 78000);
        yes(
                recovery.timerState().equals("Fighting"),
                "Boss activity recovers a missed welcome after a completed summon");
        eq(70000, recovery.ledger.activeMillis, "Recovered fight starts active timing at activity");
        message(recovery, "ARACHNE DOWN!", 79000);
        message(recovery, "Your Damage: 100,000 (Position #1)", 79010);
        tick(recovery, 83000);
        eq(
                -1,
                recovery.drainKillSummaries().getFirst().fightMillis(),
                "Recovered fight duration remains unknown");

        Tracker late = afkTracker();
        message(late, "[BOSS] Arachne: Spiders in my Den, can you count to ten?", 72000);
        tick(late, 77000);
        yes(late.isAfk(), "Late generic dialogue without a summon does not end AFK");
        eq(65000, late.ledger.activeMillis, "Late generic dialogue cannot add active time");
        message(late, "Party > Player: [BOSS] Arachne: A befitting welcome!", 78000);
        yes(late.isAfk(), "Quoted actual welcome cannot recover AFK");

        Tracker timeout = afkTracker();
        message(timeout, "☄ OtherPlayer placed an Arachne Crystal! Something is awakening!", 72000);
        for (long time = 77000; time <= 127000; time += 5000) {
            tick(timeout, time);
        }
        tick(timeout, 131999);
        yes(
                timeout.isSummoning() && !timeout.isAfk(),
                "Awakening remains visible for less than one minute");
        tick(timeout, 132000);
        yes(
                !timeout.isSummoning() && timeout.isAfk(),
                "An unconfirmed summon expires after one minute");
        eq(65000, timeout.ledger.activeMillis, "Unconfirmed awakening never adds farming time");
        message(timeout, "[BOSS] Arachne: A tough fight!", 132100);
        yes(timeout.isAfk(), "Late activity cannot revive an expired summon");

        Tracker waiting = tracker();
        message(waiting, FINAL_CALLING, 1000);
        yes(
                waiting.isSummoning() && !waiting.waitingForSpawn(),
                "First summon shows awakening before the first fight");
        tick(waiting, 6000);
        eq(0, waiting.ledger.activeMillis, "First awakening has no active time");
        message(waiting, "[BOSS] Arachne: A befitting welcome!", 7000);
        tick(waiting, 12000);
        eq(5000, waiting.ledger.activeMillis, "First actual spawn begins active time");
        System.out.println(
                "PASS: "
                        + checks
                        + " spawn dialogue, summon ownership and spoof rejection checks.");
    }

    private SpawnChecks() {}
}

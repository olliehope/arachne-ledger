package dev.arachneledger;

import java.nio.file.Files;
import java.nio.file.Path;

/** Server-format and lifecycle integration scenarios without a running Minecraft client. */
public final class IntegrationChecks {
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) {
            throw new AssertionError(why);
        }
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        String calling = "\u2604 [User head]User placed an Arachne's Calling! (1/4)";
        String rankedCalling =
                "\u00a74\u2604 \u00a7r[MVP+] [User head]User placed an Arachne\u2019s Calling! Something is awakening! (4/4)";
        String activity = "[BOSS] Arachne: Spiders in my Den, can you count to ten?";
        yes(
                Messages.parse(calling, "User") == Messages.Event.CALLING,
                "2026 player head calling format");
        yes(
                Messages.parse(rankedCalling, "User") == Messages.Event.CALLING,
                "Rank, player head, color and curly apostrophe normalize");
        yes(
                Messages.parse(calling, "Other") == Messages.Event.NONE,
                "Another summoner's costs excluded");
        yes(
                Messages.parse(activity, "User") == Messages.Event.ACTIVITY,
                "Generic boss activity opens loot context");
        yes(
                !Messages.isArachneCue("[MVP+] User: " + activity),
                "Ordinary player chat cannot activate fallback");
        yes(
                !Messages.isArachneCue("Party > User: " + calling),
                "Party chat cannot activate fallback");
        yes(
                Messages.parse("[BOSS] Arachne: No, this is impossible...", "User")
                        == Messages.Event.NONE,
                "Death dialogue cannot reopen the longer fight loot window");

        Path root =
                Files.createTempDirectory(
                        Path.of(System.getProperty("test.root", "build")), "arachne-integration-");
        Path oldSettings = root.resolve("settings.json");
        Files.writeString(
                oldSettings,
                """
            {"total":true,"hud":true,"paused":false,"view":"GRAPH","corner":3,
             "crystalCost":100000,"callingCost":700,"crystalConfigured":true,
             "profile":"default","prices":{"SOUL_STRING":5000,"ARACK":40000}}
            """);
        Tracker tracker = new Tracker(root);
        tracker.account("integration-account");
        yes(tracker.ready(), "Version 1.0 settings load successfully");
        eq(100_000, tracker.config.effectiveCrystalCost(), "Old crystal price preserved");
        eq(40_000, tracker.config.price("ARACK"), "Old custom loot price preserved");
        yes(
                tracker.config.view == Config.View.GRAPH && tracker.config.total,
                "Old dashboard view and scope preserved");
        eq(1, tracker.config.hudScale, "Missing overlay scale receives valid default");

        long now = 1_000_000;
        tracker.updateLocation(true, true, false, "Spider's Den", "Island detected", now);
        yes(!tracker.inArena, "Island alone does not start sanctuary accounting");
        tracker.message("[MVP+] User: " + activity, "User", now + 1);
        yes(!tracker.inArena, "Player chat does not start accounting");
        tracker.message(calling, "User", now + 2);
        yes(tracker.inArena, "Calling cue starts fallback within the same message");
        eq(
                1,
                tracker.ledger.stats(false).callings(),
                "First fallback cue records own calling cost");
        eq(700, tracker.ledger.stats(false).costs(), "Migrated calling price used");
        tracker.message(calling, "User", now + 3);
        eq(1, tracker.ledger.stats(false).callings(), "Relayed summon message is deduplicated");
        tracker.message(activity, "User", now + 4);
        tracker.pickup("SOUL_STRING", 44, now + 5);
        eq(
                220_000,
                tracker.ledger.stats(false).revenue(),
                "Fallback boss activity permits pickup valuation");
        tracker.tick(now + 10, tracker.refreshArea(now + 10));
        tracker.tick(now + 1010, tracker.refreshArea(now + 1010));
        eq(
                1006,
                tracker.ledger.activeMillis,
                "Fallback activity starts timing before the next client tick");
        tracker.message("    \u00a76\u00a7lARACHNE DOWN!", "User", now + 2000);
        tracker.message(
                "\u00a7eYour Damage: \u00a7a1,155,000 \u00a77(Position #1)", "User", now + 2001);
        eq(1, tracker.ledger.stats(false).kills(), "Fallback processes kill and damage pair");
        yes(
                !tracker.refreshArea(now + 93_000),
                "Boss evidence expires without fresh location or activity");
        tracker.pickup("SOUL_STRING", 44, now + 93_001);
        eq(
                220_000,
                tracker.ledger.stats(false).revenue(),
                "Expired evidence cannot keep valuing pickups");

        tracker.updateLocation(true, true, false, "Hub", "Hub detected", now + 100_000);
        tracker.message(activity, "User", now + 100_001);
        yes(!tracker.inArena, "Known other location rejects Arachne fallback");
        tracker.updateLocation(true, true, false, "", "Location unavailable", now + 110_000);
        tracker.message(activity, "User", now + 110_001);
        yes(tracker.inArena, "Hidden area can recover from a boss cue in verified SkyBlock");
        tracker.resetContext();
        tracker.message(activity, "User", now + 110_002);
        yes(
                !tracker.inArena && !tracker.inSkyblock,
                "Disconnect clears fallback and SkyBlock state");

        tracker.config.manualTracking = true;
        tracker.updateLocation(
                false, true, true, "Arachne's Sanctuary", "Spoofed world", now + 120_000);
        yes(!tracker.inArena, "Manual mode cannot activate outside Hypixel");
        tracker.updateLocation(true, false, false, "", "Lobby", now + 120_001);
        yes(!tracker.inArena, "Manual mode cannot activate in a non-SkyBlock lobby");
        tracker.updateLocation(true, true, false, "Spider's Den", "Island", now + 120_002);
        yes(tracker.inArena, "Manual mode activates in verified SkyBlock");
        tracker.config.manualTracking = false;
        tracker.profile("alternate");
        eq(0, tracker.ledger.stats(true).profit(), "Switching profiles uses separate accounting");
        yes(!tracker.inArena && !tracker.inSkyblock, "Profile switch clears location evidence");
        tracker.profile("default");
        eq(
                219_300,
                tracker.ledger.stats(true).profit(),
                "Original profile retains recorded net income");
        yes(!tracker.inArena, "Returning to profile does not restore stale evidence");
        Tracker reopened = new Tracker(root);
        reopened.account("integration-account");
        eq(
                219_300,
                reopened.ledger.stats(true).profit(),
                "Updated tracker writes and reloads existing ledger format");
        yes(!reopened.inArena, "Reopening tracker cannot restore stale live context");

        Tracker live = new Tracker(root.resolve("live-fixtures"));
        live.account("fixture-account");
        live.config.callingCost = 700;
        live.updateLocation(true, true, true, "Arachne's Sanctuary", "Sanctuary detected", now);
        for (int i = 1; i <= 4; i++) {
            String announcement =
                    "\u2604 [Nmbr1YankeeFan head]Nmbr1YankeeFan placed an Arachne's Calling!"
                            + (i == 4 ? " Something is awakening!" : "")
                            + " ("
                            + i
                            + "/4)";
            live.message(announcement, "Nmbr1YankeeFan", now + i);
        }
        eq(
                4,
                live.ledger.stats(false).callings(),
                "All four server Calling messages count as separate placements");
        eq(2800, live.ledger.stats(false).costs(), "Four Callings use four unit costs");
        live.message(
                "\u2604 [Other head]Other placed an Arachne Crystal! Something is awakening!",
                "Nmbr1YankeeFan",
                now + 10);
        eq(
                0,
                live.ledger.stats(false).crystals(),
                "Another player's Crystal is not charged to this player");
        live.message("                              ARACHNE DOWN!", "Nmbr1YankeeFan", now + 100);
        live.message("Your Damage: 0 (Position #6)", "Nmbr1YankeeFan", now + 101);
        eq(
                0,
                live.ledger.stats(false).kills(),
                "Actual zero-damage server summary does not count a personal kill");
        live.message("Your Damage: 100 (Position #6)", "Nmbr1YankeeFan", now + 102);
        eq(
                0,
                live.ledger.stats(false).kills(),
                "A consumed zero-damage summary cannot be replaced by a later stray line");
        live.message("[BOSS] Arachne: Ahhhh...A Calling...", "Nmbr1YankeeFan", now + 4_990);
        live.message("ARACHNE DOWN!", "Nmbr1YankeeFan", now + 5_000);
        live.message("Your Damage: 500,000 (Position #2)", "Nmbr1YankeeFan", now + 5_001);
        live.message("ARACHNE DOWN!", "Nmbr1YankeeFan", now + 5_002);
        live.message("Your Damage: 500,000 (Position #2)", "Nmbr1YankeeFan", now + 5_003);
        eq(
                1,
                live.ledger.stats(false).kills(),
                "Duplicate death and damage lines count one personal kill");
        live.message("[BOSS] Arachne: Ahhhh...A Calling...", "Nmbr1YankeeFan", now + 9_990);
        live.message("ARACHNE DOWN!", "Nmbr1YankeeFan", now + 10_000);
        live.message("Your Damage: 500,000 (Position #2)", "Nmbr1YankeeFan", now + 16_000);
        eq(
                1,
                live.ledger.stats(false).kills(),
                "Late damage line cannot attach to an expired death summary");
        live.message("[BOSS] Arachne: Ahhhh...A Calling...", "Nmbr1YankeeFan", now + 19_000);
        live.message("ARACHNE DOWN!", "Nmbr1YankeeFan", now + 19_990);
        live.updateLocation(
                true, true, false, "Private Island", "Other area detected", now + 20_000);
        yes(!live.inArena, "Leaving Sanctuary for a known area stops tracking immediately");
        live.message(activity, "Nmbr1YankeeFan", now + 20_001);
        yes(!live.inArena, "Arachne text cannot start fallback on the private island");
        live.updateLocation(
                true, true, true, "Arachne's Sanctuary", "Sanctuary detected", now + 20_100);
        yes(
                !live.acceptsLoot(now + 20_101),
                "Reentering Sanctuary cannot revive the previous fight's loot window");
        live.message("Your Damage: 500,000 (Position #2)", "Nmbr1YankeeFan", now + 20_102);
        eq(
                1,
                live.ledger.stats(false).kills(),
                "Reentering Sanctuary cannot revive the previous death summary");
        live.updateLocation(
                true, true, false, "Private Island", "Other area detected", now + 20_200);
        live.updateLocation(true, true, false, "Spider's Den", "Island", now + 21_000);
        yes(!live.inArena, "Returning to the island does not revive evidence from before leaving");
        live.message(activity, "Nmbr1YankeeFan", now + 21_001);
        yes(live.inArena, "Fresh boss evidence can activate fallback after returning");
        yes(
                live.acceptsLoot(now + 21_001),
                "Fresh activity opens a new loot window after returning");
        live.config.paused = true;
        live.pickup("SOUL_STRING", 44, now + 21_002);
        live.message(calling, "User", now + 21_003);
        eq(0, live.ledger.stats(false).revenue(), "Paused tracker rejects loot");
        eq(4, live.ledger.stats(false).callings(), "Paused tracker rejects summon cost messages");
        System.out.println(
                "PASS: "
                        + checks
                        + " server-format, fallback, lifecycle and migration integration checks.");
    }
}

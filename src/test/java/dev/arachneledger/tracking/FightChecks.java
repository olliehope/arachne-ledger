package dev.arachneledger.tracking;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.config.Store;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.skyblock.Messages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Spawn/death timelines, damage qualification and delayed reward summaries. */
public final class FightChecks {
    private static int checks;
    private static final long BASE = 1_000_000;
    private static final String SPAWN = "[BOSS] Arachne: Ahhhh...A Calling...";

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) {
            throw new AssertionError(why);
        }
    }

    private static Tracker tracker() throws Exception {
        Tracker t = new Tracker(Files.createTempDirectory("arachne-fights-"));
        t.account("account");
        t.config.crystalConfigured = true;
        t.config.crystalCost = 100_000;
        t.config.callingCost = 700;
        t.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE);
        t.tick(BASE, true);
        return t;
    }

    private static void message(Tracker t, String text, long elapsed) {
        t.message(text, "Player", BASE + elapsed);
    }

    private static void tick(Tracker t, long elapsed) {
        t.tick(BASE + elapsed, true);
    }

    private static void down(Tracker t, long elapsed, long damage) {
        message(t, "ARACHNE DOWN!", elapsed);
        message(t, "Your Damage: " + damage + " (Position #1)", elapsed + 10);
    }

    public static void main(String[] args) throws Exception {
        Tracker t = tracker();
        yes(t.waitingForSpawn(), "Entering Sanctuary waits for a spawn");
        tick(t, 1000);
        message(t, "☄ You placed an Arachne Crystal!", 1100);
        tick(t, 2000);
        eq(0, t.ledger.activeMillis, "Placements and pre-spawn waiting do not start the timer");
        message(t, SPAWN, 3000);
        tick(t, 8000);
        tick(t, 13000);
        eq(10000, t.ledger.activeMillis, "Spawn starts active timing");
        message(t, "[BOSS] Arachne: A befitting welcome!", 14000);
        message(t, SPAWN, 15000);
        down(t, 18000, 10000);
        eq(1, t.ledger.stats(false).kills(), "Exactly 10,000 damage qualifies");
        var stringStand = UUID.randomUUID();
        t.observeLootStand(stringStand, "Soul String x44", BASE + 18100);
        t.observeLootStand(stringStand, "Soul String x44", BASE + 18150);
        t.pickup("SOUL_STRING", 44, BASE + 18200);
        t.observeLootStand(UUID.randomUUID(), "Arachne Shard", BASE + 18250);
        message(t, "☄ You placed an Arachne's Calling! (1/4)", 18300);
        tick(t, 20000);
        yes(t.drainKillSummaries().isEmpty(), "Report waits for the reward labels");
        tick(t, 21000);
        var summaries = t.drainKillSummaries();
        eq(1, summaries.size(), "One report for a qualified death");
        var summary = summaries.getFirst();
        eq(
                15000,
                summary.fightMillis(),
                "Mid-fight dialogue and repeated spawn lines do not reset duration");
        eq(220000, summary.income(), "Summary includes drops once despite matching pickups");
        eq(100000, summary.costs(), "Summary charges own summon before spawn");
        eq(120000, summary.profit(), "Next fight's Calling is excluded from this profit");
        eq(1, summary.unpriced(), "Unpriced rewards flagged in the report");
        eq(10000, summary.damage(), "Report keeps server damage");
        eq(1, summary.killNumber(), "Report identifies the session kill");
        yes(t.drainKillSummaries().isEmpty(), "Report is delivered once");
        message(t, "ARACHNE DOWN!", 22000);
        message(t, "Your Damage: 100000 (Position #1)", 22010);
        eq(
                1,
                t.ledger.stats(false).kills(),
                "A later relayed results block needs a new fight to count again");
        tick(t, 25010);
        yes(t.drainKillSummaries().isEmpty(), "Relayed results produce no extra chat summary");
        String text = ArachneLedger.killSummaryMessage(summary).getString();
        yes(
                text.contains("15.0s")
                        && text.contains("+120.0k coins")
                        && text.contains("1 unpriced"),
                "Chat displays duration, signed profit and unpriced warning");
        for (long time = 26000; time <= 76000; time += 5000) {
            tick(t, time);
        }
        tick(t, 77999);
        yes(!t.isAfk(), "Grace still active just before 60 seconds after death");
        tick(t, 79000);
        eq(
                75000,
                t.ledger.activeMillis,
                "Crossing AFK boundary counts exactly 60 seconds after death");
        yes(t.isAfk() && t.status().startsWith("AFK"), "AFK status appears automatically");
        tick(t, 80000);
        message(t, "[BOSS] Arachne: Spiders in my Den, can you count to ten?", 80050);
        eq(75000, t.ledger.activeMillis, "Late dialogue cannot restart the AFK timer");
        yes(t.isAfk(), "Only a new spawn ends AFK after death");
        message(t, SPAWN, 83000);
        yes(!t.isAfk(), "New spawn resumes timing");
        tick(t, 88000);
        eq(80000, t.ledger.activeMillis, "AFK gap excluded from elapsed time");
        down(t, 90000, 9999);
        message(t, "ARACHNE DOWN!", 91000);
        message(t, "Your Damage: 200000 (Position #1)", 91010);
        tick(t, 94000);
        eq(1, t.ledger.stats(false).kills(), "Low damage and relayed death cannot become a kill");
        yes(t.drainKillSummaries().isEmpty(), "Rejected kill produces no success summary");
        eq(
                100700,
                t.ledger.stats(false).costs(),
                "Rejected participation does not erase money spent");
        message(t, SPAWN, 95000);
        down(t, 98000, 0);
        tick(t, 102000);
        eq(1, t.ledger.stats(false).kills(), "Zero damage excluded");
        yes(t.drainKillSummaries().isEmpty(), "Zero damage produces no success report");

        Tracker continuous = tracker();
        message(continuous, SPAWN, 1000);
        tick(continuous, 6000);
        down(continuous, 10000, 150000);
        for (long time = 15000; time <= 55000; time += 5000) {
            tick(continuous, time);
        }
        message(continuous, SPAWN, 60000);
        down(continuous, 62000, 200000);
        tick(continuous, 66000);
        eq(
                65000,
                continuous.ledger.activeMillis,
                "A new spawn during grace keeps the active cycle continuous");
        var both = continuous.drainKillSummaries();
        eq(2, both.size(), "Successive fights each produce a report");
        eq(
                2000,
                both.getLast().fightMillis(),
                "Second fight duration excludes time waiting between fights");

        Tracker unknown = tracker();
        message(unknown, "[BOSS] Arachne: A tough fight!", 1000);
        tick(unknown, 6000);
        down(unknown, 8000, 200000);
        tick(unknown, 12000);
        var partial = unknown.drainKillSummaries().getFirst();
        eq(-1, partial.fightMillis(), "Joining mid-fight marks duration as unknown");
        yes(
                ArachneLedger.killSummaryMessage(partial).getString().contains("Time unknown"),
                "Unknown duration is labelled clearly");
        Tracker missing = tracker();
        message(missing, SPAWN, 1000);
        message(missing, "ARACHNE DOWN!", 5000);
        tick(missing, 9000);
        yes(
                missing.drainKillSummaries().isEmpty(),
                "Missing damage summary cannot create a kill report");
        message(missing, "Your Damage: 200000 (Position #1)", 10001);
        eq(0, missing.ledger.stats(false).kills(), "Late damage cannot attach to expired death");
        tick(missing, 14000);
        yes(missing.drainKillSummaries().isEmpty(), "Late damage creates no report");

        Tracker quiet = tracker();
        quiet.config.killChat = false;
        message(quiet, SPAWN, 1000);
        down(quiet, 4000, 150000);
        tick(quiet, 8000);
        eq(1, quiet.ledger.stats(false).kills(), "Disabling chat preserves accounting");
        yes(quiet.drainKillSummaries().isEmpty(), "Chat toggle suppresses report");
        quiet.config.minimumDamage = 20000;
        message(quiet, SPAWN, 9000);
        down(quiet, 13000, 19999);
        tick(quiet, 17000);
        eq(1, quiet.ledger.stats(false).kills(), "Custom minimum damage is applied");
        Tracker late = tracker();
        message(late, SPAWN, 1000);
        down(late, 4000, 50000);
        late.observeLootStand(UUID.randomUUID(), "Soul String x44", BASE + 6900);
        tick(late, 7000);
        yes(
                late.drainKillSummaries().isEmpty(),
                "Recently arrived loot extends the short report delay");
        tick(late, 7700);
        eq(
                220000,
                late.drainKillSummaries().getFirst().income(),
                "Delayed reward included in chat");
        Tracker pause = tracker();
        message(pause, SPAWN, 1000);
        tick(pause, 6000);
        pause.togglePause();
        tick(pause, 7000);
        eq(5000, pause.ledger.activeMillis, "Manual pause stops active time");
        pause.togglePause();
        tick(pause, 8000);
        yes(pause.waitingForSpawn(), "Resume cannot revive an old fight");
        message(pause, SPAWN, 9000);
        tick(pause, 14000);
        eq(10000, pause.ledger.activeMillis, "Resume starts fresh at the next spawn");
        down(pause, 15000, 200000);
        pause.resetContext();
        tick(pause, 19000);
        yes(pause.drainKillSummaries().isEmpty(), "World change drops pending reports");
        yes(pause.waitingForSpawn(), "Context reset clears fight and AFK state");
        Tracker session = tracker();
        message(session, SPAWN, 1000);
        down(session, 5000, 100000);
        session.newSession();
        tick(session, 9000);
        yes(
                session.drainKillSummaries().isEmpty(),
                "New session cannot print a report from the previous session");
        eq(1, session.ledger.stats(true).kills(), "New session preserves earlier lifetime kill");
        eq(0, session.ledger.stats(false).kills(), "New session resets kill count");
        Tracker stalled = tracker();
        message(stalled, SPAWN, 1000);
        tick(stalled, 6000);
        tick(stalled, 66000);
        eq(5000, stalled.ledger.activeMillis, "Suspended client does not add offline time");
        eq(
                0,
                Messages.damage("§eYour Damage: §v0 (Position #6)").orElse(-1),
                "Custom style codes in damage summary handled");
        eq(
                136000,
                Messages.damage(" Your Damage: 136,000 ").orElse(-1),
                "Damage without position supported");
        yes(
                Messages.damage("Party > Player: Your Damage: 900000 (Position #1)").isEmpty(),
                "Player chat cannot spoof participation");
        yes(
                Messages.damage("Your Damage: 99999999999999999999999 (Position #1)").isEmpty(),
                "Malformed damage cannot crash message handler");
        Config defaults = new Config();
        eq(10000, defaults.minimumDamage, "Default damage threshold");
        yes(defaults.killChat, "Kill chat enabled by default");
        Path path = Files.createTempDirectory("arachne-config-").resolve("settings.json");
        Files.writeString(path, "{\"profile\":\"default\",\"prices\":{}}");
        Config migrated = Store.read(path, Config.class, Config::new, Config::validate);
        eq(10000, migrated.minimumDamage, "Old settings receive minimum damage default");
        yes(migrated.killChat, "Old settings receive chat default");
        migrated.minimumDamage = 12345;
        migrated.killChat = false;
        Store.write(path, migrated);
        Config saved = Store.read(path, Config.class, Config::new, Config::validate);
        eq(12345, saved.minimumDamage, "Custom damage threshold persists");
        yes(!saved.killChat, "Chat preference persists");
        pausedPreview();
        System.out.println(
                "PASS: "
                        + checks
                        + " fight timing, AFK, damage qualification and kill-summary checks.");
    }

    private static void pausedPreview() throws Exception {
        var paused = tracker();
        message(paused, SPAWN, 1000);
        tick(paused, 2000);
        paused.config.paused = true;
        tick(paused, 2100);
        yes(
                paused.ledger.fights.getFirst().outcome == FightRecord.Outcome.INTERRUPTED,
                "Direct pause transition interrupts the previous fight once");
        yes(!paused.acceptsLoot(BASE + 2100), "Pause invalidates the previous pickup window");
        yes(
                paused.rng.notice("TARANTULA_LEGENDARY", 1, 100, BASE + 2200),
                "A local preview can be queued while tracking is paused");
        tick(paused, 2300);
        tick(paused, 2400);
        yes(
                paused.rng.current(BASE + 2400) != null,
                "Paused ticks preserve an explicitly requested title preview");
        eq(
                0,
                paused.ledger.stats(false).loot().size(),
                "Title preview does not add synthetic loot");
        eq(1000, paused.ledger.activeMillis, "Paused preview does not restart active time");
        paused.config.paused = false;
        tick(paused, 2500);
        yes(
                paused.waitingForSpawn(),
                "Resume waits for a fresh boss rather than reviving the interrupted fight");
        paused.pickup("SOUL_STRING", 44, BASE + 2600);
        eq(0, paused.ledger.stats(false).revenue(), "Resume cannot revive the old pickup window");
    }
}

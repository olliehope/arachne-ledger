package dev.arachneledger.tracking;

import com.google.gson.Gson;

import dev.arachneledger.config.Config;
import dev.arachneledger.config.FarmingPreferences;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.RngSince.Reward;
import dev.arachneledger.skyblock.Messages;

import java.nio.file.Files;
import java.util.UUID;

/** Farming cues stay optional, duplicate-safe and separate from recorded boss qualification. */
public final class FarmingChecks {
    private static final long BASE = 1_000_000;
    private static final String CRYSTAL =
            "☄ OtherPlayer placed an Arachne Crystal! Something is awakening!";
    private static final String CALLING =
            "☄ OtherPlayer placed an Arachne's Calling! Something is awakening! (4/4)";
    private static final String INTRO = "[BOSS] Arachne: With your sacrifice.";
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .0001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void summon(Messages.Summon expected, String message, String why) {
        yes(Messages.summon(message) == expected, why);
    }

    public static void main(String[] args) throws Exception {
        parserAndPreferences();
        ritualEstimates();
        trackerTimerLifecycle();
        localEventQueues();
        trackerNoticesAndDedup();
        rngCache();
        staleFightRecoveryAndMetadata();
        System.out.println("PASS: " + checks + " farming cues, ritual estimates and cache checks.");
    }

    private static void parserAndPreferences() {
        summon(Messages.Summon.CRYSTAL, CRYSTAL, "Someone else's Crystal starts a ritual");
        summon(
                Messages.Summon.CRYSTAL,
                "§5☄ §b[MVP++] Player §eplaced an Arachne Crystal! Something is awakening!",
                "Rank, icon and color formatting are accepted");
        summon(
                Messages.Summon.CALLING,
                "☄ YOU placed an Arachne’s Calling! Something is awakening! (4/4)",
                "YOU and smart apostrophe Calling text are accepted");
        for (int number = 1; number <= 3; number++) {
            summon(
                    null,
                    "☄ Player placed an Arachne's Calling! (" + number + "/4)",
                    "Incomplete Calling cannot start a timer");
            summon(
                    null,
                    "☄ Player placed an Arachne's Calling! Something is awakening! ("
                            + number
                            + "/4)",
                    "Awakening words cannot override an incomplete Calling count");
        }
        summon(
                null,
                "☄ Player placed an Arachne's Calling! (4/4)",
                "Missing awakening cue is not a complete ritual");
        summon(
                null,
                "☄ Player placed an Arachne Crystal!",
                "Crystal placement without awakening does not start a timer");
        summon(null, "Party > Player: " + CRYSTAL, "Quoted party text cannot start a timer");
        summon(null, "[MVP+] Player: " + CALLING, "Public chat cannot spoof a complete Calling");
        summon(null, CRYSTAL + " (4/4)", "Crystal never borrows Calling's four-part syntax");
        summon(
                null,
                "☄ Player placed an Arachne's Calling! Something is awakening!",
                "Calling requires its completed counter");

        FarmingPreferences preferences = new FarmingPreferences();
        preferences.pedestalTimer = false;
        preferences.pedestalFightTime = true;
        preferences.pedestalThroughWalls = true;
        preferences.adaptiveCrystalTimer = false;
        preferences.crystalSpawnSeconds = 52;
        preferences.callingSpawnSeconds = 25;
        preferences.pedestalScale = .75;
        preferences.pedestalRange = 96;
        preferences.spawnSound = true;
        preferences.spawnTitle = true;
        preferences.rngChat = false;
        preferences.rngSound = false;
        preferences.achievementSound = true;
        preferences.achievementTitle = true;
        preferences.compactKillChat = true;
        FarmingPreferences copy = preferences.copy();
        Gson gson = new Gson();
        yes(
                gson.toJson(preferences).equals(gson.toJson(copy)),
                "Settings draft copies every farming option");
        copy.pedestalTimer = true;
        copy.crystalSpawnSeconds = 10;
        yes(!preferences.pedestalTimer, "Draft toggle cannot mutate saved preferences");
        eq(
                52,
                preferences.crystalSpawnSeconds,
                "Draft numerical edit cannot mutate saved fallback");
        FarmingPreferences restored =
                gson.fromJson(gson.toJson(preferences), FarmingPreferences.class);
        restored.validate();
        eq(52, restored.crystalSpawnSeconds, "Configured timer fallback survives save and reload");
        eq(.75, restored.pedestalScale, "Overlay scale survives save and reload");
        yes(
                restored.spawnTitle && restored.compactKillChat,
                "Independent notification settings survive reload");
        copy.crystalSpawnSeconds = Integer.MIN_VALUE;
        copy.callingSpawnSeconds = Integer.MAX_VALUE;
        copy.pedestalRange = Integer.MIN_VALUE;
        copy.pedestalScale = Double.NaN;
        copy.validate();
        eq(10, copy.crystalSpawnSeconds, "Crystal fallback has a bounded minimum");
        eq(60, copy.callingSpawnSeconds, "Calling fallback has a bounded maximum");
        eq(16, copy.pedestalRange, "World label range has a bounded minimum");
        eq(1, copy.pedestalScale, "Nonfinite scale recovers to a usable default");
        copy.pedestalScale = .1;
        copy.pedestalRange = Integer.MAX_VALUE;
        copy.validate();
        eq(.5, copy.pedestalScale, "Tiny world label scale is clamped");
        eq(128, copy.pedestalRange, "World label range has a bounded maximum");
        copy.pedestalScale = 99;
        copy.validate();
        eq(2, copy.pedestalScale, "Huge world label scale is clamped");
        Config oldSettings = gson.fromJson("{\"farming\":null}", Config.class);
        oldSettings.validate();
        yes(
                oldSettings.farming != null && oldSettings.farming.pedestalTimer,
                "Legacy missing farming settings migrate safely");
    }

    private static void ritualEstimates() {
        FarmingPreferences preferences = new FarmingPreferences();
        PedestalTimer timer = new PedestalTimer();
        yes(!timer.begin(null, BASE, preferences), "Unrelated chat cannot start an estimate");
        yes(
                !timer.begin(Messages.Summon.CRYSTAL, 0, preferences),
                "Invalid time cannot start an estimate");
        yes(
                timer.begin(Messages.Summon.CRYSTAL, BASE, preferences),
                "Crystal starts its configured fallback");
        eq(40, timer.view(BASE).seconds(), "Default Crystal estimate is forty seconds");
        var firstLabel = timer.view(BASE);
        var nextFrame = timer.view(BASE + 10);
        yes(
                firstLabel.label() == nextFrame.label(),
                "Repeated render requests within the displayed tenth reuse formatted text");
        eq(39.99, nextFrame.seconds(), "Label reuse preserves exact countdown data");
        yes(
                !firstLabel.label().equals(timer.view(BASE + 100).label()),
                "Visible tenths advance instead of freezing the countdown");
        yes(
                !timer.begin(Messages.Summon.CRYSTAL, BASE + 1_000, preferences),
                "Duplicate placement cannot restart countdown");
        yes(
                !timer.begin(Messages.Summon.CALLING, BASE + 2_000, preferences),
                "Another ritual relay cannot replace active countdown");
        eq(
                37,
                timer.view(BASE + 3_000).seconds(),
                "Duplicate relay retains original fallback origin");
        for (int index = 0; index < 25; index++) timer.particles(BASE + 2_000 + index);
        eq(
                36,
                timer.view(BASE + 4_000).seconds(),
                "Dust before the three-second delay does not identify a variant");
        eq(
                0,
                timer.view(BASE + 40_000).seconds(),
                "Finished estimate waits without inventing an actual spawn");
        yes(
                timer.view(BASE + 40_000).label().contains("awaiting spawn"),
                "Expired countdown says awaiting spawn");
        yes(timer.view(BASE + 59_999) != null, "Late actual spawn retains a bounded waiting label");
        yes(timer.view(BASE + 60_000) == null, "A missing spawn eventually expires the estimate");
        yes(
                timer.begin(Messages.Summon.CALLING, BASE + 60_000, preferences),
                "Expired ritual can be replaced");
        eq(19, timer.view(BASE + 60_000).seconds(), "Calling uses its separate fallback");
        timer.clear();
        yes(timer.view(BASE + 60_000) == null, "Clear removes all ritual state");
        yes(timer.begin(Messages.Summon.CRYSTAL, BASE, preferences), "Cleared timer can be reused");
        yes(timer.view(BASE - 1) == null, "Clock reversal cannot show negative countdown age");

        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        for (int index = 0; index < 20; index++) timer.particles(BASE + 3_000 + index);
        eq(
                36.941,
                timer.view(BASE + 3_059).seconds(),
                "First burst remains open while delayed packets can still arrive");
        eq(
                20.65,
                timer.view(BASE + 3_350).seconds(),
                "Twenty events select twenty-one seconds after the first burst");
        eq(
                0,
                timer.view(BASE + 24_000).seconds(),
                "Early low-density burst estimates a twenty-four-second Crystal");
        timer.particles(BASE + 3_100);
        eq(
                19,
                timer.view(BASE + 5_000).seconds(),
                "Later dust cannot revise a finished burst sample");

        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        for (int index = 0; index < 21; index++) timer.particles(BASE + 3_000 + index);
        eq(
                36.65,
                timer.view(BASE + 3_350).seconds(),
                "Twenty-one events select thirty-seven seconds after the first burst");
        eq(
                0,
                timer.view(BASE + 40_000).seconds(),
                "Early dense burst estimates a forty-second Crystal");

        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        for (int index = 0; index < 20; index++) timer.particles(BASE + 3_000 + index);
        timer.particles(BASE + 3_350);
        eq(
                20.65,
                timer.view(BASE + 3_350).seconds(),
                "Event at the burst boundary is excluded from the completed first sample");
        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        for (int index = 0; index < 20; index++) timer.particles(BASE + 6_500 + index);
        eq(
                20.65,
                timer.view(BASE + 6_850).seconds(),
                "Delayed first burst uses its own event timestamp");
        eq(
                3.5,
                timer.view(BASE + 24_000).seconds(),
                "Delayed burst does not pretend placement was observed three seconds earlier");

        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        for (int index = 0; index < 21; index++) timer.particles(BASE + 3_000 + index * 8);
        eq(
                36.65,
                timer.view(BASE + 3_350).seconds(),
                "Dense packets stretched beyond sixty milliseconds still identify a normal ritual");
        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        timer.particles(BASE + 3_000);
        eq(
                36.65,
                timer.view(BASE + 3_350).seconds(),
                "A single stray packet cannot shorten the configurable fallback");
        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        for (int index = 0; index < 8; index++) timer.particles(BASE + 3_000 + index * 10);
        timer.particles(BASE + 3_300);
        eq(
                36.65,
                timer.view(BASE + 3_350).seconds(),
                "Fragmented weak evidence retains fallback rather than guessing a quick spawn");
        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        for (int index = 0; index < 20; index++) timer.particles(BASE + 8_000 + index);
        eq(
                31,
                timer.view(BASE + 9_000).seconds(),
                "Late unrelated dust cannot start a new sample beyond its bounded window");

        preferences.crystalSpawnSeconds = 55;
        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        eq(51, timer.view(BASE + 4_000).seconds(), "No dust retains the configurable fallback");
        preferences.adaptiveCrystalTimer = false;
        timer.clear();
        timer.begin(Messages.Summon.CRYSTAL, BASE, preferences);
        for (int index = 0; index < 25; index++) timer.particles(BASE + 3_000 + index);
        eq(51, timer.view(BASE + 4_000).seconds(), "Adaptive disabled ignores dust classification");
        preferences.adaptiveCrystalTimer = true;
        preferences.callingSpawnSeconds = 25;
        timer.clear();
        timer.begin(Messages.Summon.CALLING, BASE, preferences);
        for (int index = 0; index < 25; index++) timer.particles(BASE + 3_000 + index);
        eq(21, timer.view(BASE + 4_000).seconds(), "Calling never uses Crystal dust timings");
    }

    private static Tracker tracker() throws Exception {
        Tracker tracker = new Tracker(Files.createTempDirectory("arachne-farming-"));
        tracker.account("farming-test");
        tracker.config.achievementNotifications = false;
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE);
        tracker.tick(BASE, true);
        return tracker;
    }

    private static void message(Tracker tracker, String text, long elapsed) {
        tracker.message(text, "Owner", BASE + elapsed);
    }

    private static void down(Tracker tracker, long elapsed) {
        message(tracker, "ARACHNE DOWN!", elapsed);
        message(tracker, "Your Damage: 10,000 (Position #1)", elapsed + 10);
    }

    private static void trackerTimerLifecycle() throws Exception {
        Tracker tracker = tracker();
        tracker.config.farming.spawnSound = true;
        message(tracker, CRYSTAL, 1_000);
        var initial = tracker.pedestalTimer(BASE + 1_000);
        yes(initial != null && !initial.fighting(), "Another player's ritual shows countdown");
        eq(40, initial.seconds(), "Placement time starts the visible fallback");
        eq(
                0,
                tracker.ledger.stats(true).crystals(),
                "Another player never incurs your Crystal cost");
        eq(0, tracker.ledger.fights.size(), "Countdown cannot create a confirmed boss fight");
        long active = tracker.ledger.activeMillis;
        message(tracker, CRYSTAL, 2_000);
        eq(
                39,
                tracker.pedestalTimer(BASE + 2_000).seconds(),
                "Repeated placement does not restart visible estimate");
        tracker.pedestalTimer(BASE + 41_000);
        eq(
                active,
                tracker.ledger.activeMillis,
                "Reading an estimate does not change recorded active time");
        eq(0, tracker.ledger.fights.size(), "Estimate completion still cannot create a boss fight");
        message(tracker, INTRO, 3_000);
        yes(tracker.pedestalTimer(BASE + 3_000) == null, "Actual introduction clears countdown");
        eq(1, tracker.drainSpawnNotices().size(), "One actual spawn produces an enabled spawn cue");
        message(tracker, INTRO, 4_000);
        eq(0, tracker.drainSpawnNotices().size(), "Duplicate introduction cannot replay spawn cue");
        eq(1, tracker.ledger.fights.size(), "Duplicate introduction retains a single fight");
        tracker.config.farming.pedestalFightTime = true;
        tracker.config.farming.pedestalTimer = false;
        var fight = tracker.pedestalTimer(BASE + 6_000);
        yes(
                fight != null && fight.fighting(),
                "Optional pedestal fight time replaces the ritual label");
        eq(3, fight.seconds(), "Repeated introductions cannot reset fight elapsed time");
        tracker.config.farming.pedestalTimer = true;
        down(tracker, 7_000);
        yes(tracker.pedestalTimer(BASE + 7_100) == null, "Death clears optional fight time");
        eq(
                1,
                tracker.ledger.stats(true).kills(),
                "Overlay options cannot change genuine kill qualification");

        message(tracker, CALLING, 8_000);
        yes(
                tracker.pedestalTimer(BASE + 8_000).detail().equals("Calling ritual"),
                "Next ritual uses its own kind");
        tracker.togglePause();
        yes(tracker.pedestalTimer(BASE + 8_100) == null, "Pause hides and clears pending ritual");
        tracker.togglePause();
        yes(tracker.pedestalTimer(BASE + 8_200) == null, "Resuming cannot restore stale ritual");
        message(tracker, CRYSTAL, 9_000);
        tracker.tick(BASE + 9_100, false);
        tracker.tick(BASE + 9_200, true);
        yes(
                tracker.pedestalTimer(BASE + 9_200) == null,
                "Leaving and returning cannot restore ritual");
        message(tracker, CRYSTAL, 10_000);
        tracker.resetContext();
        tracker.tick(BASE + 10_100, true);
        yes(tracker.pedestalTimer(BASE + 10_100) == null, "World reset clears ritual state");
        message(tracker, CRYSTAL, 11_000);
        tracker.newSession();
        yes(tracker.pedestalTimer(BASE + 11_100) == null, "Session reset clears ritual estimate");
        message(tracker, CRYSTAL, 12_000);
        tracker.config.farming.pedestalTimer = false;
        yes(tracker.pedestalTimer(BASE + 12_100) == null, "Master timer option hides world label");
        yes(tracker.isSummoning(), "Hiding the overlay does not disable summoning detection");
        tracker.config.farming.pedestalTimer = true;
        yes(
                tracker.pedestalTimer(BASE + 12_100) != null,
                "Hidden estimate remains valid within the same ritual");
        tracker.profile("other");
        tracker.tick(BASE + 12_200, true);
        yes(
                tracker.pedestalTimer(BASE + 12_200) == null,
                "Profile load clears transient ritual state");

        Tracker adaptive = tracker();
        message(adaptive, CRYSTAL, 1_000);
        for (int index = 0; index < 20; index++)
            adaptive.observeRitualParticles(BASE + 4_000 + index);
        eq(
                20.65,
                adaptive.pedestalTimer(BASE + 4_350).seconds(),
                "Tracker forwards dust event timestamps to the adaptive timer");
    }

    private static void localEventQueues() {
        FarmingEvents events = new FarmingEvents();
        Ledger ledger = new Ledger();
        for (int index = 1; index <= 40; index++) {
            events.rare(
                    ledger.add(
                            Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 500, "armor_stand", BASE + index));
            events.spawned(BASE + index);
        }
        var rare = events.drainRare();
        var spawned = events.drainSpawns();
        eq(32, rare.size(), "Undrained rare notices remain bounded");
        eq(
                9,
                rare.getFirst().entryId(),
                "Overflow drops oldest rare receipt, preserving recent identities");
        eq(32, spawned.size(), "Undrained spawn notices remain bounded");
        eq(BASE + 9, spawned.getFirst().at(), "Overflow drops oldest spawn notice");
        eq(0, events.drainRare().size(), "Rare drain consumes its queue");
        eq(0, events.drainSpawns().size(), "Spawn drain consumes its queue");
        boolean immutable = false;
        try {
            rare.clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        yes(immutable, "Drained notice list is immutable");
        events.rare(ledger.entries.getFirst());
        events.spawned(BASE);
        events.clear();
        eq(0, events.drainRare().size(), "Clear discards stale rare feedback");
        eq(0, events.drainSpawns().size(), "Clear discards stale spawn feedback");
    }

    private static void trackerNoticesAndDedup() throws Exception {
        Tracker tracker = tracker();
        tracker.config.farming.spawnTitle = true;
        message(tracker, INTRO, 1_000);
        message(tracker, INTRO, 1_010);
        eq(
                1,
                tracker.drainSpawnNotices().size(),
                "Enabled spawn title receives one actual-spawn event");
        down(tracker, 2_000);
        UUID petStand = new UUID(130, 1);
        tracker.observeLootStand(petStand, "Legendary Tarantula Pet", BASE + 2_100);
        tracker.observeLootStand(petStand, "Legendary Tarantula Pet", BASE + 2_150);
        tracker.pickup("TARANTULA_LEGENDARY", 1, BASE + 2_200);
        message(
                tracker,
                "You claimed a Legendary Tarantula Pet! You can manage your Pets in the Pets Menu in your SkyBlock Menu.",
                2_250);
        var notices = tracker.drainRareDrops();
        eq(1, notices.size(), "Stand, pickup and personal claim reconcile to one rare cue");
        yes(
                notices.getFirst().itemId().equals("TARANTULA_LEGENDARY"),
                "Rare notice keeps detected rarity");
        eq(1, notices.getFirst().quantity(), "Rare notice retains detected quantity");
        eq(100_000, notices.getFirst().unitValue(), "Rare notice uses recorded sell value");
        yes(
                tracker.ledger.entries.stream()
                        .anyMatch(entry -> entry.id() == notices.getFirst().entryId()),
                "Rare notice refers to the retained journal receipt");
        eq(1, tracker.rng.queued(), "Duplicate reward paths cannot create duplicate title popups");
        eq(0, tracker.drainRareDrops().size(), "Draining rare notices does not replay them");
        tracker.record(Ledger.Kind.LOOT, "TARANTULA_EPIC", 50, 2_000, "manual", BASE + 2_300);
        eq(
                0,
                tracker.drainRareDrops().size(),
                "Manual rare adjustment cannot fabricate local rare feedback");
        eq(1, tracker.rng.queued(), "Manual rare adjustment cannot fabricate title popup");
        tracker.observeLootStand(new UUID(130, 2), "Enchanted String", BASE + 2_400);
        eq(0, tracker.drainRareDrops().size(), "Ordinary loot never produces a rare cue");
        tracker.observeLootStand(new UUID(130, 3), "Arachne's Fang x2", BASE + 2_500);
        var fangs = tracker.drainRareDrops();
        eq(1, fangs.size(), "Fang reward produces its own rare cue");
        eq(2, fangs.getFirst().quantity(), "Fang cue retains multiplier quantity");
        tracker.resetContext();
        eq(0, tracker.drainRareDrops().size(), "World reset discards queued rare feedback");
        eq(0, tracker.drainSpawnNotices().size(), "World reset discards queued spawn feedback");
        eq(0, tracker.rng.queued(), "World reset discards queued title popups");

        Tracker silent = tracker();
        silent.config.killChat = false;
        silent.config.sessionRecapChat = false;
        silent.config.rngTitles = false;
        silent.config.farming.rngChat = false;
        silent.config.farming.rngSound = false;
        silent.config.farming.spawnSound = false;
        silent.config.farming.spawnTitle = false;
        silent.config.farming.achievementSound = false;
        silent.config.farming.achievementTitle = false;
        message(silent, INTRO, 1_000);
        down(silent, 2_000);
        silent.observeLootStand(new UUID(130, 4), "Epic Tarantula Pet", BASE + 2_100);
        eq(
                0,
                silent.drainSpawnNotices().size(),
                "All spawn feedback disabled creates no queued spawn notice");
        eq(
                0,
                silent.drainRareDrops().size(),
                "All rare feedback disabled creates no queued rare notice");
        eq(0, silent.rng.queued(), "Title disabled creates no title popup");
        eq(1, silent.ledger.stats(true).kills(), "Silent feedback still records qualifying kill");
        eq(
                1,
                silent.ledger.stats(true).loot().get("TARANTULA_EPIC"),
                "Silent feedback still records rare loot");
    }

    private static void rngCache() throws Exception {
        Tracker tracker = tracker();
        var initial = tracker.rngSince(false);
        yes(initial == tracker.rngSince(false), "Unchanged scope reuses its RNG snapshot");
        long revision = tracker.ledger.revision();
        tracker.ledger.tick(1_000);
        eq(revision, tracker.ledger.revision(), "Active clock tick is not a journal mutation");
        var advanced = tracker.rngSince(false);
        yes(initial != advanced, "Clock-only update refreshes live elapsed time");
        eq(1_000, advanced.elapsed(), "Clock-only cache refresh advances scope time");
        eq(
                1_000,
                advanced.row(Reward.ANY_PET).activeMillisSinceDrop(),
                "No-drop cache row advances from scope origin");
        eq(
                0,
                advanced.row(Reward.ANY_PET).drops(),
                "Clock-only cache refresh does not create drops");
        yes(advanced == tracker.rngSince(false), "Same active clock reuses refreshed snapshot");
        tracker.record(Ledger.Kind.LOOT, "ARACHNE_FANG", 100, 500, "manual", BASE + 500);
        var manual = tracker.rngSince(false);
        yes(manual != advanced, "Journal revision invalidates the cached join");
        eq(
                0,
                manual.row(Reward.FANG).drops(),
                "Manual receipt remains excluded after cache invalidation");
        message(tracker, INTRO, 1_000);
        down(tracker, 2_000);
        tracker.observeLootStand(new UUID(130, 5), "Legendary Tarantula Pet", BASE + 2_100);
        var detected = tracker.rngSince(false);
        eq(1, detected.qualifiedKills(), "Detected kill refreshes cached qualifying count");
        eq(
                1,
                detected.row(Reward.ANY_PET).drops(),
                "Detected reward refreshes cached pet quantity");
        eq(
                100,
                detected.row(Reward.ANY_PET).dropsPer100Kills(),
                "Cached rate uses observed quantities and qualifying kills");
        yes(tracker.undo(), "Latest detected reward can be undone");
        var undone = tracker.rngSince(false);
        eq(0, undone.row(Reward.ANY_PET).drops(), "Undo invalidates cached quantity");
        eq(1, undone.row(Reward.ANY_PET).killsSinceDrop(), "Undo restores cached dry streak");
        eq(
                1,
                detected.row(Reward.ANY_PET).drops(),
                "Prior cached snapshot remains immutable after undo");
        tracker.newSession();
        var session = tracker.rngSince(false);
        eq(0, session.qualifiedKills(), "Session reset invalidates cached kill scope");
        eq(0, session.elapsed(), "Session reset updates clock origin");
        var lifetime = tracker.rngSince(true);
        eq(1, lifetime.qualifiedKills(), "Total scope retains earlier genuine kills");
        yes(lifetime == tracker.rngSince(true), "Unchanged total scope reuses snapshot");
        eq(
                0,
                tracker.rngSince(false).qualifiedKills(),
                "Changing scope cannot reuse total results for session");
        tracker.ledger = new Ledger();
        eq(
                0,
                tracker.rngSince(true).qualifiedKills(),
                "Ledger identity change invalidates historical cached join");
    }

    private static void staleFightRecoveryAndMetadata() throws Exception {
        Tracker tracker = tracker();
        yes(tracker.ledger.sanctuaryVisitedAt == BASE, "Recognized Sanctuary visit is saved once");
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE + 1);
        eq(BASE, tracker.ledger.sanctuaryVisitedAt, "Repeat location reads preserve first visit");
        message(tracker, CRYSTAL, 1_000);
        message(tracker, INTRO, 41_000);
        yes(
                tracker.ledger.fights.getFirst().summonTier == FightRecord.SummonTier.CRYSTAL,
                "Another player's completed ritual attributes Crystal tier without cost");
        message(tracker, CRYSTAL, 41_100);
        eq(1, tracker.ledger.fights.size(), "Fresh-spawn relay cannot interrupt the new fight");
        long revision = tracker.ledger.revision();
        message(tracker, CALLING, 50_000);
        yes(
                tracker.ledger.fights.getFirst().outcome == FightRecord.Outcome.INTERRUPTED,
                "A completed new ritual closes a fight whose death message was missed");
        yes(tracker.ledger.revision() > revision, "Stale recovery invalidates metadata snapshots");
        yes(
                tracker.ritualTimer(BASE + 50_000) != null,
                "Recovered ritual shows countdown immediately");
        eq(0, tracker.ledger.stats(true).kills(), "Recovered stale fight never becomes a kill");
        message(tracker, CALLING, 50_500);
        eq(
                18.5,
                tracker.ritualTimer(BASE + 50_500).seconds(),
                "Repeated recovery cue keeps origin");
        tracker.config.farming.pedestalTimer = false;
        yes(tracker.pedestalTimer(BASE + 50_500) == null, "World timer can be disabled separately");
        yes(
                tracker.ritualTimer(BASE + 50_500) != null,
                "HUD ritual remains available independently");
        message(tracker, "[BOSS] Arachne: A befitting welcome!", 69_000);
        yes(tracker.ritualTimer(BASE + 69_000) == null, "Server spawn clears HUD estimate too");
        yes(
                tracker.ledger.fights.getLast().summonTier == FightRecord.SummonTier.CALLING,
                "Recovered next fight receives the new observed tier");
        message(tracker, "ARACHNE DOWN!", 70_000);
        revision = tracker.ledger.revision();
        message(tracker, "Your Damage: 9,999 (Position #2)", 70_010);
        yes(
                tracker.ledger.revision() > revision,
                "Rejected damage still invalidates fight snapshots");
        yes(
                tracker.ledger.fights.getLast().outcome == FightRecord.Outcome.LOW_DAMAGE,
                "Low damage remains excluded after stale recovery");
        eq(0, tracker.ledger.stats(true).kills(), "Below ten thousand damage still is not counted");

        Tracker recovered = tracker();
        message(recovered, CRYSTAL, 1_000);
        message(recovered, "[BOSS] Arachne: My brood will feast on you!", 4_000);
        eq(
                1,
                recovered.ledger.fights.size(),
                "Generic boss activity recovers an unconfirmed fight");
        eq(
                0,
                recovered.ledger.fights.getFirst().spawned,
                "Recovered activity never invents spawn time");
        message(recovered, CALLING, 10_000);
        yes(
                recovered.ritualTimer(BASE + 10_000).detail().equals("Calling ritual"),
                "A new ritual replaces the leftover estimate of a recovered unconfirmed fight");

        Tracker lateRewards = tracker();
        message(lateRewards, INTRO, 1_000);
        down(lateRewards, 2_000);
        long previousFight = lateRewards.ledger.fights.getFirst().id;
        message(lateRewards, CRYSTAL, 2_100);
        lateRewards.observeLootStand(new UUID(140, 1), "Legendary Tarantula Pet", BASE + 2_200);
        eq(
                1,
                lateRewards.ledger.stats(true).loot().getOrDefault("TARANTULA_LEGENDARY", 0L),
                "Immediately starting the next ritual cannot hide previous death's delayed loot");
        yes(
                lateRewards.ledger.entries.getLast().fightId() == previousFight,
                "Previous death's delayed loot retains its actual fight across a new ritual");

        down(lateRewards, 2_300);
        eq(
                1,
                lateRewards.ledger.stats(true).kills(),
                "Old relayed results after the next ritual cannot immediately count again");
        down(lateRewards, 45_000);
        eq(
                2,
                lateRewards.ledger.stats(true).kills(),
                "Actual new death and qualified damage recover a missed welcome after fresh ritual");
        FightRecord missedWelcome = lateRewards.ledger.fights.getLast();
        eq(0, missedWelcome.spawned, "Death recovery does not invent a confirmed spawn timestamp");
        yes(
                missedWelcome.summonTier == FightRecord.SummonTier.CRYSTAL,
                "Death recovered from ritual retains its observed Crystal tier");
        yes(
                !lateRewards
                        .ledger
                        .summonStats(true)
                        .row(FightRecord.SummonTier.CRYSTAL)
                        .hasTiming(),
                "Recovered unknown spawn duration cannot create a tier hourly rate");
        down(lateRewards, 47_500);
        eq(
                2,
                lateRewards.ledger.stats(true).kills(),
                "Recovered death consumes ritual evidence and later relays stay ignored");

        Tracker missing = tracker();
        message(missing, INTRO, 1_000);
        message(missing, "ARACHNE DOWN!", 2_000);
        revision = missing.ledger.revision();
        missing.tick(BASE + 7_001, true);
        yes(missing.ledger.revision() > revision, "Missing summary timeout invalidates metadata");
        yes(
                missing.ledger.fights.getLast().outcome == FightRecord.Outcome.MISSING_DAMAGE,
                "Summary timeout remains explicit and uncounted");
        eq(0, missing.ledger.stats(true).kills(), "Missing summary cannot count a kill");
        var sessionRng = missing.rngSince(false);
        var totalRng = missing.rngSince(true);
        yes(
                sessionRng == missing.rngSince(false),
                "Switching RNG scope retains cached session join");
        yes(totalRng == missing.rngSince(true), "Switching RNG scope retains cached lifetime join");

        Tracker manual = new Tracker(Files.createTempDirectory("arachne-visit-"));
        manual.account("visit-test");
        manual.config.manualTracking = true;
        manual.updateLocation(true, true, false, "Spider's Den", "sidebar", BASE);
        eq(
                0,
                manual.ledger.sanctuaryVisitedAt,
                "Manual override alone cannot claim a Sanctuary visit");
        manual.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", BASE + 1);
        eq(
                BASE + 1,
                manual.ledger.sanctuaryVisitedAt,
                "Valid Sanctuary is recognized even when manual override is enabled");
    }

    private FarmingChecks() {}
}

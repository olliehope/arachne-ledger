package dev.arachneledger.tracking;

import static dev.arachneledger.diagnostics.TrackingDiagnostics.Kind.*;
import static dev.arachneledger.diagnostics.TrackingDiagnostics.Result.*;

import dev.arachneledger.achievement.Achievements;
import dev.arachneledger.config.Config;
import dev.arachneledger.config.HudPreferences;
import dev.arachneledger.config.Store;
import dev.arachneledger.diagnostics.DetectionSnapshot;
import dev.arachneledger.diagnostics.TrackingDiagnostics;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.LedgerCsv;
import dev.arachneledger.ledger.SessionSummary;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.skyblock.LootLabels;
import dev.arachneledger.skyblock.Messages;
import dev.arachneledger.skyblock.PetDrops;
import dev.arachneledger.skyblock.PurseCoins;
import dev.arachneledger.ui.RngAlerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates the client-thread tracking lifecycle. Observations pass eligibility and duplicate
 * checks before entering the ledger; displayed totals and fight reports come from that ledger.
 */
public final class Tracker {
    private static final Logger LOG = LoggerFactory.getLogger("ArachneLedger");

    public static final long AFK_GRACE_MILLIS = TrackingClock.AFK_GRACE_MILLIS;
    private static final long PICKUP_WINDOW_MILLIS = 300_000;
    private static final long REWARD_WINDOW_MILLIS = 45_000;
    private static final long AUTOSAVE_INTERVAL_MILLIS = 10_000;
    private static final long PLACEMENT_DUPLICATE_WINDOW_MILLIS = 2_000;
    private static final long PURSE_MENU_COOLDOWN_MILLIS = 2_000;
    private static final long SCAVENGER_POST_DEATH_WINDOW_MILLIS = 10_000;

    public Config config = new Config();
    public Ledger ledger = new Ledger();
    public String error = "";
    public final RngAlerts rng = new RngAlerts();
    public boolean inArena;
    public boolean inSkyblock;
    public String detectionReason = "Waiting for Hypixel SkyBlock";
    public String location = "";

    private final Path dataDirectory;
    private Path ledgerPath;
    private String accountId = "";
    private boolean storageBlocked;
    private boolean ledgerDirty;
    private boolean pausedLastTick;
    private long lastSaveAt;

    private final TrackingArea area = new TrackingArea();
    private long pickupWindowUntil;
    private long rewardWindowUntil;
    private long lastPlacementAt;
    private String lastPlacementMessage = "";

    private final FightReports reports = new FightReports();
    private final TrackingClock clock = new TrackingClock();
    public final TrackingDiagnostics diagnostics = new TrackingDiagnostics();
    private final FightLifecycle fights = new FightLifecycle(clock, reports, diagnostics);
    private final LootDeduplicator lootDeduplicator = new LootDeduplicator();
    private final PurseCoins purseCoins = new PurseCoins();
    private long achievementRevision = -1;
    private final Deque<Achievements.Unlock> achievementNotices = new ArrayDeque<>();
    private final Deque<SessionSummary.Snapshot> sessionNotices = new ArrayDeque<>();
    private long purseMenuCooldownUntil;
    private boolean purseMenuOpen;

    public record KillSummary(
            long fightMillis,
            long damage,
            double income,
            double costs,
            long unpriced,
            long killNumber,
            double scavengerCoins) {
        public KillSummary(
                long fightMillis,
                long damage,
                double income,
                double costs,
                long unpriced,
                long killNumber) {
            this(fightMillis, damage, income, costs, unpriced, killNumber, 0);
        }

        public double profit() {
            return income - costs;
        }
    }

    public Tracker(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        try {
            config =
                    Store.read(
                            dataDirectory.resolve("settings.json"),
                            Config.class,
                            Config::new,
                            Config::validate);
        } catch (IOException exception) {
            fail(exception);
        }
        pausedLastTick = config.paused;
    }

    private void fail(Exception exception) {
        error = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        diagnostics.record(
                System.currentTimeMillis(), STORAGE, IGNORED, "Persistence failed", error);
        storageBlocked = true;
        LOG.error("Arachne Ledger persistence error", exception);
    }

    public boolean ready() {
        return !storageBlocked && ledgerPath != null;
    }

    public void account(String accountId) {
        if (accountId.equals(this.accountId)) {
            return;
        }
        resetContext();
        save();
        this.accountId = accountId;
        loadLedger();
    }

    public void profile(String profileName) {
        if (!profileName.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Use 1-32 letters, digits, _ or -.");
        }
        if (storageBlocked) {
            throw new IllegalStateException(
                    "Storage error; check the log before switching profiles.");
        }
        resetContext();
        save();
        config.profile = profileName;
        saveConfig();
        loadLedger();
    }

    private void loadLedger() {
        if (storageBlocked || accountId.isEmpty()) {
            return;
        }
        ledgerPath = dataDirectory.resolve(accountId + "-" + config.profile + ".json");
        try {
            ledger = Store.read(ledgerPath, Ledger.class, Ledger::new, Ledger::validate);
        } catch (IOException exception) {
            fail(exception);
        }
        achievementRevision = -1;
        achievementNotices.clear();
        sessionNotices.clear();
        refreshAchievements(System.currentTimeMillis(), false);
        resetContext();
        if (ledger.closeOpenFights()) {
            ledgerDirty = true;
            save();
        }
    }

    /** A world/account change clears both temporary eligibility and observed entity identities. */
    public void resetContext() {
        diagnostics.record(
                System.currentTimeMillis(),
                LOCATION,
                INFO,
                "Context reset",
                "World or account context changed");
        clearFightContext();
        lastPlacementAt = 0;
        lastPlacementMessage = "";
        inArena = false;
        inSkyblock = false;
        lootDeduplicator.reset();
        area.reset();
        detectionReason = "Waiting for Hypixel SkyBlock";
        location = "";
        pausedLastTick = config.paused;
    }

    private void clearFightContext() {
        fights.interrupt(ledger);
        clock.reset();
        pickupWindowUntil = 0;
        rewardWindowUntil = 0;
        purseCoins.reset();
        purseMenuCooldownUntil = 0;
        purseMenuOpen = false;
        fights.clearRuntime();
        reports.clear();
        rng.clear();
    }

    public void updateLocation(
            boolean onHypixel,
            boolean skyBlock,
            boolean sanctuary,
            String location,
            String reason,
            long now) {
        if (this.inSkyblock != skyBlock || !this.location.equals(location)) {
            diagnostics.record(now, LOCATION, INFO, location, reason);
        }
        area.update(onHypixel, skyBlock, sanctuary, location, reason);
        this.location = area.location();
        refreshArea(now);
    }

    public boolean refreshArea(long now) {
        inSkyblock = area.skyBlock();
        boolean trackingAllowed = area.active(now, config.manualTracking);
        if (inArena && !trackingAllowed) {
            advanceClock(now, true);
            clearFightContext();
            lootDeduplicator.reset();
        }
        inArena = trackingAllowed;
        detectionReason = area.reason(now, config.manualTracking);
        return inArena;
    }

    public void tick(long now, boolean insideArena) {
        if (inArena && !insideArena) {
            clearFightContext();
        }
        inArena = insideArena;
        advanceClock(now, insideArena);
        handlePauseTransition();
        fights.expireDamageSummary(now);
        ledgerDirty |= fights.consumeChanges();
        flushReports(now);
        if (refreshAchievements(now, config.achievementNotifications)) save();
        savePeriodically(now);
    }

    private void handlePauseTransition() {
        // Interrupt once: repeatedly clearing would discard an explicit RNG preview while paused.
        if (config.paused && !pausedLastTick) {
            clearFightContext();
        }
        pausedLastTick = config.paused;
    }

    private void savePeriodically(long now) {
        if (now - lastSaveAt > AUTOSAVE_INTERVAL_MILLIS) {
            save();
            lastSaveAt = now;
        }
    }

    private void advanceClock(long now, boolean insideArena) {
        ledgerDirty |=
                clock.advance(
                        ledger,
                        ready(),
                        config.paused,
                        insideArena,
                        fights.activeFight != null,
                        now);
    }

    public boolean isSummoning() {
        return clock.summoning(inArena, config.paused, fights.activeFight != null);
    }

    public boolean isAfk() {
        return clock.afk(inArena, config.paused, fights.activeFight != null);
    }

    public boolean waitingForSpawn() {
        return clock.waiting(inArena, config.paused, fights.activeFight != null);
    }

    public String timerState() {
        if (config.paused) {
            return "Paused";
        }
        if (!inArena) {
            return "Waiting";
        }
        if (isSummoning()) {
            return "Summoning";
        }
        if (isAfk()) {
            return "AFK";
        }
        if (waitingForSpawn()) {
            return "Waiting for spawn";
        }
        return fights.activeFight != null ? "Fighting" : "Between fights";
    }

    public String status() {
        if (storageBlocked) {
            return "Storage error - see Minecraft log";
        }
        if (config.paused) {
            return "Paused";
        }
        if (!inArena) {
            return inSkyblock ? "Waiting for Arachne's Sanctuary" : "Waiting for Hypixel SkyBlock";
        }
        if (isSummoning()) {
            return "Arachne is awakening";
        }
        if (isAfk()) {
            return "AFK - waiting for Arachne to spawn";
        }
        if (waitingForSpawn()) {
            return "Waiting for Arachne to spawn";
        }
        return "Tracking Arachne";
    }

    /** Process server observations in order: eligibility, boss lifecycle, damage, then rewards. */
    public void message(String rawMessage, String playerName, long now) {
        String message = Messages.clean(rawMessage);
        boolean relevant = Messages.isArachneCue(message) || Messages.damage(message).isPresent();
        if (!ready() || config.paused) {
            if (relevant)
                diagnostics.record(
                        now,
                        SPAWN,
                        IGNORED,
                        message,
                        !ready() ? "Ledger not ready" : "Tracking paused");
            return;
        }
        observeLocationCue(message, now);
        if (!inArena) {
            if (relevant) diagnostics.record(now, SPAWN, IGNORED, message, detectionReason);
            return;
        }
        advanceClock(now, true);
        observeSummoningCue(message, now);

        Messages.Event event = Messages.parse(message, playerName);
        switch (event) {
            case SPAWN, ACTIVITY -> {
                fights.observeBossActivity(ledger, config, event, now);
                if (fights.activeFight != null) pickupWindowUntil = now + PICKUP_WINDOW_MILLIS;
            }
            case DOWN -> {
                if (fights.observeBossDeath(ledger, config, now)) {
                    pickupWindowUntil = now + REWARD_WINDOW_MILLIS;
                    rewardWindowUntil = now + REWARD_WINDOW_MILLIS;
                    lootDeduplicator.beginRewardWindow();
                }
            }
            default -> {
                // Summon costs and personal receipts are handled after damage qualification.
            }
        }
        fights.observeDamageSummary(ledger, message, now);
        ledgerDirty |= fights.consumeChanges();
        if ((event == Messages.Event.CRYSTAL || event == Messages.Event.CALLING)
                && !recordOwnPlacement(event, message, now)) {
            return;
        }
        observePetClaim(rawMessage, now);
    }

    private void observeLocationCue(String message, long now) {
        if (inSkyblock && Messages.isArachneCue(message)) {
            area.observeArachne(now);
            refreshArea(now);
        }
    }

    private void observeSummoningCue(String message, long now) {
        // Everyone's completed summon can awaken the boss; only own placements are charged.
        if (Messages.isSummoning(message) && fights.activeFight == null) {
            clock.summon(now);
            diagnostics.record(
                    now,
                    SUMMON,
                    INFO,
                    message,
                    "Awakening detected; only your own placements incur a cost");
        }
    }

    /** Return false for a duplicate relay of the same own-placement message. */
    private boolean recordOwnPlacement(Messages.Event event, String message, long now) {
        if (message.equals(lastPlacementMessage)
                && now - lastPlacementAt < PLACEMENT_DUPLICATE_WINDOW_MILLIS) {
            diagnostics.record(now, SUMMON, IGNORED, message, "Duplicate own-placement relay");
            return false;
        }
        lastPlacementAt = now;
        lastPlacementMessage = message;
        boolean crystal = event == Messages.Event.CRYSTAL;
        Ledger.Entry placement =
                record(
                        crystal ? Ledger.Kind.CRYSTAL : Ledger.Kind.CALLING,
                        crystal ? "ARACHNE_CRYSTAL" : "ARACHNE_KEEPER_FRAGMENT",
                        1,
                        crystal ? config.effectiveCrystalCost() : config.callingCost,
                        "server",
                        now);
        fights.pendingSummonEntryIds.add(placement.id());
        diagnostics.record(
                now,
                SUMMON,
                ACCEPTED,
                crystal ? "Your Crystal" : "Your Calling",
                "Own placement cost recorded");
        pickupWindowUntil = now + PICKUP_WINDOW_MILLIS;
        return true;
    }

    private void observePetClaim(String rawMessage, long now) {
        if (!acceptsStandLoot(now)) {
            return;
        }
        LootLabels.Drop drop = PetDrops.claim(rawMessage);
        if (drop == null || !lootDeduplicator.acceptClaim(drop.item(), drop.count(), now)) {
            return;
        }
        recordAcceptedLoot(drop.item(), drop.count(), "pet_claim", now, fights.rewardFight);
    }

    private void flushReports(long now) {
        reports.flush(ledger, config.killChat, now);
    }

    public List<KillSummary> drainKillSummaries() {
        return reports.drain();
    }

    /** Observe every tick, even when a short-lived menu falls between sidebar snapshots. */
    public void observeMenu(boolean menuOpen, long now) {
        // NPC proceeds can arrive after closing a menu. Reset immediately so a stalled client
        // also establishes a fresh purse baseline after the cooldown has elapsed.
        if (menuOpen || purseMenuOpen) {
            purseMenuCooldownUntil = now + PURSE_MENU_COOLDOWN_MILLIS;
            purseCoins.reset();
        }
        purseMenuOpen = menuOpen;
    }

    /** Observe even when ineligible so menus and idle purse gains cannot leak into a fight. */
    public void observePurse(List<String> sidebar, boolean menuOpen, long now) {
        observeMenu(menuOpen, now);
        double coins = purseCoins.observe(sidebar, scavengerEligible(menuOpen, now), now);
        if (coins <= 0) {
            return;
        }
        TrackedFight target = fights.activeFight != null ? fights.activeFight : fights.rewardFight;
        record(
                Ledger.Kind.INCOME,
                PurseCoins.ITEM,
                1,
                coins,
                "scoreboard",
                now,
                target == null ? 0 : target.history.id);
        noteReward(target, now);
        diagnostics.record(
                now, SCAVENGER, ACCEPTED, Double.toString(coins), "Eligible yellow purse gain");
    }

    private boolean scavengerEligible(boolean menuOpen, long now) {
        boolean recentDeath =
                fights.rewardFight != null
                        && now >= fights.rewardFight.deathAt
                        && now - fights.rewardFight.deathAt <= SCAVENGER_POST_DEATH_WINDOW_MILLIS;
        return ready()
                && config.scavengerCoins
                && !config.paused
                && inArena
                && !menuOpen
                && now >= purseMenuCooldownUntil
                && (fights.activeFight != null || recentDeath);
    }

    public boolean acceptsLoot(long now) {
        return ready()
                && !config.paused
                && inArena
                && now <= pickupWindowUntil
                && pickupWindowUntil > 0;
    }

    public boolean acceptsStandLoot(long now) {
        return acceptsLoot(now) && rewardWindowUntil > 0 && now <= rewardWindowUntil;
    }

    /** UUIDs make repeated scans and server metadata updates count a hologram only once. */
    public void observeLootStand(UUID standId, String label, long now) {
        if (!acceptsStandLoot(now)) {
            if (LootLabels.parse(label) != null)
                diagnostics.once(
                        "closed:" + standId,
                        now,
                        LOOT,
                        IGNORED,
                        label,
                        "Drop-label reward window closed");
            return;
        }
        if (lootDeduplicator.hasSeenStand(standId)) {
            diagnostics.once(
                    "duplicate:" + standId,
                    now,
                    LOOT,
                    IGNORED,
                    label,
                    "Name tag already recorded or reconciled");
            return;
        }
        LootLabels.Drop drop = LootLabels.parse(label);
        if (drop == null) {
            String candidate = Messages.clean(label).toLowerCase(java.util.Locale.ROOT);
            if (candidate.contains("arachne")
                    || candidate.contains("tarantula")
                    || candidate.contains("pet")) {
                diagnostics.once(
                        "unknown:" + standId,
                        now,
                        LOOT,
                        IGNORED,
                        label,
                        "Unrecognized reward label");
            }
            return;
        }
        long unseenQuantity =
                lootDeduplicator.acceptStandCount(standId, drop.item(), drop.count(), now);
        if (unseenQuantity == 0) {
            return;
        }
        recordAcceptedLoot(drop.item(), unseenQuantity, "armor_stand", now, fights.rewardFight);
    }

    public void pickup(String itemId, int quantity, long now) {
        if (!Catalog.ITEMS.containsKey(itemId) || quantity <= 0) return;
        if (!acceptsLoot(now)) {
            diagnostics.record(
                    now,
                    LOOT,
                    IGNORED,
                    itemId + " x" + quantity,
                    "Pickup window closed or tracking disabled");
            return;
        }
        boolean rewardWindowOpen = acceptsStandLoot(now);
        long unseenQuantity =
                lootDeduplicator.acceptPickupCount(itemId, quantity, now, rewardWindowOpen);
        if (unseenQuantity == 0) {
            return;
        }
        // A new spawn may precede the old rewards disappearing. Those receipts still belong
        // to the previous death; ordinary mid-fight pickups belong to the active fight.
        TrackedFight target = rewardWindowOpen ? fights.rewardFight : fights.activeFight;
        recordAcceptedLoot(itemId, unseenQuantity, "pickup", now, target);
    }

    /** All accepted automatic item paths share valuation, ownership and notification rules. */
    private void recordAcceptedLoot(
            String itemId, long quantity, String source, long now, TrackedFight target) {
        double unitPrice = config.lootPrice(itemId);
        record(
                Ledger.Kind.LOOT,
                itemId,
                quantity,
                unitPrice,
                source,
                now,
                target == null ? 0 : target.history.id);
        noteReward(target, now);
        diagnostics.record(
                now,
                LOOT,
                ACCEPTED,
                itemId + " x" + quantity,
                source + " / fight " + (target == null ? "unknown" : target.history.id));
        if (config.rngTitles) {
            rng.notice(itemId, (int) quantity, unitPrice, now);
        }
    }

    private static void noteReward(TrackedFight target, long now) {
        // Money comes from the journal; this timestamp only delays reports for late receipts.
        if (target != null) {
            target.lastRewardAt = now;
        }
    }

    public Ledger.Entry record(
            Ledger.Kind kind,
            String itemId,
            long quantity,
            double unitPrice,
            String source,
            long now) {
        return record(kind, itemId, quantity, unitPrice, source, now, 0);
    }

    private Ledger.Entry record(
            Ledger.Kind kind,
            String itemId,
            long quantity,
            double unitPrice,
            String source,
            long now,
            long fightId) {
        if (!ready()) {
            throw new IllegalStateException("Join a world first, or resolve the storage error.");
        }
        Ledger.Entry entry = ledger.add(kind, itemId, quantity, unitPrice, source, now, fightId);
        ledgerDirty = true;
        return entry;
    }

    public void newSession() {
        if (ready()) {
            clearFightContext();
            long previousSession = ledger.sessionId;
            ledger.newSession();
            if (config.sessionRecapChat
                    && !ledger.sessionRecaps.isEmpty()
                    && ledger.sessionRecaps.getLast().sessionId() == previousSession) {
                sessionNotices.addLast(ledger.sessionRecaps.getLast());
            }
            diagnostics.record(
                    System.currentTimeMillis(),
                    SESSION,
                    INFO,
                    "Session " + ledger.sessionId,
                    "Previous session recap saved when it had activity");
            lootDeduplicator.beginRewardWindow();
            ledgerDirty = true;
            save();
        }
    }

    public void editFightLoot(long fightId, String itemId, long quantity) {
        if (!ready()) {
            throw new IllegalStateException("No ledger loaded.");
        }
        ledger.setFightLootCount(
                fightId, itemId, quantity, config.lootPrice(itemId), System.currentTimeMillis());
        ledgerDirty = true;
        save();
    }

    public boolean undo() {
        boolean changed = ready() && ledger.undo();
        if (changed) {
            ledgerDirty = true;
            save();
        }
        return changed;
    }

    public void reprice(String itemId, double unitPrice) {
        if (ready()) {
            ledger.reprice(itemId, unitPrice);
            ledgerDirty = true;
            save();
        }
    }

    public void togglePause() {
        config.paused = !config.paused;
        clearFightContext();
        pausedLastTick = config.paused;
        saveConfig();
    }

    public void toggleScope() {
        config.total = !config.total;
        saveConfig();
    }

    public void cycleView() {
        // Cycling arrangement preserves custom rows and exclusions; presets reset them explicitly.
        if (config.hudView == Config.View.GRAPH) {
            config.hudView = Config.View.DETAILED;
            config.hudPreferences.layout = HudPreferences.Layout.MINIMAL;
        } else if (config.hudPreferences.layout == HudPreferences.Layout.MINIMAL) {
            config.hudPreferences.layout = HudPreferences.Layout.CLASSIC;
        } else if (config.hudPreferences.layout == HudPreferences.Layout.CLASSIC) {
            config.hudPreferences.layout = HudPreferences.Layout.SPLIT;
        } else {
            config.hudView = Config.View.GRAPH;
        }
        saveConfig();
    }

    public void saveConfig() {
        if (storageBlocked) {
            return;
        }
        try {
            config.validate();
            Store.write(dataDirectory.resolve("settings.json"), config);
        } catch (Exception exception) {
            fail(exception);
        }
    }

    public void save() {
        ledgerDirty |= fights.consumeChanges();
        if (ready())
            refreshAchievements(System.currentTimeMillis(), config.achievementNotifications);
        if (!ready() || !ledgerDirty) {
            return;
        }
        try {
            Store.write(ledgerPath, ledger);
            ledgerDirty = false;
        } catch (IOException exception) {
            fail(exception);
        }
    }

    /**
     * New definitions are silently backfilled by the achievement module; live unlocks stay local.
     */
    private boolean refreshAchievements(long now, boolean notify) {
        if (!ready() || achievementRevision == ledger.revision()) return false;
        var update = Achievements.evaluate(ledger, ledger.achievements, now, notify);
        achievementRevision = ledger.revision();
        if (update.changed()) ledgerDirty = true;
        achievementNotices.addAll(update.unlocks());
        return update.changed();
    }

    public List<Achievements.Unlock> drainAchievements() {
        if (!ready()) {
            achievementNotices.clear();
            return List.of();
        }
        var result = List.copyOf(achievementNotices);
        achievementNotices.clear();
        return result;
    }

    public List<SessionSummary.Snapshot> drainSessionRecaps() {
        if (!ready()) {
            sessionNotices.clear();
            return List.of();
        }
        var result = List.copyOf(sessionNotices);
        sessionNotices.clear();
        return result;
    }

    public DetectionSnapshot detectionSnapshot(long now) {
        TrackedFight fight =
                fights.activeFight != null
                        ? fights.activeFight
                        : fights.awaitingDamageFight != null
                                ? fights.awaitingDamageFight
                                : fights.rewardFight;
        return new DetectionSnapshot(
                status(),
                location,
                detectionReason,
                ready(),
                inSkyblock,
                inArena,
                config.paused,
                timerState(),
                fight == null ? 0 : fight.history.id,
                fight == null ? "None" : fight.history.outcome.name(),
                fight == null ? 0 : fight.history.damage,
                fight == null ? config.minimumDamage : fight.history.minimumDamage,
                acceptsLoot(now),
                acceptsStandLoot(now),
                Math.max(0, rewardWindowUntil - now));
    }

    public Path export() throws IOException {
        if (!ready()) {
            throw new IOException("No ledger loaded");
        }
        return LedgerCsv.write(
                ledger,
                config.total,
                config.profile,
                dataDirectory.resolve("exports"),
                System.currentTimeMillis());
    }
}

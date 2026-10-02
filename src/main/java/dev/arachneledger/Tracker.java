package dev.arachneledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates the client-thread tracking lifecycle. Observations pass eligibility and duplicate
 * checks before entering the ledger; displayed totals and fight reports come from that ledger.
 */
public final class Tracker {
    private static final Logger LOG = LoggerFactory.getLogger("ArachneLedger");

    public static final long AFK_GRACE_MILLIS = 60_000;
    private static final long SUMMONING_TIMEOUT_MILLIS = 60_000;
    private static final long PICKUP_WINDOW_MILLIS = 300_000;
    private static final long REWARD_WINDOW_MILLIS = 45_000;
    private static final long DAMAGE_SUMMARY_WINDOW_MILLIS = 5_000;
    private static final long MAX_TICK_GAP_MILLIS = 5_000;
    private static final long AUTOSAVE_INTERVAL_MILLIS = 10_000;
    private static final long PLACEMENT_DUPLICATE_WINDOW_MILLIS = 2_000;
    private static final long REPORT_DELAY_AFTER_DEATH_MILLIS = 3_000;
    private static final long REPORT_DELAY_AFTER_DAMAGE_MILLIS = 1_000;
    private static final long REPORT_REWARD_QUIET_MILLIS = 750;
    private static final long MAX_REPORT_DELAY_MILLIS = 10_000;
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
    private long lastTickAt;
    private long lastSaveAt;

    private final TrackingArea area = new TrackingArea();
    private long activeGraceUntil;
    private long summoningUntil;
    private long pickupWindowUntil;
    private long rewardWindowUntil;
    private long awaitingDamageSince;
    private long lastDeathAt;
    private long lastPlacementAt;
    private String lastPlacementMessage = "";

    // Summon entries retain stable ledger IDs until the next actual or recovered spawn.
    private final List<Long> pendingSummonEntryIds = new ArrayList<>();
    private TrackedFight activeFight;
    private TrackedFight rewardFight;
    private TrackedFight awaitingDamageFight;
    private final Deque<TrackedFight> pendingKillReports = new ArrayDeque<>();
    private final Deque<KillSummary> readyKillSummaries = new ArrayDeque<>();
    private final LootDeduplicator lootDeduplicator = new LootDeduplicator();
    private final PurseCoins purseCoins = new PurseCoins();
    private long purseMenuCooldownUntil;
    private boolean purseMenuOpen;

    /** Runtime receipt/report timing; persistent outcomes live in the referenced FightRecord. */
    private static final class TrackedFight {
        final FightRecord history;
        long deathAt;
        long damage;
        long lastRewardAt;
        long reportAfterAt;
        long sessionKillNumber;

        TrackedFight(FightRecord history) {
            this.history = history;
        }
    }

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
        error = exception.getMessage();
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
        resetContext();
        if (ledger.closeOpenFights()) {
            ledgerDirty = true;
            save();
        }
    }

    /** A world/account change clears both temporary eligibility and observed entity identities. */
    public void resetContext() {
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
        finishInterruptedFights();
        lastTickAt = 0;
        pickupWindowUntil = 0;
        rewardWindowUntil = 0;
        awaitingDamageSince = 0;
        lastDeathAt = 0;
        activeGraceUntil = 0;
        summoningUntil = 0;
        purseCoins.reset();
        purseMenuCooldownUntil = 0;
        purseMenuOpen = false;
        pendingSummonEntryIds.clear();
        activeFight = null;
        rewardFight = null;
        awaitingDamageFight = null;
        pendingKillReports.clear();
        readyKillSummaries.clear();
        rng.clear();
    }

    private void finishInterruptedFights() {
        if (activeFight != null) {
            activeFight.history.outcome = FightRecord.Outcome.INTERRUPTED;
            activeFight.history.activeEnd = ledger.activeMillis;
            ledgerDirty = true;
        }
        if (awaitingDamageFight != null
                && awaitingDamageFight.history.outcome == FightRecord.Outcome.WAITING_DAMAGE) {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.MISSING_DAMAGE;
            ledgerDirty = true;
        }
    }

    public void updateLocation(
            boolean onHypixel,
            boolean skyBlock,
            boolean sanctuary,
            String location,
            String reason,
            long now) {
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
        expireDamageSummary(now);
        flushReports(now);
        savePeriodically(now);
    }

    private void handlePauseTransition() {
        // Interrupt once: repeatedly clearing would discard an explicit RNG preview while paused.
        if (config.paused && !pausedLastTick) {
            clearFightContext();
        }
        pausedLastTick = config.paused;
    }

    private void expireDamageSummary(long now) {
        if (awaitingDamageFight != null
                && now - awaitingDamageSince > DAMAGE_SUMMARY_WINDOW_MILLIS) {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.MISSING_DAMAGE;
            awaitingDamageFight = null;
            awaitingDamageSince = 0;
            ledgerDirty = true;
        }
    }

    private void savePeriodically(long now) {
        if (now - lastSaveAt > AUTOSAVE_INTERVAL_MILLIS) {
            save();
            lastSaveAt = now;
        }
    }

    private void advanceClock(long now, boolean insideArena) {
        if (ready()
                && !config.paused
                && insideArena
                && lastTickAt > 0
                && now > lastTickAt
                && now - lastTickAt <= MAX_TICK_GAP_MILLIS) {
            long activeEnd = activeFight != null ? now : Math.min(now, activeGraceUntil);
            if (activeEnd > lastTickAt) {
                ledger.tick(activeEnd - lastTickAt);
                ledgerDirty = true;
            }
        }
        lastTickAt = insideArena && !config.paused ? now : 0;
    }

    public boolean isSummoning() {
        return inArena && !config.paused && activeFight == null && summoningUntil > lastTickAt;
    }

    public boolean isAfk() {
        return inArena
                && !config.paused
                && activeFight == null
                && !isSummoning()
                && activeGraceUntil > 0
                && lastTickAt >= activeGraceUntil;
    }

    public boolean waitingForSpawn() {
        return inArena
                && !config.paused
                && activeFight == null
                && !isSummoning()
                && activeGraceUntil == 0;
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
        return activeFight != null ? "Fighting" : "Between fights";
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
        if (!ready() || config.paused) {
            return;
        }
        String message = Messages.clean(rawMessage);
        observeLocationCue(message, now);
        if (!inArena) {
            return;
        }
        advanceClock(now, true);
        observeSummoningCue(message, now);

        Messages.Event event = Messages.parse(message, playerName);
        switch (event) {
            case SPAWN, ACTIVITY -> observeBossActivity(event, now);
            case DOWN -> observeBossDeath(now);
            default -> {
                // Summon costs and personal receipts are handled after damage qualification.
            }
        }
        observeDamageSummary(message, now);
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
        if (Messages.isSummoning(message) && activeFight == null) {
            summoningUntil = now + SUMMONING_TIMEOUT_MILLIS;
        }
    }

    private void observeBossActivity(Messages.Event event, long now) {
        boolean confirmedSpawn = event == Messages.Event.SPAWN;
        // Generic activity can recover a missed welcome. After a death it needs a fresh summon
        // cue, so old dialogue cannot restart the fight or extend the AFK grace.
        if (activeFight == null
                && (confirmedSpawn || activeGraceUntil == 0 || now < summoningUntil)) {
            activeFight = beginFight(confirmedSpawn ? now : 0);
            activeGraceUntil = 0;
            summoningUntil = 0;
        } else if (activeFight != null && confirmedSpawn) {
            // A Crystal ritual may emit activity before its welcome. Confirm the existing fight
            // rather than losing its spawn marker or creating a second fight.
            if (ledger.confirmFightSpawn(activeFight.history.id, now)) {
                ledgerDirty = true;
            }
        }
        if (activeFight != null) {
            pickupWindowUntil = now + PICKUP_WINDOW_MILLIS;
        }
    }

    private void observeBossDeath(long now) {
        // A relayed results block cannot become another death without an intervening fight.
        if (lastDeathAt != 0 && activeFight == null) {
            return;
        }
        lastDeathAt = now;
        summoningUntil = 0;
        if (awaitingDamageFight != null
                && awaitingDamageFight.history.outcome == FightRecord.Outcome.WAITING_DAMAGE) {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.MISSING_DAMAGE;
        }
        rewardFight = activeFight != null ? activeFight : beginFight(0);
        rewardFight.deathAt = now;
        awaitingDamageFight = rewardFight;
        activeFight = null;

        FightRecord history = rewardFight.history;
        history.died = now;
        history.activeEnd = ledger.activeMillis;
        history.minimumDamage = Math.max(1, config.minimumDamage);
        history.outcome = FightRecord.Outcome.WAITING_DAMAGE;
        ledgerDirty = true;

        activeGraceUntil = now + AFK_GRACE_MILLIS;
        awaitingDamageSince = now;
        pickupWindowUntil = now + REWARD_WINDOW_MILLIS;
        rewardWindowUntil = now + REWARD_WINDOW_MILLIS;
        lootDeduplicator.beginRewardWindow();
    }

    private void observeDamageSummary(String message, long now) {
        var damage = Messages.damage(message);
        if (awaitingDamageSince <= 0
                || now < awaitingDamageSince
                || now - awaitingDamageSince > DAMAGE_SUMMARY_WINDOW_MILLIS
                || damage.isEmpty()) {
            return;
        }
        long dealtDamage = damage.getAsLong();
        awaitingDamageFight.damage = dealtDamage;
        awaitingDamageFight.history.damage = dealtDamage;
        ledgerDirty = true;
        if (dealtDamage >= awaitingDamageFight.history.minimumDamage) {
            countQualifiedKill(awaitingDamageFight, now);
        } else if (dealtDamage == 0) {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.ZERO_DAMAGE;
        } else {
            awaitingDamageFight.history.outcome = FightRecord.Outcome.LOW_DAMAGE;
        }
        awaitingDamageSince = 0;
        awaitingDamageFight = null;
    }

    private void countQualifiedKill(TrackedFight completed, long now) {
        completed.history.outcome = FightRecord.Outcome.COUNTED;
        record(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", now, completed.history.id);
        completed.sessionKillNumber = ledger.stats(false).kills();
        completed.reportAfterAt =
                Math.max(
                        completed.deathAt + REPORT_DELAY_AFTER_DEATH_MILLIS,
                        now + REPORT_DELAY_AFTER_DAMAGE_MILLIS);
        pendingKillReports.addLast(completed);
    }

    /** Return false for a duplicate relay of the same own-placement message. */
    private boolean recordOwnPlacement(Messages.Event event, String message, long now) {
        if (message.equals(lastPlacementMessage)
                && now - lastPlacementAt < PLACEMENT_DUPLICATE_WINDOW_MILLIS) {
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
        pendingSummonEntryIds.add(placement.id());
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
        recordAcceptedLoot(drop.item(), drop.count(), "pet_claim", now, rewardFight);
    }

    private TrackedFight beginFight(long spawnedAt) {
        FightRecord history = ledger.beginFight(spawnedAt, Math.max(1, config.minimumDamage));
        ledger.associateSummons(pendingSummonEntryIds, history.id);
        pendingSummonEntryIds.clear();
        ledgerDirty = true;
        return new TrackedFight(history);
    }

    private void flushReports(long now) {
        for (Iterator<TrackedFight> iterator = pendingKillReports.iterator();
                iterator.hasNext(); ) {
            TrackedFight completed = iterator.next();
            if (!reportReady(completed, now)) {
                continue;
            }
            queueKillSummary(completed);
            iterator.remove();
        }
    }

    private static boolean reportReady(TrackedFight completed, long now) {
        boolean rewardsQuiet = now - completed.lastRewardAt >= REPORT_REWARD_QUIET_MILLIS;
        boolean maximumWaitReached = now - completed.deathAt >= MAX_REPORT_DELAY_MILLIS;
        return now >= completed.reportAfterAt && (rewardsQuiet || maximumWaitReached);
    }

    private void queueKillSummary(TrackedFight completed) {
        Ledger.Stats stats = ledger.fightStats(completed.history.id);
        if (config.killChat && stats.kills() > 0) {
            readyKillSummaries.addLast(
                    new KillSummary(
                            completed.history.duration(),
                            completed.damage,
                            stats.revenue(),
                            stats.costs(),
                            stats.unpriced(),
                            completed.sessionKillNumber,
                            ledger.fightScavengerCoins(completed.history.id)));
        }
    }

    public List<KillSummary> drainKillSummaries() {
        List<KillSummary> summaries = List.copyOf(readyKillSummaries);
        readyKillSummaries.clear();
        return summaries;
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
        TrackedFight target = activeFight != null ? activeFight : rewardFight;
        record(
                Ledger.Kind.INCOME,
                PurseCoins.ITEM,
                1,
                coins,
                "scoreboard",
                now,
                target == null ? 0 : target.history.id);
        noteReward(target, now);
    }

    private boolean scavengerEligible(boolean menuOpen, long now) {
        boolean recentDeath =
                rewardFight != null
                        && now >= rewardFight.deathAt
                        && now - rewardFight.deathAt <= SCAVENGER_POST_DEATH_WINDOW_MILLIS;
        return ready()
                && config.scavengerCoins
                && !config.paused
                && inArena
                && !menuOpen
                && now >= purseMenuCooldownUntil
                && (activeFight != null || recentDeath);
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
        if (!acceptsStandLoot(now) || lootDeduplicator.hasSeenStand(standId)) {
            return;
        }
        LootLabels.Drop drop = LootLabels.parse(label);
        if (drop == null) {
            return;
        }
        long unseenQuantity =
                lootDeduplicator.acceptStandCount(standId, drop.item(), drop.count(), now);
        if (unseenQuantity == 0) {
            return;
        }
        recordAcceptedLoot(drop.item(), unseenQuantity, "armor_stand", now, rewardFight);
    }

    public void pickup(String itemId, int quantity, long now) {
        if (!acceptsLoot(now) || !Catalog.ITEMS.containsKey(itemId) || quantity <= 0) {
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
        TrackedFight target = rewardWindowOpen ? rewardFight : activeFight;
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
            ledger.newSession();
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
        config.hudView = Config.View.values()[(config.hudView.ordinal() + 1) % 3];
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

    public Path export() throws IOException {
        if (!ready()) {
            throw new IOException("No ledger loaded");
        }
        Path exportDirectory = dataDirectory.resolve("exports");
        Files.createDirectories(exportDirectory);
        Path file =
                exportDirectory.resolve(
                        "arachne-" + config.profile + "-" + System.currentTimeMillis() + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("time_utc,active_ms,kind,item,quantity,unit_coins,income,cost,source\n");
            int firstEntry = config.total ? 0 : ledger.sessionStart;
            for (int index = firstEntry; index < ledger.entries.size(); index++) {
                writer.write(csvRow(ledger.entries.get(index)));
            }
        }
        return file;
    }

    private static String csvRow(Ledger.Entry entry) {
        return String.join(
                        ",",
                        Instant.ofEpochMilli(entry.at()).toString(),
                        Long.toString(entry.elapsed()),
                        entry.kind().toString(),
                        quoteCsv(entry.item()),
                        Long.toString(entry.count()),
                        Double.toString(entry.unit()),
                        Double.toString(entry.income()),
                        Double.toString(entry.cost()),
                        quoteCsv(entry.source()))
                + "\n";
    }

    private static String quoteCsv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

package dev.arachneledger;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Tracker {
    private static final Logger LOG = LoggerFactory.getLogger("ArachneLedger");
    public Config config = new Config();
    public Ledger ledger = new Ledger();
    private final Path root;
    private Path ledgerFile;
    public String error = "";
    private boolean blocked, dirty, wasPaused;
    private String owner = "";
    private long lastTick, lastSave, lootUntil, pendingDown, lastDown, lastPlacement;
    private long standLootUntil;
    public static final long AFK_GRACE_MILLIS = 60_000;
    private long activeUntil, summoningUntil;
    private final PurseCoins purseCoins = new PurseCoins();
    private long purseMenuUntil;
    private boolean purseMenuOpen;
    private final List<Long> pendingSummons = new ArrayList<>();
    private Fight fight, rewardFight, pendingFight;
    private final Deque<Fight> pendingReports = new ArrayDeque<>();
    private final Deque<KillSummary> reports = new ArrayDeque<>();
    public final RngAlerts rng = new RngAlerts();
    private static final class Fight {
        final FightRecord history;
        long died, damage, lastLoot, reportAfter, killNumber;
        Fight(FightRecord history) { this.history=history; }
    }
    public record KillSummary(long fightMillis, long damage, double income, double costs,
                              long unpriced, long killNumber, double scavengerCoins) {
        public KillSummary(long fightMillis, long damage, double income, double costs, long unpriced, long killNumber) {
            this(fightMillis, damage, income, costs, unpriced, killNumber, 0);
        }
        public double profit() { return income - costs; }
    }
    private String lastPlacementMessage = "";
    private final LootDeduplicator lootDeduplicator = new LootDeduplicator();
    public boolean inArena;
    public boolean inSkyblock;
    public String detectionReason = "Waiting for Hypixel SkyBlock";
    public String location = "";
    private final TrackingArea area = new TrackingArea();
    public Tracker(Path root) {
        this.root = root;
        try { config = Store.read(root.resolve("settings.json"), Config.class, Config::new, Config::validate); }
        catch (IOException ex) { fail(ex); }
        wasPaused = config.paused;
    }
    private void fail(Exception ex) { error = ex.getMessage(); blocked = true; LOG.error("Arachne Ledger persistence error", ex); }
    public boolean ready() { return !blocked && ledgerFile != null; }
    public void account(String uuid) {
        if (uuid.equals(owner)) return;
        resetContext();save(); owner = uuid; loadLedger();
    }
    public void profile(String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Use 1-32 letters, digits, _ or -.");
        if (blocked) throw new IllegalStateException("Storage error; check the log before switching profiles.");
        resetContext();save(); config.profile = name; saveConfig(); loadLedger();
    }
    private void loadLedger() {
        if (blocked || owner.isEmpty()) return;
        ledgerFile = root.resolve(owner + "-" + config.profile + ".json");
        try { ledger = Store.read(ledgerFile, Ledger.class, Ledger::new, Ledger::validate); }
        catch (IOException ex) { fail(ex); }
        resetContext();
        if(ledger.closeOpenFights()){dirty=true;save();}
    }
    public void resetContext() {
        clearFightContext(); lastPlacement = 0;
        lastPlacementMessage = ""; inArena = false; inSkyblock = false;
        lootDeduplicator.reset();
        area.reset(); detectionReason = "Waiting for Hypixel SkyBlock"; location = "";
        wasPaused = config.paused;
    }
    private void clearFightContext() {
        if(fight!=null){fight.history.outcome=FightRecord.Outcome.INTERRUPTED;fight.history.activeEnd=ledger.activeMillis;dirty=true;}
        if(pendingFight!=null && pendingFight.history.outcome==FightRecord.Outcome.WAITING_DAMAGE){pendingFight.history.outcome=FightRecord.Outcome.MISSING_DAMAGE;dirty=true;}
        lastTick = 0; lootUntil = 0; standLootUntil = 0; pendingDown = 0; lastDown = 0; activeUntil = 0; summoningUntil = 0;
        purseCoins.reset();
        purseMenuUntil = 0; purseMenuOpen = false;
        pendingSummons.clear(); fight = null; rewardFight = null; pendingFight = null;
        pendingReports.clear(); reports.clear();
        rng.clear();
    }
    public void updateLocation(boolean onHypixel, boolean skyBlock, boolean sanctuary, String location, String reason, long now) {
        area.update(onHypixel, skyBlock, sanctuary, location, reason);
        this.location = area.location();
        refreshArea(now);
    }
    public boolean refreshArea(long now) {
        inSkyblock = area.skyBlock();
        boolean active = area.active(now, config.manualTracking);
        if (inArena && !active) {
            advanceClock(now, true);
            clearFightContext();
            lootDeduplicator.reset();
        }
        inArena = active;
        detectionReason = area.reason(now, config.manualTracking);
        return inArena;
    }
    public void tick(long now, boolean arena) {
        if (inArena && !arena) clearFightContext();
        inArena = arena;
        advanceClock(now, arena);
        // Interrupt once at the pause transition. Re-clearing every frame discards an
        // explicit /arachne rng test preview even though no tracking context was revived.
        if (config.paused && !wasPaused) clearFightContext();
        wasPaused = config.paused;
        if(pendingFight!=null && now-pendingDown>5000){pendingFight.history.outcome=FightRecord.Outcome.MISSING_DAMAGE;pendingFight=null;pendingDown=0;dirty=true;}
        flushReports(now);
        if (now - lastSave > 10_000) { save(); lastSave = now; }
    }
    private void advanceClock(long now, boolean arena) {
        if (ready() && !config.paused && arena && lastTick > 0 && now > lastTick && now-lastTick <= 5000) {
            long end = fight != null ? now : Math.min(now, activeUntil);
            if (end > lastTick) { ledger.tick(end-lastTick); dirty = true; }
        }
        lastTick = arena && !config.paused ? now : 0;
    }
    public boolean isSummoning() { return inArena && !config.paused && fight == null && summoningUntil > lastTick; }
    public boolean isAfk() { return inArena && !config.paused && fight == null && !isSummoning() && activeUntil > 0 && lastTick >= activeUntil; }
    public boolean waitingForSpawn() { return inArena && !config.paused && fight == null && !isSummoning() && activeUntil == 0; }
    public String timerState() {
        if (config.paused) return "Paused";
        if (!inArena) return "Waiting";
        if (isSummoning()) return "Summoning";
        if (isAfk()) return "AFK";
        if (waitingForSpawn()) return "Waiting for spawn";
        return fight != null ? "Fighting" : "Between fights";
    }
    public String status() {
        if (blocked) return "Storage error - see Minecraft log";
        if (config.paused) return "Paused";
        if (inArena) return isSummoning() ? "Arachne is awakening" : isAfk() ? "AFK - waiting for Arachne to spawn"
            : waitingForSpawn() ? "Waiting for Arachne to spawn" : "Tracking Arachne";
        return inSkyblock ? "Waiting for Arachne's Sanctuary" : "Waiting for Hypixel SkyBlock";
    }
    public void message(String raw, String player, long now) {
        if (!ready() || config.paused) return;
        String clean = Messages.clean(raw);
        if (inSkyblock && Messages.isArachneCue(clean)) { area.observeArachne(now); refreshArea(now); }
        if (!inArena) return;
        advanceClock(now, true);
        // Any player's completed summon can awaken the next boss. Partial Callings cannot.
        if (Messages.isSummoning(clean) && fight == null) summoningUntil = now + 60_000;
        Messages.Event event = Messages.parse(clean, player);
        if (event == Messages.Event.SPAWN || event == Messages.Event.ACTIVITY) {
            // Activity can recover a missed spawn when joining mid-fight. Its duration is unknown.
            // Later boss dialogue cannot restart a completed fight or extend its AFK grace.
            if (fight == null && (event == Messages.Event.SPAWN || activeUntil == 0 || now < summoningUntil)) {
                fight = beginFight(event == Messages.Event.SPAWN ? now : 0);
                activeUntil = 0; summoningUntil = 0;
            } else if (fight != null && event == Messages.Event.SPAWN) {
                // Crystal rituals may emit generic dialogue before the actual welcome. That
                // opens an unknown-time fight; the welcome must still confirm its spawn.
                if(ledger.confirmFightSpawn(fight.history.id,now))dirty=true;
            }
            if (fight != null) lootUntil = now + 300_000;
        }
        // A repeated results block cannot become another kill or extend AFK without a new spawn.
        if (event == Messages.Event.DOWN && (lastDown == 0 || fight != null)) {
            lastDown = now;
            summoningUntil = 0;
            if(pendingFight!=null && pendingFight.history.outcome==FightRecord.Outcome.WAITING_DAMAGE)pendingFight.history.outcome=FightRecord.Outcome.MISSING_DAMAGE;
            rewardFight = fight != null ? fight : beginFight(0);
            rewardFight.died = now; pendingFight = rewardFight; fight = null;
            rewardFight.history.died=now;rewardFight.history.activeEnd=ledger.activeMillis;
            rewardFight.history.minimumDamage=Math.max(1,config.minimumDamage);rewardFight.history.outcome=FightRecord.Outcome.WAITING_DAMAGE;dirty=true;
            activeUntil = now + AFK_GRACE_MILLIS;
            pendingDown = now; lootUntil = now + 45_000; standLootUntil = now + 45_000;
            lootDeduplicator.beginRewardWindow();
        }
        var damage = Messages.damage(clean);
        if (pendingDown > 0 && now >= pendingDown && now-pendingDown <= 5000 && damage.isPresent()) {
            pendingFight.damage = damage.getAsLong();
            pendingFight.history.damage=damage.getAsLong();dirty=true;
            if (damage.getAsLong() >= pendingFight.history.minimumDamage) {
                pendingFight.history.outcome=FightRecord.Outcome.COUNTED;
                record(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", now,pendingFight.history.id);
                pendingFight.killNumber = ledger.stats(false).kills();
                pendingFight.reportAfter = Math.max(pendingFight.died + 3000, now + 1000);
                pendingReports.addLast(pendingFight);
            }else pendingFight.history.outcome=damage.getAsLong()==0?FightRecord.Outcome.ZERO_DAMAGE:FightRecord.Outcome.LOW_DAMAGE;
            pendingDown = 0; pendingFight = null;
        }
        if (event == Messages.Event.CRYSTAL || event == Messages.Event.CALLING) {
            // Some mods relay the same system message twice. Distinct placements remain separate.
            if (clean.equals(lastPlacementMessage) && now - lastPlacement < 2000) return;
            lastPlacement = now; lastPlacementMessage = clean;
            boolean crystal = event == Messages.Event.CRYSTAL;
            Ledger.Entry placement=record(crystal ? Ledger.Kind.CRYSTAL : Ledger.Kind.CALLING,
                crystal ? "ARACHNE_CRYSTAL" : "ARACHNE_KEEPER_FRAGMENT", 1,
                crystal ? config.effectiveCrystalCost() : config.callingCost, "server", now);
            pendingSummons.add(placement.id());
            lootUntil = now + 300_000;
        }
        observePetClaim(raw, now);
    }

    private void observePetClaim(String raw, long now) {
        if (!acceptsStandLoot(now)) return;
        LootLabels.Drop drop = PetDrops.claim(raw);
        if (drop == null || !lootDeduplicator.acceptClaim(drop.item(), drop.count(), now)) return;
        double price = config.lootPrice(drop.item());
        record(Ledger.Kind.LOOT, drop.item(), drop.count(), price, "pet_claim", now,
            rewardFight == null ? 0 : rewardFight.history.id);
        noteReward(rewardFight, now);
        if (config.rngTitles) rng.notice(drop.item(), drop.count(), price, now);
    }
    private Fight beginFight(long spawned) {
        FightRecord history=ledger.beginFight(spawned,Math.max(1,config.minimumDamage));
        ledger.associateSummons(pendingSummons,history.id);pendingSummons.clear();dirty=true;
        return new Fight(history);
    }
    private void flushReports(long now) {
        for (Iterator<Fight> it = pendingReports.iterator(); it.hasNext();) {
            Fight completed = it.next();
            if (now >= completed.reportAfter && (now-completed.lastLoot >= 750 || now-completed.died >= 10_000)) {
                var stats=ledger.fightStats(completed.history.id);
                if (config.killChat && stats.kills()>0) reports.addLast(new KillSummary(completed.history.duration(),
                    completed.damage, stats.revenue(), stats.costs(), stats.unpriced(), completed.killNumber,
                    ledger.fightScavengerCoins(completed.history.id)));
                it.remove();
            }
        }
    }
    public List<KillSummary> drainKillSummaries() {
        List<KillSummary> result = List.copyOf(reports); reports.clear(); return result;
    }
    /** Observe every tick, even when a short-lived menu falls between sidebar snapshots. */
    public void observeMenu(boolean menuOpen, long now) {
        // Server purse updates can arrive after an NPC menu closes. Clearing the pairing
        // baseline immediately also protects a client stall that outlasts this cooldown.
        if (menuOpen || purseMenuOpen) {
            purseMenuUntil = now + 2_000;
            purseCoins.reset();
        }
        purseMenuOpen = menuOpen;
    }
    /** Observe even when ineligible so menus and idle purse gains cannot leak into a fight. */
    public void observePurse(List<String> sidebar, boolean menuOpen, long now) {
        observeMenu(menuOpen, now);
        boolean eligible = ready() && config.scavengerCoins && !config.paused && inArena && !menuOpen && now >= purseMenuUntil
            && (fight != null || (rewardFight != null && now >= rewardFight.died && now - rewardFight.died <= 10_000));
        double coins = purseCoins.observe(sidebar, eligible, now);
        if (coins <= 0) return;
        Fight target = fight != null ? fight : rewardFight;
        record(Ledger.Kind.INCOME, PurseCoins.ITEM, 1, coins, "scoreboard", now,
            target == null ? 0 : target.history.id);
        noteReward(target, now);
    }
    public boolean acceptsLoot(long now) { return ready() && !config.paused && inArena && now <= lootUntil && lootUntil > 0; }
    public boolean acceptsStandLoot(long now) { return acceptsLoot(now) && standLootUntil > 0 && now <= standLootUntil; }
    /** UUIDs make repeated scans and server metadata updates count a hologram only once. */
    public void observeLootStand(UUID uuid, String name, long now) {
        if (!acceptsStandLoot(now) || lootDeduplicator.hasSeenStand(uuid)) return;
        LootLabels.Drop drop = LootLabels.parse(name);
        if (drop == null) return;
        long count = lootDeduplicator.acceptStandCount(uuid, drop.item(), drop.count(), now);
        if (count == 0) return;
        double price = config.lootPrice(drop.item());
        record(Ledger.Kind.LOOT, drop.item(), count, price, "armor_stand", now,
            rewardFight == null ? 0 : rewardFight.history.id);
        noteReward(rewardFight, now);
        if (config.rngTitles) rng.notice(drop.item(), (int)count, price, now);
    }
    public void pickup(String item, int count, long now) {
        if (!acceptsLoot(now) || !Catalog.ITEMS.containsKey(item) || count <= 0) return;
        boolean standWindow = acceptsStandLoot(now);
        count = (int)lootDeduplicator.acceptPickupCount(item, count, now, standWindow);
        if (count == 0) return;
        Fight target = standWindow ? rewardFight : fight;
        double price = config.lootPrice(item);
        record(Ledger.Kind.LOOT, item, count, price, "pickup", now, target == null ? 0 : target.history.id);
        noteReward(target, now);
        if (config.rngTitles) rng.notice(item, count, price, now);
    }
    private static void noteReward(Fight target, long now) {
        // Monetary values come from the journal. Runtime state only delays the report for late rewards.
        if (target != null) target.lastLoot = now;
    }
    public Ledger.Entry record(Ledger.Kind kind, String item, long count, double unit, String source, long now) {
        return record(kind,item,count,unit,source,now,0);
    }
    private Ledger.Entry record(Ledger.Kind kind, String item, long count, double unit, String source, long now,long fightId) {
        if (!ready()) throw new IllegalStateException("Join a world first, or resolve the storage error.");
        Ledger.Entry result=ledger.add(kind, item, count, unit, source, now,fightId); dirty = true;return result;
    }
    public void newSession() { if (ready()) { clearFightContext();ledger.newSession(); lootDeduplicator.beginRewardWindow(); dirty = true; save(); } }
    public void editFightLoot(long fightId,String item,long count) {
        if(!ready())throw new IllegalStateException("No ledger loaded.");
        ledger.setFightLootCount(fightId,item,count,config.lootPrice(item),System.currentTimeMillis());dirty=true;save();
    }
    public boolean undo() { boolean changed = ready() && ledger.undo(); if (changed) { dirty = true; save(); } return changed; }
    public void reprice(String id, double amount) { if (ready()) { ledger.reprice(id, amount); dirty = true; save(); } }
    public void togglePause() { config.paused = !config.paused; clearFightContext(); wasPaused = config.paused; saveConfig(); }
    public void toggleScope() { config.total = !config.total; saveConfig(); }
    public void cycleView() { config.hudView = Config.View.values()[(config.hudView.ordinal()+1)%3]; saveConfig(); }
    public void saveConfig() {
        if (blocked) return;
        try { config.validate(); Store.write(root.resolve("settings.json"), config); }
        catch (Exception ex) { fail(ex); }
    }
    public void save() {
        if (!ready() || !dirty) return;
        try { Store.write(ledgerFile, ledger); dirty = false; }
        catch (IOException ex) { fail(ex); }
    }
    public Path export() throws IOException {
        if (!ready()) throw new IOException("No ledger loaded");
        Path dir = root.resolve("exports"); Files.createDirectories(dir);
        Path file = dir.resolve("arachne-" + config.profile + "-" + System.currentTimeMillis() + ".csv");
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("time_utc,active_ms,kind,item,quantity,unit_coins,income,cost,source\n");
            int start = config.total ? 0 : ledger.sessionStart;
            for (int i=start;i<ledger.entries.size();i++) {
                var e = ledger.entries.get(i);
                w.write(java.time.Instant.ofEpochMilli(e.at()) + "," + e.elapsed() + "," + e.kind() + "," + csv(e.item())
                    + "," + e.count() + "," + e.unit() + "," + e.income() + "," + e.cost() + "," + csv(e.source()) + "\n");
            }
        }
        return file;
    }
    private static String csv(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
}

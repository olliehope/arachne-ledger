package dev.arachneledger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the journal and its session/fight identities. Financial results always come from recorded
 * entries; changing current prices never rewrites history unless an explicit edit requests it.
 * Accounting is independent of Minecraft so it can be verified without a game.
 */
public final class Ledger {
    private static final long MAX_ENTRY_COUNT = 1_000_000_000L;
    private static final long MAX_ACTIVE_TICK_MILLIS = 5_000;

    public enum Kind {
        LOOT,
        CRYSTAL,
        CALLING,
        KILL,
        INCOME,
        EXPENSE
    }

    /** Immutable receipt: association and repricing preserve its stable identity. */
    public record Entry(
            long at,
            long elapsed,
            Kind kind,
            String item,
            long count,
            double unit,
            String source,
            long fightId,
            long id) {
        public Entry(
                long at,
                long elapsed,
                Kind kind,
                String item,
                long count,
                double unit,
                String source) {
            this(at, elapsed, kind, item, count, unit, source, 0, 0);
        }

        double income() {
            return kind == Kind.LOOT || kind == Kind.INCOME ? count * unit : 0;
        }

        double cost() {
            return kind == Kind.CRYSTAL || kind == Kind.CALLING || kind == Kind.EXPENSE
                    ? count * unit
                    : 0;
        }
    }

    public record Point(long elapsed, double profit) {}

    public record Stats(
            double revenue,
            double costs,
            long crystals,
            long callings,
            long kills,
            long elapsed,
            Map<String, Long> loot,
            List<Point> graph,
            long unpriced) {
        public double profit() {
            return revenue - costs;
        }

        public double hourly() {
            return elapsed <= 0 ? 0 : profit() * 3_600_000.0 / elapsed;
        }
    }

    public int schema = 1;
    public long activeMillis = 0;
    public long sessionMillis = 0;

    /** Index of the first current-session entry; historical edits must preserve this boundary. */
    public int sessionStart = 0;

    public List<Entry> entries = new ArrayList<>();
    public List<FightRecord> fights = new ArrayList<>();
    public long sessionId = 1;
    public long nextFightId = 1;
    public long nextEntryId = 1;
    private transient long revision = 0;
    private transient long cachedStatsRevision = -1;
    private transient boolean cachedStatsTotalScope;
    private transient Stats cachedStats;
    private transient double cachedScavengerCoins;
    private transient Analytics.Spending cachedSpending;
    private transient Analytics.Snapshot cachedAnalytics;
    private transient long cachedAnalyticsRevision = -1;
    private transient long cachedAnalyticsActiveMillis = -1;
    private transient boolean cachedAnalyticsTotalScope;

    public long revision() {
        return revision;
    }

    public void tick(long delta) {
        // A stalled or suspended client must not add offline hours.
        if (delta > 0 && delta <= MAX_ACTIVE_TICK_MILLIS) {
            activeMillis += delta;
        }
    }

    public Entry add(Kind kind, String item, long count, double unit, String source, long now) {
        return add(kind, item, count, unit, source, now, 0);
    }

    public Entry add(
            Kind kind,
            String item,
            long count,
            double unit,
            String source,
            long now,
            long fightId) {
        if (kind == null
                || item == null
                || source == null
                || count < 1
                || count > MAX_ENTRY_COUNT) {
            throw new IllegalArgumentException("Invalid ledger entry");
        }
        Config.validateRecordedPrice(unit);
        Entry entry =
                new Entry(
                        now, activeMillis, kind, item, count, unit, source, fightId, nextEntryId++);
        entries.add(entry);
        revision++;
        return entry;
    }

    public void newSession() {
        sessionStart = entries.size();
        sessionMillis = activeMillis;
        sessionId++;
        revision++;
    }

    public FightRecord beginFight(long spawned, long minimumDamage) {
        FightRecord record = new FightRecord();
        record.id = nextFightId++;
        record.session = sessionId;
        record.spawned = spawned;
        record.minimumDamage = minimumDamage;
        record.activeStart = activeMillis;
        record.activeEnd = activeMillis;
        if (spawned > 0) {
            record.spawnActiveMillis = activeMillis;
        }
        fights.add(record);
        revision++;
        return record;
    }

    /**
     * A later welcome fills an unknown spawn without adding another fight or rewriting its journal.
     */
    public boolean confirmFightSpawn(long id, long spawned) {
        if (spawned <= 0) {
            throw new IllegalArgumentException("A confirmed spawn needs a timestamp.");
        }
        FightRecord record = fight(id);
        if (record.spawned != 0
                || record.died != 0
                || record.outcome != FightRecord.Outcome.FIGHTING) {
            return false;
        }
        record.spawned = spawned;
        record.spawnActiveMillis = activeMillis;
        revision++;
        return true;
    }

    public void associateSummons(Collection<Long> ids, long fightId) {
        Set<Long> selectedEntryIds = new HashSet<>(ids);
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            Entry entry = entries.get(entryIndex);
            if (selectedEntryIds.contains(entry.id())
                    && (entry.kind() == Kind.CRYSTAL || entry.kind() == Kind.CALLING)) {
                entries.set(
                        entryIndex,
                        new Entry(
                                entry.at,
                                entry.elapsed,
                                entry.kind,
                                entry.item,
                                entry.count,
                                entry.unit,
                                entry.source,
                                fightId,
                                entry.id));
            }
        }
        revision++;
    }

    public FightRecord fight(long id) {
        return fights.stream()
                .filter(fight -> fight.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown fight."));
    }

    public List<FightRecord> recentFights(boolean total) {
        return fights.reversed().stream()
                .filter(fight -> total || fight.session == sessionId)
                .limit(50)
                .toList();
    }

    public Stats fightStats(long id) {
        FightRecord fight = fight(id);
        LedgerTotals totals = new LedgerTotals(fight.activeStart);
        for (Entry entry : entries) {
            if (entry.fightId == id) {
                totals.include(entry);
            }
        }
        long activeEnd =
                fight.outcome == FightRecord.Outcome.FIGHTING ? activeMillis : fight.activeEnd;
        return totals.stats(Math.max(0, activeEnd - fight.activeStart));
    }

    public Map<String, Double> fightLootValues(long id) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry.fightId == id && entry.kind == Kind.LOOT) {
                values.merge(entry.item, entry.income(), Double::sum);
            }
        }
        return values;
    }

    public double fightSpend(long id, Kind kind) {
        return entries.stream()
                .filter(entry -> entry.fightId == id && entry.kind == kind)
                .mapToDouble(Entry::cost)
                .sum();
    }

    public double scavengerCoins(boolean total) {
        stats(total);
        return cachedScavengerCoins;
    }

    public double fightScavengerCoins(long id) {
        return entries.stream()
                .filter(
                        entry ->
                                entry.fightId == id
                                        && entry.kind == Kind.INCOME
                                        && entry.item.equals(PurseCoins.ITEM))
                .mapToDouble(Entry::income)
                .sum();
    }

    /**
     * Replace a fight's quantity in place so old-session corrections cannot leak into this session.
     */
    public void setFightLootCount(
            long fightId, String item, long count, double fallbackUnit, long now) {
        if (!Catalog.ITEMS.containsKey(item) || count < 0 || count > MAX_ENTRY_COUNT) {
            throw new IllegalArgumentException(
                    "Use a known item and quantity from 0 to 1 billion.");
        }
        Config.validateRecordedPrice(fallbackUnit);
        FightRecord fight = fight(fightId);
        requireCompletedFight(fight);

        LootReplacement replacement = planLootReplacement(fight, item, fallbackUnit);
        int insertionIndex = removeFightLoot(fightId, item, replacement.insertionIndex());
        if (count > 0) {
            insertFightLoot(fight, item, count, now, insertionIndex, replacement);
        }
        revision++;
    }

    private static void requireCompletedFight(FightRecord fight) {
        if (fight.outcome == FightRecord.Outcome.FIGHTING
                || fight.outcome == FightRecord.Outcome.WAITING_DAMAGE) {
            throw new IllegalArgumentException(
                    "Wait for the fight's damage summary before editing.");
        }
    }

    private record LootReplacement(int insertionIndex, long activePosition, double unitPrice) {}

    private LootReplacement planLootReplacement(
            FightRecord fight, String item, double fallbackUnit) {
        int insertionIndex = -1;
        long existingCount = 0;
        double existingValue = 0;
        long activePosition = fight.activeEnd;
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            Entry entry = entries.get(entryIndex);
            if (entry.fightId == fight.id) {
                insertionIndex = entryIndex + 1;
                activePosition = entry.elapsed;
            }
            if (isFightLoot(entry, fight.id, item)) {
                existingCount += entry.count;
                existingValue += entry.income();
            }
        }

        // Multiple receipts may have different prices. Their weighted recorded unit value
        // preserves the historical valuation; today's price is used only for a missing item.
        double unitPrice = existingCount > 0 ? existingValue / existingCount : fallbackUnit;
        if (insertionIndex < 0) {
            insertionIndex = findHistoricalInsertionIndex(fight, activePosition);
        }
        return new LootReplacement(insertionIndex, activePosition, unitPrice);
    }

    private int findHistoricalInsertionIndex(FightRecord fight, long activePosition) {
        int insertionIndex = 0;
        while (insertionIndex < entries.size()
                && entries.get(insertionIndex).elapsed <= activePosition) {
            insertionIndex++;
        }
        // Two sessions can share an active timestamp. An older fight must still remain before
        // the current-session journal boundary, even when time alone cannot distinguish them.
        return fight.session < sessionId ? Math.min(insertionIndex, sessionStart) : insertionIndex;
    }

    private int removeFightLoot(long fightId, String item, int insertionIndex) {
        for (int entryIndex = entries.size() - 1; entryIndex >= 0; entryIndex--) {
            if (isFightLoot(entries.get(entryIndex), fightId, item)) {
                entries.remove(entryIndex);
                if (entryIndex < insertionIndex) {
                    insertionIndex--;
                }
                if (entryIndex < sessionStart) {
                    sessionStart--;
                }
            }
        }
        return insertionIndex;
    }

    private void insertFightLoot(
            FightRecord fight,
            String item,
            long count,
            long now,
            int insertionIndex,
            LootReplacement replacement) {
        // Replacing receipts must retain the journal's chronological order for graphs and the
        // rolling projection, including when other fights' late rewards are interleaved.
        long previousTime = insertionIndex > 0 ? entries.get(insertionIndex - 1).elapsed : 0;
        long nextTime =
                insertionIndex < entries.size()
                        ? entries.get(insertionIndex).elapsed
                        : activeMillis;
        long activePosition =
                Math.max(previousTime, Math.min(nextTime, replacement.activePosition()));
        entries.add(
                insertionIndex,
                new Entry(
                        now,
                        activePosition,
                        Kind.LOOT,
                        item,
                        count,
                        replacement.unitPrice(),
                        "fight_edit",
                        fight.id,
                        nextEntryId++));
        if (insertionIndex < sessionStart
                || (insertionIndex == sessionStart && fight.session < sessionId)) {
            sessionStart++;
        }
    }

    private static boolean isFightLoot(Entry entry, long fightId, String item) {
        return entry.fightId == fightId && entry.kind == Kind.LOOT && entry.item.equals(item);
    }

    public boolean closeOpenFights() {
        boolean changed = false;
        for (FightRecord fight : fights) {
            if (fight.outcome == FightRecord.Outcome.FIGHTING) {
                fight.outcome = FightRecord.Outcome.INTERRUPTED;
                changed = true;
            } else if (fight.outcome == FightRecord.Outcome.WAITING_DAMAGE) {
                fight.outcome = FightRecord.Outcome.MISSING_DAMAGE;
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }
        return changed;
    }

    public boolean undo() {
        if (entries.size() <= sessionStart) {
            return false;
        }
        entries.removeLast();
        revision++;
        return true;
    }

    public void reprice(String id, double unit) {
        Config.validateRecordedPrice(unit);
        for (int entryIndex = sessionStart; entryIndex < entries.size(); entryIndex++) {
            Entry entry = entries.get(entryIndex);
            if (entry.item.equals(id)) {
                entries.set(
                        entryIndex,
                        new Entry(
                                entry.at,
                                entry.elapsed,
                                entry.kind,
                                entry.item,
                                entry.count,
                                unit,
                                entry.source,
                                entry.fightId,
                                entry.id));
            }
        }
        revision++;
    }

    public Stats stats(boolean total) {
        if (cachedStats == null
                || cachedStatsRevision != revision
                || cachedStatsTotalScope != total) {
            rebuildStatsCache(total);
        }

        // Time changes between journal mutations, so the cached financial snapshot deliberately
        // excludes duration. Rates use the live active clock without rescanning old entries.
        return new Stats(
                cachedStats.revenue,
                cachedStats.costs,
                cachedStats.crystals,
                cachedStats.callings,
                cachedStats.kills,
                activeMillis - (total ? 0 : sessionMillis),
                cachedStats.loot,
                cachedStats.graph,
                cachedStats.unpriced);
    }

    private void rebuildStatsCache(boolean total) {
        long timeOrigin = total ? 0 : sessionMillis;
        int firstEntryIndex = total ? 0 : sessionStart;
        LedgerTotals totals = new LedgerTotals(timeOrigin);
        for (int entryIndex = firstEntryIndex; entryIndex < entries.size(); entryIndex++) {
            totals.include(entries.get(entryIndex));
        }
        cachedStats = totals.stats(0);
        cachedScavengerCoins = totals.scavengerCoins();
        cachedSpending = totals.spending();
        cachedStatsRevision = revision;
        cachedStatsTotalScope = total;
    }

    /** Scope affects totals; projected pace always comes from this session's recent active time. */
    public Analytics.Snapshot analytics(boolean total) {
        Stats selected = stats(total);
        if (cachedAnalytics == null
                || cachedAnalyticsRevision != revision
                || cachedAnalyticsActiveMillis != activeMillis
                || cachedAnalyticsTotalScope != total) {
            cachedAnalytics = Analytics.calculate(this, selected, cachedSpending);
            cachedAnalyticsRevision = revision;
            cachedAnalyticsActiveMillis = activeMillis;
            cachedAnalyticsTotalScope = total;
        }
        return cachedAnalytics;
    }

    public void validate() {
        revision++;
        validateHeader();
        migrateLegacyHistory();
        Set<Long> fightIds = validateFightHistory();
        advanceEntryIdPastSavedEntries();
        validateJournal(fightIds);
        clearDerivedCaches();
    }

    private void validateHeader() {
        if (schema != 1
                || entries == null
                || activeMillis < 0
                || sessionMillis < 0
                || sessionMillis > activeMillis
                || sessionStart < 0
                || sessionStart > entries.size()) {
            throw new IllegalArgumentException("Invalid ledger schema");
        }
    }

    private void migrateLegacyHistory() {
        if (fights == null) {
            fights = new ArrayList<>();
        }
        if (sessionId < 1) {
            sessionId = 1;
        }
    }

    private Set<Long> validateFightHistory() {
        Set<Long> fightIds = new HashSet<>();
        long maxFightId = 0;
        for (FightRecord fight : fights) {
            if (fight == null) {
                throw new IllegalArgumentException("Invalid fight history");
            }
            fight.validate();
            if (!fightIds.add(fight.id)
                    || fight.session > sessionId
                    || fight.activeEnd > activeMillis
                    || fight.spawnActiveMillis > activeMillis) {
                throw new IllegalArgumentException("Invalid fight identity");
            }
            maxFightId = Math.max(maxFightId, fight.id);
        }
        nextFightId = Math.max(nextFightId, maxFightId + 1);
        return fightIds;
    }

    private void advanceEntryIdPastSavedEntries() {
        long maxEntryId = 0;
        for (Entry entry : entries) {
            if (entry != null) {
                maxEntryId = Math.max(maxEntryId, entry.id);
            }
        }
        nextEntryId = Math.max(nextEntryId, maxEntryId + 1);
    }

    private void validateJournal(Set<Long> fightIds) {
        long previousElapsed = 0;
        Set<Long> entryIds = new HashSet<>();
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            Entry entry = entries.get(entryIndex);
            validateEntryFields(entry, previousElapsed, fightIds);
            entry = assignLegacyEntryId(entryIndex, entry);
            if (entry.id < 1 || !entryIds.add(entry.id)) {
                throw new IllegalArgumentException("Invalid entry identity");
            }
            Config.validateRecordedPrice(entry.unit);
            previousElapsed = entry.elapsed;
        }
    }

    private void validateEntryFields(Entry entry, long previousElapsed, Set<Long> fightIds) {
        if (entry == null
                || entry.kind == null
                || entry.item == null
                || entry.source == null
                || entry.count < 1
                || entry.count > MAX_ENTRY_COUNT
                || entry.elapsed < previousElapsed
                || entry.elapsed > activeMillis
                || entry.fightId < 0
                || (entry.fightId > 0 && !fightIds.contains(entry.fightId))) {
            throw new IllegalArgumentException("Invalid ledger entry");
        }
    }

    private Entry assignLegacyEntryId(int entryIndex, Entry entry) {
        if (entry.id != 0) {
            return entry;
        }
        // Old financial entries receive stable IDs without acquiring invented fight metadata.
        Entry migrated =
                new Entry(
                        entry.at,
                        entry.elapsed,
                        entry.kind,
                        entry.item,
                        entry.count,
                        entry.unit,
                        entry.source,
                        entry.fightId,
                        nextEntryId++);
        entries.set(entryIndex, migrated);
        return migrated;
    }

    private void clearDerivedCaches() {
        cachedStats = null;
        cachedStatsRevision = -1;
        cachedSpending = null;
        cachedAnalytics = null;
        cachedAnalyticsRevision = -1;
        cachedAnalyticsActiveMillis = -1;
    }
}

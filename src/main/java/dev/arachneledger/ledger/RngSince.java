package dev.arachneledger.ledger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Observed RNG history, derived from detected receipts and qualifying fights. A reward belongs to
 * its original fight, so a late pickup cannot erase dry kills that happened after that fight.
 * Nothing is persisted separately: undo, manual corrections and deleted kills remain reversible.
 */
public final class RngSince {
    public enum Reward {
        ANY_PET("Any Tarantula pet", Set.of("TARANTULA_EPIC", "TARANTULA_LEGENDARY")),
        EPIC_PET("Epic Tarantula pet", Set.of("TARANTULA_EPIC")),
        LEGENDARY_PET("Legendary Tarantula pet", Set.of("TARANTULA_LEGENDARY")),
        FANG("Arachne's Fang", Set.of("ARACHNE_FANG"));

        private final String label;
        private final Set<String> items;

        Reward(String label, Set<String> items) {
            this.label = label;
            this.items = items;
        }

        public String label() {
            return label;
        }

        public boolean includes(String item) {
            return items.contains(item);
        }
    }

    /** Receipt date and quantity, with the original fight ending as its active-clock anchor. */
    public record Drop(long fightId, long at, long activeMillis, String item, long count) {}

    /** Notification intervals use original fight endings, not a delayed pickup's arrival time. */
    public record DropInterval(long kills, long activeMillis, boolean hasPrevious) {}

    public record Streak(
            Reward reward,
            long drops,
            long qualifiedKills,
            long killsSinceDrop,
            long activeMillisSinceDrop,
            long longestDryStreak,
            Drop lastDrop) {
        public boolean hasDrop() {
            return lastDrop != null;
        }

        /** Observed quantities, not a predicted chance for the next kill. */
        public double dropsPer100Kills() {
            return qualifiedKills == 0 ? 0 : drops * 100.0 / qualifiedKills;
        }
    }

    public record Snapshot(boolean total, long qualifiedKills, long elapsed, List<Streak> rows) {
        public Snapshot {
            rows = List.copyOf(rows);
        }

        public Streak row(Reward reward) {
            return rows.get(reward.ordinal());
        }
    }

    private static final class FightRewards {
        long count;
        Ledger.Entry latest;

        void include(Ledger.Entry entry) {
            count += entry.count();
            // Journal order breaks ties when two reward labels appeared during the same tick.
            if (latest == null || entry.elapsed() >= latest.elapsed()) {
                latest = entry;
            }
        }
    }

    public static Snapshot calculate(Ledger ledger, boolean total) {
        return calculate(ledger, total, ledger.entries.size(), ledger.activeMillis);
    }

    /** Refresh the live active clock without rejoining an unchanged reward journal. */
    public static Snapshot advanceClock(Snapshot saved, long activeMillis, long scopeOrigin) {
        long elapsed = Math.max(0, activeMillis - scopeOrigin);
        List<Streak> rows = new ArrayList<>(saved.rows().size());
        for (Streak row : saved.rows()) {
            rows.add(
                    new Streak(
                            row.reward(),
                            row.drops(),
                            row.qualifiedKills(),
                            row.killsSinceDrop(),
                            row.lastDrop() == null
                                    ? elapsed
                                    : Math.max(0, activeMillis - row.lastDrop().activeMillis()),
                            row.longestDryStreak(),
                            row.lastDrop()));
        }
        return new Snapshot(saved.total(), saved.qualifiedKills(), elapsed, rows);
    }

    /** Reuse an existing trusted join when deriving lifetime achievement progress. */
    public static long longestDryStreak(HistoryIndex.Snapshot history, Reward reward) {
        long dry = 0, longest = 0;
        for (HistoryIndex.Fight fight : history.fights()) {
            if (!fight.qualifying()) {
                continue;
            }
            boolean found =
                    fight.detectedLoot().entrySet().stream()
                            .anyMatch(
                                    entry ->
                                            reward.includes(entry.getKey())
                                                    && entry.getValue() > 0);
            dry = found ? 0 : dry + 1;
            longest = Math.max(longest, dry);
        }
        return longest;
    }

    /** Calculate through a retained receipt, excluding all receipts that appear after it. */
    public static Snapshot calculateAt(Ledger ledger, boolean total, long entryId) {
        int boundary = receiptIndex(ledger, entryId);
        return calculate(ledger, total, boundary + 1, ledger.entries.get(boundary).elapsed());
    }

    /** Receipt-order snapshot excluding the incoming receipt and later journal entries. */
    public static Snapshot calculateBefore(Ledger ledger, boolean total, long entryId) {
        int boundary = receiptIndex(ledger, entryId);
        return calculate(ledger, total, boundary, ledger.entries.get(boundary).elapsed());
    }

    /**
     * A local rare-drop notification follows the reward's original qualifying fight. Current
     * retained qualification is required, even when damage confirmation arrived after the loot.
     * Later fights and later-fight drops cannot extend or reset this historical interval.
     */
    public static DropInterval intervalForDrop(Ledger ledger, boolean total, long entryId) {
        Ledger.Entry receipt =
                ledger.entries.stream()
                        .filter(entry -> entry.id() == entryId)
                        .findFirst()
                        .orElse(null);
        if (receipt == null
                || receipt.kind() != Ledger.Kind.LOOT
                || !HistoryIndex.isDetectedReward(receipt)
                || !(Reward.ANY_PET.includes(receipt.item())
                        || Reward.FANG.includes(receipt.item()))) {
            return null;
        }
        HistoryIndex.Snapshot history = HistoryIndex.build(ledger);
        Map<Long, Long> fightEnds = fightEnds(ledger);
        long kills = 0, previousEnd = total ? 0 : ledger.sessionMillis;
        boolean hasPrevious = false;
        for (HistoryIndex.Fight fight : history.fights()) {
            if (!fight.qualifying() || (!total && fight.session() != ledger.sessionId)) continue;
            kills++;
            long end = fightEnds.get(fight.id());
            if (fight.id() == receipt.fightId()) {
                // Multiple real receipts of the same rarity within one fight have a zero-kill
                // interval after the first. Manual corrections never act as previous drops.
                boolean priorSameFight =
                        ledger.entries.stream()
                                .anyMatch(
                                        entry ->
                                                entry.id() < entryId
                                                        && entry.fightId() == fight.id()
                                                        && entry.kind() == Ledger.Kind.LOOT
                                                        && entry.item().equals(receipt.item())
                                                        && HistoryIndex.isDetectedReward(entry));
                return priorSameFight
                        ? new DropInterval(0, 0, true)
                        : new DropInterval(kills, Math.max(0, end - previousEnd), hasPrevious);
            }
            if (fight.detectedLoot().getOrDefault(receipt.item(), 0L) > 0) {
                kills = 0;
                previousEnd = end;
                hasPrevious = true;
            }
        }
        return null;
    }

    private static int receiptIndex(Ledger ledger, long entryId) {
        for (int index = 0; index < ledger.entries.size(); index++) {
            if (ledger.entries.get(index).id() == entryId) {
                return index;
            }
        }
        throw new IllegalArgumentException("Receipt not found: " + entryId);
    }

    private static Map<Long, Long> fightEnds(Ledger ledger) {
        Map<Long, Long> ends = new LinkedHashMap<>();
        for (FightRecord fight : ledger.fights) ends.put(fight.id, fight.activeEnd);
        return ends;
    }

    private static Snapshot calculate(Ledger ledger, boolean total, int limit, long activeMillis) {
        HistoryIndex.Snapshot history = HistoryIndex.build(ledger);
        Map<Long, Long> fightEnds = fightEnds(ledger);
        Map<Long, HistoryIndex.Fight> eligible = new LinkedHashMap<>();
        for (HistoryIndex.Fight fight : history.fights()) {
            if (fight.qualifying() && (total || fight.session() == ledger.sessionId)) {
                eligible.put(fight.id(), fight);
            }
        }
        int start = total ? 0 : ledger.sessionStart;
        Set<Long> retainedKills = new HashSet<>();
        Map<Reward, Map<Long, FightRewards>> rewards = new EnumMap<>(Reward.class);
        for (Reward reward : Reward.values()) {
            rewards.put(reward, new LinkedHashMap<>());
        }
        for (int index = start; index < limit; index++) {
            Ledger.Entry entry = ledger.entries.get(index);
            if (!eligible.containsKey(entry.fightId())) {
                continue;
            }
            if (entry.kind() == Ledger.Kind.KILL
                    && entry.item().equals("ARACHNE")
                    && entry.source().equals("server")) {
                retainedKills.add(entry.fightId());
            }
            if (entry.kind() != Ledger.Kind.LOOT || !HistoryIndex.isDetectedReward(entry)) {
                continue;
            }
            for (Reward reward : Reward.values()) {
                if (reward.includes(entry.item())) {
                    rewards.get(reward)
                            .computeIfAbsent(entry.fightId(), ignored -> new FightRewards())
                            .include(entry);
                }
            }
        }

        // Fight creation order is stable even if damage confirmation or a pickup arrives late.
        List<HistoryIndex.Fight> kills =
                eligible.values().stream()
                        .filter(fight -> retainedKills.contains(fight.id()))
                        .toList();
        long origin = total ? 0 : ledger.sessionMillis;
        long elapsed = Math.max(0, activeMillis - origin);
        List<Streak> rows = new ArrayList<>(Reward.values().length);
        for (Reward reward : Reward.values()) {
            long drops = 0, dry = 0, longest = 0;
            Drop lastDrop = null;
            for (HistoryIndex.Fight fight : kills) {
                FightRewards detected = rewards.get(reward).get(fight.id());
                if (detected == null) {
                    dry++;
                    longest = Math.max(longest, dry);
                } else {
                    drops += detected.count;
                    dry = 0;
                    Ledger.Entry latest = detected.latest;
                    lastDrop =
                            new Drop(
                                    fight.id(),
                                    latest.at(),
                                    fightEnds.get(fight.id()),
                                    latest.item(),
                                    detected.count);
                }
            }
            rows.add(
                    new Streak(
                            reward,
                            drops,
                            kills.size(),
                            dry,
                            lastDrop == null
                                    ? elapsed
                                    : Math.max(0, activeMillis - lastDrop.activeMillis()),
                            longest,
                            lastDrop));
        }
        return new Snapshot(total, kills.size(), elapsed, rows);
    }

    private RngSince() {}
}

package dev.arachneledger.ledger;

import dev.arachneledger.skyblock.PurseCoins;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable join of fight metadata and journal receipts. A single join supports records and
 * achievements without rescanning the entire journal for every historical fight.
 */
public final class HistoryIndex {
    private static final Set<String> DETECTED_SOURCES =
            Set.of("server", "pickup", "armor_stand", "pet_claim", "scoreboard");

    public record Snapshot(List<Fight> fights, List<Ledger.Entry> entries) {
        public Snapshot {
            fights = List.copyOf(fights);
            entries = List.copyOf(entries);
        }
    }

    public record Fight(
            long id,
            long session,
            long spawned,
            long died,
            long spawnActiveMillis,
            long damage,
            long minimumDamage,
            FightRecord.Outcome outcome,
            boolean qualifying,
            double detectedRevenue,
            double rngRevenue,
            double costs,
            double scavengerCoins,
            Map<String, Long> detectedLoot) {
        public Fight {
            detectedLoot = Map.copyOf(detectedLoot);
        }

        /** Legacy and unconfirmed starts never create speed records. */
        public long duration() {
            return spawnActiveMillis >= 0 && spawned > 0 && died > spawned ? died - spawned : -1;
        }

        public double net() {
            return detectedRevenue - costs;
        }

        public double ordinaryNet() {
            return net() - rngRevenue;
        }
    }

    private static final class Receipts {
        double revenue, rng, costs, scavenger;
        boolean kill;
        final Map<String, Long> loot = new LinkedHashMap<>();

        void include(Ledger.Entry entry) {
            // Cost corrections may lower a record; manual income must never raise one.
            costs += entry.cost();
            if (entry.kind() == Ledger.Kind.KILL
                    && entry.item().equals("ARACHNE")
                    && entry.source().equals("server")) {
                kill = true;
            }
            if (!isDetectedReward(entry)) {
                return;
            }
            revenue += entry.income();
            if (entry.kind() == Ledger.Kind.LOOT) {
                loot.merge(entry.item(), entry.count(), Long::sum);
                if (ProfitBreakdown.isRngItem(entry.item())) {
                    rng += entry.income();
                }
            } else if (entry.item().equals(PurseCoins.ITEM)) {
                scavenger += entry.income();
            }
        }
    }

    public static boolean isDetectedReward(Ledger.Entry entry) {
        return (entry.kind() == Ledger.Kind.LOOT || entry.kind() == Ledger.Kind.INCOME)
                && DETECTED_SOURCES.contains(entry.source());
    }

    public static Snapshot build(Ledger ledger) {
        Map<Long, Receipts> joined = new LinkedHashMap<>();
        for (Ledger.Entry entry : ledger.entries) {
            if (entry.fightId() > 0) {
                joined.computeIfAbsent(entry.fightId(), ignored -> new Receipts()).include(entry);
            }
        }
        List<Fight> fights = new ArrayList<>(ledger.fights.size());
        for (FightRecord record : ledger.fights) {
            Receipts receipts = joined.getOrDefault(record.id, new Receipts());
            boolean qualifying =
                    record.outcome == FightRecord.Outcome.COUNTED
                            && record.damage >= record.minimumDamage
                            && receipts.kill;
            fights.add(
                    new Fight(
                            record.id,
                            record.session,
                            record.spawned,
                            record.died,
                            record.spawnActiveMillis,
                            record.damage,
                            record.minimumDamage,
                            record.outcome,
                            qualifying,
                            receipts.revenue,
                            receipts.rng,
                            receipts.costs,
                            receipts.scavenger,
                            receipts.loot));
        }
        return new Snapshot(fights, ledger.entries);
    }

    private HistoryIndex() {}
}

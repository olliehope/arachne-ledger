package dev.arachneledger.ledger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comparisons join retained qualifying kills with detected rewards, never inferred summon tiers.
 */
public final class SummonStats {
    public record Row(
            FightRecord.SummonTier tier,
            long qualifiedKills,
            double detectedLootRevenue,
            double scavengerCoins,
            double costs,
            double rngRevenue,
            long fightMillis,
            long timedKills) {
        public double profit() {
            return detectedLootRevenue + scavengerCoins - costs;
        }

        public double ordinaryProfit() {
            return profit() - rngRevenue;
        }

        public boolean hasTiming() {
            return qualifiedKills > 0 && timedKills == qualifiedKills && fightMillis > 0;
        }

        public double averageFightMillis() {
            return timedKills > 0 ? (double) fightMillis / timedKills : 0;
        }

        /**
         * Waiting and ritual time cannot be attributed to a tier, so these are fight-only rates.
         */
        public double fightHourly() {
            return hasTiming() ? profit() * 3_600_000.0 / fightMillis : 0;
        }

        public double ordinaryFightHourly() {
            return hasTiming() ? ordinaryProfit() * 3_600_000.0 / fightMillis : 0;
        }
    }

    public record Snapshot(
            boolean total, List<Row> rows, double unassignedRevenue, double unassignedCosts) {
        public Snapshot {
            rows = List.copyOf(rows);
        }

        public Row row(FightRecord.SummonTier tier) {
            return rows.stream().filter(row -> row.tier() == tier).findFirst().orElseThrow();
        }
    }

    private static final class Totals {
        long kills, millis, timed;
        double loot, scavenger, costs, rng;

        Row row(FightRecord.SummonTier tier) {
            return new Row(tier, kills, loot, scavenger, costs, rng, millis, timed);
        }
    }

    public static Snapshot calculate(Ledger ledger, boolean total) {
        HistoryIndex.Snapshot history = HistoryIndex.build(ledger);
        Map<Long, FightRecord.SummonTier> tiers = new HashMap<>();
        for (FightRecord fight : ledger.fights) {
            tiers.put(
                    fight.id,
                    fight.summonTier == null ? FightRecord.SummonTier.UNKNOWN : fight.summonTier);
        }
        Map<FightRecord.SummonTier, Totals> rows = new EnumMap<>(FightRecord.SummonTier.class);
        for (FightRecord.SummonTier tier : FightRecord.SummonTier.values())
            rows.put(tier, new Totals());
        Map<Long, Totals> qualifying = new HashMap<>();
        for (HistoryIndex.Fight fight : history.fights()) {
            if (!fight.qualifying() || (!total && fight.session() != ledger.sessionId)) continue;
            Totals totals =
                    rows.get(tiers.getOrDefault(fight.id(), FightRecord.SummonTier.UNKNOWN));
            totals.kills++;
            long duration = fight.duration();
            if (duration > 0) {
                totals.millis += duration;
                totals.timed++;
            }
            qualifying.put(fight.id(), totals);
        }
        double unassignedRevenue = 0, unassignedCosts = 0;
        int start = total ? 0 : ledger.sessionStart;
        for (int i = start; i < ledger.entries.size(); i++) {
            Ledger.Entry entry = ledger.entries.get(i);
            Totals row = qualifying.get(entry.fightId());
            if (row == null) {
                unassignedRevenue += entry.income();
                unassignedCosts += entry.cost();
                continue;
            }
            row.costs += entry.cost();
            if (!HistoryIndex.isDetectedReward(entry)) {
                unassignedRevenue += entry.income();
            } else if (entry.kind() == Ledger.Kind.LOOT) {
                row.loot += entry.income();
                if (ProfitBreakdown.isRngItem(entry.item())) row.rng += entry.income();
            } else {
                row.scavenger += entry.income();
            }
        }
        return new Snapshot(
                total,
                List.of(
                        rows.get(FightRecord.SummonTier.CALLING)
                                .row(FightRecord.SummonTier.CALLING),
                        rows.get(FightRecord.SummonTier.CRYSTAL)
                                .row(FightRecord.SummonTier.CRYSTAL),
                        rows.get(FightRecord.SummonTier.UNKNOWN)
                                .row(FightRecord.SummonTier.UNKNOWN)),
                unassignedRevenue,
                unassignedCosts);
    }

    private SummonStats() {}
}

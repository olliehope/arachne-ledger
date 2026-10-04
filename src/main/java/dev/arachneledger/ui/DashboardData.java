package dev.arachneledger.ui;

import dev.arachneledger.ledger.Analytics;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.ProfitBreakdown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Screen cache: receipt joins refresh on edits, hourly figures at the displayed clock second. */
public final class DashboardData {
    public record Fight(
            long id,
            long died,
            long duration,
            long damage,
            String reason,
            double profit,
            boolean completed) {}

    public record Snapshot(
            Ledger.Stats stats,
            Analytics.Snapshot analytics,
            ProfitBreakdown.Snapshot profit,
            List<Fight> fights) {
        public Fight lastFinished() {
            return fights.stream().filter(Fight::completed).findFirst().orElse(null);
        }
    }

    private Ledger cachedLedger;
    private long revision = -1, second = -1;
    private boolean total;
    private List<Fight> fights = List.of();
    private Snapshot snapshot;

    public Snapshot get(Ledger ledger, boolean totalScope) {
        long clockSecond = ledger.activeMillis / 1000;
        boolean receiptsChanged =
                cachedLedger != ledger || revision != ledger.revision() || total != totalScope;
        if (receiptsChanged) fights = joinFights(ledger, totalScope);
        if (snapshot == null || receiptsChanged || second != clockSecond) {
            snapshot =
                    new Snapshot(
                            ledger.stats(totalScope),
                            ledger.analytics(totalScope),
                            ledger.profitBreakdown(totalScope),
                            fights);
            cachedLedger = ledger;
            revision = ledger.revision();
            second = clockSecond;
            total = totalScope;
        }
        return snapshot;
    }

    private static final class Receipts {
        double profit;
        long kills;
    }

    private static List<Fight> joinFights(Ledger ledger, boolean total) {
        List<FightRecord> recent = ledger.recentFights(total);
        Map<Long, Receipts> joined = new HashMap<>();
        for (FightRecord fight : recent) joined.put(fight.id, new Receipts());
        // One journal traversal supports all visible rows; never one full traversal per fight.
        for (Ledger.Entry entry : ledger.entries) {
            Receipts receipts = joined.get(entry.fightId());
            if (receipts == null) continue;
            receipts.profit += entry.income() - entry.cost();
            if (entry.kind() == Ledger.Kind.KILL) receipts.kills += entry.count();
        }
        List<Fight> rows = new ArrayList<>(recent.size());
        for (FightRecord fight : recent) {
            Receipts receipts = joined.get(fight.id);
            boolean finished =
                    fight.outcome != FightRecord.Outcome.FIGHTING
                            && fight.outcome != FightRecord.Outcome.WAITING_DAMAGE;
            rows.add(
                    new Fight(
                            fight.id,
                            fight.died,
                            fight.duration(),
                            fight.damage,
                            fight.reason(receipts.kills > 0),
                            receipts.profit,
                            finished));
        }
        return List.copyOf(rows);
    }

    /** Local clipboard text; totals use the same scope and immutable prices as the dashboard. */
    public static String recap(Snapshot snapshot, boolean total) {
        Ledger.Stats stats = snapshot.stats();
        return "Arachne Ledger · "
                + (total ? "Total" : "Session")
                + "\nProfit: "
                + Format.coins(stats.profit())
                + " coins"
                + "\nProfit without RNG: "
                + Format.coins(snapshot.profit().ordinaryNet())
                + " coins"
                + "\nLoot: "
                + Format.coins(
                        snapshot.analytics().lootRevenue().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum())
                + " coins"
                + "\nCosts: "
                + Format.coins(stats.costs())
                + " coins"
                + "\nKills: "
                + stats.kills()
                + " ("
                + String.format(java.util.Locale.ROOT, "%.1f", snapshot.analytics().killsPerHour())
                + "/hour)"
                + "\nActive time: "
                + Format.time(stats.elapsed());
    }
}

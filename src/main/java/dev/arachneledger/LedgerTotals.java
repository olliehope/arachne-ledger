package dev.arachneledger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates one ordered selection of journal entries for a scope or fight. This is temporary
 * calculation state; the ledger entries remain the only stored financial record.
 */
final class LedgerTotals {
    private final long timeOrigin;
    private final Map<String, Long> lootCounts = new LinkedHashMap<>();
    private final Map<String, Double> lootRevenue = new LinkedHashMap<>();
    private final List<Ledger.Point> graph = new ArrayList<>();
    private double revenue;
    private double costs;
    private double scavengerCoins;
    private double crystalSpend;
    private double callingSpend;
    private double otherSpend;
    private long crystals;
    private long callings;
    private long kills;
    private long unpriced;

    LedgerTotals(long timeOrigin) {
        this.timeOrigin = timeOrigin;
        graph.add(new Ledger.Point(0, 0));
    }

    void include(Ledger.Entry entry) {
        double entryIncome = entry.income();
        double entryCost = entry.cost();
        revenue += entryIncome;
        costs += entryCost;

        switch (entry.kind()) {
            case LOOT -> {
                lootCounts.merge(entry.item(), entry.count(), Long::sum);
                lootRevenue.merge(entry.item(), entryIncome, Double::sum);
                if (entry.unit() == 0) {
                    unpriced += entry.count();
                }
            }
            case CRYSTAL -> {
                crystals += entry.count();
                crystalSpend += entryCost;
            }
            case CALLING -> {
                callings += entry.count();
                callingSpend += entryCost;
            }
            case KILL -> kills += entry.count();
            case EXPENSE -> otherSpend += entryCost;
            case INCOME -> {
                if (entry.item().equals(PurseCoins.ITEM)) {
                    // This is a subtotal of revenue, never an additional income contribution.
                    scavengerCoins += entryIncome;
                }
            }
        }
        graph.add(new Ledger.Point(Math.max(0, entry.elapsed() - timeOrigin), revenue - costs));
    }

    Ledger.Stats stats(long elapsed) {
        return new Ledger.Stats(
                revenue,
                costs,
                crystals,
                callings,
                kills,
                elapsed,
                Collections.unmodifiableMap(new LinkedHashMap<>(lootCounts)),
                List.copyOf(graph),
                unpriced);
    }

    Analytics.Spending spending() {
        return new Analytics.Spending(
                crystalSpend,
                callingSpend,
                otherSpend,
                Collections.unmodifiableMap(new LinkedHashMap<>(lootRevenue)));
    }

    double scavengerCoins() {
        return scavengerCoins;
    }
}

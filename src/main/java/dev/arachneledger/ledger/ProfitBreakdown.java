package dev.arachneledger.ledger;

import dev.arachneledger.skyblock.PetDrops;

/**
 * Separates unusually valuable rewards from the normal earning rate without changing receipts. Both
 * rates retain all recorded costs and coin income. RNG means Tarantula pets and Arachne Fangs; it
 * is an explicit item classification, not a price or rarity guess.
 */
public final class ProfitBreakdown {
    public record Snapshot(
            double revenue,
            double rngRevenue,
            double costs,
            long elapsed,
            boolean projectionReady,
            long windowMillis,
            double windowNet,
            double windowOrdinaryNet) {
        public void validate() {
            if (!Double.isFinite(revenue)
                    || revenue < 0
                    || !Double.isFinite(rngRevenue)
                    || rngRevenue < 0
                    || rngRevenue > revenue
                    || !Double.isFinite(costs)
                    || costs < 0
                    || elapsed < 0
                    || windowMillis < 0
                    || windowMillis > Analytics.WINDOW_MILLIS
                    || windowMillis > elapsed
                    || !Double.isFinite(windowNet)
                    || !Double.isFinite(windowOrdinaryNet)) {
                throw new IllegalArgumentException("Invalid profit breakdown");
            }
        }

        public double ordinaryRevenue() {
            return revenue - rngRevenue;
        }

        public double net() {
            return revenue - costs;
        }

        public double ordinaryNet() {
            return ordinaryRevenue() - costs;
        }

        public double hourly() {
            return hourly(net(), elapsed);
        }

        public double ordinaryHourly() {
            return hourly(ordinaryNet(), elapsed);
        }

        public double projectedHourly() {
            return projectionReady ? hourly(windowNet, windowMillis) : 0;
        }

        public double projectedOrdinaryHourly() {
            return projectionReady ? hourly(windowOrdinaryNet, windowMillis) : 0;
        }

        private static double hourly(double amount, long millis) {
            return millis > 0 ? amount * 3_600_000.0 / millis : 0;
        }
    }

    public static boolean isRngItem(String item) {
        return PetDrops.isTarantula(item) || "ARACHNE_FANG".equals(item);
    }

    /** Refresh recorded totals after a receipt or scope change. Projections use session pace. */
    public static Snapshot calculate(Ledger ledger, boolean total) {
        double revenue = 0, rngRevenue = 0, costs = 0;
        int start = total ? 0 : ledger.sessionStart;
        for (int i = start; i < ledger.entries.size(); i++) {
            Ledger.Entry entry = ledger.entries.get(i);
            double income = entry.income();
            revenue += income;
            if (entry.kind() == Ledger.Kind.LOOT && isRngItem(entry.item())) {
                rngRevenue += income;
            }
            costs += entry.cost();
        }
        return retime(ledger, total, new Snapshot(revenue, rngRevenue, costs, 0, false, 0, 0, 0));
    }

    /**
     * Reuses receipt totals when only active time changes. The caller refreshes them with {@link
     * #calculate} after journal or scope changes. Shared prefix sums make window queries
     * logarithmic rather than scanning thousands of recent reward receipts on every active tick.
     */
    public static Snapshot retime(Ledger ledger, boolean total, Snapshot financials) {
        long sessionElapsed = Math.max(0, ledger.activeMillis - ledger.sessionMillis);
        RecentWindow.Snapshot window = ledger.recentWindow();
        long windowMillis = window.millis();
        return new Snapshot(
                financials.revenue(),
                financials.rngRevenue(),
                financials.costs(),
                total ? ledger.activeMillis : sessionElapsed,
                windowMillis >= Analytics.WARMUP_MILLIS && window.kills() > 0,
                windowMillis,
                window.net(),
                window.ordinaryNet());
    }

    private ProfitBreakdown() {}
}

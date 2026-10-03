package dev.arachneledger.ledger;

import dev.arachneledger.skyblock.PurseCoins;

/** Captured financial summaries survive a new session without retaining a mutable ledger. */
public final class SessionSummary {
    public record Snapshot(
            long sessionId,
            boolean total,
            long capturedAt,
            ProfitBreakdown.Snapshot profit,
            long crystals,
            long callings,
            long qualifiedKills,
            long timedKills,
            double averageKillMillis,
            long fastestKillMillis,
            double crystalSpend,
            double callingSpend,
            double otherSpend,
            double scavengerCoins,
            long unpriced,
            boolean activity) {
        public boolean hasActivity() {
            return activity;
        }

        /** Saved recaps are untrusted JSON; malformed summaries must not become display records. */
        public void validate() {
            if (sessionId < 1
                    || capturedAt < 0
                    || profit == null
                    || crystals < 0
                    || callings < 0
                    || qualifiedKills < 0
                    || timedKills < 0
                    || timedKills > qualifiedKills
                    || !Double.isFinite(averageKillMillis)
                    || averageKillMillis < 0
                    || (timedKills == 0 && (averageKillMillis != 0 || fastestKillMillis != -1))
                    || (timedKills > 0
                            && (fastestKillMillis <= 0 || averageKillMillis < fastestKillMillis))
                    || !nonnegative(crystalSpend)
                    || !nonnegative(callingSpend)
                    || !nonnegative(otherSpend)
                    || !nonnegative(scavengerCoins)
                    || unpriced < 0) {
                throw new IllegalArgumentException("Invalid session recap");
            }
            profit.validate();
            // Components accumulate separately from the journal's ordered total. Decimal
            // valuations may differ by rounding noise after many receipts, so compare with
            // a small absolute/relative tolerance rather than exact double equality.
            if (!sameAmount(crystalSpend + callingSpend + otherSpend, profit.costs())
                    || (scavengerCoins > profit.revenue()
                            && !sameAmount(scavengerCoins, profit.revenue()))) {
                throw new IllegalArgumentException("Inconsistent session recap subtotals");
            }
        }

        private static boolean nonnegative(double value) {
            return Double.isFinite(value) && value >= 0;
        }

        private static boolean sameAmount(double expected, double actual) {
            double tolerance =
                    Math.max(0.000001, Math.max(Math.abs(expected), Math.abs(actual)) * 1e-9);
            return Double.isFinite(expected)
                    && Double.isFinite(actual)
                    && Math.abs(expected - actual) <= tolerance;
        }
    }

    public static Snapshot capture(Ledger ledger) {
        return capture(ledger, false);
    }

    public static Snapshot capture(Ledger ledger, boolean total) {
        return capture(ledger, total, System.currentTimeMillis());
    }

    public static Snapshot capture(Ledger ledger, boolean total, long capturedAt) {
        long crystals = 0, callings = 0, unpriced = 0;
        double crystalSpend = 0, callingSpend = 0, otherSpend = 0, scavenger = 0;
        int start = total ? 0 : ledger.sessionStart;
        for (int i = start; i < ledger.entries.size(); i++) {
            Ledger.Entry entry = ledger.entries.get(i);
            switch (entry.kind()) {
                case CRYSTAL -> {
                    crystals += entry.count();
                    crystalSpend += entry.cost();
                }
                case CALLING -> {
                    callings += entry.count();
                    callingSpend += entry.cost();
                }
                case EXPENSE -> otherSpend += entry.cost();
                case LOOT -> {
                    if (entry.unpriced()) {
                        unpriced += entry.count();
                    }
                }
                case INCOME -> {
                    if (entry.item().equals(PurseCoins.ITEM)) {
                        scavenger += entry.income();
                    }
                }
                default -> {}
            }
        }
        long qualified = 0, timed = 0, fastest = -1;
        double durations = 0;
        boolean anyFight = false;
        for (HistoryIndex.Fight fight : HistoryIndex.build(ledger).fights()) {
            if (!total && fight.session() != ledger.sessionId) {
                continue;
            }
            anyFight = true;
            if (!fight.qualifying()) {
                continue;
            }
            qualified++;
            long duration = fight.duration();
            if (duration > 0) {
                timed++;
                durations += duration;
                fastest = fastest < 0 ? duration : Math.min(fastest, duration);
            }
        }
        ProfitBreakdown.Snapshot profit = ProfitBreakdown.calculate(ledger, total);
        return new Snapshot(
                ledger.sessionId,
                total,
                capturedAt,
                profit,
                crystals,
                callings,
                qualified,
                timed,
                timed > 0 ? durations / timed : 0,
                fastest,
                crystalSpend,
                callingSpend,
                otherSpend,
                scavenger,
                unpriced,
                anyFight || ledger.entries.size() > start || profit.elapsed() > 0);
    }

    private SessionSummary() {}
}

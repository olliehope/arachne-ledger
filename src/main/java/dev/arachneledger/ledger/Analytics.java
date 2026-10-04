package dev.arachneledger.ledger;

import java.util.Map;

/** Projections use observed net income and active time; they do not predict unobserved drops. */
public final class Analytics {
    public static final long WINDOW_MILLIS = 300_000;
    public static final long WARMUP_MILLIS = 60_000;

    private Analytics() {}

    public record Snapshot(
            boolean projectionReady,
            double projectedHourly,
            long windowMillis,
            long windowKills,
            double windowNet,
            double averageNetPerKill,
            double killsPerHour,
            double averageMillisPerKill,
            double crystalSpend,
            double callingSpend,
            double otherSpend,
            Map<String, Double> lootRevenue) {}

    record Spending(
            double crystals, double callings, double other, Map<String, Double> lootRevenue) {}

    static Snapshot calculate(Ledger ledger, Ledger.Stats selected, Spending spending) {
        RecentWindow.Snapshot window = ledger.recentWindow();
        long windowMillis = window.millis();
        double windowNet = window.net();
        long windowKills = window.kills();
        boolean ready = windowMillis >= WARMUP_MILLIS && windowKills > 0;
        double hourly = ready ? windowNet * 3_600_000.0 / windowMillis : 0;
        long kills = selected.kills();
        return new Snapshot(
                ready,
                hourly,
                windowMillis,
                windowKills,
                windowNet,
                kills > 0 ? selected.profit() / kills : 0,
                selected.elapsed() > 0 ? kills * 3_600_000.0 / selected.elapsed() : 0,
                kills > 0 ? (double) selected.elapsed() / kills : 0,
                spending.crystals(),
                spending.callings(),
                spending.other(),
                spending.lootRevenue());
    }
}

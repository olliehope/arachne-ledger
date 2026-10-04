package dev.arachneledger.ledger;

/** Prefix sums make live five-minute projections independent of the number of reward receipts. */
final class RecentWindow {
    record Snapshot(long millis, double net, double ordinaryNet, long kills) {}

    private final long[] positions, kills;
    private final double[] net, rng;
    private final long sessionOrigin;

    RecentWindow(Ledger ledger) {
        int size = ledger.entries.size() - ledger.sessionStart;
        positions = new long[size];
        net = new double[size + 1];
        rng = new double[size + 1];
        kills = new long[size + 1];
        sessionOrigin = ledger.sessionMillis;
        for (int i = 0; i < size; i++) {
            Ledger.Entry entry = ledger.entries.get(ledger.sessionStart + i);
            positions[i] = entry.elapsed();
            net[i + 1] = net[i] + entry.income() - entry.cost();
            rng[i + 1] =
                    rng[i]
                            + (entry.kind() == Ledger.Kind.LOOT
                                            && ProfitBreakdown.isRngItem(entry.item())
                                    ? entry.income()
                                    : 0);
            kills[i + 1] = kills[i] + (entry.kind() == Ledger.Kind.KILL ? entry.count() : 0);
        }
    }

    Snapshot at(long activeMillis) {
        long sessionElapsed = Math.max(0, activeMillis - sessionOrigin);
        long millis = Math.min(Analytics.WINDOW_MILLIS, sessionElapsed);
        int left =
                sessionElapsed <= Analytics.WINDOW_MILLIS ? 0 : firstAfter(activeMillis - millis);
        int right = positions.length;
        double windowNet = net[right] - net[left];
        return new Snapshot(
                millis,
                windowNet,
                windowNet - (rng[right] - rng[left]),
                kills[right] - kills[left]);
    }

    private int firstAfter(long position) {
        int low = 0, high = positions.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (positions[middle] <= position) low = middle + 1;
            else high = middle;
        }
        return low;
    }
}

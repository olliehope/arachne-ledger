package dev.arachneledger.ledger;

/** Inventory stock and a rough personal Crystal consumption estimate, with no inferred drops. */
public final class Supplies {
    public record Snapshot(
            boolean available, long crystals, long callings, long crystalMillisLeft) {}

    public static Snapshot calculate(
            boolean available, long crystals, long callings, Ledger ledger) {
        Ledger.Stats session = ledger.stats(false);
        long remaining = -1;
        if (available && session.crystals() >= 3 && session.elapsed() >= 60_000) {
            remaining =
                    (long)
                            Math.min(
                                    Long.MAX_VALUE,
                                    Math.max(0, crystals)
                                            * (session.elapsed() / (double) session.crystals()));
        }
        return new Snapshot(available, Math.max(0, crystals), Math.max(0, callings), remaining);
    }

    private Supplies() {}
}

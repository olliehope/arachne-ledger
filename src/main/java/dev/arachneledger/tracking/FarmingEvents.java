package dev.arachneledger.tracking;

import dev.arachneledger.ledger.Ledger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Bounded, unsaved local feedback; entries retain the identity of their recorded reward. */
public final class FarmingEvents {
    private static final int LIMIT = 32;

    public record RareDrop(String itemId, long quantity, double unitValue, long entryId, long at) {}

    public record Spawn(long at) {}

    private final Deque<RareDrop> drops = new ArrayDeque<>();
    private final Deque<Spawn> spawns = new ArrayDeque<>();

    void rare(Ledger.Entry receipt) {
        if (drops.size() == LIMIT) drops.removeFirst();
        drops.addLast(
                new RareDrop(
                        receipt.item(),
                        receipt.count(),
                        receipt.unit(),
                        receipt.id(),
                        receipt.at()));
    }

    void spawned(long now) {
        if (spawns.size() == LIMIT) spawns.removeFirst();
        spawns.addLast(new Spawn(now));
    }

    List<RareDrop> drainRare() {
        List<RareDrop> result = List.copyOf(drops);
        drops.clear();
        return result;
    }

    List<Spawn> drainSpawns() {
        List<Spawn> result = List.copyOf(spawns);
        spawns.clear();
        return result;
    }

    void clear() {
        drops.clear();
        spawns.clear();
    }
}

package dev.arachneledger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Matches observations of the same reward: its named stand, pickup, and pet claim.
 * Call only after checking the reward window and parsing a valid item/quantity. This class
 * deliberately knows nothing about Minecraft entities, item prices, or journal entries.
 * Instances belong to one live world context and are confined to the client's event thread.
 */
public final class LootDeduplicator {
    public static final long MATCH_WINDOW_MILLIS = 10_000;
    private static final class Signal {
        final String item;
        final long at;
        long remaining;
        Signal(String item, long count, long at) { this.item = item; this.remaining = count; this.at = at; }
    }
    private final Deque<Signal> stands = new ArrayDeque<>();
    private final Deque<Signal> pickups = new ArrayDeque<>();
    private final Set<UUID> seenStands = new HashSet<>();
    private enum PetSource { STAND, PICKUP, CLAIM }
    private final Map<String, EnumMap<PetSource, Long>> pets = new HashMap<>();
    private final Set<String> petClaims = new HashSet<>();

    /** Lets the entity scanner skip reparsing labels already accepted in this world context. */
    public boolean hasSeenStand(UUID uuid) { return seenStands.contains(uuid); }

    /**
     * True means record this reward. A matched stand's UUID is still remembered so that
     * its next metadata update or client scan cannot turn the suppressed copy into loot.
     */
    public boolean acceptStand(UUID uuid, String item, long count, long now) {
        return acceptStandCount(uuid, item, count, now) > 0;
    }

    /** Returns only units not already recorded through a physical pickup. */
    public long acceptStandCount(UUID uuid, String item, long count, long now) {
        if (uuid == null || item == null || count < 1 || count > 1_000_000_000L) return 0;
        if (!seenStands.add(uuid)) return 0;
        if (PetDrops.isTarantula(item)) return acceptPet(item, count, PetSource.STAND);
        prune(now);
        long unseen = consume(pickups, item, count);
        if (unseen > 0) stands.addLast(new Signal(item, unseen, now));
        return unseen;
    }

    /**
     * True means record this reward. Pickup-first matching is enabled only while new
     * reward stands are eligible; ordinary mid-fight pickups cannot suppress later loot.
     */
    public boolean acceptPickup(String item, long count, long now, boolean rememberForStands) {
        return acceptPickupCount(item, count, now, rememberForStands) > 0;
    }

    /** Inventory space can split a single labelled stack across multiple pickup packets. */
    public long acceptPickupCount(String item, long count, long now, boolean rememberForStands) {
        if (item == null || count < 1 || count > 1_000_000_000L) return 0;
        if (rememberForStands && PetDrops.isTarantula(item)) return acceptPet(item, count, PetSource.PICKUP);
        prune(now);
        long unseen = consume(stands, item, count);
        if (rememberForStands && unseen > 0) pickups.addLast(new Signal(item, unseen, now));
        return unseen;
    }

    /** Claims may be relayed twice; keep their identity for the whole boss reward window. */
    public boolean acceptClaim(String item, int count, long now) {
        if (!PetDrops.isTarantula(item) || count < 1 || count > 1_000_000_000 || !petClaims.add(item + ":" + count)) return false;
        return acceptPet(item, count, PetSource.CLAIM) > 0;
    }

    private long acceptPet(String item, long count, PetSource source) {
        // Three sources describe the same rewards. Keep cumulative coverage per source
        // for the entire window so a late claim cannot recount a matched stand/pickup,
        // even when the server split the pickup into smaller quantities.
        var sources = pets.computeIfAbsent(item, ignored -> new EnumMap<>(PetSource.class));
        long recorded = sources.values().stream().mapToLong(Long::longValue).max().orElse(0);
        long observed = sources.getOrDefault(source, 0L) + count;
        sources.put(source, observed);
        return Math.max(0, observed - recorded);
    }

    /**
     * A death or new session clears transient pairs, but keeps old stand identities.
     * Holograms can survive into another fight and must not be counted again there.
     */
    public void beginRewardWindow() {
        stands.clear();
        pickups.clear();
        pets.clear();
        petClaims.clear();
    }

    /** A disconnect or location/context change also invalidates world entity identities. */
    public void reset() {
        beginRewardWindow();
        seenStands.clear();
    }

    private static long consume(Deque<Signal> signals, String item, long count) {
        for (Iterator<Signal> iterator = signals.iterator(); iterator.hasNext();) {
            Signal signal = iterator.next();
            if (signal.item.equals(item)) {
                long matched = Math.min(signal.remaining, count);
                count -= matched;
                signal.remaining -= matched;
                if (signal.remaining == 0) iterator.remove();
                if (count == 0) break;
            }
        }
        return count;
    }

    private void prune(long now) {
        // A pair exactly ten seconds old still matches; older signals cannot suppress new rewards.
        while (!stands.isEmpty() && now - stands.peekFirst().at > MATCH_WINDOW_MILLIS) stands.removeFirst();
        while (!pickups.isEmpty() && now - pickups.peekFirst().at > MATCH_WINDOW_MILLIS) pickups.removeFirst();
    }
}

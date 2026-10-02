package dev.arachneledger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
    private record Signal(String item, long count, long at) {}
    private final Deque<Signal> stands = new ArrayDeque<>();
    private final Deque<Signal> pickups = new ArrayDeque<>();
    private final Set<UUID> seenStands = new HashSet<>();
    private enum PetSource { STAND, PICKUP, CLAIM }
    private record PetReceipt(String item, long count, EnumSet<PetSource> sources) {}
    private final List<PetReceipt> pets = new ArrayList<>();
    private final Set<String> petClaims = new HashSet<>();

    /** Lets the entity scanner skip reparsing labels already accepted in this world context. */
    public boolean hasSeenStand(UUID uuid) { return seenStands.contains(uuid); }

    /**
     * True means record this reward. A matched stand's UUID is still remembered so that
     * its next metadata update or client scan cannot turn the suppressed copy into loot.
     */
    public boolean acceptStand(UUID uuid, String item, long count, long now) {
        if (!seenStands.add(uuid)) return false;
        if (PetDrops.isTarantula(item)) return acceptPet(item, count, PetSource.STAND);
        prune(now);
        if (consume(pickups, item, count)) return false;
        stands.addLast(new Signal(item, count, now));
        return true;
    }

    /**
     * True means record this reward. Pickup-first matching is enabled only while new
     * reward stands are eligible; ordinary mid-fight pickups cannot suppress later loot.
     */
    public boolean acceptPickup(String item, long count, long now, boolean rememberForStands) {
        if (rememberForStands && PetDrops.isTarantula(item)) return acceptPet(item, count, PetSource.PICKUP);
        prune(now);
        if (consume(stands, item, count)) return false;
        if (rememberForStands) pickups.addLast(new Signal(item, count, now));
        return true;
    }

    /** Claims may be relayed twice; keep their identity for the whole boss reward window. */
    public boolean acceptClaim(String item, int count, long now) {
        if (!PetDrops.isTarantula(item) || count < 1 || !petClaims.add(item + ":" + count)) return false;
        return acceptPet(item, count, PetSource.CLAIM);
    }

    private boolean acceptPet(String item, long count, PetSource source) {
        // Remember all three sources instead of consuming a pair: a late personal
        // claim must not recount a pet already seen as both a stand and a pickup.
        for (PetReceipt pet : pets) {
            if (pet.item().equals(item) && pet.count() == count && !pet.sources().contains(source)) {
                pet.sources().add(source);
                return false;
            }
        }
        pets.add(new PetReceipt(item, count, EnumSet.of(source)));
        return true;
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

    private static boolean consume(Deque<Signal> signals, String item, long count) {
        for (Iterator<Signal> iterator = signals.iterator(); iterator.hasNext();) {
            Signal signal = iterator.next();
            // Exact quantity matching is intentional: partial and unrelated receipts are separate rewards.
            if (signal.item().equals(item) && signal.count() == count) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private void prune(long now) {
        // A pair exactly ten seconds old still matches; older signals cannot suppress new rewards.
        while (!stands.isEmpty() && now - stands.peekFirst().at() > MATCH_WINDOW_MILLIS) stands.removeFirst();
        while (!pickups.isEmpty() && now - pickups.peekFirst().at() > MATCH_WINDOW_MILLIS) pickups.removeFirst();
    }
}

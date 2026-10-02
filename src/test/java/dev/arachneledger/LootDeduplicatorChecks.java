package dev.arachneledger;

import java.nio.file.Files;
import java.util.UUID;

/** Reward observations retain their identity and pair exactly once in either arrival order. */
public final class LootDeduplicatorChecks {
    private static int checks;
    private static void yes(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static void eq(long expected, long actual, String why) {
        checks++;
        if (expected != actual) throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }
    private static UUID id(long n) { return new UUID(123, n); }

    public static void main(String[] args) throws Exception {
        standFirst();
        pickupFirst();
        splitQuantities();
        matchingAndExpiry();
        contextResets();
        trackerIntegration();
        System.out.println("PASS: " + checks + " loot identity, pairing, expiry and context checks.");
    }

    private static void standFirst() {
        LootDeduplicator dedup = new LootDeduplicator();
        yes(dedup.acceptStand(id(1), "STRING", 10, 1000), "First stand is a new reward");
        yes(dedup.hasSeenStand(id(1)), "Accepted identity can skip repeated label parsing");
        yes(!dedup.acceptStand(id(1), "STRING", 10, 1100), "Repeated scan cannot count the same stand");
        yes(!dedup.acceptStand(id(1), "STRING", 20, 1200), "Metadata changes cannot count the same stand again");
        yes(!dedup.acceptPickup("STRING", 10, 1300, true), "Matching pickup consumes one stand signal");
        yes(dedup.acceptPickup("STRING", 10, 1400, true), "A second receipt is a separate reward after the pair is consumed");
        yes(!dedup.acceptStand(id(1), "STRING", 10, 1500), "Consumed stand identity remains suppressed");

        dedup.reset();
        yes(dedup.acceptStand(id(2), "STRING", 10, 2000), "First distinct stand counts");
        yes(dedup.acceptStand(id(3), "STRING", 10, 2001), "Second distinct stand with identical label counts");
        yes(!dedup.acceptPickup("STRING", 10, 2010, true), "First pickup consumes one of two stand signals");
        yes(!dedup.acceptPickup("STRING", 10, 2011, true), "Second pickup consumes the other stand signal");
        yes(dedup.acceptPickup("STRING", 10, 2012, true), "Two stand signals cannot suppress a third receipt");
    }

    private static void pickupFirst() {
        LootDeduplicator dedup = new LootDeduplicator();
        yes(dedup.acceptPickup("SPIDER_EYE", 30, 1000, true), "Pickup-first reward counts");
        yes(!dedup.acceptStand(id(1), "SPIDER_EYE", 30, 1100), "Late stand pairs with the recorded pickup");
        yes(dedup.hasSeenStand(id(1)), "Suppressed counterpart still has a remembered UUID");
        yes(!dedup.acceptStand(id(1), "SPIDER_EYE", 30, 1200), "Repeated suppressed counterpart stays suppressed");
        yes(dedup.acceptStand(id(2), "SPIDER_EYE", 30, 1300), "One pickup cannot suppress two distinct stands");

        dedup.reset();
        yes(dedup.acceptPickup("STRING", 10, 2000, false), "Mid-fight pickup counts normally");
        yes(dedup.acceptStand(id(3), "STRING", 10, 2001), "Mid-fight pickup is not retained as a later stand counterpart");
        yes(!dedup.acceptPickup("STRING", 10, 2002, false), "Already observed stand can pair regardless of pickup retention");
    }

    private static void matchingAndExpiry() {
        LootDeduplicator dedup = new LootDeduplicator();
        dedup.acceptStand(id(1), "STRING", 10, 1000);
        yes(dedup.acceptPickup("SPIDER_EYE", 10, 1100, true), "Different item does not pair despite equal quantity");
        yes(!dedup.acceptPickup("STRING", 5, 1200, true), "Partial pickup consumes only half of the matching stand");
        yes(dedup.acceptPickup("STRING", 10, 1300, true), "A larger pickup records only its five previously unseen units");
        yes(!dedup.acceptStand(id(2), "SPIDER_EYE", 10, 1400), "Matching search can consume a different pending item");
        yes(!dedup.acceptStand(id(3), "STRING", 5, 1500), "Matching search preserves other pending quantities");

        dedup.reset(); dedup.acceptStand(id(4), "STRING", 10, 2000);
        yes(!dedup.acceptPickup("STRING", 10, 12_000, true), "Stand-first pair matches at exactly ten seconds");
        dedup.reset(); dedup.acceptStand(id(5), "STRING", 10, 2000);
        yes(dedup.acceptPickup("STRING", 10, 12_001, true), "Stand-first pair expires after ten seconds");
        dedup.reset(); dedup.acceptPickup("STRING", 10, 2000, true);
        yes(!dedup.acceptStand(id(6), "STRING", 10, 12_000), "Pickup-first pair matches at exactly ten seconds");
        dedup.reset(); dedup.acceptPickup("STRING", 10, 2000, true);
        yes(dedup.acceptStand(id(7), "STRING", 10, 12_001), "Pickup-first pair expires after ten seconds");
        yes(!dedup.acceptStand(id(7), "STRING", 10, 30_000), "UUID identity outlives the short pairing timeout");
    }

    private static void splitQuantities() {
        var dedup = new LootDeduplicator();
        eq(10,dedup.acceptStandCount(id(10),"STRING",10,1000),"Stand-first records its whole stack");
        eq(0,dedup.acceptPickupCount("STRING",5,1100,true),"First half-pickup is already recorded");
        eq(0,dedup.acceptPickupCount("STRING",5,1200,true),"Second half-pickup is already recorded");
        eq(5,dedup.acceptPickupCount("STRING",5,1300,true),"A third pickup remains a new receipt");
        eq(0,dedup.acceptStandCount(id(11),"STRING",5,1400),"Third pickup pairs with its own later stand");

        dedup.reset();
        eq(5,dedup.acceptPickupCount("STRING",5,2000,true),"Pickup-first records the first five units");
        eq(5,dedup.acceptStandCount(id(12),"STRING",10,2100),"Later stand records only five missing units");
        eq(0,dedup.acceptPickupCount("STRING",5,2200,true),"Final half-pickup pairs with the stand remainder");
        eq(0,dedup.acceptStandCount(id(12),"STRING",10,2300),"Partially matched stand retains its UUID identity");

        dedup.reset();
        eq(5,dedup.acceptPickupCount("STRING",5,3000,true),"First split packet counts before the label");
        eq(5,dedup.acceptPickupCount("STRING",5,3100,true),"Second split packet counts before the label");
        eq(0,dedup.acceptStandCount(id(13),"STRING",10,3200),"One label reconciles multiple earlier packets");
        eq(10,dedup.acceptStandCount(id(14),"STRING",10,3300),"Distinct same-source reward still counts");
        eq(0,dedup.acceptPickupCount("STRING",10,3400,true),"Distinct reward has its own counterpart balance");

        dedup.reset();
        eq(2,dedup.acceptStandCount(id(15),"TARANTULA_LEGENDARY",2,4000),"Pet label can describe two units");
        eq(0,dedup.acceptPickupCount("TARANTULA_LEGENDARY",1,4100,true),"Pet split pickup shares source coverage");
        eq(0,dedup.acceptPickupCount("TARANTULA_LEGENDARY",1,4200,true),"Second pet split pickup stays suppressed");
        yes(!dedup.acceptClaim("TARANTULA_LEGENDARY",1,4300),"Late personal claim cannot recount the split pair");
        dedup.beginRewardWindow();
        yes(dedup.acceptClaim("TARANTULA_LEGENDARY",1,5000),"Next boss has fresh pet-source coverage");
        dedup.reset();
        eq(0,dedup.acceptStandCount(id(16),"STRING",0,6000),"Invalid zero label cannot create a counterpart signal");
        yes(!dedup.hasSeenStand(id(16)),"Invalid quantity cannot poison a stand identity");
        eq(10,dedup.acceptStandCount(id(16),"STRING",10,6100),"Corrected valid label can still be accepted");
        eq(0,dedup.acceptPickupCount("STRING",Long.MAX_VALUE,6200,true),"Invalid oversized pickup cannot overflow balances");
        eq(0,dedup.acceptPickupCount("STRING",10,6300,true),"Invalid pickup leaves a valid counterpart balance intact");
    }

    private static void contextResets() {
        LootDeduplicator dedup = new LootDeduplicator();
        dedup.acceptStand(id(1), "STRING", 10, 1000);
        dedup.acceptPickup("SPIDER_EYE", 30, 1001, true);
        dedup.beginRewardWindow();
        yes(!dedup.acceptStand(id(1), "STRING", 10, 2000), "Next death cannot recount a surviving hologram");
        yes(dedup.acceptPickup("STRING", 10, 2001, true), "Next reward window clears old stand pairs");
        yes(dedup.acceptStand(id(2), "SPIDER_EYE", 30, 2002), "Next reward window clears old pickup pairs");
        dedup.reset();
        yes(!dedup.hasSeenStand(id(1)), "Full world reset forgets old entity identities");
        yes(dedup.acceptStand(id(1), "STRING", 10, 3000), "Fresh world may reuse an old UUID");
        yes(dedup.acceptStand(id(3), "SPIDER_EYE", 30, 3001), "Full reset also clears old pickup pairs");
    }

    private static void trackerIntegration() throws Exception {
        Tracker tracker = new Tracker(Files.createTempDirectory("arachne-dedup-context-"));
        tracker.account("owner");
        long now = 1_000_000;
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", now);
        tracker.message("ARACHNE DOWN!", "Player", now+100);
        tracker.observeLootStand(id(1), "Arachne's Brood 1,000❤", now+101);
        tracker.observeLootStand(id(1), "String x10", now+102);
        eq(10, tracker.ledger.stats(false).loot().getOrDefault("STRING", 0L),
            "A non-loot label does not poison a UUID before its valid reward label arrives");
        tracker.newSession();
        tracker.message("ARACHNE DOWN!", "Player", now+200);
        tracker.observeLootStand(id(1), "String x10", now+201);
        eq(0, tracker.ledger.stats(false).loot().getOrDefault("STRING", 0L),
            "New session retains hologram identity rather than counting old rewards again");
        tracker.pickup("STRING", 10, now+202);
        eq(10, tracker.ledger.stats(false).loot().getOrDefault("STRING", 0L),
            "New session clears matching queues so old stand receipt cannot suppress a new pickup");
        tracker.updateLocation(true, true, false, "Hub", "elsewhere", now+300);
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", now+400);
        tracker.message("ARACHNE DOWN!", "Player", now+500);
        tracker.observeLootStand(id(1), "String x10", now+501);
        eq(20, tracker.ledger.stats(false).loot().getOrDefault("STRING", 0L),
            "Leaving and re-entering the arena starts a new entity context");
    }

    private LootDeduplicatorChecks() {}
}

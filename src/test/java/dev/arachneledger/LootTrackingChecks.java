package dev.arachneledger;

import java.nio.file.Files;
import java.util.UUID;

/** Verifies boss reward windows, stand identity, summon ownership and packet pairing. */
public final class LootTrackingChecks {
    private static int checks;
    private static void eq(long expected, long actual, String label) {
        checks++;
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
    private static void yes(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static UUID id(long n) { return new UUID(123, n); }
    public static void main(String[] args) throws Exception {
        long now = 1_000_000;
        Tracker t = new Tracker(Files.createTempDirectory("arachne-drop-checks-"));
        t.account("owner");
        t.config.callingCost = 700;
        t.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", now);
        for (int i=1;i<=4;i++) t.message("☄ You placed an Arachne's Calling!"
            + (i==4?" Something is awakening!":"") + " ("+i+"/4)", "ActualPlayer", now+i);
        eq(4,t.ledger.stats(false).callings(),"All four 'You placed' Callings charged to local player");
        eq(2800,(long)t.ledger.stats(false).costs(),"Calling costs use configured value");
        t.message("☄ You placed an Arachne Crystal!", "ActualPlayer", now+10);
        eq(1,t.ledger.stats(false).crystals(),"'You placed' Crystal charged to local player");
        t.message("☄ Other placed an Arachne Crystal!", "ActualPlayer", now+11);
        eq(1,t.ledger.stats(false).crystals(),"Another player's Crystal excluded");

        t.observeLootStand(id(1),"§fString x10",now+15);
        eq(0,t.ledger.stats(false).loot().getOrDefault("STRING",0L),"Stray stands before boss down ignored");
        t.message("ARACHNE DOWN!", "ActualPlayer", now+100);
        yes(t.acceptsStandLoot(now+100),"Death opens armor-stand loot window");
        t.observeLootStand(id(1),"§fString x10",now+101);
        t.observeLootStand(id(1),"§fString x10",now+102);
        eq(10,t.ledger.stats(false).loot().getOrDefault("STRING",0L),"Same hologram counted once across ticks");
        t.pickup("STRING",10,now+103);
        eq(10,t.ledger.stats(false).loot().getOrDefault("STRING",0L),"Matching physical pickup does not double count hologram");
        t.pickup("SPIDER_EYE",30,now+104);
        t.observeLootStand(id(2),"Spider Eye §8x30",now+105);
        eq(30,t.ledger.stats(false).loot().getOrDefault("SPIDER_EYE",0L),"Pickup followed by hologram counts once");
        t.observeLootStand(id(3),"§dSpider Essence x8",now+106);
        t.observeLootStand(id(4),"§9Arachne Shard",now+107);
        eq(8,t.ledger.stats(false).loot().getOrDefault("ESSENCE_SPIDER",0L),"Quantity from screenshot recorded");
        eq(1,t.ledger.stats(false).loot().getOrDefault("ARACHNE_SHARD",0L),"Previously missing Shard recorded");
        t.observeLootStand(id(5),"§fString x10",now+108);
        eq(20,t.ledger.stats(false).loot().getOrDefault("STRING",0L),"Distinct stands with the same label both count");
        t.observeLootStand(id(6),"Arachne's Brood 1,000❤",now+109);
        eq(4,t.ledger.stats(false).loot().size(),"Mob names are not treated as loot");
        t.observeLootStand(id(7),"§9Arachne Fragment",now+46_000);
        eq(0,t.ledger.stats(false).loot().getOrDefault("ARACHNE_FRAGMENT",0L),"Old holograms outside reward window ignored");
        t.updateLocation(true,true,false,"Hub","elsewhere",now+46_100);
        t.updateLocation(true,true,true,"Arachne's Sanctuary","sidebar",now+46_200);
        t.observeLootStand(id(8),"§fString x10",now+46_201);
        eq(20,t.ledger.stats(false).loot().getOrDefault("STRING",0L),"Leaving arena clears reward window");
        System.out.println("PASS: " + checks + " reward, summon and duplicate-suppression checks.");
    }
}

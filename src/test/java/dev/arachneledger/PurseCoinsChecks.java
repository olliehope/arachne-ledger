package dev.arachneledger;

import java.nio.file.Files;
import java.util.List;

/** Repeated sidebar snapshots, packet ordering and journal integration. */
public final class PurseCoinsChecks {
    private static int checks;
    private static final long BASE = 1_000_000;
    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001)
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }
    private static void yes(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static List<String> row(double purse, double gain) {
        return List.of("Purse: " + purse + (gain > 0 ? " (+" + gain + ")" : ""));
    }
    public static void main(String[] args) throws Exception {
        var screenshot = PurseCoins.parse(List.of("§fPurse: §640,806,301 §e(+2,317)"));
        yes(screenshot != null, "User's yellow Scavenger display parses");
        eq(40806301, screenshot.purse(), "Comma purse value");
        eq(2317, screenshot.gain(), "Comma gain value");
        eq(1.5, PurseCoins.parse(List.of("Piggy: 20.50 (+1.50)")).gain(), "Piggy and decimals");
        eq(0, PurseCoins.parse(List.of("Purse: 20 (-10)")).gain(), "Loss annotation preserves baseline");
        for (String bad : List.of("Purse: NaN (+10)", "Purse: -1 (+10)",
                "Player: Purse: 20 (+10)", "Purse: 20 (+Infinity)", "Bank: 20 (+10)"))
            yes(PurseCoins.parse(List.of(bad)) == null, "Reject unrelated/invalid line: " + bad);

        var coins = new PurseCoins();
        eq(0, coins.observe(row(1000, 100), true, BASE), "Initial displayed gain establishes baseline");
        eq(100, coins.observe(row(1100, 100), true, BASE+500), "First actual gain");
        for (int i=1; i<=12; i++)
            eq(0, coins.observe(row(1100, 100), true, BASE+500+i*500), "Repeated polls never duplicate");
        eq(100, coins.observe(row(1200, 100), true, BASE+7000), "Same amount can be a distinct gain");
        eq(50, coins.observe(row(1250, 150), true, BASE+7500), "Cumulative annotation capped by new purse increase");
        eq(0, coins.observe(row(1250, 200), true, BASE+8000), "Annotation alone is not income");
        eq(0, coins.observe(row(1300, 0), true, BASE+8500), "Wait for delayed annotation");
        eq(50, coins.observe(row(1300, 50), true, BASE+9000), "Delayed annotation paired once");
        eq(0, coins.observe(row(1300, 50), true, BASE+9500), "Delayed annotation not duplicated");
        eq(0, coins.observe(row(1400, 0), true, BASE+10000), "Unmarked gain held briefly");
        eq(0, coins.observe(row(1400, 100), true, BASE+12500), "Unmarked gain expires");
        eq(0, coins.observe(row(1200, 100), true, BASE+13000), "Purse spending is not income");
        eq(20, coins.observe(row(1220, 20), true, BASE+13500), "Spending does not suppress next reward");
        eq(0, coins.observe(row(2000, 780), false, BASE+14000), "Menus/idle changes ignored");
        eq(0, coins.observe(row(2010, 10), true, BASE+14500), "Entering eligibility starts a fresh baseline");
        eq(10, coins.observe(row(2020, 10), true, BASE+15000), "Next eligible reward accepted");
        eq(0, coins.observe(List.of(), true, BASE+15500), "Missing sidebar resets");
        eq(0, coins.observe(row(50000, 47980), true, BASE+16000), "Returning sidebar baseline protects warps");
        coins.reset();
        eq(0, coins.observe(row(100000, 50000), true, BASE+17000), "Explicit context reset protects account/session changes");
        coins.reset();
        coins.observe(row(1000,50),true,BASE);
        eq(50,coins.observe(row(1200,50),true,BASE+500),"Old annotation may precede updated total");
        eq(0,coins.observe(row(1200,50),true,BASE+600),"Unchanged old annotation cannot consume remaining delta");
        eq(150,coins.observe(row(1200,200),true,BASE+700),"Updated annotation recovers uncredited delta");
        eq(0,coins.observe(row(1200,200),true,BASE+800),"Recovered delta not counted twice");
        eq(0,coins.observe(List.of("Purse: 1100 (-100)"),true,BASE+900),"Loss keeps the reduced baseline");
        eq(50,coins.observe(row(1150,50),true,BASE+1000),"Reward after loss uses reduced baseline");

        var root = Files.createTempDirectory("arachne-coins-");
        var t = new Tracker(root); t.account("account");
        t.updateLocation(true,true,true,"Arachne's Sanctuary","fixture",BASE); t.tick(BASE,true);
        t.observePurse(row(1000,0),false,BASE);
        t.message("[BOSS] Arachne: With your sacrifice.","Player",BASE+100);
        t.observePurse(row(1000,0),false,BASE+200);
        t.observePurse(row(1100,100),false,BASE+700);
        eq(100,t.ledger.scavengerCoins(false),"Scavenger subtotal recorded during fight");
        eq(100,t.ledger.stats(false).revenue(),"Income counted exactly once in revenue");
        long id = t.ledger.fights.getFirst().id;
        eq(100,t.ledger.fightScavengerCoins(id),"Coins associated with current fight");
        eq(100,t.ledger.fightStats(id).profit(),"Fight net includes coin income");
        t.observePurse(row(1200,100),true,BASE+800);
        t.observePurse(row(1200,100),false,BASE+900);
        eq(100,t.ledger.stats(false).profit(),"NPC menu update does not add sale income");
        t.observePurse(row(201200,100),false,BASE+1400);
        eq(100,t.ledger.scavengerCoins(false),"Delayed NPC sale excluded after closing the menu");
        t.observePurse(row(201200,100),false,BASE+3000);
        t.message("ARACHNE DOWN!","Player",BASE+3100);
        t.message("Your Damage: 10000 (Position #1)","Player",BASE+3110);
        t.observePurse(row(203517,2317),false,BASE+3500);
        t.observePurse(row(203517,2317),false,BASE+4000);
        eq(2417,t.ledger.fightScavengerCoins(id),"Death rewards paired to completed fight");
        t.tick(BASE+7000,true);
        var reports = t.drainKillSummaries();
        eq(1,reports.size(),"Qualified death produces one summary");
        eq(2417,reports.getFirst().income(),"Chat income already includes coins");
        eq(2417,reports.getFirst().scavengerCoins(),"Chat exposes the coin subtotal");
        yes(ClientMessages.killSummary(reports.getFirst()).getString().contains("Scavenger"),"Coin subtotal appears in local chat");
        t.observePurse(row(203617,100),false,BASE+14000);
        eq(2417,t.ledger.scavengerCoins(true),"Post-death window excludes unrelated late income");
        t.save();
        var reopened = new Tracker(root); reopened.account("account");
        eq(2417,reopened.ledger.scavengerCoins(true),"Coin journal survives reload");
        eq(2417,reopened.ledger.stats(true).graph().getLast().profit(),"Graph includes coins once");
        t.newSession();
        eq(0,t.ledger.scavengerCoins(false),"Session reset removes old subtotal from session");
        eq(2417,t.ledger.scavengerCoins(true),"Session reset preserves total coin income");
        t.record(Ledger.Kind.INCOME,PurseCoins.ITEM,1,10,"scoreboard",BASE+15000);
        eq(10,t.ledger.scavengerCoins(false),"Current session subtotal");
        yes(t.undo(),"Coin entry can be undone");
        eq(0,t.ledger.scavengerCoins(false),"Undo updates subtotal without stale cache");
        shortMenu();
        System.out.println("Purse coins checks passed: " + checks);
    }

    private static void shortMenu() throws Exception {
        var tracker = new Tracker(Files.createTempDirectory("arachne-short-menu-"));
        tracker.account("account");
        tracker.updateLocation(true,true,true,"Arachne's Sanctuary","fixture",BASE);
        tracker.message("[BOSS] Arachne: With your sacrifice.","Player",BASE+100);
        tracker.observePurse(row(1000,0),false,BASE+200);
        tracker.observePurse(row(1100,100),false,BASE+700);
        eq(100,tracker.ledger.scavengerCoins(false),"Fixture has a continuous eligible purse baseline");
        // These menu transitions fall between two of the adapter's half-second polls.
        tracker.observeMenu(true,BASE+800);
        tracker.observeMenu(false,BASE+900);
        tracker.observePurse(row(201100,200000),false,BASE+1200);
        eq(100,tracker.ledger.scavengerCoins(false),"Brief NPC menu excludes the next sale snapshot");
        tracker.observePurse(row(201100,200000),false,BASE+2900);
        tracker.observePurse(row(201200,100),false,BASE+3400);
        eq(200,tracker.ledger.scavengerCoins(false),"Genuine rewards resume after the menu cooldown");
        tracker.observeMenu(true,BASE+3500);
        tracker.observeMenu(false,BASE+3600);
        tracker.observePurse(row(401200,200000),false,BASE+9000);
        eq(200,tracker.ledger.scavengerCoins(false),"A stalled snapshot after cooldown still establishes a fresh baseline");
        tracker.observePurse(row(401300,100),false,BASE+9500);
        eq(300,tracker.ledger.scavengerCoins(false),"Fresh reward after a stalled menu is counted once");
    }
}

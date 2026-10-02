package dev.arachneledger;

import java.nio.file.*;
import java.util.*;

/** Regression scenarios, no live Minecraft instance or third-party test runtime required. */
public final class TrackerChecks {
    static int checks;
    static void eq(double expected,double actual,String why){checks++;if(Math.abs(expected-actual)>0.00001)throw new AssertionError(why+": expected "+expected+", got "+actual);}
    static void yes(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void event(Messages.Event expected,String message,String player){yes(Messages.parse(message,player)==expected,"Parse "+message);}
    static void reject(Runnable action,String why){checks++;try{action.run();}catch(IllegalArgumentException ex){return;}throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        Ledger l=new Ledger();
        eq(0,l.stats(false).hourly(),"Empty hourly rate");
        for(int i=0;i<720;i++)l.tick(5000);
        l.add(Ledger.Kind.CRYSTAL,"ARACHNE_CRYSTAL",1,120000,"test",1);
        l.add(Ledger.Kind.LOOT,"SOUL_STRING",44,5000,"test",2);
        l.add(Ledger.Kind.KILL,"ARACHNE",1,0,"test",3);
        var s=l.stats(false);
        eq(220000,s.revenue(),"Drop count times price");eq(120000,s.costs(),"Crystal cost");
        eq(100000,s.profit(),"Net profit");eq(100000,s.hourly(),"One-hour rate");
        eq(1,s.crystals(),"One crystal");eq(1,s.kills(),"One boss");eq(44,s.loot().get("SOUL_STRING"),"Loot quantity");
        l.newSession();eq(0,l.stats(false).profit(),"New session clear");eq(0,l.stats(false).elapsed(),"New session time");
        eq(100000,l.stats(true).profit(),"Lifetime preserved");eq(3600000,l.stats(true).elapsed(),"Lifetime time preserved");
        yes(!l.undo(),"Undo cannot erase earlier session");
        l.tick(5000);l.add(Ledger.Kind.CRYSTAL,"ARACHNE_CRYSTAL",2,150000,"test",4);
        eq(-300000,l.stats(false).profit(),"Negative profit supported");
        l.reprice("ARACHNE_CRYSTAL",100000);eq(-200000,l.stats(false).profit(),"Current session reprice");
        eq(320000,l.stats(true).costs(),"Earlier crystal retains its old cost");
        l.add(Ledger.Kind.LOOT,"ARACK",1,0,"test",5);eq(1,l.stats(false).unpriced(),"Unpriced loot flagged");
        l.reprice("ARACK",30000);eq(0,l.stats(false).unpriced(),"Pricing resolves warning");
        eq(-170000,l.stats(false).profit(),"New price included");
        var graph=l.stats(false).graph();eq(l.stats(false).profit(),graph.getLast().profit(),"Graph endpoint equals profit");
        eq(0,graph.getFirst().elapsed(),"Graph starts at session origin");
        yes(l.undo(),"Undo removes latest entry");eq(-200000,l.stats(false).profit(),"Undo updates totals");
        long elapsed=l.activeMillis;l.tick(3_600_000);l.tick(-1);eq(elapsed,l.activeMillis,"Sleep and clock reversal excluded");
        reject(()->Config.amount("NaN"),"Reject NaN");reject(()->Config.amount("Infinity"),"Reject infinity");
        reject(()->Config.amount("-1"),"Reject negative prices");eq(150000,Config.amount("150,000"),"Comma price input");
        reject(()->l.add(Ledger.Kind.LOOT,"STRING",0,3,"test",6),"Reject zero quantity");
        reject(()->l.add(Ledger.Kind.LOOT,"STRING",-2,3,"test",6),"Reject negative quantity");
        l.validate();

        event(Messages.Event.CRYSTAL,"\u2620 [MVP+] Alice placed an Arachne Crystal! Something is awakening!","Alice");
        event(Messages.Event.CRYSTAL,"\u00a74\u2620 \u00a77Alice \u00a7eplaced an Arachne Crystal! Something is awakening!","Alice");
        event(Messages.Event.NONE,"\u2620 Bob placed an Arachne Crystal! Something is awakening!","Alice");
        event(Messages.Event.NONE,"[MVP+] Bob: Alice placed an Arachne Crystal! Something is awakening!","Alice");
        event(Messages.Event.CALLING,"\u2620 Alice placed an Arachne's Calling! Something is awakening! (4/4)","Alice");
        event(Messages.Event.DOWN,"    \u00a76\u00a7lARACHNE DOWN!","Alice");
        event(Messages.Event.NONE,"Party > Alice: ARACHNE DOWN!","Alice");
        event(Messages.Event.SPAWN,"[BOSS] Arachne: With your sacrifice.","Alice");

        Path root=Files.createTempDirectory(Path.of(System.getProperty("test.root","build")),"arachne-checks-");
        Path file=root.resolve("ledger.json");
        Store.write(file,l);
        Ledger loaded=Store.read(file,Ledger.class,Ledger::new,Ledger::validate);
        eq(l.stats(true).profit(),loaded.stats(true).profit(),"Persist lifetime profit");
        eq(l.stats(false).elapsed(),loaded.stats(false).elapsed(),"Persist session timer");
        eq(l.entries.size(),loaded.entries.size(),"Persist history");
        loaded.add(Ledger.Kind.INCOME,"MANUAL",1,99,"test",7);Store.write(file,loaded);
        Files.writeString(file,"{damaged JSON");
        Ledger restored=Store.read(file,Ledger.class,Ledger::new,Ledger::validate);
        eq(l.stats(true).profit(),restored.stats(true).profit(),"Recover previous valid backup");
        yes(Files.list(root).anyMatch(p2->p2.getFileName().toString().contains(".corrupt-")),"Preserve damaged original");
        Config config=new Config();eq(17896,config.effectiveCrystalCost(),"Current default crystal recipe");
        config.prices.put("ARACHNE_FRAGMENT",1000.0);eq(18896,config.effectiveCrystalCost(),"Recipe updates with material prices");
        config.crystalCost=123456;config.crystalConfigured=true;eq(123456,config.effectiveCrystalCost(),"Fixed cost overrides recipe");config.prices.put("ARACK",54321.0);
        Path cf=root.resolve("settings.json");Store.write(cf,config);
        Config saved=Store.read(cf,Config.class,Config::new,Config::validate);
        eq(123456,saved.crystalCost,"Persist crystal price");eq(54321,saved.price("ARACK"),"Persist loot price");

        Tracker t=new Tracker(root.resolve("tracker"));t.account("test-account");t.config.crystalCost=100000;t.config.crystalConfigured=true;
        long time=1_000_000;
        t.tick(time,true);t.message("[BOSS] Arachne: Ahhhh...A Calling...","Alice",time);
        t.tick(time+1000,true);eq(1000,t.ledger.activeMillis,"Spawn timer");
        t.message("\u2620 Bob placed an Arachne Crystal! Something is awakening!","Alice",time+1100);
        eq(0,t.ledger.stats(true).crystals(),"Other player's cost excluded");
        String placed="\u2620 Alice placed an Arachne Crystal! Something is awakening!";
        t.message(placed,"Alice",time+1200);t.message(placed,"Alice",time+1250);
        eq(1,t.ledger.stats(true).crystals(),"Duplicate summon excluded");
        t.pickup("SOUL_STRING",44,time+2000);eq(220000,t.ledger.stats(true).revenue(),"Pickup valued");
        t.message("ARACHNE DOWN!","Alice",time+3000);
        t.message("Your Damage: 1,155,000 (Position #1)","Alice",time+3100);
        t.message("ARACHNE DOWN!","Alice",time+3200);
        t.message("Your Damage: 1,155,000 (Position #1)","Alice",time+3300);
        eq(1,t.ledger.stats(true).kills(),"Kill counted once with positive damage");
        t.message("[BOSS] Arachne: Ahhhh...A Calling...","Alice",time+9000);
        t.message("ARACHNE DOWN!","Alice",time+10_000);
        t.message("Your Damage: 0 (Position #7)","Alice",time+10_100);
        eq(1,t.ledger.stats(true).kills(),"Zero-damage spectating excluded");
        long beforePause=t.ledger.activeMillis;
        t.config.paused=true;t.tick(time+11000,true);t.pickup("SOUL_STRING",99,time+11500);
        eq(220000,t.ledger.stats(true).revenue(),"Pause ignores pickup");eq(beforePause,t.ledger.activeMillis,"Pause stops time");
        t.config.paused=false;t.message("[BOSS] Arachne: Ahhhh...A Calling...","Alice",time+12000);
        t.tick(time+13000,true);eq(beforePause+1000,t.ledger.activeMillis,"Resume excludes pause");
        t.tick(time+14000,false);t.tick(time+15000,false);eq(beforePause+1000,t.ledger.activeMillis,"Outside arena stops timer");
        t.pickup("SOUL_STRING",99,time+15500);eq(220000,t.ledger.stats(true).revenue(),"Outside arena ignores loot");
        t.tick(time+60_000,true);t.pickup("SOUL_STRING",99,time+60_001);
        eq(220000,t.ledger.stats(true).revenue(),"Expired drop window ignores loot");
        t.save();var csv=t.export();yes(Files.readString(csv).contains("SOUL_STRING,44")||Files.readString(csv).contains("\"SOUL_STRING\",44"),"CSV contains ledger");
        t.newSession();eq(0,t.ledger.stats(false).profit(),"Tracker new session");
        eq(120000,t.ledger.stats(true).profit(),"Tracker lifetime net profit");
        t.profile("second");eq(0,t.ledger.stats(true).profit(),"Separate profile");
        t.profile("default");eq(120000,t.ledger.stats(true).profit(),"Switch profile restores ledger");
        reject(()->t.profile("../outside"),"Reject path traversal in profile");
        t.resetContext();yes(!t.acceptsLoot(time+1000),"Disconnect clears loot context");
        Tracker reopened=new Tracker(root.resolve("tracker"));reopened.account("test-account");
        eq(120000,reopened.ledger.stats(true).profit(),"Reopen tracker restores total");eq(0,reopened.ledger.stats(false).profit(),"Reopen preserves session boundary");
        System.out.println("PASS: "+checks+" accounting, parser, timing, persistence and isolation checks.");
    }
}

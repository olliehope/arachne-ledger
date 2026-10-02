package dev.arachneledger;

import java.util.*;

/** Accounting is independent of Minecraft so it can be verified without a game. */
public final class Ledger {
    public enum Kind { LOOT, CRYSTAL, CALLING, KILL, INCOME, EXPENSE }
    public record Entry(long at, long elapsed, Kind kind, String item, long count, double unit, String source, long fightId, long id) {
        public Entry(long at,long elapsed,Kind kind,String item,long count,double unit,String source) { this(at,elapsed,kind,item,count,unit,source,0,0); }
        double income() { return kind == Kind.LOOT || kind == Kind.INCOME ? count * unit : 0; }
        double cost() { return kind == Kind.CRYSTAL || kind == Kind.CALLING || kind == Kind.EXPENSE ? count * unit : 0; }
    }
    public record Point(long elapsed, double profit) {}
    public record Stats(double revenue, double costs, long crystals, long callings, long kills,
                        long elapsed, Map<String, Long> loot, List<Point> graph, long unpriced) {
        public double profit() { return revenue - costs; }
        public double hourly() { return elapsed <= 0 ? 0 : profit() * 3_600_000.0 / elapsed; }
    }
    public int schema = 1;
    public long activeMillis = 0;
    public long sessionMillis = 0;
    public int sessionStart = 0;
    public List<Entry> entries = new ArrayList<>();
    public List<FightRecord> fights = new ArrayList<>();
    public long sessionId = 1, nextFightId = 1, nextEntryId = 1;
    private transient long revision = 0;
    private transient long cachedRevision = -1;
    private transient boolean cachedScope;
    private transient Stats cached;
    private transient double cachedScavengerCoins;
    private transient Analytics.Spending cachedSpending;
    private transient Analytics.Snapshot cachedAnalytics;
    private transient long analyticsRevision = -1;
    private transient long analyticsMillis = -1;
    private transient boolean analyticsScope;
    public long revision() { return revision; }

    public void tick(long delta) {
        // A stalled or suspended client must not add offline hours.
        if (delta > 0 && delta <= 5000) activeMillis += delta;
    }
    public Entry add(Kind kind, String item, long count, double unit, String source, long now) {
        return add(kind,item,count,unit,source,now,0);
    }
    public Entry add(Kind kind, String item, long count, double unit, String source, long now,long fightId) {
        if (kind == null || item == null || source == null || count < 1 || count > 1_000_000_000L)
            throw new IllegalArgumentException("Invalid ledger entry");
        Config.amount(Double.toString(unit));
        Entry entry = new Entry(now, activeMillis, kind, item, count, unit, source, fightId, nextEntryId++);
        entries.add(entry);
        revision++;
        return entry;
    }
    public void newSession() { sessionStart = entries.size(); sessionMillis = activeMillis; sessionId++; revision++; }
    public FightRecord beginFight(long spawned,long minimumDamage) {
        FightRecord record = new FightRecord();record.id=nextFightId++;record.session=sessionId;
        record.spawned=spawned;record.minimumDamage=minimumDamage;record.activeStart=activeMillis;record.activeEnd=activeMillis;
        if(spawned>0)record.spawnActiveMillis=activeMillis;
        fights.add(record);revision++;return record;
    }
    /** A later welcome fills an unknown spawn without adding another fight or rewriting its journal. */
    public boolean confirmFightSpawn(long id,long spawned) {
        if(spawned<=0)throw new IllegalArgumentException("A confirmed spawn needs a timestamp.");
        FightRecord record=fight(id);
        if(record.spawned!=0 || record.died!=0 || record.outcome!=FightRecord.Outcome.FIGHTING)return false;
        record.spawned=spawned;record.spawnActiveMillis=activeMillis;revision++;return true;
    }
    public void associateSummons(Collection<Long> ids,long fightId) {
        Set<Long> selected=new HashSet<>(ids);
        for(int i=0;i<entries.size();i++) {
            Entry e=entries.get(i);
            if(selected.contains(e.id()) && (e.kind()==Kind.CRYSTAL || e.kind()==Kind.CALLING))
                entries.set(i,new Entry(e.at,e.elapsed,e.kind,e.item,e.count,e.unit,e.source,fightId,e.id));
        }
        revision++;
    }
    public FightRecord fight(long id) { return fights.stream().filter(f->f.id==id).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown fight.")); }
    public List<FightRecord> recentFights(boolean total) {
        return fights.reversed().stream().filter(f->total || f.session==sessionId).limit(50).toList();
    }
    public Stats fightStats(long id) {
        FightRecord fight=fight(id);double income=0,costs=0;long crystals=0,callings=0,kills=0,unpriced=0;
        Map<String,Long> loot=new LinkedHashMap<>();List<Point> graph=new ArrayList<>();graph.add(new Point(0,0));
        for(Entry e:entries)if(e.fightId==id) {
            income+=e.income();costs+=e.cost();
            if(e.kind==Kind.CRYSTAL)crystals+=e.count;if(e.kind==Kind.CALLING)callings+=e.count;if(e.kind==Kind.KILL)kills+=e.count;
            if(e.kind==Kind.LOOT){loot.merge(e.item,e.count,Long::sum);if(e.unit==0)unpriced+=e.count;}
            graph.add(new Point(Math.max(0,e.elapsed-fight.activeStart),income-costs));
        }
        return new Stats(income,costs,crystals,callings,kills,Math.max(0,fight.activeEnd-fight.activeStart),Map.copyOf(loot),List.copyOf(graph),unpriced);
    }
    public Map<String,Double> fightLootValues(long id) {
        Map<String,Double> values=new LinkedHashMap<>();
        for(Entry e:entries)if(e.fightId==id && e.kind==Kind.LOOT)values.merge(e.item,e.income(),Double::sum);
        return values;
    }
    public double fightSpend(long id,Kind kind) { return entries.stream().filter(e->e.fightId==id && e.kind==kind).mapToDouble(Entry::cost).sum(); }
    public double scavengerCoins(boolean total) {
        stats(total);
        return cachedScavengerCoins;
    }
    public double fightScavengerCoins(long id) {
        return entries.stream().filter(e -> e.fightId == id && e.kind == Kind.INCOME && e.item.equals(PurseCoins.ITEM))
            .mapToDouble(Entry::income).sum();
    }
    /** Replace a fight's quantity in place so old-session corrections cannot leak into this session. */
    public void setFightLootCount(long fightId,String item,long count,double fallbackUnit,long now) {
        if(!Catalog.ITEMS.containsKey(item) || count<0 || count>1_000_000_000L)throw new IllegalArgumentException("Use a known item and quantity from 0 to 1 billion.");
        Config.amount(Double.toString(fallbackUnit));FightRecord fight=fight(fightId);
        if(fight.outcome==FightRecord.Outcome.FIGHTING || fight.outcome==FightRecord.Outcome.WAITING_DAMAGE)
            throw new IllegalArgumentException("Wait for the fight's damage summary before editing.");
        int insertion=-1;long existingCount=0;double existingValue=0;long elapsed=fight.activeEnd;
        for(int i=0;i<entries.size();i++) {
            Entry e=entries.get(i);
            if(e.fightId==fightId){insertion=i+1;elapsed=e.elapsed;}
            if(e.fightId==fightId && e.kind==Kind.LOOT && e.item.equals(item)){existingCount+=e.count;existingValue+=e.income();}
        }
        double unit=existingCount>0?existingValue/existingCount:fallbackUnit;
        if(insertion<0) {
            insertion=0;
            while(insertion<entries.size() && entries.get(insertion).elapsed<=elapsed)insertion++;
            if(fight.session<sessionId)insertion=Math.min(insertion,sessionStart);
        }
        for(int i=entries.size()-1;i>=0;i--) {
            Entry e=entries.get(i);
            if(e.fightId==fightId && e.kind==Kind.LOOT && e.item.equals(item)) {
                entries.remove(i);if(i<insertion)insertion--;if(i<sessionStart)sessionStart--;
            }
        }
        if(count>0) {
            long lower=insertion>0?entries.get(insertion-1).elapsed:0;
            long upper=insertion<entries.size()?entries.get(insertion).elapsed:activeMillis;
            elapsed=Math.max(lower,Math.min(upper,elapsed));
            entries.add(insertion,new Entry(now,elapsed,Kind.LOOT,item,count,unit,"fight_edit",fightId,nextEntryId++));
            if(insertion<sessionStart || (insertion==sessionStart && fight.session<sessionId))sessionStart++;
        }
        revision++;
    }
    public boolean closeOpenFights() {
        boolean changed=false;
        for(FightRecord fight:fights) {
            if(fight.outcome==FightRecord.Outcome.FIGHTING){fight.outcome=FightRecord.Outcome.INTERRUPTED;changed=true;}
            else if(fight.outcome==FightRecord.Outcome.WAITING_DAMAGE){fight.outcome=FightRecord.Outcome.MISSING_DAMAGE;changed=true;}
        }
        return changed;
    }
    public boolean undo() {
        if (entries.size() <= sessionStart) return false;
        entries.removeLast(); revision++; return true;
    }
    public void reprice(String id, double unit) {
        Config.amount(Double.toString(unit));
        for (int i = sessionStart; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.item.equals(id)) entries.set(i, new Entry(e.at, e.elapsed, e.kind, e.item, e.count, unit, e.source,e.fightId,e.id));
        }
        revision++;
    }
    public Stats stats(boolean total) {
        if (cached == null || cachedRevision != revision || cachedScope != total) {
            double revenue = 0, costs = 0, scavenger = 0;
            double crystalSpend = 0, callingSpend = 0, otherSpend = 0;
            long crystals = 0, callings = 0, kills = 0, unpriced = 0;
            Map<String, Long> loot = new LinkedHashMap<>();
            Map<String, Double> lootRevenue = new LinkedHashMap<>();
            List<Point> graph = new ArrayList<>(); graph.add(new Point(0, 0));
            long origin = total ? 0 : sessionMillis;
            for (int i = total ? 0 : sessionStart; i < entries.size(); i++) {
                Entry e = entries.get(i);
                revenue += e.income(); costs += e.cost();
                if (e.kind == Kind.INCOME && e.item.equals(PurseCoins.ITEM)) scavenger += e.income();
                if (e.kind == Kind.CRYSTAL) { crystals += e.count; crystalSpend += e.cost(); }
                if (e.kind == Kind.CALLING) { callings += e.count; callingSpend += e.cost(); }
                if (e.kind == Kind.EXPENSE) otherSpend += e.cost();
                if (e.kind == Kind.KILL) kills += e.count;
                if (e.kind == Kind.LOOT) {
                    loot.merge(e.item, e.count, Long::sum);
                    lootRevenue.merge(e.item, e.income(), Double::sum);
                    if (e.unit == 0) unpriced += e.count;
                }
                graph.add(new Point(Math.max(0, e.elapsed - origin), revenue - costs));
            }
            cached = new Stats(revenue, costs, crystals, callings, kills, 0,
                Collections.unmodifiableMap(loot), List.copyOf(graph), unpriced);
            cachedScavengerCoins = scavenger;
            cachedSpending = new Analytics.Spending(crystalSpend, callingSpend, otherSpend,
                Collections.unmodifiableMap(lootRevenue));
            cachedRevision = revision; cachedScope = total;
        }
        return new Stats(cached.revenue, cached.costs, cached.crystals, cached.callings, cached.kills,
            activeMillis - (total ? 0 : sessionMillis), cached.loot, cached.graph, cached.unpriced);
    }
    /** Scope affects totals; projected pace always comes from this session's recent active time. */
    public Analytics.Snapshot analytics(boolean total) {
        Stats selected = stats(total);
        if (cachedAnalytics == null || analyticsRevision != revision || analyticsMillis != activeMillis
                || analyticsScope != total) {
            cachedAnalytics = Analytics.calculate(this, selected, cachedSpending);
            analyticsRevision = revision; analyticsMillis = activeMillis; analyticsScope = total;
        }
        return cachedAnalytics;
    }
    public void validate() {
        revision++;
        if (schema != 1 || entries == null || activeMillis < 0 || sessionMillis < 0 || sessionMillis > activeMillis
                || sessionStart < 0 || sessionStart > entries.size()) throw new IllegalArgumentException("Invalid ledger schema");
        if(fights==null)fights=new ArrayList<>();if(sessionId<1)sessionId=1;
        Set<Long> fightIds=new HashSet<>();long maxFightId=0;
        for(FightRecord fight:fights){if(fight==null)throw new IllegalArgumentException("Invalid fight history");fight.validate();if(!fightIds.add(fight.id)||fight.session>sessionId||fight.activeEnd>activeMillis||fight.spawnActiveMillis>activeMillis)throw new IllegalArgumentException("Invalid fight identity");maxFightId=Math.max(maxFightId,fight.id);}
        nextFightId=Math.max(nextFightId,maxFightId+1);
        long last = 0,maxEntryId=0;for(Entry e:entries)if(e!=null)maxEntryId=Math.max(maxEntryId,e.id);nextEntryId=Math.max(nextEntryId,maxEntryId+1);
        Set<Long> entryIds=new HashSet<>();
        for (int i=0;i<entries.size();i++) {
            Entry e=entries.get(i);
            if (e == null || e.kind == null || e.item == null || e.source == null || e.count < 1 || e.count > 1_000_000_000L
                    || e.elapsed < last || e.elapsed > activeMillis || e.fightId<0 || (e.fightId>0 && !fightIds.contains(e.fightId))) throw new IllegalArgumentException("Invalid ledger entry");
            if(e.id==0){e=new Entry(e.at,e.elapsed,e.kind,e.item,e.count,e.unit,e.source,e.fightId,nextEntryId++);entries.set(i,e);}
            if(e.id<1 || !entryIds.add(e.id))throw new IllegalArgumentException("Invalid entry identity");
            Config.amount(Double.toString(e.unit)); last = e.elapsed;
        }
        cached = null; cachedRevision = -1; cachedSpending = null;
        cachedAnalytics = null; analyticsRevision = -1; analyticsMillis = -1;
    }
}

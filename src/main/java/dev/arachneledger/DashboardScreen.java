package dev.arachneledger;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A compact text tracker with a larger view for details and history. */
public final class DashboardScreen extends Screen {
    private final Screen parent;
    private final Tracker t=ArachneLedger.tracker;
    private int x,y,w,h,scroll;
    private long confirmUntil,noteUntil;
    private String note="";
    private record Row(String label,String value,int labelColor,int valueColor,String explanation) {}
    public DashboardScreen(Screen parent){super(Component.literal("Arachne Profit Tracker"));this.parent=parent;}
    @Override protected void init(){
        w=Math.min(470,width-24);h=Math.min(328,height-20);x=(width-w)/2;y=(height-h)/2;
        button(x+w-122,y+8,39,18,"Total",t.config.total,"Show lifetime totals.",()->scope(true));
        button(x+w-80,y+8,68,18,"This session",!t.config.total,"Show this session's totals.",()->scope(false));
        String[] tabs={"Overview","Drops","Graph","Fights"};int[] sizes={66,48,48,50};int xx=x+8;
        for(int i=0;i<tabs.length;i++){
            int n=i;
            button(xx,y+36,sizes[i],18,tabs[i],i==3?t.config.dashboardFights:!t.config.dashboardFights && t.config.view.ordinal()==i,"Change dashboard view.",()->{
                t.config.dashboardFights=n==3;if(n<3)t.config.view=Config.View.values()[n];t.saveConfig();scroll=0;rebuildWidgets();
            });xx+=sizes[i]+4;
        }
        if(!t.config.dashboardFights&&t.config.view==Config.View.GRAPH)
            button(x+w-60,y+36,52,18,"Options",false,"Choose graph lines, text and spawn markers.",
                ()->minecraft.setScreen(new GraphOptionsScreen(this)));
        int fw=(w-16)/6;
        String[] labels={t.config.paused?"Resume":"Pause","Prices","Adjust","HUD",confirmUntil>System.currentTimeMillis()?"Confirm":"Reset","Close"};
        String[] tips={"Pause automatic tracking.","Edit item values and summoning costs.","Correct loot or coins, undo an entry, export CSV.","Move or resize the overlay.","Start a new session; confirm with a second click.","Return to the game."};
        Runnable[] actions={()->{t.togglePause();rebuildWidgets();},()->minecraft.setScreen(new SettingsScreen(this)),
            ()->minecraft.setScreen(new AdjustScreen(this)),()->minecraft.setScreen(new HudEditorScreen(this)),()->{
                if(confirmUntil>System.currentTimeMillis()){t.newSession();confirmUntil=0;setNote("New session started.");}
                else{confirmUntil=System.currentTimeMillis()+5000;setNote("Click Confirm to start a new session.");}
                scroll=0;rebuildWidgets();},this::onClose};
        for(int i=0;i<labels.length;i++)button(x+8+i*fw,y+h-24,fw,18,labels[i],false,tips[i],actions[i]);
    }
    private void scope(boolean total){t.config.total=total;t.saveConfig();scroll=0;rebuildWidgets();}
    private void button(int xx,int yy,int ww,int hh,String label,boolean selected,String tip,Runnable action){
        var b=new FlatButton(xx,yy,ww,hh,label,selected,action);
        b.setTooltip(Tooltip.create(Component.literal(tip)));addRenderableWidget(b);
    }
    private void setNote(String text){note=text;noteUntil=System.currentTimeMillis()+6000;}
    @Override public void tick(){if(confirmUntil!=0&&System.currentTimeMillis()>confirmUntil){confirmUntil=0;rebuildWidgets();}}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0x80000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        extractBackground(g,mx,my,delta);g.fill(x,y,x+w,y+h,0xDF101010);
        g.text(font,Component.literal("Arachne Profit Tracker").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD),x+12,y+12,Hud.WHITE,true);
        String state=t.timerState();
        boolean graphView=!t.config.dashboardFights&&t.config.view==Config.View.GRAPH;
        if(!graphView||t.config.graph.showScope)
            g.text(font,font.plainSubstrByWidth(state+" · "+t.config.profile,w-24),x+12,y+26,Graph.MUTED,true);
        g.horizontalLine(x+12,x+w-12,y+59,0xFF444444);
        var s=t.ledger.stats(t.config.total);var a=t.ledger.analytics(t.config.total);
        String projectionTip="Current-session pace from the last "+Hud.shortTime(a.windowMillis())+" of active time. Includes costs and waiting; needs 60 active seconds and one recent kill.";
        GraphData.Snapshot graph=graphView?GraphData.build(t.ledger,t.config.total,t.config.graph):null;
        int top;
        if(graphView)top=drawGraphSummary(g,graph,mx,my);
        else {
        line(g,"Total profit",Format.coins(s.profit())+" coins",y+69,s.profit()>=0?Graph.GREEN:Graph.RED,
            "Recorded income minus summoning costs and other expenses.",mx,my);
        line(g,"Profit / hour",s.elapsed()<1000?"--":Format.coins(s.hourly())+" coins",y+82,Hud.GOLD,
            "Net profit divided by active time in the selected scope.",mx,my);
        line(g,"Projected / hour",a.projectionReady()?Format.coins(a.projectedHourly())+" coins":"Warming up",y+95,Hud.GOLD,projectionTip,mx,my);
        g.horizontalLine(x+12,x+w-12,y+110,0xFF333333);
        top=y+121;
        }
        int bottom=y+h-(graphView?47:52);
        if(t.config.dashboardFights){
            drawFights(g,top,bottom,mx,my);
        }else if(t.config.view==Config.View.GRAPH){
            Graph.draw(g,font,graph,t.config.graph,x+10,top-4,w-20,Math.max(24,bottom-top+4),mx,my,true);
        }else if(t.config.view==Config.View.DETAILED){
            List<Row> rows=new ArrayList<>();
            for(String id:Hud.sortedLoot(a,s)){
                double value=a.lootRevenue().getOrDefault(id,0.0);
                rows.add(new Row(String.format(Locale.ROOT,"%,d",s.loot().get(id))+"x "+Catalog.name(id),
                    value==0?"Unpriced":Format.coins(value)+" coins",Hud.itemColor(id),value==0?Graph.MUTED:Hud.GOLD,
                    Catalog.name(id)+": "+s.loot().get(id)+" items, valued at "+Format.coins(value)+" recorded coins."));
            }
            if(rows.isEmpty())g.text(font,"No drops recorded yet.",x+12,top,Graph.MUTED,true);
            else drawRows(g,rows,top,bottom,mx,my);
        }else{
            List<Row> rows=new ArrayList<>();
            rows.add(row("Rewards + other income",Format.coins(s.revenue())+" coins",Hud.GOLD,"Item values, scoreboard coins and manual income recorded in this scope."));
            rows.add(row("Scavenger coins",Format.coins(t.ledger.scavengerCoins(t.config.total))+" coins",Hud.GOLD,"Already included in profit. Positive purse changes paired with the yellow (+coins) display during fights or within 10 seconds of death. Other marked coin rewards cannot be distinguished from Scavenger."));
            rows.add(row("Crystal costs ("+s.crystals()+")",Format.coins(a.crystalSpend())+" coins",Graph.RED,"Your own crystal placements and their recorded costs."));
            rows.add(row("Calling costs ("+s.callings()+")",Format.coins(a.callingSpend())+" coins",Graph.RED,"Your own Calling placements and their recorded costs."));
            rows.add(row("All costs",Format.coins(s.costs())+" coins",Graph.RED,"Crystals, Callings and manual expenses."));
            rows.add(row("Bosses killed",Long.toString(s.kills()),Hud.TITLE,"Arachne kills with at least "+t.config.minimumDamage+" reported damage. Use /arachne mindamage to change the threshold."));
            rows.add(row("Kills / hour",Hud.decimal(a.killsPerHour()),Hud.TITLE,"Kills divided by active time."));
            rows.add(row("Average profit / kill",s.kills()==0?"--":Format.coins(a.averageNetPerKill())+" coins",Hud.GOLD,"Net profit divided by participating kills."));
            rows.add(row("Average time / kill",s.kills()==0?"--":Hud.shortTime((long)a.averageMillisPerKill()),0xFF55FFFF,"Active time per kill, including waits."));
            rows.add(row("Active time",Hud.shortTime(s.elapsed()),0xFF55FFFF,"Runs from Arachne's spawn until 60 seconds after death. Another spawn resumes it. Pauses and time outside the arena are excluded."));
            rows.add(row("Projection sample",Hud.shortTime(a.windowMillis())+" / "+a.windowKills()+" kills",Graph.MUTED,projectionTip));
            rows.add(row("Next crystal",Format.coins(t.config.effectiveCrystalCost())+" coins",Graph.RED,"Current crystal cost for future placements."));
            rows.add(row("Unpriced drops",Long.toString(s.unpriced()),s.unpriced()>0?Hud.TITLE:Graph.MUTED,"Set prices and use Reprice session to value these drops."));
            drawRows(g,rows,top,bottom,mx,my);
        }
        String footer=System.currentTimeMillis()<noteUntil?note:!t.error.isEmpty()?"Storage error - check logs":s.unpriced()>0?s.unpriced()+" unpriced drop"+(s.unpriced()==1?"":"s")+" · set Prices":!a.projectionReady()?"Projection: waiting for 60s and a kill":a.windowKills()<3?"Projection: small sample":t.status();
        if(graphView&&System.currentTimeMillis()>=noteUntil&&t.error.isEmpty()) {
            List<String> parts=new ArrayList<>();
            if(t.config.graph.showActiveTime)parts.add("Active "+Hud.shortTime(graph.elapsed()));
            if(t.config.graph.showSpawnCount)parts.add("Spawns "+graph.spawnCount());
            if(t.config.graph.showScope)parts.add((t.config.total?"Total":"This session")+" · "+t.status());
            footer=String.join(" · ",parts);
        }
        g.text(font,font.plainSubstrByWidth(footer,w-24),x+12,y+h-42,Graph.MUTED,true);
        if(mx>=x+12&&mx<x+w-12&&my>=y+h-44&&my<y+h-31)g.setTooltipForNextFrame(Component.literal(footer),mx,my);
        g.horizontalLine(x+12,x+w-12,y+h-29,0xFF333333);
        for(var child:children())if(child instanceof net.minecraft.client.gui.components.Renderable r)r.extractRenderState(g,mx,my,delta);
    }
    private int drawGraphSummary(GuiGraphicsExtractor g,GraphData.Snapshot data,int mx,int my) {
        var p=t.config.graph;var metric=data.primary();int yy=y+65;
        if(!data.series().isEmpty()) {
            String description=switch(metric){
                case PROFIT->"All recorded income minus all costs.";
                case LOOT->"Recorded item-drop value only; excludes Scavenger and manual coin income.";
                case COSTS->"All recorded summoning costs and other expenses.";
            };
            if(p.showTotal){line(g,metric.totalLabel(),Format.coins(data.value(metric))+" coins",yy,metric.color(data.value(metric)),description,mx,my);yy+=11;}
            if(p.showHourly){line(g,metric.hourlyLabel(),data.elapsed()<1000?"--":Format.coins(data.hourly(metric))+" coins",yy,data.elapsed()<1000?Graph.MUTED:metric.color(data.hourly(metric)),description+" Divided by active time in this scope.",mx,my);yy+=11;}
            if(p.showProjectedHourly){line(g,metric.projectedLabel(),data.projectionReady()?Format.coins(data.projectedHourly(metric))+" coins":"Warming up",yy,data.projectionReady()?metric.color(data.projectedHourly(metric)):Graph.MUTED,"Observed current-session pace over up to five active minutes; needs 60 seconds and a recent qualifying kill.",mx,my);yy+=11;}
            if(p.showProjectedTotal){line(g,metric.projectedTotalLabel(),data.projectionReady()?Format.coins(data.projectedTotal(metric))+" coins":"Warming up",yy,data.projectionReady()?metric.color(data.projectedTotal(metric)):Graph.MUTED,"This scope's current value plus five more active minutes at the observed recent pace. An estimate, not a guaranteed reward.",mx,my);yy+=11;}
        }
        g.horizontalLine(x+12,x+w-12,yy+4,0xFF333333);
        return yy+15;
    }
    private void drawFights(GuiGraphicsExtractor g,int top,int bottom,int mx,int my) {
        var fights=t.ledger.recentFights(t.config.total);
        if(fights.isEmpty()){g.text(font,"No fights recorded yet.",x+12,top,Graph.MUTED,true);return;}
        int visible=Math.max(1,(bottom-top)/25);scroll=Math.max(0,Math.min(scroll,Math.max(0,fights.size()-visible)));
        for(int i=scroll;i<Math.min(fights.size(),scroll+visible);i++) {
            FightRecord fight=fights.get(i);var stats=t.ledger.fightStats(fight.id);int yy=top+(i-scroll)*25;
            boolean hovered=mx>=x+10 && mx<x+w-10 && my>=yy-2 && my<yy+22;
            if(hovered)g.fill(x+10,yy-2,x+w-10,yy+22,0x30666666);
            String value=Format.coins(stats.profit())+" coins";int valueX=x+w-16-font.width(value);
            String label="Fight #"+fight.id+" · "+FightDetailsScreen.duration(fight);
            g.text(font,font.plainSubstrByWidth(label,Math.max(25,valueX-x-24)),x+12,yy,Hud.TITLE,true);
            g.text(font,value,valueX,yy,stats.profit()>=0?Graph.GREEN:Graph.RED,true);
            String detail=fight.reason(stats.kills()>0)+(fight.damage>=0?" · "+String.format(Locale.ROOT,"%,d",fight.damage)+" damage":"");
            g.text(font,font.plainSubstrByWidth(detail,w-28),x+12,yy+11,Graph.MUTED,true);
            if(hovered)g.setTooltipForNextFrame(Component.literal(detail+"\n"+FightDetailsScreen.date(fight)+"\nClick for drops, costs and corrections."),mx,my);
        }
        if(fights.size()>visible){int track=bottom-top,thumb=Math.max(6,track*visible/fights.size());int offset=(track-thumb)*scroll/Math.max(1,fights.size()-visible);g.fill(x+w-8,top,x+w-7,bottom,0xFF333333);g.fill(x+w-8,top+offset,x+w-7,top+offset+thumb,0xFF888888);}
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick) {
        if(t.config.dashboardFights && event.button()==0 && event.x()>=x+10 && event.x()<x+w-10 && event.y()>=y+119 && event.y()<y+h-52) {
            var fights=t.ledger.recentFights(t.config.total);int index=scroll+(int)((event.y()-(y+121))/25);
            if(index>=0 && index<fights.size()){minecraft.setScreen(new FightDetailsScreen(this,fights.get(index).id));return true;}
        }
        return super.mouseClicked(event,doubleClick);
    }
    private Row row(String label,String value,int color,String tip){return new Row(label,value,Graph.MUTED,color,tip);}
    private void line(GuiGraphicsExtractor g,String label,String value,int yy,int color,String tip,int mx,int my){
        int valueX=x+w-12-font.width(value);
        g.text(font,font.plainSubstrByWidth(label+":",Math.max(25,valueX-x-24)),x+12,yy,Graph.MUTED,true);
        g.text(font,value,valueX,yy,color,true);
        if(mx>=x+12&&mx<x+w-12&&my>=yy-1&&my<yy+10)g.setTooltipForNextFrame(Component.literal(tip),mx,my);
    }
    private void drawRows(GuiGraphicsExtractor g,List<Row> rows,int top,int bottom,int mx,int my){
        int visible=Math.max(1,(bottom-top)/13);scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visible)));
        g.enableScissor(x+10,top-1,x+w-10,bottom);
        for(int i=scroll;i<Math.min(rows.size(),scroll+visible);i++){
            Row row=rows.get(i);int yy=top+(i-scroll)*13;int valueX=x+w-16-font.width(row.value());
            g.text(font,font.plainSubstrByWidth(row.label(),Math.max(25,valueX-x-24)),x+12,yy,row.labelColor(),true);
            g.text(font,row.value(),valueX,yy,row.valueColor(),true);
            if(mx>=x+10&&mx<x+w-10&&my>=yy-1&&my<yy+11)g.setTooltipForNextFrame(Component.literal(row.explanation()),mx,my);
        }
        g.disableScissor();
        if(rows.size()>visible){
            int track=bottom-top,thumb=Math.max(6,track*visible/rows.size());
            int offset=(track-thumb)*scroll/Math.max(1,rows.size()-visible);
            g.fill(x+w-8,top,x+w-7,bottom,0xFF333333);g.fill(x+w-8,top+offset,x+w-7,top+offset+thumb,0xFF888888);
        }
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){scroll=Math.max(0,scroll-(int)Math.signum(vertical));return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){t.save();minecraft.setScreen(parent);}
}

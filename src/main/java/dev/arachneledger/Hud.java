package dev.arachneledger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The live overlay is independent of the dashboard's selected tab. */
public final class Hud {
    static final int WIDTH=224, WHITE=0xFFFFFFFF, GOLD=0xFFFFAA00, TITLE=0xFFFFFF55;
    public record Bounds(int x,int y,int width,int height,float scale) {
        boolean contains(double mx,double my){return mx>=x&&my>=y&&mx<x+width&&my<y+height;}
    }
    public static void draw(GuiGraphicsExtractor g) {
        var mc=Minecraft.getInstance();var t=ArachneLedger.tracker;
        if(t==null||!t.config.hud||mc.player==null||mc.options.hideGui)return;
        if(mc.screen!=null&&!(mc.screen instanceof ChatScreen))return;
        if(!t.inArena&&!(t.config.hudAlwaysShow&&t.inSkyblock))return;
        drawPanel(g,t,bounds(t.config,g.guiWidth(),g.guiHeight()),false);
    }
    static int panelHeight(Config c){
        if(c.hudView!=Config.View.GRAPH)return c.hudView==Config.View.COMPACT?82:180;
        var p=c.graph;int rows=0;
        if(!p.enabledSeries().isEmpty())rows=(p.showTotal?1:0)+(p.showHourly?1:0)+(p.showProjectedHourly?1:0)+(p.showProjectedTotal?1:0);
        int extra=(p.showActiveTime?1:0)+(p.showScope?1:0)+(p.showSpawnCount?1:0);
        return 89+(rows+extra)*11+(rows>0?3:0);
    }
    static Bounds bounds(Config c,int width,int height) {
        int baseHeight=panelHeight(c);
        float scale=(float)Math.min(c.hudScale,Math.min((width-8.0)/WIDTH,(height-8.0)/baseHeight));
        scale=Math.max(.25f,scale);
        int ww=(int)Math.ceil(WIDTH*scale),hh=(int)Math.ceil(baseHeight*scale);
        int maxX=Math.max(0,width-ww-4),maxY=Math.max(0,height-hh-4);
        int xx=c.hudX<0?(c.corner%2==0?8:maxX-4):(int)Math.round(c.hudX*maxX);
        int yy=c.hudY<0?(c.corner<2?8:maxY-28):(int)Math.round(c.hudY*maxY);
        return new Bounds(Math.max(4,Math.min(maxX,xx)),Math.max(4,Math.min(maxY,yy)),ww,hh,scale);
    }
    static void move(Config c,int width,int height,double x,double y) {
        Bounds b=bounds(c,width,height);
        c.hudX=Math.max(0,Math.min(1,x/Math.max(1,width-b.width()-4)));
        c.hudY=Math.max(0,Math.min(1,y/Math.max(1,height-b.height()-4)));
    }
    static void drawPanel(GuiGraphicsExtractor g,Tracker t,Bounds b,boolean editing) {
        var s=t.ledger.stats(t.config.total);var a=t.ledger.analytics(t.config.total);var f=Minecraft.getInstance().font;
        int h=panelHeight(t.config);
        g.pose().pushMatrix();g.pose().translate(b.x(),b.y());g.pose().scale(b.scale());
        if(t.config.hudBackground)g.fill(0,0,WIDTH,h,0x80000000);
        g.text(f,Component.literal("Arachne Profit Tracker").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD),4,4,WHITE,true);
        int yy=19;
        if(t.config.hudView==Config.View.GRAPH){
            drawGraphPanel(g,t,f,yy);
            if(editing)g.outline(0,0,WIDTH,h,0x80666666);
            g.pose().popMatrix();return;
        }
        if(t.config.hudView==Config.View.DETAILED){
            List<String> ids=sortedLoot(a,s);
            for(String id:ids.stream().limit(3).toList()){
                String count=String.format(Locale.ROOT,"%,d",s.loot().get(id))+"x ";
                double value=a.lootRevenue().getOrDefault(id,0.0);
                String price=value==0?"--":Format.coins(value);
                int available=WIDTH-18-f.width(price)-f.width(count);
                g.text(f,count,4,yy,Graph.MUTED,true);
                g.text(f,f.plainSubstrByWidth(Catalog.name(id),Math.max(20,available)),4+f.width(count),yy,itemColor(id),true);
                right(g,f,price,WIDTH-4,yy,value==0?Graph.MUTED:GOLD);yy+=11;
            }
            if(ids.isEmpty()){g.text(f,"No drops recorded yet",4,yy,Graph.MUTED,true);yy+=11;}
            if(ids.size()>3){g.text(f,"+ "+(ids.size()-3)+" other drops",4,yy,Graph.MUTED,true);yy+=11;}
            row(g,f,"Scavenger coins",Format.coins(t.ledger.scavengerCoins(t.config.total)),yy,GOLD);yy+=11;
            row(g,f,"Crystal costs ("+s.crystals()+")",Format.coins(a.crystalSpend()),yy,Graph.RED);yy+=11;
            row(g,f,"Calling costs ("+s.callings()+")",Format.coins(a.callingSpend()),yy,Graph.RED);yy+=11;
            row(g,f,"Bosses killed",Long.toString(s.kills()),yy,TITLE);yy+=12;
        }
        row(g,f,"Total profit",Format.coins(s.profit()),yy,s.profit()>=0?Graph.GREEN:Graph.RED);yy+=11;
        row(g,f,"Profit / hour",s.elapsed()<1000?"--":Format.coins(s.hourly()),yy,GOLD);yy+=11;
        row(g,f,"Projected / hour",a.projectionReady()?Format.coins(a.projectedHourly()):"--",yy,GOLD);yy+=t.config.hudView==Config.View.DETAILED?10:14;
        row(g,f,"Active time",shortTime(s.elapsed()),yy,0xFF55FFFF);yy+=11;
        row(g,f,"Display",displayMode(t),yy,Graph.MUTED);yy+=11;
        if(t.config.hudView==Config.View.DETAILED && s.unpriced()>0)g.text(f,s.unpriced()+" unpriced drop"+(s.unpriced()==1?"":"s"),4,yy,TITLE,true);
        if(editing)g.outline(0,0,WIDTH,h,0x80666666);
        g.pose().popMatrix();
    }
    private static void drawGraphPanel(GuiGraphicsExtractor g,Tracker t,Font f,int yy){
        var p=t.config.graph;var data=GraphData.build(t.ledger,t.config.total,p);var metric=data.primary();int start=yy;
        if(!data.series().isEmpty()){
            if(p.showTotal){row(g,f,metric.totalLabel(),Format.coins(data.value(metric)),yy,metric.color(data.value(metric)));yy+=11;}
            if(p.showHourly){row(g,f,metric.hourlyLabel(),data.elapsed()<1000?"--":Format.coins(data.hourly(metric)),yy,data.elapsed()<1000?Graph.MUTED:metric.color(data.hourly(metric)));yy+=11;}
            if(p.showProjectedHourly){row(g,f,metric.projectedLabel(),data.projectionReady()?Format.coins(data.projectedHourly(metric)):"--",yy,data.projectionReady()?metric.color(data.projectedHourly(metric)):Graph.MUTED);yy+=11;}
            if(p.showProjectedTotal){row(g,f,metric.projectedTotalLabel(),data.projectionReady()?Format.coins(data.projectedTotal(metric)):"--",yy,data.projectionReady()?metric.color(data.projectedTotal(metric)):Graph.MUTED);yy+=11;}
        }
        if(yy>start)yy+=3;
        Graph.draw(g,f,data,p,4,yy,WIDTH-8,62,-1,-1,false);yy+=66;
        if(p.showSpawnCount){row(g,f,"Arachne spawns",Long.toString(data.spawnCount()),yy,0xFF7777FF);yy+=11;}
        if(p.showActiveTime){row(g,f,"Active time",shortTime(data.elapsed()),yy,0xFF55FFFF);yy+=11;}
        if(p.showScope)row(g,f,"Display",displayMode(t),yy,Graph.MUTED);
    }
    private static String displayMode(Tracker t){
        String mode=t.config.total?"Total":"This session";
        if(t.config.paused)mode+=" (paused)";else if(!t.inArena)mode+=" (waiting)";
        else if(t.isSummoning())mode+=" (summoning)";else if(t.isAfk())mode+=" (AFK)";else if(t.waitingForSpawn())mode+=" (waiting)";
        return mode;
    }
    static List<String> sortedLoot(Analytics.Snapshot a,Ledger.Stats s){
        List<String> ids=new ArrayList<>(s.loot().keySet());
        ids.sort(Comparator.<String>comparingDouble(id->a.lootRevenue().getOrDefault(id,0.0)).reversed().thenComparing(id->id));
        return ids;
    }
    static int itemColor(String id){return switch(id){
        case "ENCHANTED_STRING","ENCHANTED_SPIDER_EYE","LUXURIOUS_SPOOL","ARACHNE_FANG"->0xFF55FF55;
        case "ARACHNE_FRAGMENT","ARACHNE_SHARD","ARACHNE_HELMET","ARACHNE_CHESTPLATE","ARACHNE_LEGGINGS","ARACHNE_BOOTS","ARACK"->0xFF5555FF;
        case "ESSENCE_SPIDER","DARK_QUEENS_SOUL_DROP"->0xFFFF55FF;
        case "TARANTULA_EPIC"->0xFFAA00AA;
        case "TARANTULA_LEGENDARY"->GOLD;
        default->WHITE;
    };}
    static String decimal(double value){return String.format(Locale.ROOT,"%.1f",value);}
    static String shortTime(long ms){return ms>=60_000?(ms/60_000)+"m "+(ms/1000%60)+"s":(ms/1000)+"s";}
    static void row(GuiGraphicsExtractor g,Font f,String label,String value,int y,int color){
        int labelWidth=Math.max(20,WIDTH-14-f.width(value));
        g.text(f,f.plainSubstrByWidth(label+":",labelWidth),4,y,Graph.MUTED,true);right(g,f,value,WIDTH-4,y,color);
    }
    static void right(GuiGraphicsExtractor g,Font f,String text,int x,int y,int color){g.text(f,text,x-f.width(text),y,color,true);}
    private Hud(){}
}

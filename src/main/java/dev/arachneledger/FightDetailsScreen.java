package dev.arachneledger;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FightDetailsScreen extends Screen {
    private final Screen parent;
    private final Tracker t=ArachneLedger.tracker;
    private final long id;
    private int x,y,w,h,scroll;
    private FlatButton edit;
    public FightDetailsScreen(Screen parent,long id){super(Component.literal("Fight breakdown"));this.parent=parent;this.id=id;}
    @Override protected void init(){
        w=Math.min(470,width-24);h=Math.min(328,height-20);x=(width-w)/2;y=(height-h)/2;
        edit=new FlatButton(x+12,y+h-24,96,18,"Edit drops",false,()->minecraft.setScreen(new FightEditScreen(this,id)));
        updateEditButton();addRenderableWidget(edit);
        addRenderableWidget(new FlatButton(x+w-80,y+h-24,68,18,"Back",false,this::onClose));
    }
    private void updateEditButton(){
        var outcome=t.ledger.fight(id).outcome;
        edit.active=outcome!=FightRecord.Outcome.FIGHTING && outcome!=FightRecord.Outcome.WAITING_DAMAGE;
    }
    @Override public void tick(){updateEditButton();}
    static String duration(FightRecord fight){return fight.duration()<0?"Time unknown":String.format(Locale.ROOT,"%.1fs",fight.duration()/1000.0);}
    static String date(FightRecord fight){long at=fight.died>0?fight.died:fight.spawned;return at>0?DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at)):"Start time unknown";}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0x80000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        extractBackground(g,mx,my,delta);g.fill(x,y,x+w,y+h,0xDF101010);
        var fight=t.ledger.fight(id);var stats=t.ledger.fightStats(id);var values=t.ledger.fightLootValues(id);
        g.text(font,Component.literal("Fight #"+id).withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD),x+12,y+12,Hud.WHITE,true);
        String reason=fight.reason(stats.kills()>0);
        g.text(font,font.plainSubstrByWidth(reason,w-24),x+12,y+26,Graph.MUTED,true);
        if(my>=y+24&&my<y+38)g.setTooltipForNextFrame(Component.literal(reason+"\n"+date(fight)),mx,my);
        g.horizontalLine(x+12,x+w-12,y+46,0xFF333333);
        line(g,"Kill time",duration(fight),56,0xFF55FFFF);
        line(g,"Your damage",fight.damage<0?"Unknown":String.format(Locale.ROOT,"%,d",fight.damage),69,Hud.TITLE);
        line(g,"Net profit",Format.coins(stats.profit())+" coins",87,stats.profit()>=0?Graph.GREEN:Graph.RED);
        line(g,"Rewards value",Format.coins(stats.revenue())+" coins",100,Hud.GOLD);
        line(g,"Crystal costs ("+stats.crystals()+")",Format.coins(t.ledger.fightSpend(id,Ledger.Kind.CRYSTAL))+" coins",113,Graph.RED);
        line(g,"Calling costs ("+stats.callings()+")",Format.coins(t.ledger.fightSpend(id,Ledger.Kind.CALLING))+" coins",126,Graph.RED);
        g.horizontalLine(x+12,x+w-12,y+140,0xFF333333);
        List<String> ids=new ArrayList<>(stats.loot().keySet());ids.sort(Comparator.<String>comparingDouble(item->values.getOrDefault(item,0.0)).reversed().thenComparing(item->item));
        double coins=t.ledger.fightScavengerCoins(id);
        if(coins>0){ids.addFirst(PurseCoins.ITEM);values.put(PurseCoins.ITEM,coins);}
        int top=y+151,bottom=y+h-52,visible=Math.max(1,(bottom-top)/13);scroll=Math.max(0,Math.min(scroll,Math.max(0,ids.size()-visible)));
        if(ids.isEmpty())g.text(font,"No drops recorded.",x+12,top,Graph.MUTED,true);
        g.enableScissor(x+10,top-1,x+w-10,bottom);
        for(int i=scroll;i<Math.min(ids.size(),scroll+visible);i++){
            String item=ids.get(i),name=item.equals(PurseCoins.ITEM)?"Scavenger coins":String.format(Locale.ROOT,"%,d",stats.loot().get(item))+"x "+Catalog.name(item);
            double value=values.getOrDefault(item,0.0);String price=value==0?"Unpriced":Format.coins(value)+" coins";int yy=top+(i-scroll)*13,right=x+w-16-font.width(price);
            g.text(font,font.plainSubstrByWidth(name,Math.max(25,right-x-24)),x+12,yy,Hud.itemColor(item),true);g.text(font,price,right,yy,value==0?Graph.MUTED:Hud.GOLD,true);
            if(my>=yy-1&&my<yy+11)g.setTooltipForNextFrame(Component.literal(name+" · "+price),mx,my);
        }
        g.disableScissor();
        if(ids.size()>visible){int track=bottom-top,thumb=Math.max(6,track*visible/ids.size()),offset=(track-thumb)*scroll/Math.max(1,ids.size()-visible);g.fill(x+w-8,top,x+w-7,bottom,0xFF333333);g.fill(x+w-8,top+offset,x+w-7,top+offset+thumb,0xFF888888);}
        String footer=stats.unpriced()>0?stats.unpriced()+" unpriced · set prices or reprice session":date(fight)+" · Session "+fight.session;
        g.text(font,font.plainSubstrByWidth(footer,w-24),x+12,y+h-42,Graph.MUTED,true);
        g.horizontalLine(x+12,x+w-12,y+h-29,0xFF333333);
        for(var child:children())if(child instanceof net.minecraft.client.gui.components.Renderable r)r.extractRenderState(g,mx,my,delta);
    }
    private void line(GuiGraphicsExtractor g,String label,String value,int offset,int color){int right=x+w-12-font.width(value);g.text(font,font.plainSubstrByWidth(label+":",Math.max(20,right-x-24)),x+12,y+offset,Graph.MUTED,true);g.text(font,value,right,y+offset,color,true);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){scroll=Math.max(0,scroll-(int)Math.signum(vertical));return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}

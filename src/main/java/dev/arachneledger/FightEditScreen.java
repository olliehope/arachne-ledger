package dev.arachneledger;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Absolute quantity corrections update the existing fight and all ledger views together. */
public final class FightEditScreen extends Screen {
    private final Screen parent;
    private final Tracker t=ArachneLedger.tracker;
    private final long fightId;
    private final List<String> ids=new ArrayList<>(Catalog.ITEMS.keySet());
    private int index,x,y,w;
    private EditBox count;
    private String note="Set the total quantity; 0 removes this item.";
    public FightEditScreen(Screen parent,long fightId){super(Component.literal("Correct fight drops"));this.parent=parent;this.fightId=fightId;}
    @Override protected void init(){
        String input=count==null?null:count.getValue();
        w=Math.min(440,width-24);x=(width-w)/2;y=Math.max(8,(height-218)/2);
        addRenderableWidget(new FlatButton(x+12,y+43,24,20,"<",false,()->change(-1)));
        addRenderableWidget(new FlatButton(x+w-36,y+43,24,20,">",false,()->change(1)));
        count=new EditBox(font,x+w-144,y+80,132,20,Component.literal("Total quantity"));count.setMaxLength(12);addRenderableWidget(count);
        if(input==null)refreshCount();else count.setValue(input);
        addRenderableWidget(new FlatButton(x+12,y+182,90,20,"Save",true,()->{
            if(!"".equals(t.error)){note="Storage error; correction was not saved. See Minecraft log.";return;}
            try{
                long quantity=Long.parseLong(count.getValue().trim().replace(",",""));t.editFightLoot(fightId,ids.get(index),quantity);
                note="".equals(t.error)?"Saved; fight, totals and graph updated.":"Storage error; correction was not saved. See Minecraft log.";
            }
            catch(RuntimeException ex){note=ex instanceof NumberFormatException?"Enter a whole number from 0 to 1 billion.":ex.getMessage();}
        }));
        addRenderableWidget(new FlatButton(x+w-102,y+182,90,20,"Back",false,this::onClose));
    }
    private void change(int delta){index=Math.floorMod(index+delta,ids.size());refreshCount();}
    private void refreshCount(){count.setValue(Long.toString(t.ledger.fightStats(fightId).loot().getOrDefault(ids.get(index),0L)));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xDF101010);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.text(font,Component.literal("Edit fight #"+fightId).withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD),x+12,y+14,Hud.WHITE,true);
        g.centeredText(font,font.plainSubstrByWidth(Catalog.name(ids.get(index)),w-80),x+w/2,y+49,Hud.itemColor(ids.get(index)));
        g.text(font,"Total quantity",x+12,y+86,Graph.MUTED,true);
        g.text(font,font.plainSubstrByWidth("Existing drops keep their recorded unit value.",w-24),x+12,y+120,Graph.MUTED,true);
        String price="New items use "+t.config.lootPriceSource(ids.get(index))+": "+Format.coins(t.config.lootPrice(ids.get(index)))+" coins each.";
        g.text(font,font.plainSubstrByWidth(price,w-24),x+12,y+133,Graph.MUTED,true);
        g.text(font,font.plainSubstrByWidth(note,w-24),x+12,y+155,Graph.MUTED,true);
        if(my>=y+153&&my<y+168)g.setTooltipForNextFrame(Component.literal(note),mx,my);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}

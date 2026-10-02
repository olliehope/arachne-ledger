package dev.arachneledger;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class AdjustScreen extends Screen {
    private final Screen parent;
    private final Tracker t=ArachneLedger.tracker;
    private final List<String> ids=new ArrayList<>(Catalog.ITEMS.keySet());
    private int x,y,w,index;
    private EditBox amount;
    private String note="Correct missing drops or add actual coin adjustments.";
    public AdjustScreen(Screen parent){super(Component.literal("Ledger adjustments"));this.parent=parent;}
    @Override protected void init(){
        String input=amount==null?"1":amount.getValue();
        w=Math.min(500,width-20);x=(width-w)/2;y=Math.max(8,(height-234)/2);
        addRenderableWidget(new FlatButton(x+16,y+42,24,20,"<",false,()->index=Math.floorMod(index-1,ids.size())));
        addRenderableWidget(new FlatButton(x+w-40,y+42,24,20,">",false,()->index=(index+1)%ids.size()));
        amount=new EditBox(font,x+140,y+74,w-156,20,Component.literal("Quantity or coins"));amount.setMaxLength(24);amount.setValue(input);addRenderableWidget(amount);
        int bw=(w-40)/3;
        addRenderableWidget(new FlatButton(x+16,y+107,bw,20,"Add loot",true,()->attempt(()->{
            double n=Config.amount(amount.getValue());if(n<1||n>1_000_000||n!=Math.floor(n))throw new IllegalArgumentException("Loot quantity must be a whole number: 1-1,000,000.");
            String id=ids.get(index);t.record(Ledger.Kind.LOOT,id,(long)n,t.config.lootPrice(id),"manual",System.currentTimeMillis());note="Added "+(long)n+" "+Catalog.name(id)+".";
        })));
        addRenderableWidget(new FlatButton(x+20+bw,y+107,bw,20,"Add income",false,()->addMoney(true)));
        addRenderableWidget(new FlatButton(x+24+bw*2,y+107,bw,20,"Add expense",false,()->addMoney(false)));
        addRenderableWidget(new FlatButton(x+16,y+141,bw,20,"Undo last",false,()->attempt(()->note=t.undo()?"Removed last session entry.":"No session entries to undo.")));
        addRenderableWidget(new FlatButton(x+20+bw,y+141,bw,20,"Export CSV",false,()->{
            try{note="Saved in config/arachneledger/exports";ArachneLedger.say("Exported: "+t.export());}catch(Exception ex){note=ex.getMessage();}
        }));
        addRenderableWidget(new FlatButton(x+24+bw*2,y+141,bw,20,"Back",false,this::onClose));
    }
    private void attempt(Runnable action){
        if(!"".equals(t.error)){note="Storage error; changes were not saved. See Minecraft log.";return;}
        try{
            action.run();t.save();
            if(!"".equals(t.error))note="Storage error; changes were not saved. See Minecraft log.";
        }catch(Exception ex){note=ex.getMessage();}
    }
    private void addMoney(boolean income){attempt(()->{t.record(income?Ledger.Kind.INCOME:Ledger.Kind.EXPENSE,"MANUAL",1,Config.amount(amount.getValue()),"manual",System.currentTimeMillis());note="Coin adjustment saved.";});}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xDF101010);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);g.text(font,Component.literal("Adjust tracker").withStyle(net.minecraft.ChatFormatting.YELLOW,net.minecraft.ChatFormatting.BOLD),x+16,y+14,Hud.WHITE,true);
        g.centeredText(font,font.plainSubstrByWidth(Catalog.name(ids.get(index)),w-92),x+w/2,y+48,Hud.itemColor(ids.get(index)));
        g.text(font,"Quantity / coins",x+16,y+80,Graph.MUTED,false);
        g.text(font,font.plainSubstrByWidth(note,w-32),x+16,y+182,Graph.MUTED,false);
        g.text(font,"Manual entries are included in the selected ledger.",x+16,y+202,Graph.MUTED,false);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}

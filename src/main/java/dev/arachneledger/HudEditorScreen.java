package dev.arachneledger;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Edits the real viewport-relative HUD position, including from the title screen. */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private final Tracker t=ArachneLedger.tracker;
    private boolean dragging;
    private double offsetX,offsetY;
    private int toolbarX,toolbarY,toolbarWidth;
    private FlatButton graphOptions;
    public HudEditorScreen(Screen parent){super(Component.literal("Edit Arachne HUD"));this.parent=parent;}
    @Override protected void init(){
        toolbarWidth=Math.min(440,width-12);toolbarX=(width-toolbarWidth)/2;toolbarY=height-52;
        int bw=(toolbarWidth-28)/4;
        button(0,0,bw,t.config.hud?"HUD: on":"HUD: off","Show or hide the live overlay.",()->{t.config.hud=!t.config.hud;changed();});
        button(1,0,bw,switch(t.config.hudView){case COMPACT->"Compact";case DETAILED->"Detailed";case GRAPH->"Graph";},"Change the overlay layout. The dashboard tab stays unchanged.",()->{t.cycleView();rebuildWidgets();});
        button(2,0,bw,t.config.hudAlwaysShow?"SkyBlock":"Arena only","Show while in any SkyBlock area, or only when Arachne tracking is active.",()->{t.config.hudAlwaysShow=!t.config.hudAlwaysShow;changed();});
        button(3,0,bw,t.config.hudBackground?"Background":"Text only","Toggle a subtle black background behind the overlay.",()->{t.config.hudBackground=!t.config.hudBackground;changed();});
        button(0,1,bw,"Smaller","Decrease overlay size. You can also scroll over the overlay.",()->scale(-.05));
        button(1,1,bw,"Larger","Increase overlay size. It automatically fits the screen.",()->scale(.05));
        button(2,1,bw,"Reset","Reset HUD position and size.",()->{t.config.corner=0;t.config.hudX=-1;t.config.hudY=-1;t.config.hudScale=1;changed();});
        button(3,1,bw,"Done","Save the HUD layout and return.",this::onClose);
        graphOptions=null;
        if(t.config.hudView==Config.View.GRAPH){
            graphOptions=new FlatButton(toolbarX+toolbarWidth-92,toolbarY-22,84,18,"Graph options",false,
                ()->minecraft.setScreen(new GraphOptionsScreen(this)));
            addRenderableWidget(graphOptions);
        }
    }
    private void button(int col,int row,int bw,String label,String tip,Runnable action){
        var b=new FlatButton(toolbarX+8+col*(bw+4),toolbarY+14+row*18,bw,17,label,false,action);
        b.setTooltip(Tooltip.create(Component.literal(tip)));addRenderableWidget(b);
    }
    private void changed(){t.saveConfig();rebuildWidgets();}
    private void scale(double amount){t.config.hudScale=Math.max(.65,Math.min(1.6,t.config.hudScale+amount));changed();}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0x50000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        extractBackground(g,mx,my,delta);
        Hud.drawPanel(g,t,Hud.bounds(t.config,width,height),true);
        g.fill(toolbarX,toolbarY,toolbarX+toolbarWidth,height-2,0xDF101010);
        String hint="Drag to move · scroll to resize · "+Math.round(t.config.hudScale*100)+"%";
        g.text(font,font.plainSubstrByWidth(hint,toolbarWidth-16),toolbarX+8,toolbarY+3,Graph.MUTED,true);
        for(var child:children())if(child instanceof net.minecraft.client.gui.components.Renderable r)r.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(graphOptions!=null&&graphOptions.isMouseOver(event.x(),event.y()))return super.mouseClicked(event,doubleClick);
        if(event.y()>=toolbarY&&event.x()>=toolbarX&&event.x()<toolbarX+toolbarWidth)return super.mouseClicked(event,doubleClick);
        Hud.Bounds b=Hud.bounds(t.config,width,height);
        if(event.button()==0&&b.contains(event.x(),event.y())){dragging=true;offsetX=event.x()-b.x();offsetY=event.y()-b.y();return true;}
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(dragging){Hud.move(t.config,width,height,event.x()-offsetX,event.y()-offsetY);return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(dragging){dragging=false;t.saveConfig();return true;}
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(Hud.bounds(t.config,width,height).contains(mx,my)){scale(Math.signum(vertical)*.05);return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){t.saveConfig();minecraft.setScreen(parent);}
}

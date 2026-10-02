package dev.arachneledger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class FlatButton extends Button {
    private final boolean selected;
    public FlatButton(int x,int y,int w,int h,String label,boolean selected,Runnable action) {
        super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION);
        this.selected=selected;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
        if(isHoveredOrFocused())g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0x30666666);
        var font=Minecraft.getInstance().font;
        String label=font.plainSubstrByWidth(getMessage().getString(),Math.max(0,getWidth()-4));
        int color=!active?0xFF555555:selected?Hud.TITLE:isHoveredOrFocused()?Hud.WHITE:Graph.MUTED;
        g.centeredText(font,label,getX()+getWidth()/2,getY()+(getHeight()-8)/2,color);
        if(selected)g.horizontalLine(getX()+(getWidth()-font.width(label))/2,getX()+(getWidth()+font.width(label))/2,getY()+getHeight()-3,0xFF777777);
    }
}

package dev.arachneledger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class FlatButton extends Button {
    private final boolean selected;

    public FlatButton(
            int buttonX,
            int buttonY,
            int buttonWidth,
            int buttonHeight,
            String label,
            boolean selected,
            Runnable action) {
        super(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                Component.literal(label),
                ignoredButton -> action.run(),
                DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (isHoveredOrFocused()) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x30666666);
        }
        var font = Minecraft.getInstance().font;
        String label =
                font.plainSubstrByWidth(getMessage().getString(), Math.max(0, getWidth() - 4));
        int color =
                !active
                        ? 0xFF555555
                        : selected ? Hud.TITLE : isHoveredOrFocused() ? Hud.WHITE : Graph.MUTED;
        graphics.centeredText(
                font, label, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
        if (selected) {
            graphics.horizontalLine(
                    getX() + (getWidth() - font.width(label)) / 2,
                    getX() + (getWidth() + font.width(label)) / 2,
                    getY() + getHeight() - 3,
                    0xFF777777);
        }
    }
}

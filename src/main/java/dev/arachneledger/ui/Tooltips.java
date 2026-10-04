package dev.arachneledger.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Wrap custom hover text, including explicit newlines, within the current GUI viewport. */
public final class Tooltips {
    public static void show(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        graphics.setTooltipForNextFrame(
                font.split(
                        Component.literal(text),
                        Math.max(40, Math.min(300, graphics.guiWidth() - 24))),
                x,
                y);
    }

    private Tooltips() {}
}

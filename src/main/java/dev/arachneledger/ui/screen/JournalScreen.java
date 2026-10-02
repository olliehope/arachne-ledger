package dev.arachneledger.ui.screen;

import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Navigation for optional details, keeping the live dashboard compact. */
public final class JournalScreen extends Screen {
    private final Screen parent;
    private int x, y, panelWidth;

    public JournalScreen(Screen parent) {
        super(Component.literal("Arachne journal"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(340, width - 24);
        x = (width - panelWidth) / 2;
        y = (height - 200) / 2;
        addRenderableWidget(
                new FlatButton(
                        x + 12,
                        y + 54,
                        panelWidth - 24,
                        22,
                        "Achievements",
                        false,
                        () -> minecraft.setScreen(new AchievementsScreen(this))));
        addRenderableWidget(
                new FlatButton(
                        x + 12,
                        y + 86,
                        panelWidth - 24,
                        22,
                        "Recaps & records",
                        false,
                        () -> minecraft.setScreen(new RecapScreen(this))));
        addRenderableWidget(
                new FlatButton(
                        x + 12,
                        y + 118,
                        panelWidth - 24,
                        22,
                        "Tracking diagnostics",
                        false,
                        () -> minecraft.setScreen(new DiagnosticsScreen(this))));
        addRenderableWidget(
                new FlatButton(x + 12, y + 170, panelWidth - 24, 20, "Back", false, this::onClose));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        graphics.fill(0, 0, width, height, 0xDF101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        super.extractRenderState(graphics, mx, my, delta);
        graphics.text(font, "Arachne journal", x + 12, y + 12, Hud.TITLE, true);
        graphics.text(
                font, "Progress, sessions and detection.", x + 12, y + 31, Graph.MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.client.ClientSupplies;
import dev.arachneledger.ledger.Supplies;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Stock information is optional dashboard detail, refreshed once a second rather than per frame.
 */
public final class SuppliesScreen extends Screen {
    private final Screen parent;
    private int x, y, panelWidth, panelHeight, ticks;
    private Supplies.Snapshot snapshot;

    public SuppliesScreen(Screen parent) {
        super(Component.literal("Summon supplies"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(360, width - 24);
        panelHeight = Math.min(206, height - 20);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        refresh();
        addRenderableWidget(
                new FlatButton(
                        x + 12,
                        y + panelHeight - 24,
                        panelWidth - 24,
                        18,
                        "Back",
                        false,
                        this::onClose));
    }

    private void refresh() {
        snapshot = ClientSupplies.read(minecraft, ArachneLedger.tracker.ledger);
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) refresh();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        extractBackground(graphics, mx, my, delta);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xDF101010);
        graphics.text(font, "Summon supplies", x + 12, y + 12, Hud.TITLE, true);
        graphics.text(
                font,
                "Carried inventory · current session pace",
                x + 12,
                y + 30,
                Graph.MUTED,
                false);
        int shift = panelHeight < 190 ? -12 : 0;
        row(
                graphics,
                "Crystals",
                snapshot.available() ? Long.toString(snapshot.crystals()) : "--",
                62 + shift);
        row(
                graphics,
                "Callings",
                snapshot.available() ? Long.toString(snapshot.callings()) : "--",
                84 + shift);
        row(
                graphics,
                "Crystal stock lasts",
                snapshot.crystalMillisLeft() >= 0
                        ? Format.time(snapshot.crystalMillisLeft())
                        : "--",
                106 + shift);
        String hint =
                !snapshot.available()
                        ? "Join a world to read your inventory."
                        : snapshot.crystalMillisLeft() < 0
                                ? "Estimate needs 3 own Crystals and 1 active minute."
                                : "Rough estimate; other players' summons do not consume your stock.";
        graphics.text(
                font,
                font.plainSubstrByWidth(hint, panelWidth - 24),
                x + 12,
                y + 136 + shift,
                Graph.MUTED,
                false);
        if (my >= y + 128 + shift && my < y + 150 + shift)
            graphics.setTooltipForNextFrame(
                    font.split(
                            Component.literal(hint + " Storage is not included."), panelWidth - 24),
                    mx,
                    my);
        for (var child : children())
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
                renderable.extractRenderState(graphics, mx, my, delta);
    }

    private void row(GuiGraphicsExtractor graphics, String label, String value, int offset) {
        graphics.text(font, label, x + 12, y + offset, Hud.WHITE, false);
        graphics.text(
                font, value, x + panelWidth - 12 - font.width(value), y + offset, 0xFF55FFFF, true);
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

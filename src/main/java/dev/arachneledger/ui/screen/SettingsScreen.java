package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The common settings entry point used by the dashboard, command and optional Mod Menu. */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private int panelX, panelY, panelWidth, panelHeight;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Arachne settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(360, width - 20);
        panelHeight = Math.min(228, height - 12);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        category(
                0,
                "HUD",
                "Choose individual rows, items and layouts; move or resize the overlay.",
                () -> minecraft.setScreen(new HudOptionsScreen(this)));
        category(
                1,
                "Tracking",
                "Minimum damage, kill summaries, rare-drop titles and Scavenger coins.",
                () -> minecraft.setScreen(new BehaviorSettingsScreen(this)));
        category(
                2,
                "Prices & salvage",
                "Bazaar sale mode, item values, summoning costs and equipment salvage.",
                () -> minecraft.setScreen(new PriceSettingsScreen(this)));
        category(
                3,
                "Graph",
                "Choose graph lines, projections, spawn markers and graph text.",
                () -> minecraft.setScreen(new GraphOptionsScreen(this)));
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + panelHeight - 24,
                        panelWidth - 32,
                        20,
                        "Back",
                        false,
                        this::onClose));
    }

    private void category(int index, String label, String tip, Runnable action) {
        var button =
                new FlatButton(
                        panelX + 16,
                        panelY + 49 + index * 30,
                        panelWidth - 32,
                        22,
                        label,
                        false,
                        action);
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        addRenderableWidget(button);
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xDF101010);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(
                font,
                getTitle().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 16,
                panelY + 12,
                Hud.WHITE,
                true);
        graphics.text(font, "Choose what to change.", panelX + 16, panelY + 30, Graph.MUTED, false);
        if (!ArachneLedger.tracker.error.isEmpty()) {
            graphics.text(
                    font,
                    font.plainSubstrByWidth(
                            "Storage error; see the Minecraft log.", panelWidth - 32),
                    panelX + 16,
                    panelY + panelHeight - 37,
                    Hud.TITLE,
                    false);
        }
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

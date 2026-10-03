package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Standalone choices save immediately; the price editor supplies its own unsaved draft. */
public final class ValuationScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private final Config choices;
    private final boolean saveImmediately;
    private int panelX, panelY, panelWidth;

    public ValuationScreen(Screen parent) {
        this(parent, ArachneLedger.tracker.config, true);
    }

    public ValuationScreen(Screen parent, Config draft) {
        this(parent, draft, false);
    }

    private ValuationScreen(Screen parent, Config choices, boolean saveImmediately) {
        super(Component.literal("Arachne gear values"));
        this.parent = parent;
        this.choices = choices;
        this.saveImmediately = saveImmediately;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(460, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(6, (height - 218) / 2);
        modeButton(
                panelY + 49,
                choices.salvageArmor,
                () -> {
                    choices.salvageArmor = !choices.salvageArmor;
                    changed();
                });
        modeButton(
                panelY + 97,
                choices.salvageWeapons,
                () -> {
                    choices.salvageWeapons = !choices.salvageWeapons;
                    changed();
                });
        var coins =
                new FlatButton(
                        panelX + 16,
                        panelY + 171,
                        panelWidth - 32,
                        18,
                        choices.scavengerCoins ? "Scavenger coins: on" : "Scavenger coins: off",
                        choices.scavengerCoins,
                        () -> {
                            choices.scavengerCoins = !choices.scavengerCoins;
                            changed();
                        });
        coins.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Count marked purse gains while Arachne tracking is active. Existing coin entries are kept.")));
        addRenderableWidget(coins);
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 198,
                        panelWidth - 32,
                        20,
                        "Done",
                        false,
                        this::onClose));
    }

    private void modeButton(int rowY, boolean salvage, Runnable action) {
        var button =
                new FlatButton(
                        panelX + panelWidth - 152,
                        rowY,
                        136,
                        20,
                        salvage ? "Salvage: 5 essence" : "Sell to NPC",
                        salvage,
                        action);
        button.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Choose the value used for future drops. Salvage uses 5 Spider Essence per item. Saved manual sale prices return when you switch back to NPC. This setting does not sell or salvage items.")));
        addRenderableWidget(button);
    }

    private void changed() {
        if (saveImmediately) tracker.saveConfig();
        rebuildWidgets();
    }

    private void line(
            GuiGraphicsExtractor graphics, String text, int rowY, int mouseX, int mouseY) {
        graphics.text(
                font,
                font.plainSubstrByWidth(text, panelWidth - 32),
                panelX + 16,
                rowY,
                Graph.MUTED,
                false);
        if (mouseX >= panelX + 16
                && mouseX < panelX + panelWidth - 16
                && mouseY >= rowY - 2
                && mouseY < rowY + 11) {
            graphics.setTooltipForNextFrame(Component.literal(text), mouseX, mouseY);
        }
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
                Component.literal("Gear values")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 16,
                panelY + 12,
                Hud.WHITE,
                true);
        line(
                graphics,
                choices.ironman
                        ? "Ironman: salvage keeps counts with no coin value."
                        : "Choose how you value unupgraded drops.",
                panelY + 31,
                mouseX,
                mouseY);
        graphics.text(font, "Armor", panelX + 16, panelY + 55, Hud.WHITE, true);
        line(
                graphics,
                choices.salvageArmor
                        ? choices.ironman
                                ? "5 essence each; excluded from coin totals."
                                : "Each piece: "
                                        + Format.coins(choices.lootPrice("ARACHNE_HELMET"))
                                        + " coins in essence"
                        : choices.ironman
                                ? "NPC: 2,000 each. Market prices are kept."
                                : "NPC: 2,000 each. Saved price overrides apply.",
                panelY + 78,
                mouseX,
                mouseY);
        graphics.text(font, "Tools / weapons", panelX + 16, panelY + 103, Hud.WHITE, true);
        line(
                graphics,
                choices.salvageWeapons
                        ? choices.ironman
                                ? "5 essence each; excluded from coin totals."
                                : "Arack: "
                                        + Format.coins(choices.lootPrice("ARACK"))
                                        + " coins in essence"
                        : choices.ironman
                                ? "Arack NPC: 5,000. Market prices are kept."
                                : "Arack NPC: 5,000. Saved price overrides apply.",
                panelY + 126,
                mouseX,
                mouseY);
        line(
                graphics,
                choices.ironman
                        ? "Spider Essence: excluded; no missing-price warning."
                        : "Spider Essence: "
                                + Format.coins(choices.price("ESSENCE_SPIDER"))
                                + " each ("
                                + choices.priceSource("ESSENCE_SPIDER")
                                + ")",
                panelY + 147,
                mouseX,
                mouseY);
        line(
                graphics,
                saveImmediately
                        ? "Future drops only; use Reprice for this session."
                        : "Done returns to Prices; Save applies these choices.",
                panelY + 160,
                mouseX,
                mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (saveImmediately) tracker.saveConfig();
        minecraft.setScreen(parent);
    }
}

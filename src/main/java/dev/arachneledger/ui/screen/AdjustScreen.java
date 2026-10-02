package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class AdjustScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private final List<String> itemIds = new ArrayList<>(Catalog.ITEMS.keySet());
    private int panelX, panelY, panelWidth, selectedItemIndex;
    private EditBox amountField;
    private String note = "Correct missing drops or add actual coin adjustments.";

    public AdjustScreen(Screen parent) {
        super(Component.literal("Ledger adjustments"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String amountInput = amountField == null ? "1" : amountField.getValue();
        panelWidth = Math.min(500, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(8, (height - 234) / 2);
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 42,
                        24,
                        20,
                        "<",
                        false,
                        () ->
                                selectedItemIndex =
                                        Math.floorMod(selectedItemIndex - 1, itemIds.size())));
        addRenderableWidget(
                new FlatButton(
                        panelX + panelWidth - 40,
                        panelY + 42,
                        24,
                        20,
                        ">",
                        false,
                        () -> selectedItemIndex = (selectedItemIndex + 1) % itemIds.size()));
        amountField =
                new EditBox(
                        font,
                        panelX + 140,
                        panelY + 74,
                        panelWidth - 156,
                        20,
                        Component.literal("Quantity or coins"));
        amountField.setMaxLength(24);
        amountField.setValue(amountInput);
        addRenderableWidget(amountField);
        int buttonWidth = (panelWidth - 40) / 3;
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 107,
                        buttonWidth,
                        20,
                        "Add loot",
                        true,
                        () ->
                                attempt(
                                        () -> {
                                            double quantity = Config.amount(amountField.getValue());
                                            if (quantity < 1
                                                    || quantity > 1_000_000
                                                    || quantity != Math.floor(quantity)) {
                                                throw new IllegalArgumentException(
                                                        "Loot quantity must be a whole number: 1-1,000,000.");
                                            }
                                            String itemId = itemIds.get(selectedItemIndex);
                                            tracker.record(
                                                    Ledger.Kind.LOOT,
                                                    itemId,
                                                    (long) quantity,
                                                    tracker.config.lootPrice(itemId),
                                                    "manual",
                                                    System.currentTimeMillis());
                                            note =
                                                    "Added "
                                                            + (long) quantity
                                                            + " "
                                                            + Catalog.name(itemId)
                                                            + ".";
                                        })));
        addRenderableWidget(
                new FlatButton(
                        panelX + 20 + buttonWidth,
                        panelY + 107,
                        buttonWidth,
                        20,
                        "Add income",
                        false,
                        () -> addMoney(true)));
        addRenderableWidget(
                new FlatButton(
                        panelX + 24 + buttonWidth * 2,
                        panelY + 107,
                        buttonWidth,
                        20,
                        "Add expense",
                        false,
                        () -> addMoney(false)));
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 141,
                        buttonWidth,
                        20,
                        "Undo last",
                        false,
                        () ->
                                attempt(
                                        () ->
                                                note =
                                                        tracker.undo()
                                                                ? "Removed last session entry."
                                                                : "No session entries to undo.")));
        addRenderableWidget(
                new FlatButton(
                        panelX + 20 + buttonWidth,
                        panelY + 141,
                        buttonWidth,
                        20,
                        "Export CSV",
                        false,
                        () -> {
                            try {
                                note = "Saved in config/arachneledger/exports";
                                ArachneLedger.say("Exported: " + tracker.export());
                            } catch (Exception ex) {
                                note = ex.getMessage();
                            }
                        }));
        addRenderableWidget(
                new FlatButton(
                        panelX + 24 + buttonWidth * 2,
                        panelY + 141,
                        buttonWidth,
                        20,
                        "Back",
                        false,
                        this::onClose));
    }

    private void attempt(Runnable action) {
        if (!"".equals(tracker.error)) {
            note = "Storage error; changes were not saved. See Minecraft log.";
            return;
        }
        try {
            action.run();
            tracker.save();
            if (!"".equals(tracker.error)) {
                note = "Storage error; changes were not saved. See Minecraft log.";
            }
        } catch (Exception ex) {
            note = ex.getMessage();
        }
    }

    private void addMoney(boolean income) {
        attempt(
                () -> {
                    tracker.record(
                            income ? Ledger.Kind.INCOME : Ledger.Kind.EXPENSE,
                            "MANUAL",
                            1,
                            Config.amount(amountField.getValue()),
                            "manual",
                            System.currentTimeMillis());
                    note = "Coin adjustment saved.";
                });
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
                Component.literal("Adjust tracker")
                        .withStyle(
                                net.minecraft.ChatFormatting.YELLOW,
                                net.minecraft.ChatFormatting.BOLD),
                panelX + 16,
                panelY + 14,
                Hud.WHITE,
                true);
        graphics.centeredText(
                font,
                font.plainSubstrByWidth(
                        Catalog.name(itemIds.get(selectedItemIndex)), panelWidth - 92),
                panelX + panelWidth / 2,
                panelY + 48,
                Hud.itemColor(itemIds.get(selectedItemIndex)));
        graphics.text(font, "Quantity / coins", panelX + 16, panelY + 80, Graph.MUTED, false);
        graphics.text(
                font,
                font.plainSubstrByWidth(note, panelWidth - 32),
                panelX + 16,
                panelY + 182,
                Graph.MUTED,
                false);
        graphics.text(
                font,
                "Manual entries are included in the selected ledger.",
                panelX + 16,
                panelY + 202,
                Graph.MUTED,
                false);
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

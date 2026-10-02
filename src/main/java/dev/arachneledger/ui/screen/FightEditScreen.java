package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Absolute quantity corrections update the existing fight and all ledger views together. */
public final class FightEditScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private final long fightId;
    private final List<String> itemIds = new ArrayList<>(Catalog.ITEMS.keySet());
    private int selectedItemIndex, panelX, panelY, panelWidth;
    private EditBox quantityField;
    private String note = "Set the total quantity; 0 removes this item.";

    public FightEditScreen(Screen parent, long fightId) {
        super(Component.literal("Correct fight drops"));
        this.parent = parent;
        this.fightId = fightId;
    }

    @Override
    protected void init() {
        String quantityInput = quantityField == null ? null : quantityField.getValue();
        panelWidth = Math.min(440, width - 24);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(8, (height - 218) / 2);
        addRenderableWidget(
                new FlatButton(panelX + 12, panelY + 43, 24, 20, "<", false, () -> change(-1)));
        addRenderableWidget(
                new FlatButton(
                        panelX + panelWidth - 36,
                        panelY + 43,
                        24,
                        20,
                        ">",
                        false,
                        () -> change(1)));
        quantityField =
                new EditBox(
                        font,
                        panelX + panelWidth - 144,
                        panelY + 80,
                        132,
                        20,
                        Component.literal("Total quantity"));
        quantityField.setMaxLength(12);
        addRenderableWidget(quantityField);
        if (quantityInput == null) {
            refreshCount();
        } else {
            // Resizing recreates the widgets, but must preserve an unfinished correction.
            quantityField.setValue(quantityInput);
        }
        addRenderableWidget(
                new FlatButton(
                        panelX + 12,
                        panelY + 182,
                        90,
                        20,
                        "Save",
                        true,
                        () -> {
                            if (!"".equals(tracker.error)) {
                                note =
                                        "Storage error; correction was not saved. See Minecraft log.";
                                return;
                            }
                            try {
                                long quantity =
                                        Long.parseLong(
                                                quantityField.getValue().trim().replace(",", ""));
                                tracker.editFightLoot(
                                        fightId, itemIds.get(selectedItemIndex), quantity);
                                note =
                                        "".equals(tracker.error)
                                                ? "Saved; fight, totals and graph updated."
                                                : "Storage error; correction was not saved. See Minecraft log.";
                            } catch (RuntimeException ex) {
                                note =
                                        ex instanceof NumberFormatException
                                                ? "Enter a whole number from 0 to 1 billion."
                                                : ex.getMessage();
                            }
                        }));
        addRenderableWidget(
                new FlatButton(
                        panelX + panelWidth - 102,
                        panelY + 182,
                        90,
                        20,
                        "Back",
                        false,
                        this::onClose));
    }

    private void change(int delta) {
        selectedItemIndex = Math.floorMod(selectedItemIndex + delta, itemIds.size());
        refreshCount();
    }

    private void refreshCount() {
        quantityField.setValue(
                Long.toString(
                        tracker.ledger
                                .fightStats(fightId)
                                .loot()
                                .getOrDefault(itemIds.get(selectedItemIndex), 0L)));
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
                Component.literal("Edit fight #" + fightId)
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 12,
                panelY + 14,
                Hud.WHITE,
                true);
        graphics.centeredText(
                font,
                font.plainSubstrByWidth(
                        Catalog.name(itemIds.get(selectedItemIndex)), panelWidth - 80),
                panelX + panelWidth / 2,
                panelY + 49,
                Hud.itemColor(itemIds.get(selectedItemIndex)));
        graphics.text(font, "Total quantity", panelX + 12, panelY + 86, Graph.MUTED, true);
        graphics.text(
                font,
                font.plainSubstrByWidth(
                        "Existing drops keep their recorded unit value.", panelWidth - 24),
                panelX + 12,
                panelY + 120,
                Graph.MUTED,
                true);
        String price =
                "New items use "
                        + tracker.config.lootPriceSource(itemIds.get(selectedItemIndex))
                        + ": "
                        + Format.coins(tracker.config.lootPrice(itemIds.get(selectedItemIndex)))
                        + " coins each.";
        graphics.text(
                font,
                font.plainSubstrByWidth(price, panelWidth - 24),
                panelX + 12,
                panelY + 133,
                Graph.MUTED,
                true);
        graphics.text(
                font,
                font.plainSubstrByWidth(note, panelWidth - 24),
                panelX + 12,
                panelY + 155,
                Graph.MUTED,
                true);
        if (mouseY >= panelY + 153 && mouseY < panelY + 168) {
            graphics.setTooltipForNextFrame(Component.literal(note), mouseX, mouseY);
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

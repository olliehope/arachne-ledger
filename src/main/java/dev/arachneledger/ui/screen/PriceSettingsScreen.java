package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.pricing.BazaarPrices;
import dev.arachneledger.pricing.GearValuation;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PriceSettingsScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private final List<String> itemIds = new ArrayList<>(Catalog.ITEMS.keySet());
    private final Map<String, Double> draftPrices = new LinkedHashMap<>(tracker.config.prices);
    private final Set<String> manualPriceItems =
            new LinkedHashSet<>(
                    tracker.config.manualPriceItems == null
                            ? tracker.config.prices.keySet()
                            : tracker.config.manualPriceItems);
    private final Map<String, String> draftPriceInputs = new HashMap<>();
    private EditBox crystalCostField, callingCostField, unitPriceField;
    private String fixedCrystalInput = Double.toString(tracker.config.crystalCost);
    private String callingInput = Double.toString(tracker.config.callingCost), displayedPriceItemId;
    private int selectedItemIndex, panelX, panelY, panelWidth;
    private boolean useRecipeCost = !tracker.config.crystalConfigured;
    private boolean draftAutoBazaar = tracker.config.autoBazaar;
    private String note = "Saved prices stay manual until you choose Auto.";

    public PriceSettingsScreen(Screen parent) {
        super(Component.literal("Arachne prices"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        captureWidgetDrafts();
        panelWidth = Math.min(500, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(6, (height - 228) / 2);
        addBazaarControls();
        addSummoningCostFields();
        addItemPriceFields();
        addUtilityButtons();
        addSaveButtons();
    }

    private void captureWidgetDrafts() {
        // Minecraft recreates widgets on resize and when returning from another screen.
        // Keep the editable draft separate from both the widgets and the recipe preview.
        if (crystalCostField != null && crystalCostField.active) {
            fixedCrystalInput = crystalCostField.getValue();
        }
        if (callingCostField != null) {
            callingInput = callingCostField.getValue();
        }
        // The item index may already have changed; this widget still belongs to its previous item.
        if (unitPriceField != null && unitPriceField.active) {
            draftPriceInputs.put(displayedPriceItemId, unitPriceField.getValue());
        }
    }

    private void addBazaarControls() {
        addRenderableWidget(
                new FlatButton(
                        panelX + panelWidth - 148,
                        panelY + 8,
                        132,
                        18,
                        draftAutoBazaar ? "Auto Bazaar: on" : "Auto Bazaar: off",
                        draftAutoBazaar,
                        () -> {
                            if (!rememberSelectedPrice()) {
                                return;
                            }
                            draftAutoBazaar = !draftAutoBazaar;
                            if (commitPrices()) {
                                refreshBazaar();
                                rebuildWidgets();
                            }
                        }));
        var saleMode =
                new FlatButton(
                        panelX + 16,
                        panelY + 28,
                        Math.min(120, panelWidth - 170),
                        18,
                        tracker.config.bazaarMode.label(),
                        tracker.config.bazaarMode == Config.BazaarMode.SELL_OFFER,
                        () -> {
                            if (!commitPrices()) {
                                return;
                            }
                            tracker.config.bazaarMode =
                                    tracker.config.bazaarMode == Config.BazaarMode.INSTANT_SELL
                                            ? Config.BazaarMode.SELL_OFFER
                                            : Config.BazaarMode.INSTANT_SELL;
                            tracker.saveConfig();
                            refreshBazaar();
                            rebuildWidgets();
                        });
        saleMode.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Choose the Bazaar estimate for future items: instant sell or a sell offer. Both are before tax; sell offers can take time or remain unfilled. Manual prices win. Reprice session explicitly to change recorded values.")));
        addRenderableWidget(saleMode);
    }

    private void addSummoningCostFields() {
        crystalCostField =
                createPriceField(
                        panelX + panelWidth - 148,
                        panelY + 48,
                        132,
                        "Crystal cost",
                        useRecipeCost
                                ? Double.toString(tracker.config.recipeCost())
                                : fixedCrystalInput);
        crystalCostField.active = !useRecipeCost;
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 48,
                        120,
                        20,
                        useRecipeCost ? "Recipe cost" : "Fixed cost",
                        useRecipeCost,
                        () -> {
                            if (!rememberSelectedPrice()) {
                                return;
                            }
                            useRecipeCost = !useRecipeCost;
                            rebuildWidgets();
                        }));
        callingCostField =
                createPriceField(
                        panelX + panelWidth - 148, panelY + 72, 132, "Calling cost", callingInput);
    }

    private void addItemPriceFields() {
        addRenderableWidget(
                new FlatButton(panelX + 16, panelY + 96, 24, 18, "<", false, () -> selectItem(-1)));
        addRenderableWidget(
                new FlatButton(
                        panelX + panelWidth - 40,
                        panelY + 96,
                        24,
                        18,
                        ">",
                        false,
                        () -> selectItem(1)));
        displayedPriceItemId = itemIds.get(selectedItemIndex);
        unitPriceField =
                createPriceField(
                        panelX + panelWidth - 148,
                        panelY + 120,
                        132,
                        "Loot unit price",
                        manualPriceItems.contains(displayedPriceItemId)
                                ? draftPriceInputs.getOrDefault(
                                        displayedPriceItemId,
                                        Double.toString(draftPrice(displayedPriceItemId)))
                                : Double.toString(draftPrice(displayedPriceItemId)));
        unitPriceField.active = manualPriceItems.contains(displayedPriceItemId);
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 120,
                        90,
                        20,
                        unitPriceField.active ? "Manual price" : "Auto price",
                        !unitPriceField.active,
                        this::toggleManual));
    }

    private void addUtilityButtons() {
        int utilityButtonWidth = (panelWidth - 44) / 4;
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 163,
                        utilityButtonWidth,
                        18,
                        panelWidth < 400 ? "Bazaar" : "Use Bazaar items",
                        false,
                        () -> {
                            if (!rememberSelectedPrice()) {
                                return;
                            }
                            for (String itemId : itemIds) {
                                if (BazaarPrices.supports(itemId, tracker.config)) {
                                    manualPriceItems.remove(itemId);
                                }
                            }
                            draftAutoBazaar = true;
                            if (commitPrices()) {
                                refreshBazaar();
                                note = "Bazaar materials use Auto; other saved prices are kept.";
                                rebuildWidgets();
                            }
                        }));
        addRenderableWidget(
                new FlatButton(
                        panelX + 20 + utilityButtonWidth,
                        panelY + 163,
                        utilityButtonWidth,
                        18,
                        tracker.config.hud ? "HUD: on" : "HUD: off",
                        false,
                        () -> {
                            if (!rememberSelectedPrice()) {
                                return;
                            }
                            tracker.config.hud = !tracker.config.hud;
                            tracker.saveConfig();
                            rebuildWidgets();
                        }));
        addRenderableWidget(
                new FlatButton(
                        panelX + 28 + utilityButtonWidth * 3,
                        panelY + 163,
                        utilityButtonWidth,
                        18,
                        "Edit HUD",
                        false,
                        () -> {
                            if (commitPrices()) {
                                minecraft.setScreen(new HudEditorScreen(this));
                            }
                        }));
        addRenderableWidget(
                new FlatButton(
                        panelX + 24 + utilityButtonWidth * 2,
                        panelY + 163,
                        utilityButtonWidth,
                        18,
                        "Salvage",
                        false,
                        () -> {
                            if (commitPrices()) {
                                minecraft.setScreen(new ValuationScreen(this));
                            }
                        }));
    }

    private void addSaveButtons() {
        int actionButtonWidth = (panelWidth - 40) / 3;
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 205,
                        actionButtonWidth,
                        20,
                        "Save",
                        true,
                        () -> {
                            if (commitPrices()) {
                                note = "Saved. New drops use these prices.";
                            }
                        }));
        var repriceSessionButton =
                new FlatButton(
                        panelX + 20 + actionButtonWidth,
                        panelY + 205,
                        actionButtonWidth,
                        20,
                        panelWidth < 400 ? "Reprice" : "Reprice session",
                        false,
                        () -> {
                            if (commitPrices()) {
                                for (String itemId : itemIds) {
                                    tracker.reprice(itemId, tracker.config.lootPrice(itemId));
                                }
                                tracker.reprice(
                                        "ARACHNE_CRYSTAL", tracker.config.effectiveCrystalCost());
                                tracker.reprice(
                                        "ARACHNE_KEEPER_FRAGMENT", tracker.config.callingCost);
                                if (!"".equals(tracker.error)) {
                                    storageError();
                                } else {
                                    note = "Session repriced; older sessions unchanged.";
                                }
                            }
                        });
        repriceSessionButton.active = tracker.ready() && "".equals(tracker.error);
        if (!repriceSessionButton.active) {
            repriceSessionButton.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "".equals(tracker.error)
                                            ? "Join a world to load an account ledger before repricing a session."
                                            : "Resolve the storage error before repricing. See the Minecraft log.")));
        }
        addRenderableWidget(repriceSessionButton);
        addRenderableWidget(
                new FlatButton(
                        panelX + 24 + actionButtonWidth * 2,
                        panelY + 205,
                        actionButtonWidth,
                        20,
                        "Back",
                        false,
                        this::onClose));
    }

    private double draftPrice(String itemId) {
        return draftAutoBazaar
                        && !manualPriceItems.contains(itemId)
                        && tracker.config.selectedBazaarPrices().containsKey(itemId)
                ? tracker.config.selectedBazaarPrices().get(itemId)
                : draftPrices.getOrDefault(itemId, 0.0);
    }

    private EditBox createPriceField(
            int fieldX, int rowY, int fieldWidth, String title, String value) {
        EditBox box = new EditBox(font, fieldX, rowY, fieldWidth, 20, Component.literal(title));
        box.setMaxLength(24);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private boolean rememberSelectedPrice() {
        try {
            if (unitPriceField != null && unitPriceField.active) {
                draftPrices.put(
                        itemIds.get(selectedItemIndex), Config.amount(unitPriceField.getValue()));
            }
            return true;
        } catch (RuntimeException ex) {
            note = "Enter a valid non-negative price first.";
            return false;
        }
    }

    private void selectItem(int delta) {
        if (!rememberSelectedPrice()) {
            return;
        }
        selectedItemIndex = Math.floorMod(selectedItemIndex + delta, itemIds.size());
        rebuildWidgets();
    }

    private void toggleManual() {
        if (!rememberSelectedPrice()) {
            return;
        }
        String itemId = itemIds.get(selectedItemIndex);
        if (manualPriceItems.contains(itemId)) {
            if (!BazaarPrices.supports(itemId, tracker.config)) {
                note = "No Bazaar price for this item; enter a manual value.";
                return;
            }
            manualPriceItems.remove(itemId);
        } else {
            draftPrices.put(itemId, draftPrice(itemId));
            manualPriceItems.add(itemId);
            draftPriceInputs.put(itemId, Double.toString(draftPrices.get(itemId)));
        }
        rebuildWidgets();
    }

    private boolean commitPrices() {
        if (!"".equals(tracker.error)) {
            storageError();
            return false;
        }
        try {
            if (!rememberSelectedPrice()) {
                return false;
            }
            // Validate all editable amounts before changing the live Config.
            double
                    fixedCrystalCost =
                            Config.amount(
                                    useRecipeCost
                                            ? fixedCrystalInput
                                            : crystalCostField.getValue()),
                    callingCost = Config.amount(callingCostField.getValue());
            tracker.config.crystalCost = fixedCrystalCost;
            tracker.config.callingCost = callingCost;
            tracker.config.crystalConfigured = !useRecipeCost;
            tracker.config.prices = new LinkedHashMap<>(draftPrices);
            tracker.config.manualPriceItems = new LinkedHashSet<>(manualPriceItems);
            tracker.config.autoBazaar = draftAutoBazaar;
            tracker.saveConfig();
            if (!"".equals(tracker.error)) {
                storageError();
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            note = "Invalid price. Use a number from 0 to 1 trillion.";
            return false;
        }
    }

    private void storageError() {
        note = "Storage error; prices were not saved. See Minecraft log.";
    }

    private void refreshBazaar() {
        if (BazaarPrices.GLOBAL.tick(tracker.config, System.currentTimeMillis())) {
            tracker.saveConfig();
        }
    }

    @Override
    public void tick() {
        if (!"".equals(tracker.error)) {
            storageError();
        }
        if (unitPriceField != null && !unitPriceField.active) {
            unitPriceField.setValue(Double.toString(draftPrice(itemIds.get(selectedItemIndex))));
        }
        if (crystalCostField != null && useRecipeCost) {
            crystalCostField.setValue(Double.toString(tracker.config.recipeCost()));
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
                Component.literal("Prices")
                        .withStyle(
                                net.minecraft.ChatFormatting.YELLOW,
                                net.minecraft.ChatFormatting.BOLD),
                panelX + 16,
                panelY + 12,
                Hud.WHITE,
                true);
        String bazaarStatus =
                BazaarPrices.GLOBAL.status(tracker.config, System.currentTimeMillis());
        String shortBazaarStatus = font.plainSubstrByWidth(bazaarStatus, 132);
        graphics.text(
                font,
                shortBazaarStatus,
                panelX + panelWidth - 148,
                panelY + 33,
                Graph.MUTED,
                false);
        if (mouseX >= panelX + panelWidth - 148
                && mouseX < panelX + panelWidth - 16
                && mouseY >= panelY + 30
                && mouseY < panelY + 45) {
            graphics.setTooltipForNextFrame(Component.literal(bazaarStatus), mouseX, mouseY);
        }
        graphics.text(font, "Cost per Calling", panelX + 16, panelY + 78, Graph.MUTED, true);
        graphics.centeredText(
                font,
                font.plainSubstrByWidth(
                        Catalog.name(itemIds.get(selectedItemIndex)), panelWidth - 92),
                panelX + panelWidth / 2,
                panelY + 101,
                Hud.itemColor(itemIds.get(selectedItemIndex)));
        String itemId = itemIds.get(selectedItemIndex), source;
        if (GearValuation.supports(itemId) && GearValuation.salvaging(itemId, tracker.config)) {
            source =
                    "Salvage: "
                            + Format.coins(tracker.config.lootPrice(itemId))
                            + " coins in 5 Spider Essence. Field keeps your NPC value.";
        } else if (manualPriceItems.contains(itemId)) {
            source = "Manual: net coins per item. 0 = unpriced.";
        } else if (!draftAutoBazaar) {
            source = "Auto Bazaar is off; using your saved fallback price.";
        } else if (tracker.config.selectedBazaarPrices().containsKey(itemId)) {
            source =
                    "Bazaar: "
                            + tracker.config.bazaarMode.label().toLowerCase(Locale.ROOT)
                            + " estimate, before tax."
                            + (BazaarPrices.isStale(tracker.config, System.currentTimeMillis())
                                    ? " (stale)"
                                    : "");
        } else {
            source =
                    "No "
                            + tracker.config.bazaarMode.label().toLowerCase(Locale.ROOT)
                            + " quote; using your saved fallback price.";
        }
        line(graphics, source, panelY + 148, mouseX, mouseY);
        line(graphics, note, panelY + 189, mouseX, mouseY);
        if (mouseX >= panelX + 16
                && mouseX < panelX + 136
                && mouseY >= panelY + 48
                && mouseY < panelY + 68) {
            graphics.setTooltipForNextFrame(
                    Component.literal(
                            "Recipe: 2 fragments + 16 enchanted eyes + 16 enchanted string, using effective item prices."),
                    mouseX,
                    mouseY);
        }
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

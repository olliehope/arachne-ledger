package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.pricing.BazaarPrices;
import dev.arachneledger.pricing.GearValuation;
import dev.arachneledger.pricing.NpcPrices;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.ChatFormatting;
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
import java.util.Map;

/** Pricing choices stay in a draft until Save or Reprice; closing discards the draft. */
public final class PriceSettingsScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private final List<String> itemIds = new ArrayList<>(Catalog.ITEMS.keySet());
    private final Config draft = pricingDraft(tracker.config);
    private final Map<String, String> draftPriceInputs = new HashMap<>();
    private EditBox crystalCostField, callingCostField, unitPriceField;
    private String fixedCrystalInput = Double.toString(draft.crystalCost);
    private String callingInput = Double.toString(draft.callingCost), displayedPriceItemId;
    private int selectedItemIndex, panelX, panelY, panelWidth;
    private boolean useRecipeCost = !draft.crystalConfigured;
    private String note = "Save applies all choices. Back discards unsaved changes.";

    public PriceSettingsScreen(Screen parent) {
        super(Component.literal("Arachne prices"));
        this.parent = parent;
    }

    private static Config pricingDraft(Config saved) {
        Config copy = new Config();
        copy.prices = new LinkedHashMap<>(saved.prices);
        copy.manualPriceItems =
                new LinkedHashSet<>(
                        saved.manualPriceItems == null
                                ? saved.prices.keySet()
                                : saved.manualPriceItems);
        copy.bazaarPrices = new LinkedHashMap<>(saved.bazaarPrices);
        copy.bazaarSellOfferPrices = new LinkedHashMap<>(saved.bazaarSellOfferPrices);
        copy.bazaarUpdatedAt = saved.bazaarUpdatedAt;
        copy.bazaarMode = saved.bazaarMode;
        copy.autoBazaar = saved.autoBazaar;
        copy.ironman = saved.ironman;
        copy.npcDefaultsVersion = saved.npcDefaultsVersion;
        copy.crystalCost = saved.crystalCost;
        copy.callingCost = saved.callingCost;
        copy.crystalConfigured = saved.crystalConfigured;
        copy.salvageArmor = saved.salvageArmor;
        copy.salvageWeapons = saved.salvageWeapons;
        copy.scavengerCoins = saved.scavengerCoins;
        // Finish legacy defaults before edits, including a first launch from Mod Menu.
        copy.validate();
        return copy;
    }

    @Override
    protected void init() {
        captureWidgetDrafts();
        panelWidth = Math.min(500, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(6, (height - 228) / 2);
        addPricingControls();
        addSummoningCostFields();
        addItemPriceFields();
        addUtilityButtons();
        addSaveButtons();
    }

    private void captureWidgetDrafts() {
        // Widgets are recreated on resize and on returning from a child screen.
        if (crystalCostField != null && crystalCostField.active) {
            fixedCrystalInput = crystalCostField.getValue();
        }
        if (callingCostField != null) callingInput = callingCostField.getValue();
        if (unitPriceField != null && unitPriceField.active) {
            draftPriceInputs.put(displayedPriceItemId, unitPriceField.getValue());
        }
    }

    private void addPricingControls() {
        var ironman =
                new FlatButton(
                        panelX + panelWidth - 148,
                        panelY + 8,
                        132,
                        18,
                        draft.ironman ? "Ironman: on" : "Ironman: off",
                        draft.ironman,
                        () -> {
                            if (!rememberSelectedPrice()) return;
                            draft.ironman = !draft.ironman;
                            note =
                                    draft.ironman
                                            ? "NPC / George values; unsellable rewards have no coin value."
                                            : "Saved manual and Bazaar choices restored.";
                            rebuildWidgets();
                        });
        ironman.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Use NPC and George pet sale values. Essence, shards, and salvaged gear keep their counts without coin value or missing-price warnings. Saved manual and Bazaar choices are kept.")));
        addRenderableWidget(ironman);
        var automatic =
                new FlatButton(
                        panelX + 16,
                        panelY + 28,
                        132,
                        18,
                        draft.autoBazaar ? "Auto Bazaar: on" : "Auto Bazaar: off",
                        draft.autoBazaar,
                        () -> {
                            if (!rememberSelectedPrice()) return;
                            draft.autoBazaar = !draft.autoBazaar;
                            rebuildWidgets();
                        });
        automatic.active = !draft.ironman;
        addRenderableWidget(automatic);
        var saleMode =
                new FlatButton(
                        panelX + panelWidth - 148,
                        panelY + 28,
                        132,
                        18,
                        draft.bazaarMode.label(),
                        draft.bazaarMode == Config.BazaarMode.SELL_OFFER,
                        () -> {
                            if (!rememberSelectedPrice()) return;
                            draft.bazaarMode =
                                    draft.bazaarMode == Config.BazaarMode.INSTANT_SELL
                                            ? Config.BazaarMode.SELL_OFFER
                                            : Config.BazaarMode.INSTANT_SELL;
                            rebuildWidgets();
                        });
        saleMode.active = !draft.ironman;
        saleMode.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Instant sell or sell-offer estimate for future rewards, before tax. Sell offers can take time or remain unfilled. Manual prices win. Reprice session explicitly to change recorded values.")));
        addRenderableWidget(saleMode);
    }

    private void addSummoningCostFields() {
        crystalCostField =
                createPriceField(
                        panelX + panelWidth - 148,
                        panelY + 48,
                        132,
                        "Crystal cost",
                        useRecipeCost ? Double.toString(draft.recipeCost()) : fixedCrystalInput);
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
                            if (!rememberSelectedPrice()) return;
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
        boolean editable = !draft.ironman && draft.isManualPrice(displayedPriceItemId);
        unitPriceField =
                createPriceField(
                        panelX + panelWidth - 148,
                        panelY + 120,
                        132,
                        "Loot unit price",
                        editable
                                ? draftPriceInputs.getOrDefault(
                                        displayedPriceItemId,
                                        Double.toString(
                                                draft.prices.getOrDefault(
                                                        displayedPriceItemId, 0.0)))
                                : Double.toString(draft.lootPrice(displayedPriceItemId)));
        unitPriceField.active = editable;
        var manual =
                new FlatButton(
                        panelX + 16,
                        panelY + 120,
                        120,
                        20,
                        draft.ironman ? "NPC / George" : editable ? "Manual price" : "Auto price",
                        !editable,
                        this::toggleManual);
        manual.active = !draft.ironman;
        addRenderableWidget(manual);
    }

    private void addUtilityButtons() {
        int buttonWidth = (panelWidth - 44) / 4;
        int defaultsWidth = Math.max(buttonWidth, font.width("NPC defaults") + 4);
        int bazaarWidth = Math.max(buttonWidth, font.width("Bazaar items") + 4);
        int salvageWidth = (panelWidth - 44 - defaultsWidth - bazaarWidth) / 2;
        int hudWidth = panelWidth - 44 - defaultsWidth - bazaarWidth - salvageWidth;
        var defaults =
                new FlatButton(
                        panelX + 16,
                        panelY + 163,
                        defaultsWidth,
                        18,
                        "NPC defaults",
                        false,
                        () -> {
                            captureWidgetDrafts();
                            draft.useNpcDefaults();
                            draftPriceInputs.clear();
                            // Do not let rebuilding copy the replaced item field back into the
                            // draft.
                            unitPriceField = null;
                            note = "NPC / George defaults selected. Summon costs are kept.";
                            rebuildWidgets();
                        });
        defaults.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Replace loot values with NPC and George pet sale defaults, clear manual overrides, and turn Auto Bazaar off. Save to apply. Summon costs and recorded entries stay unchanged.")));
        addRenderableWidget(defaults);
        var bazaar =
                new FlatButton(
                        panelX + 20 + defaultsWidth,
                        panelY + 163,
                        bazaarWidth,
                        18,
                        "Bazaar items",
                        false,
                        () -> {
                            if (!rememberSelectedPrice()) return;
                            draft.useBazaarItems();
                            note =
                                    "Supported Bazaar materials use Auto; other saved prices are kept.";
                            rebuildWidgets();
                        });
        bazaar.active = !draft.ironman;
        addRenderableWidget(bazaar);
        addRenderableWidget(
                new FlatButton(
                        panelX + 24 + defaultsWidth + bazaarWidth,
                        panelY + 163,
                        salvageWidth,
                        18,
                        "Salvage",
                        false,
                        () -> {
                            if (!rememberSelectedPrice()) return;
                            captureWidgetDrafts();
                            minecraft.setScreen(new ValuationScreen(this, draft));
                        }));
        addRenderableWidget(
                new FlatButton(
                        panelX + 28 + defaultsWidth + bazaarWidth + salvageWidth,
                        panelY + 163,
                        hudWidth,
                        18,
                        "Edit HUD",
                        false,
                        () -> {
                            captureWidgetDrafts();
                            minecraft.setScreen(new HudEditorScreen(this));
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
                            if (commitPrices()) note = "Saved. New drops use these values.";
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
                            if (!commitPrices()) return;
                            for (String itemId : itemIds)
                                tracker.reprice(itemId, tracker.config.lootPrice(itemId));
                            tracker.reprice(
                                    "ARACHNE_CRYSTAL", tracker.config.effectiveCrystalCost());
                            tracker.reprice("ARACHNE_KEEPER_FRAGMENT", tracker.config.callingCost);
                            if (!"".equals(tracker.error)) storageError();
                            else note = "Session repriced; older sessions unchanged.";
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
                double value = Config.amount(unitPriceField.getValue());
                draft.prices.put(displayedPriceItemId, value);
                draftPriceInputs.put(displayedPriceItemId, unitPriceField.getValue());
            }
            return true;
        } catch (RuntimeException ex) {
            note = "Enter a valid non-negative price first.";
            return false;
        }
    }

    private void selectItem(int delta) {
        if (!rememberSelectedPrice()) return;
        selectedItemIndex = Math.floorMod(selectedItemIndex + delta, itemIds.size());
        rebuildWidgets();
    }

    private void toggleManual() {
        if (draft.ironman || !rememberSelectedPrice()) return;
        String itemId = itemIds.get(selectedItemIndex);
        if (draft.isManualPrice(itemId)) {
            draft.manualPriceItems.remove(itemId);
        } else {
            draft.prices.put(itemId, draft.lootPrice(itemId));
            draft.manualPriceItems.add(itemId);
            draftPriceInputs.put(itemId, Double.toString(draft.prices.get(itemId)));
        }
        rebuildWidgets();
    }

    private boolean commitPrices() {
        if (!"".equals(tracker.error)) {
            storageError();
            return false;
        }
        try {
            captureWidgetDrafts();
            // Validate the entire form before copying any setting into the live Config.
            double fixedCrystalCost = Config.amount(fixedCrystalInput);
            double callingCost = Config.amount(callingInput);
            Map<String, Double> prices = new LinkedHashMap<>(draft.prices);
            for (var input : draftPriceInputs.entrySet()) {
                if (draft.isManualPrice(input.getKey())) {
                    prices.put(input.getKey(), Config.amount(input.getValue()));
                }
            }
            tracker.config.crystalCost = fixedCrystalCost;
            tracker.config.callingCost = callingCost;
            tracker.config.crystalConfigured = !useRecipeCost;
            tracker.config.prices = prices;
            tracker.config.manualPriceItems = new LinkedHashSet<>(draft.manualPriceItems);
            tracker.config.autoBazaar = draft.autoBazaar;
            tracker.config.bazaarMode = draft.bazaarMode;
            tracker.config.ironman = draft.ironman;
            tracker.config.npcDefaultsVersion = draft.npcDefaultsVersion;
            tracker.config.salvageArmor = draft.salvageArmor;
            tracker.config.salvageWeapons = draft.salvageWeapons;
            tracker.config.scavengerCoins = draft.scavengerCoins;
            tracker.saveConfig();
            if (!"".equals(tracker.error)) {
                storageError();
                return false;
            }
            draft.prices = new LinkedHashMap<>(prices);
            draft.crystalCost = fixedCrystalCost;
            draft.callingCost = callingCost;
            draft.crystalConfigured = !useRecipeCost;
            if (!draft.ironman
                    && BazaarPrices.GLOBAL.tick(tracker.config, System.currentTimeMillis())) {
                tracker.saveConfig();
            }
            return "".equals(tracker.error);
        } catch (RuntimeException ex) {
            note = "Invalid price. Use a number from 0 to 1 trillion.";
            return false;
        }
    }

    private void storageError() {
        note = "Storage error; prices were not saved. See Minecraft log.";
    }

    @Override
    public void tick() {
        if (!"".equals(tracker.error)) storageError();
        // Cache refreshes may arrive while this screen is open; unsaved pricing choices stay local.
        draft.bazaarPrices = new LinkedHashMap<>(tracker.config.bazaarPrices);
        draft.bazaarSellOfferPrices = new LinkedHashMap<>(tracker.config.bazaarSellOfferPrices);
        draft.bazaarUpdatedAt = tracker.config.bazaarUpdatedAt;
        if (unitPriceField != null && !unitPriceField.active) {
            unitPriceField.setValue(
                    Double.toString(draft.lootPrice(itemIds.get(selectedItemIndex))));
        }
        if (crystalCostField != null && useRecipeCost) {
            crystalCostField.setValue(Double.toString(draft.recipeCost()));
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
                Component.literal("Prices").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 16,
                panelY + 12,
                Hud.WHITE,
                true);
        graphics.text(font, "Cost per Calling", panelX + 16, panelY + 78, Graph.MUTED, true);
        String itemId = itemIds.get(selectedItemIndex);
        graphics.centeredText(
                font,
                font.plainSubstrByWidth(Catalog.name(itemId), panelWidth - 92),
                panelX + panelWidth / 2,
                panelY + 101,
                Hud.itemColor(itemId));
        line(graphics, priceDescription(itemId), panelY + 148, mouseX, mouseY);
        line(graphics, note, panelY + 189, mouseX, mouseY);
        if (mouseX >= panelX + 16
                && mouseX < panelX + 148
                && mouseY >= panelY + 28
                && mouseY < panelY + 46) {
            graphics.setTooltipForNextFrame(
                    Component.literal(
                            BazaarPrices.GLOBAL.status(draft, System.currentTimeMillis())),
                    mouseX,
                    mouseY);
        }
        if (mouseX >= panelX + 16
                && mouseX < panelX + 136
                && mouseY >= panelY + 48
                && mouseY < panelY + 68) {
            graphics.setTooltipForNextFrame(
                    Component.literal(
                            "Recipe: 2 fragments + 16 enchanted eyes + 16 enchanted string, using effective item prices. Fixed costs remain available in either pricing mode."),
                    mouseX,
                    mouseY);
        }
    }

    private String priceDescription(String itemId) {
        if (draft.intentionalZeroLoot(itemId)) {
            return GearValuation.supports(itemId)
                    ? "Ironman salvage: counts kept; essence has no coin value."
                    : "Ironman: counts kept; no NPC sale value or price warning.";
        }
        if (draft.ironman)
            return NpcPrices.source(itemId) + " value. Saved market prices are kept.";
        if (GearValuation.supports(itemId) && GearValuation.salvaging(itemId, draft)) {
            return "Salvage: "
                    + Format.coins(draft.lootPrice(itemId))
                    + " coins in 5 essence. Field keeps the sale value.";
        }
        if (draft.isManualPrice(itemId))
            return draft.lootPriceSource(itemId) + ": net coins per item. 0 = unpriced.";
        return draft.lootPriceSource(itemId)
                + " | "
                + BazaarPrices.GLOBAL.status(draft, System.currentTimeMillis());
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

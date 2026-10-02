package dev.arachneledger;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final Tracker t = ArachneLedger.tracker;
    private final List<String> ids = new ArrayList<>(Catalog.ITEMS.keySet());
    private final Map<String, Double> draft = new LinkedHashMap<>(t.config.prices);
    private final Set<String> manual = new LinkedHashSet<>(t.config.manualPriceItems == null
        ? t.config.prices.keySet() : t.config.manualPriceItems);
    private EditBox crystal, calling, price;
    private int index, x, y, w;
    private boolean recipe = !t.config.crystalConfigured;
    private boolean autoBazaar = t.config.autoBazaar;
    private String note = "Saved prices stay manual until you choose Auto.";
    public SettingsScreen(Screen parent) { super(Component.literal("Arachne prices")); this.parent = parent; }
    @Override protected void init() {
        w = Math.min(500, width-20); x = (width-w)/2; y = Math.max(6, (height-228)/2);
        addRenderableWidget(new FlatButton(x+w-148, y+8, 132, 18,
            autoBazaar ? "Auto Bazaar: on" : "Auto Bazaar: off", autoBazaar, () -> {
                if (!rememberPrice()) return;
                autoBazaar = !autoBazaar;
                if (commit()) { refreshBazaar(); rebuildKeepingCosts(); }
            }));
        var saleMode = new FlatButton(x+16, y+28, Math.min(120,w-170), 18,
            t.config.bazaarMode.label(), t.config.bazaarMode==Config.BazaarMode.SELL_OFFER, () -> {
                if(!commit())return;
                t.config.bazaarMode=t.config.bazaarMode==Config.BazaarMode.INSTANT_SELL
                    ?Config.BazaarMode.SELL_OFFER:Config.BazaarMode.INSTANT_SELL;
                t.saveConfig(); refreshBazaar(); rebuildKeepingCosts();
            });
        saleMode.setTooltip(Tooltip.create(Component.literal("Choose the Bazaar estimate for future items: instant sell or a sell offer. Both are before tax; sell offers can take time or remain unfilled. Manual prices win. Reprice session explicitly to change recorded values.")));
        addRenderableWidget(saleMode);
        crystal = field(x+w-148, y+48, 132, "Crystal cost",
            t.config.crystalConfigured ? t.config.crystalCost : t.config.recipeCost());
        crystal.active = !recipe;
        addRenderableWidget(new FlatButton(x+16, y+48, 120, 20, recipe ? "Recipe cost" : "Fixed cost", recipe, () -> {
            if (!rememberPrice()) return;
            recipe = !recipe; rebuildKeepingCosts();
        }));
        calling = field(x+w-148, y+72, 132, "Calling cost", t.config.callingCost);
        addRenderableWidget(new FlatButton(x+16, y+96, 24, 18, "<", false, () -> change(-1)));
        addRenderableWidget(new FlatButton(x+w-40, y+96, 24, 18, ">", false, () -> change(1)));
        price = field(x+w-148, y+120, 132, "Loot unit price", draftPrice(ids.get(index)));
        price.active = manual.contains(ids.get(index));
        addRenderableWidget(new FlatButton(x+16, y+120, 90, 20,
            price.active ? "Manual price" : "Auto price", !price.active, this::toggleManual));
        int utilities = (w-44)/4;
        addRenderableWidget(new FlatButton(x+16, y+163, utilities, 18, w<400?"Bazaar":"Use Bazaar items", false, () -> {
            if (!rememberPrice()) return;
            for (String id : ids) if (BazaarPrices.supports(id, t.config)) manual.remove(id);
            autoBazaar = true;
            if (commit()) {
                refreshBazaar();
                note = "Bazaar materials use Auto; other saved prices are kept."; rebuildKeepingCosts();
            }
        }));
        addRenderableWidget(new FlatButton(x+20+utilities, y+163, utilities, 18, t.config.hud ? "HUD: on" : "HUD: off", false, () -> {
            if (!rememberPrice()) return;
            t.config.hud = !t.config.hud; t.saveConfig(); rebuildKeepingCosts();
        }));
        addRenderableWidget(new FlatButton(x+28+utilities*3, y+163, utilities, 18, "Edit HUD", false, () -> {
            if (commit()) minecraft.setScreen(new HudEditorScreen(this));
        }));
        addRenderableWidget(new FlatButton(x+24+utilities*2, y+163, utilities, 18, "Salvage", false, () -> {
            if (commit()) minecraft.setScreen(new ValuationScreen(this));
        }));
        int bw = (w-40)/3;
        addRenderableWidget(new FlatButton(x+16, y+205, bw, 20, "Save", true, () -> {
            if (commit()) note = "Saved. New drops use these prices.";
        }));
        addRenderableWidget(new FlatButton(x+20+bw, y+205, bw, 20, w<400 ? "Reprice" : "Reprice session", false, () -> {
            if (commit()) {
                for (String id : ids) t.reprice(id, t.config.lootPrice(id));
                t.reprice("ARACHNE_CRYSTAL", t.config.effectiveCrystalCost());
                t.reprice("ARACHNE_KEEPER_FRAGMENT", t.config.callingCost);
                note = "Session repriced; older sessions unchanged.";
            }
        }));
        addRenderableWidget(new FlatButton(x+24+bw*2, y+205, bw, 20, "Back", false, this::onClose));
    }
    private double draftPrice(String id) {
        return autoBazaar && !manual.contains(id) && t.config.selectedBazaarPrices().containsKey(id)
            ? t.config.selectedBazaarPrices().get(id) : draft.getOrDefault(id, 0.0);
    }
    private EditBox field(int xx, int yy, int ww, String title, double value) {
        EditBox box = new EditBox(font, xx, yy, ww, 20, Component.literal(title)); box.setMaxLength(24);
        box.setValue(Double.toString(value)); addRenderableWidget(box); return box;
    }
    private boolean rememberPrice() {
        try { if (price != null && price.active) draft.put(ids.get(index), Config.amount(price.getValue())); return true; }
        catch (RuntimeException ex) { note = "Enter a valid non-negative price first."; return false; }
    }
    private void change(int delta) {
        if (!rememberPrice()) return;
        index = Math.floorMod(index+delta, ids.size()); rebuildKeepingCosts();
    }
    private void toggleManual() {
        if (!rememberPrice()) return;
        String id = ids.get(index);
        if (manual.contains(id)) {
            if (!BazaarPrices.supports(id, t.config)) { note = "No Bazaar price for this item; enter a manual value."; return; }
            manual.remove(id);
        } else { draft.put(id, draftPrice(id)); manual.add(id); }
        rebuildKeepingCosts();
    }
    private boolean commit() {
        try {
            if (!rememberPrice()) return false;
            double c = Config.amount(crystal.getValue()), a = Config.amount(calling.getValue());
            t.config.crystalCost = c; t.config.callingCost = a; t.config.crystalConfigured = !recipe;
            t.config.prices = new LinkedHashMap<>(draft); t.config.manualPriceItems = new LinkedHashSet<>(manual);
            t.config.autoBazaar = autoBazaar; t.saveConfig(); return true;
        } catch (RuntimeException ex) { note = "Invalid price. Use a number from 0 to 1 trillion."; return false; }
    }
    private void rebuildKeepingCosts() {
        String c = crystal.getValue(), a = calling.getValue();
        rebuildWidgets(); crystal.setValue(c); calling.setValue(a);
    }
    private void refreshBazaar() {
        if (BazaarPrices.GLOBAL.tick(t.config, System.currentTimeMillis())) t.saveConfig();
    }
    @Override public void tick() {
        if (price != null && !price.active) price.setValue(Double.toString(draftPrice(ids.get(index))));
        if (crystal != null && recipe) crystal.setValue(Double.toString(t.config.recipeCost()));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xDF101010);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(font, Component.literal("Prices").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD),
            x+16, y+12, Hud.WHITE, true);
        String status = BazaarPrices.GLOBAL.status(t.config, System.currentTimeMillis());
        String shortStatus=font.plainSubstrByWidth(status,132);
        g.text(font,shortStatus,x+w-148,y+33,Graph.MUTED,false);
        if(mx>=x+w-148&&mx<x+w-16&&my>=y+30&&my<y+45)
            g.setTooltipForNextFrame(Component.literal(status),mx,my);
        g.text(font, "Cost per Calling", x+16, y+78, Graph.MUTED, true);
        g.centeredText(font, font.plainSubstrByWidth(Catalog.name(ids.get(index)), w-92),
            x+w/2, y+101, Hud.itemColor(ids.get(index)));
        String id = ids.get(index), source;
        if (GearValuation.supports(id) && GearValuation.salvaging(id,t.config)) source = "Salvage: " + Format.coins(t.config.lootPrice(id)) + " coins in 5 Spider Essence. Field keeps your NPC value.";
        else if (manual.contains(id)) source = "Manual: net coins per item. 0 = unpriced.";
        else if (!autoBazaar) source = "Auto Bazaar is off; using your saved fallback price.";
        else if (t.config.selectedBazaarPrices().containsKey(id)) source = "Bazaar: " + t.config.bazaarMode.label().toLowerCase(Locale.ROOT) + " estimate, before tax."
            + (BazaarPrices.isStale(t.config, System.currentTimeMillis()) ? " (stale)" : "");
        else source = "No " + t.config.bazaarMode.label().toLowerCase(Locale.ROOT) + " quote; using your saved fallback price.";
        line(g, source, y+148, mx, my); line(g, note, y+189, mx, my);
        if (mx>=x+16 && mx<x+136 && my>=y+48 && my<y+68)
            g.setTooltipForNextFrame(Component.literal("Recipe: 2 fragments + 16 enchanted eyes + 16 enchanted string, using effective item prices."), mx, my);
    }
    private void line(GuiGraphicsExtractor g, String text, int yy, int mx, int my) {
        g.text(font, font.plainSubstrByWidth(text, w-32), x+16, yy, Graph.MUTED, false);
        if (mx>=x+16 && mx<x+w-16 && my>=yy-2 && my<yy+11)
            g.setTooltipForNextFrame(Component.literal(text), mx, my);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

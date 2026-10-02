package dev.arachneledger;

import java.util.*;

public final class Config {
    public enum View { COMPACT, DETAILED, GRAPH }
    public enum BazaarMode { INSTANT_SELL, SELL_OFFER;
        public String label() { return this==INSTANT_SELL?"Instant sell":"Sell offer"; }
    }
    public boolean total = false;
    public boolean hud = true;
    public boolean paused = false;
    public boolean killChat = true;
    public boolean rngTitles = true;
    public boolean rngValue = true;
    public boolean dashboardFights = false;
    public boolean autoBazaar = false;
    public BazaarMode bazaarMode = BazaarMode.INSTANT_SELL;
    public GraphPreferences graph = new GraphPreferences();
    public boolean scavengerCoins = true;
    public boolean salvageArmor = false;
    public boolean salvageWeapons = false;
    public long minimumDamage = 10_000;
    public View view = View.DETAILED;
    public View hudView = View.DETAILED;
    public boolean hudAlwaysShow = false;
    public boolean hudBackground = false;
    public double hudX = -1;
    public double hudY = -1;
    public double hudScale = 1.0;
    public boolean manualTracking = false;
    public int corner = 0;
    public double crystalCost = 0;
    public double callingCost = 0;
    public boolean crystalConfigured = false;
    public String profile = "default";
    public Map<String, Double> prices = new LinkedHashMap<>();
    /** Null in pre-Bazaar settings: migrate every stored value, including zero, as an override. */
    public Set<String> manualPriceItems;
    public Map<String, Double> bazaarPrices = new LinkedHashMap<>();
    public Map<String, Double> bazaarSellOfferPrices = new LinkedHashMap<>();
    public long bazaarUpdatedAt = 0;
    public Config() {
        for (String id : Catalog.ITEMS.keySet()) prices.put(id, 0.0);
        prices.put("SOUL_STRING", 5000.0);
        prices.put("ARACHNE_FRAGMENT", 500.0);
        prices.put("STRING", 3.0);
        prices.put("SPIDER_EYE", 3.0);
        prices.put("ENCHANTED_STRING", 576.0);
        prices.put("ENCHANTED_SPIDER_EYE", 480.0);
        for (String id : Catalog.ITEMS.keySet()) {
            if (GearValuation.supports(id)) prices.put(id, GearValuation.npcPrice(id));
        }
    }
    public boolean isManualPrice(String id) { return manualPriceItems == null || manualPriceItems.contains(id); }
    public double price(String id) {
        if (autoBazaar && !isManualPrice(id) && selectedBazaarPrices().containsKey(id)) return selectedBazaarPrices().get(id);
        return prices.getOrDefault(id, 0.0);
    }
    public String priceSource(String id) {
        if (isManualPrice(id)) return "Manual";
        if (autoBazaar && selectedBazaarPrices().containsKey(id)) return "Bazaar";
        return prices.getOrDefault(id, 0.0) > 0 ? "Fallback" : "Unpriced";
    }
    /** Values future loot only; journal entries retain the unit value saved when recorded. */
    public double lootPrice(String id) { return GearValuation.supports(id) ? GearValuation.price(id, this) : price(id); }
    public String lootPriceSource(String id) { return GearValuation.supports(id) ? GearValuation.source(id, this) : priceSource(id); }
    public Map<String,Double> selectedBazaarPrices() { return bazaarMode==BazaarMode.SELL_OFFER?bazaarSellOfferPrices:bazaarPrices; }
    private void migratePrices() {
        if (manualPriceItems == null) manualPriceItems = new LinkedHashSet<>(prices.keySet());
    }
    public void manualSet(String id, double value) {
        if (!Catalog.ITEMS.containsKey(id)) throw new IllegalArgumentException("Unknown loot item.");
        amount(Double.toString(value)); migratePrices(); prices.put(id, value); manualPriceItems.add(id);
    }
    public void clearManual(String id) {
        if (!Catalog.ITEMS.containsKey(id)) throw new IllegalArgumentException("Unknown loot item.");
        migratePrices(); manualPriceItems.remove(id);
    }
    /** Deliberate opt-in; Auction-only prices remain untouched. */
    public void useBazaarItems() {
        migratePrices();
        for (String id : Catalog.ITEMS.keySet()) if (BazaarPrices.supports(id, this)) manualPriceItems.remove(id);
        autoBazaar = true;
    }
    /** Current Shaggy recipe: 2 fragments, 16 enchanted spider eyes, 16 enchanted string. */
    public double recipeCost() { return 2*price("ARACHNE_FRAGMENT") + 16*price("ENCHANTED_SPIDER_EYE") + 16*price("ENCHANTED_STRING"); }
    public double effectiveCrystalCost() { return crystalConfigured ? crystalCost : recipeCost(); }
    public static double amount(String input) {
        double n = Double.parseDouble(input.trim().replace(",", ""));
        if (!Double.isFinite(n) || n < 0 || n > 1_000_000_000_000.0)
            throw new IllegalArgumentException("Use a number from 0 to 1 trillion.");
        return n;
    }
    public void validate() {
        if (view == null || prices == null || profile == null || !profile.matches("[A-Za-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("Invalid settings");
        amount(Double.toString(crystalCost)); amount(Double.toString(callingCost));
        if (minimumDamage < 1 || minimumDamage > 1_000_000_000_000L)
            throw new IllegalArgumentException("Minimum damage must be from 1 to 1 trillion.");
        for (var p : prices.values()) amount(Double.toString(p));
        migratePrices();
        if (bazaarPrices == null) bazaarPrices = new LinkedHashMap<>();
        if (bazaarSellOfferPrices == null) bazaarSellOfferPrices = new LinkedHashMap<>();
        if (bazaarMode == null) bazaarMode = BazaarMode.INSTANT_SELL;
        if (graph == null) graph = new GraphPreferences();
        graph.validate();
        validateBazaarPrices(bazaarPrices);
        validateBazaarPrices(bazaarSellOfferPrices);
        if (bazaarUpdatedAt < 0) throw new IllegalArgumentException("Invalid Bazaar timestamp");
        if (manualPriceItems.stream().anyMatch(id -> id == null || !Catalog.ITEMS.containsKey(id)))
            throw new IllegalArgumentException("Invalid manual price item");
        corner = Math.floorMod(corner, 4);
        if (hudView == null) hudView = View.DETAILED;
        if (!Double.isFinite(hudScale)) hudScale = 1.0;
        hudScale = Math.max(0.65, Math.min(1.6, hudScale));
        if (!Double.isFinite(hudX) || hudX < 0) hudX = -1; else hudX = Math.min(1, hudX);
        if (!Double.isFinite(hudY) || hudY < 0) hudY = -1; else hudY = Math.min(1, hudY);
        for (String id : Catalog.ITEMS.keySet()) prices.putIfAbsent(id, 0.0);
    }
    private static void validateBazaarPrices(Map<String,Double> values) {
        for(var price:values.entrySet()) {
            if(!Catalog.ITEMS.containsKey(price.getKey()) || price.getKey().startsWith("TARANTULA_"))
                throw new IllegalArgumentException("Invalid Bazaar item");
            amount(Double.toString(price.getValue()));
        }
    }
}

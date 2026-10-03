package dev.arachneledger.config;

import dev.arachneledger.pricing.BazaarPrices;
import dev.arachneledger.pricing.GearValuation;
import dev.arachneledger.pricing.NpcPrices;
import dev.arachneledger.skyblock.Catalog;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persisted player preferences and future-item valuation rules.
 *
 * <p>Field names are part of the JSON save format. Keep them stable; migrations belong in {@link
 * #validate()}, and price changes must never revalue recorded journal entries implicitly.
 */
public final class Config {
    public static final double MAX_MANUAL_PRICE = 1_000_000_000_000.0;
    // A Crystal uses 34 priced ingredients; its recorded unit cost can exceed any one input.
    private static final double MAX_RECORDED_PRICE = 34 * MAX_MANUAL_PRICE;

    public enum View {
        COMPACT,
        DETAILED,
        GRAPH
    }

    public enum BazaarMode {
        INSTANT_SELL,
        SELL_OFFER;

        public String label() {
            return this == INSTANT_SELL ? "Instant sell" : "Sell offer";
        }
    }

    // Session selection and tracking behavior.
    public boolean total = false;
    public boolean hud = true;
    public boolean paused = false;
    public boolean killChat = true;
    public boolean rngTitles = true;
    public boolean rngValue = true;
    public boolean achievementNotifications = true;
    public boolean sessionRecapChat = true;
    public boolean dashboardFights = false;
    public FarmingPreferences farming = new FarmingPreferences();
    // Valuation preferences and participation threshold.
    public boolean autoBazaar = false;
    public boolean ironman = false;
    // Zero in legacy JSON; empty NPC defaults are filled once, before future manual edits.
    public int npcDefaultsVersion = 0;
    public BazaarMode bazaarMode = BazaarMode.INSTANT_SELL;
    public GraphPreferences graph = new GraphPreferences();
    public boolean scavengerCoins = true;
    public boolean salvageArmor = false;
    public boolean salvageWeapons = false;
    public long minimumDamage = 10_000;
    // Dashboard and HUD layout. Coordinates are normalized to the available screen space.
    public View view = View.DETAILED;
    public View hudView = View.DETAILED;
    public HudPreferences hudPreferences = new HudPreferences();
    public boolean hudAlwaysShow = false;
    public boolean hudBackground = false;
    public double hudX = -1;
    public double hudY = -1;
    public double hudScale = 1.0;
    public boolean manualTracking = false;
    public int corner = 0;
    // Saved manual values remain available when automatic pricing is disabled.
    public double crystalCost = 0;
    public double callingCost = 0;
    public boolean crystalConfigured = false;
    public String profile = "default";
    public Map<String, Double> prices = new LinkedHashMap<>();

    /** Null in pre-Bazaar settings: migrate every stored value, including zero, as an override. */
    public Set<String> manualPriceItems;

    // Each Bazaar mode has its own cache; a bid estimate must not become an ask estimate.
    public Map<String, Double> bazaarPrices = new LinkedHashMap<>();
    public Map<String, Double> bazaarSellOfferPrices = new LinkedHashMap<>();
    public long bazaarUpdatedAt = 0;

    public Config() {
        for (String id : Catalog.ITEMS.keySet()) {
            prices.put(id, NpcPrices.price(id));
        }
    }

    public boolean isManualPrice(String id) {
        return manualPriceItems == null || manualPriceItems.contains(id);
    }

    public double price(String id) {
        if (ironman) return NpcPrices.price(id);
        if (autoBazaar && !isManualPrice(id) && selectedBazaarPrices().containsKey(id)) {
            return selectedBazaarPrices().get(id);
        }
        return prices.getOrDefault(id, 0.0);
    }

    public String priceSource(String id) {
        if (ironman) {
            return NpcPrices.known(id) && NpcPrices.price(id) == 0
                    ? "Excluded (Ironman)"
                    : NpcPrices.source(id);
        }
        if (isManualPrice(id)) {
            return defaultPriceSource(id, "Manual");
        }
        if (autoBazaar && selectedBazaarPrices().containsKey(id)) {
            return "Bazaar";
        }
        return prices.getOrDefault(id, 0.0) > 0 ? defaultPriceSource(id, "Fallback") : "Unpriced";
    }

    private String defaultPriceSource(String id, String otherwise) {
        double value = prices.getOrDefault(id, 0.0);
        return value > 0 && Double.compare(value, NpcPrices.price(id)) == 0
                ? NpcPrices.source(id)
                : otherwise;
    }

    /** Values future loot only; journal entries retain the unit value saved when recorded. */
    public double lootPrice(String id) {
        return GearValuation.supports(id) ? GearValuation.price(id, this) : price(id);
    }

    public String lootPriceSource(String id) {
        return GearValuation.supports(id) ? GearValuation.source(id, this) : priceSource(id);
    }

    /** An excluded receipt remains a known zero even after the pricing mode changes. */
    public boolean intentionalZeroLoot(String id) {
        return ironman && NpcPrices.known(id) && lootPrice(id) == 0;
    }

    /** Reset future loot values deliberately; summon costs and recorded entries are untouched. */
    public void useNpcDefaults() {
        prices.clear();
        for (String id : Catalog.ITEMS.keySet()) prices.put(id, NpcPrices.price(id));
        manualPriceItems = new LinkedHashSet<>();
        autoBazaar = false;
        npcDefaultsVersion = 1;
    }

    private void migrateNpcDefaults() {
        if (npcDefaultsVersion >= 1) return;
        for (String id : NpcPrices.LEGACY_EMPTY_DEFAULTS) {
            if (Double.compare(prices.getOrDefault(id, 0.0), 0.0) == 0) {
                prices.put(id, NpcPrices.price(id));
            }
        }
        npcDefaultsVersion = 1;
    }

    public Map<String, Double> selectedBazaarPrices() {
        return bazaarMode == BazaarMode.SELL_OFFER ? bazaarSellOfferPrices : bazaarPrices;
    }

    private void migratePrices() {
        if (manualPriceItems == null) {
            manualPriceItems = new LinkedHashSet<>(prices.keySet());
        }
    }

    public void manualSet(String id, double value) {
        if (!Catalog.ITEMS.containsKey(id)) {
            throw new IllegalArgumentException("Unknown loot item.");
        }
        amount(Double.toString(value));
        migrateNpcDefaults();
        migratePrices();
        prices.put(id, value);
        manualPriceItems.add(id);
    }

    public void clearManual(String id) {
        if (!Catalog.ITEMS.containsKey(id)) {
            throw new IllegalArgumentException("Unknown loot item.");
        }
        migratePrices();
        manualPriceItems.remove(id);
    }

    /** Deliberate opt-in; Auction-only prices remain untouched. */
    public void useBazaarItems() {
        migratePrices();
        for (String id : Catalog.ITEMS.keySet()) {
            if (BazaarPrices.supports(id, this)) {
                manualPriceItems.remove(id);
            }
        }
        autoBazaar = true;
    }

    /** Current Shaggy recipe: 2 fragments, 16 enchanted spider eyes, 16 enchanted string. */
    public double recipeCost() {
        return 2 * price("ARACHNE_FRAGMENT")
                + 16 * price("ENCHANTED_SPIDER_EYE")
                + 16 * price("ENCHANTED_STRING");
    }

    public double effectiveCrystalCost() {
        return crystalConfigured ? crystalCost : recipeCost();
    }

    public static double amount(String input) {
        double value = Double.parseDouble(input.trim().replace(",", ""));
        if (!Double.isFinite(value) || value < 0 || value > MAX_MANUAL_PRICE) {
            throw new IllegalArgumentException("Use a number from 0 to 1 trillion.");
        }
        return value;
    }

    /** Composite recipe/salvage values are journal amounts, rather than individual price inputs. */
    public static void validateRecordedPrice(double value) {
        if (!Double.isFinite(value) || value < 0 || value > MAX_RECORDED_PRICE) {
            throw new IllegalArgumentException("Invalid recorded unit price.");
        }
    }

    /** Validate saved values, fill legacy defaults, and normalize display-only preferences. */
    public void validate() {
        validateManualSettings();
        migrateOptionalSettings();
        validateAutomaticPrices();
        normalizeHudLayout();
        for (String id : Catalog.ITEMS.keySet()) {
            prices.putIfAbsent(id, NpcPrices.price(id));
        }
    }

    private void validateManualSettings() {
        if (view == null
                || prices == null
                || profile == null
                || !profile.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Invalid settings");
        }
        amount(Double.toString(crystalCost));
        amount(Double.toString(callingCost));
        if (minimumDamage < 1 || minimumDamage > 1_000_000_000_000L) {
            throw new IllegalArgumentException("Minimum damage must be from 1 to 1 trillion.");
        }
        for (double price : prices.values()) {
            amount(Double.toString(price));
        }
    }

    private void migrateOptionalSettings() {
        if (farming == null) farming = new FarmingPreferences();
        farming.validate();
        migrateNpcDefaults();
        migratePrices();
        if (bazaarPrices == null) {
            bazaarPrices = new LinkedHashMap<>();
        }
        if (bazaarSellOfferPrices == null) {
            bazaarSellOfferPrices = new LinkedHashMap<>();
        }
        if (bazaarMode == null) {
            bazaarMode = BazaarMode.INSTANT_SELL;
        }
        if (graph == null) {
            graph = new GraphPreferences();
        }
        graph.validate();
        // Missing HUD choices receive the current Loot ledger default, without changing
        // an existing graph view, position, scale, or financial settings.
        if (hudPreferences == null) {
            hudPreferences = new HudPreferences();
        }
        hudPreferences.validate();
    }

    private void validateAutomaticPrices() {
        validateBazaarPrices(bazaarPrices);
        validateBazaarPrices(bazaarSellOfferPrices);
        if (bazaarUpdatedAt < 0) {
            throw new IllegalArgumentException("Invalid Bazaar timestamp");
        }
        if (manualPriceItems.stream()
                .anyMatch(id -> id == null || !Catalog.ITEMS.containsKey(id))) {
            throw new IllegalArgumentException("Invalid manual price item");
        }
    }

    private void normalizeHudLayout() {
        corner = Math.floorMod(corner, 4);
        if (hudView == null) {
            hudView = View.DETAILED;
        }
        if (!Double.isFinite(hudScale)) {
            hudScale = 1.0;
        }
        hudScale = Math.max(0.65, Math.min(1.6, hudScale));
        if (!Double.isFinite(hudX) || hudX < 0) {
            hudX = -1;
        } else {
            hudX = Math.min(1, hudX);
        }
        if (!Double.isFinite(hudY) || hudY < 0) {
            hudY = -1;
        } else {
            hudY = Math.min(1, hudY);
        }
    }

    private static void validateBazaarPrices(Map<String, Double> values) {
        for (var price : values.entrySet()) {
            if (!Catalog.ITEMS.containsKey(price.getKey())
                    || price.getKey().startsWith("TARANTULA_")) {
                throw new IllegalArgumentException("Invalid Bazaar item");
            }
            amount(Double.toString(price.getValue()));
        }
    }
}

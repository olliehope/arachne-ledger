package dev.arachneledger.pricing;

import dev.arachneledger.config.Config;
import dev.arachneledger.config.Store;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.Catalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Sale defaults, retained market settings and one-time migration without a game client. */
public final class NpcPricingChecks {
    private static int checks;
    private static final Map<String, Double> SALES =
            Map.ofEntries(
                    Map.entry("SOUL_STRING", 5_000.0),
                    Map.entry("ARACHNE_FRAGMENT", 500.0),
                    Map.entry("ENCHANTED_STRING", 576.0),
                    Map.entry("ENCHANTED_SPIDER_EYE", 480.0),
                    Map.entry("STRING", 3.0),
                    Map.entry("SPIDER_EYE", 3.0),
                    Map.entry("DARK_QUEENS_SOUL_DROP", 5_000.0),
                    Map.entry("ARACHNE_HELMET", 2_000.0),
                    Map.entry("ARACHNE_CHESTPLATE", 2_000.0),
                    Map.entry("ARACHNE_LEGGINGS", 2_000.0),
                    Map.entry("ARACHNE_BOOTS", 2_000.0),
                    Map.entry("LUXURIOUS_SPOOL", 1.0),
                    Map.entry("ARACHNE_FANG", 500.0),
                    Map.entry("ARACHNE_SHARD", 0.0),
                    Map.entry("ARACK", 5_000.0),
                    Map.entry("ESSENCE_SPIDER", 0.0),
                    Map.entry("TARANTULA_EPIC", 2_000.0),
                    Map.entry("TARANTULA_LEGENDARY", 100_000.0));

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static Config fresh() {
        Config config = new Config();
        config.validate();
        return config;
    }

    public static void main(String[] args) throws Exception {
        saleDefaults();
        retainedMarketPreferences();
        skippedMarketRequests();
        independentSalvageChoices();
        deliberateDefaultsReset();
        legacyMigrationAndPersistence();
        System.out.println(
                "PASS: " + checks + " NPC defaults, Ironman settings and migration checks.");
    }

    private static void saleDefaults() {
        Config config = fresh();
        yes(
                SALES.keySet().equals(Catalog.ITEMS.keySet()),
                "Every supported drop has a researched sale basis");
        for (var sale : SALES.entrySet()) {
            String id = sale.getKey();
            yes(NpcPrices.known(id), "The sale table recognizes " + id);
            eq(sale.getValue(), NpcPrices.price(id), "Researched base sale value for " + id);
            eq(sale.getValue(), config.prices.get(id), "New saved fallback value for " + id);
            eq(sale.getValue(), config.lootPrice(id), "Fresh loot value for " + id);
            if (sale.getValue() > 0) {
                String source = id.startsWith("TARANTULA_") ? "George pet sale" : "NPC sale";
                yes(source.equals(config.lootPriceSource(id)), "The seller is shown for " + id);
            } else {
                yes(
                        "No NPC sale".equals(NpcPrices.source(id)),
                        "Unsellable items are explicitly known");
                yes(
                        !config.intentionalZeroLoot(id),
                        "Normal pricing still permits a market value for " + id);
            }
        }
        eq(
                17_896,
                config.recipeCost(),
                "Crystal recipe uses ingredient values, not Crystal NPC sale value");
        config.ironman = true;
        for (String id : List.of("ESSENCE_SPIDER", "ARACHNE_SHARD")) {
            yes(
                    config.intentionalZeroLoot(id),
                    "Ironman marks unsellable loot as a complete zero valuation");
            yes(
                    "Excluded (Ironman)".equals(config.lootPriceSource(id)),
                    "Ironman explains excluded loot");
        }
        yes(!NpcPrices.known("FUTURE_DROP"), "An unknown item is not assumed to be unsellable");
        eq(0, config.lootPrice("FUTURE_DROP"), "An unknown drop cannot invent an NPC value");
        yes(
                "Unpriced".equals(config.lootPriceSource("FUTURE_DROP")),
                "Unknown drop prices remain missing");
        yes(
                !config.intentionalZeroLoot("FUTURE_DROP"),
                "Ironman cannot hide a genuinely unknown price");
    }

    private static void retainedMarketPreferences() {
        Config config = fresh();
        config.autoBazaar = true;
        config.bazaarMode = Config.BazaarMode.SELL_OFFER;
        config.bazaarUpdatedAt = 1_790_769_600_000L;
        config.bazaarPrices.putAll(Map.of("STRING", 20.0, "ESSENCE_SPIDER", 800.0));
        config.bazaarSellOfferPrices.putAll(Map.of("STRING", 30.0, "ESSENCE_SPIDER", 900.0));
        config.clearManual("STRING");
        config.clearManual("ESSENCE_SPIDER");
        config.manualSet("SOUL_STRING", 12_345);
        config.manualSet("ARACK", 13_579);
        config.manualSet("TARANTULA_LEGENDARY", 2_400_000);
        config.manualSet("ARACHNE_SHARD", 75_000);
        Map<String, Double> manual = Map.copyOf(config.prices);
        Set<String> overrides = Set.copyOf(config.manualPriceItems);
        Map<String, Double> bids = Map.copyOf(config.bazaarPrices);
        Map<String, Double> asks = Map.copyOf(config.bazaarSellOfferPrices);

        eq(30, config.lootPrice("STRING"), "Regular mode follows selected sell offers");
        eq(
                900,
                config.lootPrice("ESSENCE_SPIDER"),
                "Regular mode can value virtual currency on Bazaar");
        eq(
                12_345,
                config.lootPrice("SOUL_STRING"),
                "Regular mode retains deliberate manual prices");
        config.ironman = true;
        eq(3, config.lootPrice("STRING"), "Ironman bypasses Bazaar sell offers");
        eq(0, config.lootPrice("ESSENCE_SPIDER"), "Ironman bypasses market currency prices");
        eq(0, config.lootPrice("ARACHNE_SHARD"), "Ironman bypasses a shard's saved manual price");
        eq(
                5_000,
                config.lootPrice("SOUL_STRING"),
                "Ironman bypasses a sellable item's manual override");
        eq(5_000, config.lootPrice("ARACK"), "Ironman uses gear's researched NPC value");
        eq(
                100_000,
                config.lootPrice("TARANTULA_LEGENDARY"),
                "Ironman values pets at George rather than an auction estimate");
        yes(
                "George pet sale".equals(config.lootPriceSource("TARANTULA_LEGENDARY")),
                "George remains the visible pet seller");
        config.bazaarMode = Config.BazaarMode.INSTANT_SELL;
        eq(3, config.lootPrice("STRING"), "Ironman also bypasses instant-sell estimates");
        config.bazaarMode = Config.BazaarMode.SELL_OFFER;
        yes(
                config.prices.equals(manual) && config.manualPriceItems.equals(overrides),
                "Ironman retains all saved manual values and override choices");
        yes(
                config.bazaarPrices.equals(bids) && config.bazaarSellOfferPrices.equals(asks),
                "Ironman retains both market caches");
        yes(
                config.autoBazaar && config.bazaarMode == Config.BazaarMode.SELL_OFFER,
                "Ironman retains regular-mode Bazaar preferences");
        config.ironman = false;
        eq(30, config.lootPrice("STRING"), "Switching back restores selected market values");
        eq(
                900,
                config.lootPrice("ESSENCE_SPIDER"),
                "Switching back restores currency market value");
        eq(75_000, config.lootPrice("ARACHNE_SHARD"), "Switching back restores the shard override");
        eq(13_579, config.lootPrice("ARACK"), "Switching back restores saved gear value");
        eq(
                2_400_000,
                config.lootPrice("TARANTULA_LEGENDARY"),
                "Switching back restores saved pet value");
    }

    private static void independentSalvageChoices() {
        Config config = fresh();
        config.manualSet("ESSENCE_SPIDER", 800);
        config.ironman = true;
        config.salvageArmor = true;
        for (String id :
                List.of(
                        "ARACHNE_HELMET",
                        "ARACHNE_CHESTPLATE",
                        "ARACHNE_LEGGINGS",
                        "ARACHNE_BOOTS")) {
            eq(0, config.lootPrice(id), "Ironman armor salvage has no coin sale value");
            yes(config.intentionalZeroLoot(id), "All salvaged armor pieces are known zero");
            yes(
                    "Excluded salvage (Ironman)".equals(config.lootPriceSource(id)),
                    "The armor salvage exclusion is shown");
        }
        eq(5_000, config.lootPrice("ARACK"), "Armor salvage leaves weapon NPC sale available");
        yes(!config.intentionalZeroLoot("ARACK"), "A sold weapon remains ordinary priced loot");
        config.salvageArmor = false;
        config.salvageWeapons = true;
        eq(
                2_000,
                config.lootPrice("ARACHNE_BOOTS"),
                "Weapon salvage leaves armor NPC sale available");
        yes(!config.intentionalZeroLoot("ARACHNE_BOOTS"), "Sold armor no longer has a zero basis");
        eq(0, config.lootPrice("ARACK"), "Ironman weapon salvage has no coin sale value");
        yes(config.intentionalZeroLoot("ARACK"), "Salvaged weapons are known zero");
        eq(
                500,
                config.lootPrice("ARACHNE_FANG"),
                "Weapon salvage does not guess that a fang can be salvaged");
        config.ironman = false;
        eq(
                4_000,
                config.lootPrice("ARACK"),
                "Regular mode restores five essence at the saved valuation");
        yes(
                !config.intentionalZeroLoot("ARACK"),
                "Regular mode no longer excludes salvage revenue");
    }

    private static void skippedMarketRequests() {
        Config config = fresh();
        config.useBazaarItems();
        config.ironman = true;
        AtomicInteger requests = new AtomicInteger();
        long now = 1_000_000;
        BazaarPrices service =
                new BazaarPrices(
                        () -> {
                            requests.incrementAndGet();
                            return new BazaarPrices.Snapshot(now, Map.of("STRING", 99.0));
                        },
                        Runnable::run);
        yes(
                !service.tick(config, now) && requests.get() == 0,
                "Ironman does not request unused market prices");
        yes(
                !service.requestRefresh(config, now),
                "Manual market refresh is also bypassed in Ironman");
        yes(
                service.status(config, now).contains("Ironman"),
                "Ironman has no missing-market-price status");
        config.ironman = false;
        service.tick(config, now);
        yes(requests.get() == 1, "Returning to market pricing resumes saved automatic pricing");
        service.tick(config, now + 1);
        eq(99, config.price("STRING"), "Resumed refresh applies its market value");
    }

    private static void deliberateDefaultsReset() {
        Config config = fresh();
        config.manualSet("TARANTULA_EPIC", 77_777);
        config.manualSet("ESSENCE_SPIDER", 987);
        config.autoBazaar = true;
        config.bazaarMode = Config.BazaarMode.SELL_OFFER;
        config.bazaarPrices.put("STRING", 8.0);
        config.bazaarSellOfferPrices.put("STRING", 9.0);
        config.bazaarUpdatedAt = 123_456;
        config.ironman = true;
        config.salvageArmor = true;
        config.salvageWeapons = true;
        config.crystalConfigured = true;
        config.crystalCost = 98_765;
        config.callingCost = 4_440;
        config.minimumDamage = 25_000;
        config.scavengerCoins = false;
        config.hud = false;
        Ledger ledger = new Ledger();
        ledger.add(Ledger.Kind.LOOT, "ESSENCE_SPIDER", 8, 987, "test", 100);
        ledger.add(
                Ledger.Kind.CRYSTAL,
                "ARACHNE_CRYSTAL",
                1,
                config.effectiveCrystalCost(),
                "test",
                200);
        List<Ledger.Entry> recorded = List.copyOf(ledger.entries);

        config.useNpcDefaults();
        config.validate();
        yes(config.prices.equals(SALES), "NPC reset fills the complete sale table");
        yes(
                config.manualPriceItems.isEmpty() && !config.autoBazaar,
                "NPC reset deliberately removes future loot overrides and automatic pricing");
        eq(
                98_765,
                config.effectiveCrystalCost(),
                "NPC reset preserves actual configured Crystal acquisition cost");
        eq(4_440, config.callingCost, "NPC reset preserves Calling acquisition cost");
        yes(config.crystalConfigured, "NPC reset does not switch a fixed cost to recipe valuation");
        yes(
                config.ironman && config.salvageArmor && config.salvageWeapons,
                "NPC reset preserves pricing and salvage preferences");
        yes(
                config.minimumDamage == 25_000 && !config.scavengerCoins && !config.hud,
                "NPC reset preserves tracking and display choices");
        yes(
                config.bazaarMode == Config.BazaarMode.SELL_OFFER
                        && config.bazaarUpdatedAt == 123_456,
                "NPC reset preserves market mode and cache timestamp");
        eq(8, config.bazaarPrices.get("STRING"), "NPC reset retains cached bids for later opt-in");
        eq(
                9,
                config.bazaarSellOfferPrices.get("STRING"),
                "NPC reset retains cached asks for later opt-in");
        yes(
                recorded.equals(ledger.entries),
                "NPC reset cannot rewrite already recorded loot or costs");
        eq(7_896, ledger.stats(false).revenue(), "Old essence income retains its recorded basis");
        config.ironman = false;
        config.crystalConfigured = false;
        eq(
                17_896,
                config.effectiveCrystalCost(),
                "An explicitly selected recipe still costs its ingredient sum");
    }

    private static void legacyMigrationAndPersistence() throws Exception {
        Path root = Files.createTempDirectory("arachne-npc-pricing-");
        Path file = root.resolve("settings.json");
        Files.writeString(
                file,
                """
                {"prices":{
                  "SOUL_STRING":0,
                  "DARK_QUEENS_SOUL_DROP":0,
                  "LUXURIOUS_SPOOL":0,
                  "ARACHNE_FANG":0,
                  "TARANTULA_EPIC":0,
                  "TARANTULA_LEGENDARY":2345678,
                  "ARACHNE_BOOTS":0,
                  "ESSENCE_SPIDER":765
                },"crystalConfigured":true,"crystalCost":45678,"callingCost":3456}
                """);
        Config migrated = Store.read(file, Config.class, Config::new, Config::validate);
        for (String id :
                List.of(
                        "DARK_QUEENS_SOUL_DROP",
                        "LUXURIOUS_SPOOL",
                        "ARACHNE_FANG",
                        "TARANTULA_EPIC")) {
            eq(
                    SALES.get(id),
                    migrated.prices.get(id),
                    "Previously empty sale default is filled once: " + id);
        }
        eq(
                2_345_678,
                migrated.lootPrice("TARANTULA_LEGENDARY"),
                "A positive legacy pet override survives the defaults update");
        eq(
                0,
                migrated.lootPrice("SOUL_STRING"),
                "Existing zero overrides outside old empty defaults remain deliberate");
        eq(
                0,
                migrated.lootPrice("ARACHNE_BOOTS"),
                "An existing zero gear override survives migration");
        eq(
                765,
                migrated.lootPrice("ESSENCE_SPIDER"),
                "A legacy virtual-currency price remains available");
        eq(
                480,
                migrated.lootPrice("ENCHANTED_SPIDER_EYE"),
                "A missing legacy item gets the current sale fallback");
        eq(45_678, migrated.effectiveCrystalCost(), "Migration preserves configured summon costs");
        yes(migrated.npcDefaultsVersion == 1, "Defaults migration records completion");
        migrated.manualSet("TARANTULA_EPIC", 0);
        migrated.validate();
        eq(
                0,
                migrated.lootPrice("TARANTULA_EPIC"),
                "A new explicit zero is not replaced by repeated validation");
        migrated.ironman = true;
        Store.write(file, migrated);
        Config restored = Store.read(file, Config.class, Config::new, Config::validate);
        yes(restored.ironman, "Ironman preference persists across restart");
        eq(
                2_000,
                restored.lootPrice("TARANTULA_EPIC"),
                "Saved Ironman mode uses George even with a retained zero override");
        eq(
                100_000,
                restored.lootPrice("TARANTULA_LEGENDARY"),
                "Saved Ironman mode ignores retained auction values");
        eq(
                0,
                restored.prices.get("TARANTULA_EPIC"),
                "The migrated manual zero survives persistence");
        yes(
                restored.isManualPrice("TARANTULA_EPIC"),
                "The explicit zero retains its override status");
        restored.ironman = false;
        eq(
                0,
                restored.lootPrice("TARANTULA_EPIC"),
                "Turning Ironman off restores the explicit zero");
        eq(
                2_345_678,
                restored.lootPrice("TARANTULA_LEGENDARY"),
                "Turning Ironman off restores the original positive pet override");
        eq(
                765,
                restored.lootPrice("ESSENCE_SPIDER"),
                "Turning Ironman off restores the original essence value");
        eq(
                3_456,
                restored.callingCost,
                "Calling costs remain unchanged after migration and restart");
    }

    private NpcPricingChecks() {}
}

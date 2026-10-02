package dev.arachneledger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Valuation choices, saved-price migration and immutable history without a Minecraft client. */
public final class ValuationChecks {
    private static int checks;

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) {
            throw new AssertionError(why);
        }
    }

    private static void eq(double expected, double actual, String why) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        Config fresh = new Config();
        fresh.validate();
        yes(
                !fresh.salvageArmor && !fresh.salvageWeapons && fresh.scavengerCoins,
                "Fresh settings default to NPC and include marked coin gains");
        for (String id :
                List.of(
                        "ARACHNE_HELMET",
                        "ARACHNE_CHESTPLATE",
                        "ARACHNE_LEGGINGS",
                        "ARACHNE_BOOTS")) {
            eq(2_000, fresh.lootPrice(id), "Fresh armor uses per-piece NPC value: " + id);
            yes(fresh.lootPriceSource(id).equals("NPC sale"), "Default armor source is NPC: " + id);
        }
        eq(5_000, fresh.lootPrice("ARACK"), "Arack uses its own NPC price");
        fresh.manualSet("ESSENCE_SPIDER", 782.5);
        fresh.salvageArmor = true;
        eq(
                3_912.5,
                fresh.lootPrice("ARACHNE_BOOTS"),
                "Salvage values five essence, including fractional coins");
        eq(5_000, fresh.lootPrice("ARACK"), "Armor choice does not alter weapons");
        fresh.salvageWeapons = true;
        eq(3_912.5, fresh.lootPrice("ARACK"), "Weapon salvage uses effective essence");
        yes(fresh.lootPriceSource("ARACK").contains("Salvage"), "Salvage basis is visible");
        eq(782.5, fresh.lootPrice("ESSENCE_SPIDER"), "Direct essence rewards are not multiplied");
        eq(5_000, fresh.lootPrice("SOUL_STRING"), "Non-gear valuation is unchanged");
        yes(
                !GearValuation.supports("ARACHNE_FANG") && !GearValuation.supports("ARACHNE_SHARD"),
                "Unsupported drop salvage is never guessed");

        fresh.manualSet("ARACK", 12_345);
        fresh.manualSet("ARACHNE_BOOTS", 0);
        eq(
                3_912.5,
                fresh.lootPrice("ARACK"),
                "Deliberately selecting salvage uses essence instead of a saved sale price");
        eq(12_345, fresh.price("ARACK"), "Salvage preserves the saved sale-price override");
        fresh.salvageWeapons = false;
        fresh.salvageArmor = false;
        eq(12_345, fresh.lootPrice("ARACK"), "Returning to NPC restores explicit sale value");
        eq(
                0,
                fresh.lootPrice("ARACHNE_BOOTS"),
                "Explicit manual zero remains intentional in NPC mode");
        yes(
                fresh.lootPriceSource("ARACK").equals("Manual NPC value"),
                "Overridden sale basis is visible");
        fresh.clearManual("ARACK");
        eq(5_000, fresh.lootPrice("ARACK"), "Clearing the override restores NPC value");

        fresh.salvageWeapons = true;
        fresh.autoBazaar = true;
        fresh.bazaarPrices.put("ESSENCE_SPIDER", 910.125);
        eq(3_912.5, fresh.lootPrice("ARACK"), "Manual essence price wins over market cache");
        fresh.clearManual("ESSENCE_SPIDER");
        eq(4_550.625, fresh.lootPrice("ARACK"), "Salvage follows the automatic essence price");
        fresh.autoBazaar = false;
        eq(3_912.5, fresh.lootPrice("ARACK"), "Disabling Bazaar uses the saved essence fallback");
        fresh.manualSet("ESSENCE_SPIDER", 0);
        eq(0, fresh.lootPrice("ARACK"), "Unpriced essence leaves salvage explicitly unpriced");

        Path root =
                Files.createTempDirectory(
                        Path.of(System.getProperty("test.root", "build")), "arachne-valuations-");
        Path file = root.resolve("settings.json");
        Files.writeString(
                file, "{\"prices\":{\"ARACK\":54321,\"ARACHNE_BOOTS\":0,\"ESSENCE_SPIDER\":800}}");
        Config migrated = Store.read(file, Config.class, Config::new, Config::validate);
        eq(54_321, migrated.lootPrice("ARACK"), "An old saved gear value survives migration");
        eq(0, migrated.lootPrice("ARACHNE_BOOTS"), "An old saved zero survives migration");
        eq(
                2_000,
                migrated.lootPrice("ARACHNE_HELMET"),
                "A missing old gear price gets NPC fallback");
        yes(
                !migrated.salvageArmor && !migrated.salvageWeapons && migrated.scavengerCoins,
                "Old settings receive safe feature defaults");
        migrated.salvageArmor = true;
        migrated.salvageWeapons = true;
        migrated.scavengerCoins = false;
        Store.write(file, migrated);
        Config reopened = Store.read(file, Config.class, Config::new, Config::validate);
        yes(
                reopened.salvageArmor && reopened.salvageWeapons && !reopened.scavengerCoins,
                "Both gear choices and coin preference persist");
        eq(4_000, reopened.lootPrice("ARACK"), "Saved salvage choice survives restart");
        eq(
                54_321,
                reopened.price("ARACK"),
                "Stored manual sale price survives restart and salvage");

        Ledger ledger = new Ledger();
        ledger.add(Ledger.Kind.LOOT, "ARACK", 2, 5_000, "test", 100);
        reopened.manualSet("ESSENCE_SPIDER", 1_200);
        eq(
                10_000,
                ledger.stats(false).revenue(),
                "Changing valuation and essence prices never rewrites history");
        ledger.add(Ledger.Kind.LOOT, "ARACK", 1, reopened.lootPrice("ARACK"), "test", 200);
        eq(
                16_000,
                ledger.stats(false).revenue(),
                "A salvage drop contributes once, alongside old NPC-valued drops");
        eq(
                3,
                ledger.stats(false).loot().get("ARACK"),
                "Gear count stays gear rather than synthetic essence entries");
        ledger.newSession();
        ledger.add(Ledger.Kind.LOOT, "ARACK", 1, 5_000, "test", 300);
        ledger.reprice("ARACK", reopened.lootPrice("ARACK"));
        eq(6_000, ledger.stats(false).revenue(), "Explicit session reprice uses the chosen basis");
        eq(22_000, ledger.stats(true).revenue(), "Reprice preserves older sessions");
        trackerIntegration(root.resolve("tracker"));
        compositePriceBounds(root.resolve("composite"));
        System.out.println(
                "PASS: " + checks + " gear valuation, migration, market and history checks.");
    }

    private static void compositePriceBounds(Path root) throws Exception {
        Config config = new Config();
        config.manualSet("ESSENCE_SPIDER", Config.MAX_MANUAL_PRICE);
        config.salvageArmor = true;
        config.salvageWeapons = true;
        config.manualSet("ARACHNE_FRAGMENT", Config.MAX_MANUAL_PRICE);
        config.manualSet("ENCHANTED_STRING", Config.MAX_MANUAL_PRICE);
        config.manualSet("ENCHANTED_SPIDER_EYE", Config.MAX_MANUAL_PRICE);
        config.validate();
        eq(
                5 * Config.MAX_MANUAL_PRICE,
                config.lootPrice("ARACK"),
                "Valid essence input may yield a larger salvage unit value");
        eq(
                34 * Config.MAX_MANUAL_PRICE,
                config.effectiveCrystalCost(),
                "Recipe sums all valid ingredient inputs");

        Tracker tracker = new Tracker(root);
        tracker.account("composite-owner");
        tracker.config = config;
        long now = 1_000_000;
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", now);
        tracker.tick(now, true);
        tracker.message("☄ You placed an Arachne Crystal!", "Player", now + 1);
        tracker.message("[BOSS] Arachne: Ahhhh...A Calling...", "Player", now + 2);
        tracker.message("ARACHNE DOWN!", "Player", now + 1_000);
        tracker.message("Your Damage: 10,001 (Position #1)", "Player", now + 1_001);
        tracker.observeLootStand(new UUID(81, 1), "Arachne's Boots", now + 1_002);
        eq(
                34 * Config.MAX_MANUAL_PRICE,
                tracker.ledger.stats(false).costs(),
                "Automatic placement records the complete composite recipe cost");
        eq(
                5 * Config.MAX_MANUAL_PRICE,
                tracker.ledger.stats(false).revenue(),
                "Automatic salvage reward records its complete composite value");

        long fightId = tracker.ledger.recentFights(true).getFirst().id;
        tracker.editFightLoot(fightId, "ARACK", 1);
        tracker.reprice("ARACHNE_BOOTS", config.lootPrice("ARACHNE_BOOTS"));
        eq(
                10 * Config.MAX_MANUAL_PRICE,
                tracker.ledger.fightStats(fightId).revenue(),
                "Corrections and repricing accept valid composite units");
        tracker.ledger.validate();
        tracker.saveConfig();
        tracker.save();
        Tracker reopened = new Tracker(root);
        reopened.account("composite-owner");
        yes(reopened.ready(), "Composite recorded prices survive settings and ledger validation");
        eq(
                -24 * Config.MAX_MANUAL_PRICE,
                reopened.ledger.stats(true).profit(),
                "Persistence preserves full composite revenue and cost");
        yes(
                reopened.ledger.stats(true).graph().getLast().profit()
                        == reopened.ledger.stats(true).profit(),
                "Graph matches composite accounting after reload");

        boolean rejected = false;
        try {
            config.manualSet("STRING", Config.MAX_MANUAL_PRICE + 1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        yes(rejected, "Individual manual-price inputs retain their original limit");
        rejected = false;
        try {
            reopened.ledger.add(
                    Ledger.Kind.LOOT, "STRING", 1, Double.POSITIVE_INFINITY, "test", now);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        yes(rejected, "Derived-unit support never admits infinite prices");
    }

    private static void trackerIntegration(Path root) throws Exception {
        Tracker tracker = new Tracker(root);
        tracker.account("owner");
        long now = 1_000_000;
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "sidebar", now);
        tracker.tick(now, true);
        tracker.message("[BOSS] Arachne: Ahhhh...A Calling...", "Player", now + 10);
        tracker.message("ARACHNE DOWN!", "Player", now + 1_000);
        tracker.message("Your Damage: 10,001 (Position #1)", "Player", now + 1_010);
        long firstFight = tracker.ledger.recentFights(true).getFirst().id;

        tracker.observeLootStand(new UUID(42, 1), "§9Arachne's Helmet", now + 1_100);
        tracker.pickup("ARACHNE_HELMET", 1, now + 1_200);
        eq(
                1,
                tracker.ledger.stats(false).loot().get("ARACHNE_HELMET"),
                "NPC armor hologram and matching pickup count one gear item");
        eq(
                2_000,
                tracker.ledger.fightStats(firstFight).revenue(),
                "Automatic armor-stand recording uses NPC valuation");
        Ledger.Entry helmet =
                tracker.ledger.entries.stream()
                        .filter(
                                e ->
                                        e.kind() == Ledger.Kind.LOOT
                                                && e.item().equals("ARACHNE_HELMET"))
                        .findFirst()
                        .orElseThrow();
        eq(2_000, helmet.unit(), "NPC gear entry records its unit value");
        yes(
                helmet.fightId() == firstFight && helmet.source().equals("armor_stand"),
                "NPC gear remains associated with the completed fight");

        tracker.config.manualSet("ESSENCE_SPIDER", 1_200);
        tracker.config.salvageArmor = true;
        tracker.observeLootStand(new UUID(42, 2), "§9Arachne's Boots", now + 1_300);
        tracker.pickup("ARACHNE_BOOTS", 1, now + 1_400);
        eq(
                1,
                tracker.ledger.stats(false).loot().get("ARACHNE_BOOTS"),
                "Salvage armor still deduplicates hologram and pickup");
        eq(
                8_000,
                tracker.ledger.fightStats(firstFight).revenue(),
                "New armor stand uses five essence while earlier NPC armor keeps its value");
        eq(2_000, helmet.unit(), "Toggling salvage does not mutate an already recorded gear entry");

        tracker.pickup("ARACK", 1, now + 1_500);
        eq(
                13_000,
                tracker.ledger.fightStats(firstFight).revenue(),
                "Armor salvage toggle leaves weapon pickup on NPC valuation");
        tracker.config.salvageWeapons = true;
        tracker.pickup("ARACK", 1, now + 1_600);
        eq(
                19_000,
                tracker.ledger.fightStats(firstFight).revenue(),
                "New weapon pickup uses salvage value without repricing the earlier NPC weapon");
        eq(
                2,
                tracker.ledger.stats(false).loot().get("ARACK"),
                "Two independent weapon pickups remain two gear drops");
        eq(
                0,
                tracker.ledger.stats(false).loot().getOrDefault("ESSENCE_SPIDER", 0L),
                "Salvage valuation never creates synthetic essence reward entries");
        eq(
                0,
                tracker.ledger.entries.stream()
                        .filter(e -> e.item().equals("ESSENCE_SPIDER"))
                        .count(),
                "Gear salvage contributes income once in the journal");

        tracker.editFightLoot(firstFight, "ARACHNE_LEGGINGS", 1);
        eq(
                6_000,
                tracker.ledger.fightLootValues(firstFight).get("ARACHNE_LEGGINGS"),
                "A missing armor correction uses the current salvage basis");
        tracker.editFightLoot(firstFight, "ARACHNE_HELMET", 2);
        eq(
                4_000,
                tracker.ledger.fightLootValues(firstFight).get("ARACHNE_HELMET"),
                "Editing an existing NPC armor quantity preserves its historical unit value");
        tracker.config.salvageArmor = false;
        tracker.editFightLoot(firstFight, "ARACHNE_CHESTPLATE", 1);
        eq(
                2_000,
                tracker.ledger.fightLootValues(firstFight).get("ARACHNE_CHESTPLATE"),
                "A missing armor correction uses NPC after switching back");
        eq(
                6_000,
                tracker.ledger.fightLootValues(firstFight).get("ARACHNE_BOOTS"),
                "Switching back to NPC preserves older salvage armor value");
        eq(
                29_000,
                tracker.ledger.fightStats(firstFight).revenue(),
                "Mixed historical valuations and corrections sum once");

        tracker.message("[BOSS] Arachne: Ahhhh...A Calling...", "Player", now + 2_000);
        tracker.message("ARACHNE DOWN!", "Player", now + 3_000);
        tracker.message("Your Damage: 20,000 (Position #1)", "Player", now + 3_010);
        long secondFight = tracker.ledger.recentFights(true).getFirst().id;
        tracker.editFightLoot(secondFight, "ARACK", 1);
        eq(
                6_000,
                tracker.ledger.fightLootValues(secondFight).get("ARACK"),
                "A missing weapon correction uses five essence instead of raw saved NPC price");
        eq(
                29_000,
                tracker.ledger.fightStats(firstFight).revenue(),
                "A later fight correction cannot alter prior gear history");
        eq(
                0,
                tracker.ledger.stats(false).loot().getOrDefault("ESSENCE_SPIDER", 0L),
                "Corrections also keep gear counts separate from actual essence drops");
        tracker.saveConfig();
        tracker.save();
        Tracker restored = new Tracker(root);
        restored.account("owner");
        eq(
                35_000,
                restored.ledger.stats(true).revenue(),
                "NPC and salvage unit values survive full tracker persistence");
        eq(
                29_000,
                restored.ledger.fightStats(firstFight).revenue(),
                "Persisted earlier fight retains all historical gear bases");
    }
}

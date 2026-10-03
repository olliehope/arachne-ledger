package dev.arachneledger.pricing;

import java.util.Map;
import java.util.Set;

/** Coin sale values for base drops; virtual currencies have no NPC sale value. */
public final class NpcPrices {
    // NPC values: https://api.hypixel.net/v2/resources/skyblock/items
    // Pet sales: https://hypixelskyblock.minecraft.wiki/w/George/Prices
    // Pet values are per rarity; selling to George does not use Auction House prices.
    private static final Map<String, Double> PRICES =
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

    /** These were unpriced defaults before NPC/George values were supplied. */
    public static final Set<String> LEGACY_EMPTY_DEFAULTS =
            Set.of(
                    "DARK_QUEENS_SOUL_DROP",
                    "LUXURIOUS_SPOOL",
                    "ARACHNE_FANG",
                    "TARANTULA_EPIC",
                    "TARANTULA_LEGENDARY");

    public static boolean known(String id) {
        return PRICES.containsKey(id);
    }

    public static double price(String id) {
        return PRICES.getOrDefault(id, 0.0);
    }

    public static String source(String id) {
        if (!known(id)) return "Unpriced";
        if (price(id) == 0) return "No NPC sale";
        return id.startsWith("TARANTULA_") ? "George pet sale" : "NPC sale";
    }

    private NpcPrices() {}
}

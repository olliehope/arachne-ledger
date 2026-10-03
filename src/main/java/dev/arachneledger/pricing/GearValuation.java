package dev.arachneledger.pricing;

import dev.arachneledger.config.Config;

import java.util.Set;

/** Base, unupgraded Arachne drops. Star bonuses cannot be inferred from reward name tags. */
public final class GearValuation {
    private static final Set<String> ARMOR =
            Set.of("ARACHNE_HELMET", "ARACHNE_CHESTPLATE", "ARACHNE_LEGGINGS", "ARACHNE_BOOTS");
    public static final int SPIDER_ESSENCE = 5;

    public static boolean armor(String id) {
        return ARMOR.contains(id);
    }

    public static boolean weapon(String id) {
        return "ARACK".equals(id);
    }

    public static boolean supports(String id) {
        return armor(id) || weapon(id);
    }

    public static boolean salvaging(String id, Config config) {
        return armor(id) ? config.salvageArmor : weapon(id) && config.salvageWeapons;
    }

    // Hypixel's item data supplies NPC prices and five-essence salvage for base gear.
    // https://api.hypixel.net/v2/resources/skyblock/items
    public static double npcPrice(String id) {
        if (!supports(id)) throw new IllegalArgumentException("Unsupported gear item.");
        return NpcPrices.price(id);
    }

    public static double price(String id, Config config) {
        if (!supports(id)) {
            throw new IllegalArgumentException("Unsupported gear item.");
        }
        // Choosing salvage changes the valuation basis, without erasing a saved sale-price
        // override.
        if (salvaging(id, config)) {
            return SPIDER_ESSENCE * config.price("ESSENCE_SPIDER");
        }
        if (config.isManualPrice(id) && config.prices.containsKey(id)) {
            return config.price(id);
        }
        return npcPrice(id);
    }

    public static String source(String id, Config config) {
        if (salvaging(id, config)) {
            return config.ironman ? "Excluded salvage (Ironman)" : "Salvage (5 Spider Essence)";
        }
        if (config.ironman) return "NPC sale";
        return config.isManualPrice(id)
                        && config.prices.containsKey(id)
                        && Double.compare(config.price(id), npcPrice(id)) != 0
                ? "Manual NPC value"
                : "NPC sale";
    }

    private GearValuation() {}
}

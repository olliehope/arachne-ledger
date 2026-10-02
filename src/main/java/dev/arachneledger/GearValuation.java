package dev.arachneledger;

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

    // Wiki: armor 2,000 each; Arack 5,000. Both salvage into 5 Spider Essence.
    // https://hypixel-skyblock.fandom.com/wiki/Arachne%27s_Armor
    // https://hypixel-skyblock.fandom.com/wiki/Arack
    // https://wiki.hypixel.net/Salvaging
    public static double npcPrice(String id) {
        if (armor(id)) {
            return 2_000;
        }
        if (weapon(id)) {
            return 5_000;
        }
        throw new IllegalArgumentException("Unsupported gear item.");
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
            return "Salvage (5 Spider Essence)";
        }
        return config.isManualPrice(id)
                        && config.prices.containsKey(id)
                        && Double.compare(config.price(id), npcPrice(id)) != 0
                ? "Manual NPC value"
                : "NPC sale";
    }

    private GearValuation() {}
}

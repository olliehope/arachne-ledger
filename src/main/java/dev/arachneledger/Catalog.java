package dev.arachneledger;

import java.util.*;

public final class Catalog {
    public static final Map<String, String> ITEMS = new LinkedHashMap<>();
    static {
        ITEMS.put("SOUL_STRING", "Soul String");
        ITEMS.put("ARACHNE_FRAGMENT", "Arachne Fragment");
        ITEMS.put("ENCHANTED_STRING", "Enchanted String");
        ITEMS.put("ENCHANTED_SPIDER_EYE", "Enchanted Spider Eye");
        ITEMS.put("STRING", "String");
        ITEMS.put("SPIDER_EYE", "Spider Eye");
        ITEMS.put("DARK_QUEENS_SOUL_DROP", "Dark Queen's Soul");
        ITEMS.put("ARACHNE_HELMET", "Arachne's Helmet");
        ITEMS.put("ARACHNE_CHESTPLATE", "Arachne's Chestplate");
        ITEMS.put("ARACHNE_LEGGINGS", "Arachne's Leggings");
        ITEMS.put("ARACHNE_BOOTS", "Arachne's Boots");
        ITEMS.put("LUXURIOUS_SPOOL", "Luxurious Spool");
        ITEMS.put("ARACHNE_FANG", "Arachne's Fang");
        ITEMS.put("ARACHNE_SHARD", "Arachne Shard");
        ITEMS.put("ARACK", "Arack");
        ITEMS.put("ESSENCE_SPIDER", "Spider Essence");
        ITEMS.put("TARANTULA_EPIC", "Tarantula Pet (Epic)");
        ITEMS.put("TARANTULA_LEGENDARY", "Tarantula Pet (Legendary)");
    }
    public static String name(String id) { return ITEMS.getOrDefault(id, id); }
    private Catalog() {}
}

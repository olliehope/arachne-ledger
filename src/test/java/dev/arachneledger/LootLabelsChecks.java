package dev.arachneledger;

/** Captured Arachne hologram labels and close variants. */
public final class LootLabelsChecks {
    private static int checks;
    private static void drop(String raw, String item, int count) {
        checks++;
        var found = LootLabels.parse(raw);
        if (found == null || !found.item().equals(item) || found.count() != count)
            throw new AssertionError(raw + " expected " + item + " x" + count + ", got " + found);
    }
    private static void absent(String raw) {
        checks++;
        if (LootLabels.parse(raw) != null) throw new AssertionError("Not a drop: " + raw);
    }
    public static void main(String[] args) {
        drop("§aEnchanted String", "ENCHANTED_STRING", 1);
        drop("§dSpider Essence §8x8", "ESSENCE_SPIDER", 8);
        drop("§fString §8x10", "STRING", 10);
        drop("§aEnchanted Spider Eye", "ENCHANTED_SPIDER_EYE", 1);
        drop("§9Arachne Fragment", "ARACHNE_FRAGMENT", 1);
        drop("§fSpider Eye x30", "SPIDER_EYE", 30);
        drop("§aLuxurious Spool", "LUXURIOUS_SPOOL", 1);
        drop("§9Arachne's Leggings", "ARACHNE_LEGGINGS", 1);
        drop("§9Arachne Shard", "ARACHNE_SHARD", 1);
        drop("Arachne’s Leggings x2", "ARACHNE_LEGGINGS", 2);
        drop("44x Soul String", "SOUL_STRING", 44);
        absent("Arachne's Brood 10,000❤");
        absent("Spider Essence x0");
        absent("Spider Essence x1000001");
        absent("Visit Arachne's Sanctuary");
        System.out.println("PASS: " + checks + " Arachne armor-stand label checks.");
    }
}

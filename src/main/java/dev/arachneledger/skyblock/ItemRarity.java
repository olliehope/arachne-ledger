package dev.arachneledger.skyblock;

/** Shared loot display colours; synthetic essence uses its familiar SkyBlock pink. */
public final class ItemRarity {
    public enum Rarity {
        COMMON(0xFFFFFFFF),
        UNCOMMON(0xFF55FF55),
        RARE(0xFF5555FF),
        EPIC(0xFFAA00AA),
        LEGENDARY(0xFFFFAA00),
        SPECIAL(0xFFFF55FF);

        private final int color;

        Rarity(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    public static Rarity of(String item) {
        if (item == null) return Rarity.COMMON;
        return switch (item) {
            case "ENCHANTED_STRING", "ENCHANTED_SPIDER_EYE", "LUXURIOUS_SPOOL", "ARACHNE_FANG" ->
                    Rarity.UNCOMMON;
            case "SOUL_STRING",
                    "ARACHNE_FRAGMENT",
                    "ARACHNE_SHARD",
                    "ARACHNE_HELMET",
                    "ARACHNE_CHESTPLATE",
                    "ARACHNE_LEGGINGS",
                    "ARACHNE_BOOTS" ->
                    Rarity.RARE;
            case "ARACK", "DARK_QUEENS_SOUL_DROP", "TARANTULA_EPIC" -> Rarity.EPIC;
            case "TARANTULA_LEGENDARY" -> Rarity.LEGENDARY;
            case "ESSENCE_SPIDER" -> Rarity.SPECIAL;
            default -> Rarity.COMMON;
        };
    }

    public static int color(String item) {
        return of(item).color();
    }

    private ItemRarity() {}
}

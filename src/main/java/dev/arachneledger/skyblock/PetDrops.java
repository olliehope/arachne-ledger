package dev.arachneledger.skyblock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Personal pet receipts: pets claimed into the Pets menu need no item pickup packet. */
public final class PetDrops {
    // Anchoring the entire system receipt excludes player chat and another player's pet.
    // SkyHanni can insert an explicit rarity and change "a" to "an" in this message.
    private static final Pattern CLAIM =
            Pattern.compile(
                    "^You claimed (?:a|an) (.+?)! You can manage your Pets in the Pets Menu in your SkyBlock Menu\\.$",
                    Pattern.CASE_INSENSITIVE);

    public static LootLabels.Drop claim(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher match = CLAIM.matcher(LocationDetection.clean(raw));
        return match.matches() ? LootLabels.pet(raw, match.group(1), 1) : null;
    }

    public static boolean isTarantula(String item) {
        return "TARANTULA_EPIC".equals(item) || "TARANTULA_LEGENDARY".equals(item);
    }

    private PetDrops() {}
}

package dev.arachneledger;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the item holograms placed around Arachne when the boss dies. */
public final class LootLabels {
    public record Drop(String item, int count) {}

    private static final Pattern SUFFIX =
            Pattern.compile("^(.+?)\\s+[x×]\\s*([0-9][0-9,]*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX =
            Pattern.compile("^([0-9][0-9,]*)\\s*[x×]\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PET =
            Pattern.compile(
                    "^(?:\\[Lvl\\s+[0-9]{1,3}]\\s*)?(?:(Epic|Legendary)\\s+)?Tarantula(?: Pet)?(?:\\s+\\((Epic|Legendary)\\))?$",
                    Pattern.CASE_INSENSITIVE);

    public static Drop parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text =
                LocationDetection.clean(raw)
                        .replaceAll("^[^\\p{L}\\p{N}\\[]+", "")
                        .replaceAll("[^\\p{L}\\p{N})]+$", "")
                        .trim();
        int count = 1;
        Matcher suffix = SUFFIX.matcher(text);
        Matcher prefix = PREFIX.matcher(text);
        if (suffix.matches()) {
            count = quantity(suffix.group(2));
            text = suffix.group(1);
        } else if (prefix.matches()) {
            count = quantity(prefix.group(1));
            text = prefix.group(2);
        }
        if (count < 1) {
            return null;
        }
        for (var entry : Catalog.ITEMS.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(text.trim())) {
                return new Drop(entry.getKey(), count);
            }
        }
        // Preserve the spelling accepted by older ledger versions and manual corrections.
        if (text.equalsIgnoreCase("Arachne Fang")) {
            return new Drop("ARACHNE_FANG", count);
        }
        return pet(raw, text, count);
    }

    /** Personal claim messages and holograms share the same explicit-name/colour rules. */
    static Drop pet(String raw, String name, int count) {
        Matcher match = PET.matcher(name.trim());
        if (!match.matches() || count < 1) {
            return null;
        }
        String tier = match.group(1) != null ? match.group(1) : match.group(2);
        if (match.group(1) != null
                && match.group(2) != null
                && !match.group(1).equalsIgnoreCase(match.group(2))) {
            return null;
        }
        if (tier != null) {
            return new Drop("TARANTULA_" + tier.toUpperCase(Locale.ROOT), count);
        }
        char color = colorAtPet(raw);
        if (color == '5') {
            return new Drop("TARANTULA_EPIC", count);
        }
        if (color == '6') {
            return new Drop("TARANTULA_LEGENDARY", count);
        }
        // An uncoloured or unsupported-tier pet label must not guess a market value.
        return null;
    }

    /** getString() drops JSON text colours, which carry a pet's otherwise absent rarity. */
    public static String formatted(Component label) {
        if (label == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        label.visit(
                (style, text) -> {
                    char code = 'r';
                    if (style.getColor() != null) {
                        int rgb = style.getColor().getValue();
                        for (ChatFormatting format : ChatFormatting.values()) {
                            if (format.isColor()
                                    && format.getColor() != null
                                    && format.getColor() == rgb) {
                                code = format.getChar();
                                break;
                            }
                        }
                    }
                    result.append('\u00a7').append(code).append(text);
                    return Optional.empty();
                },
                Style.EMPTY);
        return result.toString();
    }

    private static char colorAtPet(String raw) {
        // Components can split a word into styled spans. Locate the visible name rather
        // than requiring "Tarantula" to be contiguous in the legacy-coloured string.
        StringBuilder visible = new StringBuilder();
        StringBuilder colors = new StringBuilder();
        char color = 'r';
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '\u00a7' && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(++i));
                if ("0123456789abcdefr".indexOf(code) >= 0) {
                    color = code;
                }
            } else {
                visible.append(raw.charAt(i));
                colors.append(color);
            }
        }
        int pet = visible.toString().toLowerCase(Locale.ROOT).indexOf("tarantula");
        return pet < 0 ? 'r' : colors.charAt(pet);
    }

    private static int quantity(String digits) {
        try {
            int value = Integer.parseInt(digits.replace(",", ""));
            return value > 0 && value <= 1_000_000 ? value : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private LootLabels() {}
}

package dev.arachneledger;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Interprets only server HUD text; it never treats player chat as a location. */
public final class LocationDetection {
    // Hypixel also inserts non-vanilla section codes (for example §v) into score owners.
    // They are formatting artifacts in the rendered sidebar, not letters of the location.
    private static final Pattern FORMAT = Pattern.compile("\u00a7.");
    private static final Pattern BRAND =
            Pattern.compile("(?i)(?:^|[^a-z0-9])hypixel(?:[^a-z0-9]|$)");

    public record Result(
            boolean onHypixel,
            boolean skyBlock,
            boolean sanctuary,
            String detectionReason,
            String location) {}

    public static Result detect(
            String host, String brand, String title, List<String> sidebar, List<String> tab) {
        boolean hypixel = hypixelHost(host) || BRAND.matcher(brand == null ? "" : brand).find();
        if (!hypixel) {
            return new Result(false, false, false, "Not connected to Hypixel", "");
        }
        String sideLocation = findLocation(sidebar);
        String tabLocation = findLocation(tab);
        // The sidebar gives the sub-area; tab commonly gives only the island name.
        String location = !sideLocation.isEmpty() ? sideLocation : tabLocation;
        boolean skyBlock =
                key(title).startsWith("skyblock")
                        || tab.stream().anyMatch(LocationDetection::skyBlockLabel)
                        || isSanctuary(location)
                        || key(tabLocation).equals("spidersden");
        if (!skyBlock) {
            return new Result(
                    true, false, false, "Waiting for SkyBlock sidebar or tab data", location);
        }
        if (isSanctuary(location)) {
            return new Result(
                    true,
                    true,
                    true,
                    sideLocation.isEmpty()
                            ? "Sanctuary detected in tab list"
                            : "Sanctuary detected in sidebar",
                    "Arachne's Sanctuary");
        }
        return new Result(
                true,
                true,
                false,
                location.isEmpty()
                        ? "SkyBlock detected; waiting for location"
                        : "Outside Sanctuary: " + location,
                location);
    }

    public static boolean hypixelHost(String address) {
        if (address == null) {
            return false;
        }
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon >= 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String normalized =
                Normalizer.normalize(FORMAT.matcher(text).replaceAll(""), Normalizer.Form.NFKC);
        StringBuilder result = new StringBuilder();
        normalized
                .codePoints()
                .forEach(
                        c -> {
                            if (Character.getType(c) == Character.FORMAT) {
                                return;
                            }
                            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                                result.append(' ');
                            } else if (c == 0x2018
                                    || c == 0x2019
                                    || c == 0x02BC
                                    || c == 0xFF07
                                    || c == '`') {
                                result.append('\'');
                            } else if (!Character.isISOControl(c)) {
                                result.appendCodePoint(c);
                            }
                        });
        return result.toString().replaceAll(" +", " ").trim();
    }

    /**
     * Hypixel's score owner may insert an emoji between the prefix and suffix, even inside a word.
     */
    public static String key(String text) {
        return clean(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static boolean isSanctuary(String text) {
        String value = key(text);
        return value.equals("arachnessanctuary") || value.equals("arachnesanctuary");
    }

    /** An explicit sub-area elsewhere rules out using a recent boss message as a fallback. */
    public static boolean definitelyElsewhere(String location) {
        return !key(location).isEmpty()
                && !isSanctuary(location)
                && !key(location).equals("spidersden");
    }

    private static boolean skyBlockLabel(String row) {
        String value = key(row);
        return value.equals("skyblock")
                || value.equals("gameskyblock")
                || value.equals("modeskyblock")
                || value.equals("gamemodeskyblock");
    }

    private static String findLocation(List<String> lines) {
        String area = "";
        String standalone = "";
        for (String line : lines) {
            String row = clean(line);
            // A strict whole-row comparison avoids matching quests, travel-scroll names or player
            // names.
            if (isSanctuary(row)) {
                standalone = "Arachne's Sanctuary";
            }
            int colon = row.indexOf(':');
            if (colon >= 0) {
                String label = key(row.substring(0, colon));
                String value = displayLocation(row.substring(colon + 1));
                // Tab can expose both an island (Area) and a sub-area (Location). Prefer the
                // latter.
                if (label.equals("location") && !value.isEmpty()) {
                    return value;
                }
                if (label.equals("area") && area.isEmpty()) {
                    area = value;
                }
            }
            int marker = row.indexOf('\u23e3');
            if (marker >= 0 && key(row.substring(0, marker)).isEmpty()) {
                String value = displayLocation(row.substring(marker + 1));
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return !area.isEmpty() ? area : standalone;
    }

    private static String displayLocation(String row) {
        if (isSanctuary(row)) {
            return "Arachne's Sanctuary";
        }
        if (key(row).equals("spidersden")) {
            return "Spider's Den";
        }
        // Remove scoreboard split symbols while preserving readable location names.
        StringBuilder value = new StringBuilder();
        clean(row)
                .codePoints()
                .forEach(
                        c -> {
                            if (Character.isLetterOrDigit(c)
                                    || c == ' '
                                    || c == '\''
                                    || c == '-'
                                    || c == '('
                                    || c == ')') {
                                value.appendCodePoint(c);
                            }
                        });
        return value.toString().trim();
    }

    private LocationDetection() {}
}

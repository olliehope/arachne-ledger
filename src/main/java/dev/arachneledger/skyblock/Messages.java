package dev.arachneledger.skyblock;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Anchored server-message parsers. Parsing a message never mutates tracking state. */
public final class Messages {
    public enum Summon {
        CRYSTAL,
        CALLING
    }

    public enum Event {
        NONE,
        SPAWN,
        ACTIVITY,
        DOWN,
        CRYSTAL,
        CALLING
    }

    // Emblems and player heads can appear on either side of a rank prefix. Accept only
    // decorative Unicode symbols here: chat delimiters such as ':' and '>' are excluded.
    private static final String DECORATION = "[\\p{So}\\p{Sk}\\p{Co}\\p{Mn}\\p{Me} ]*";
    private static final String INTEGER =
            "(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,3}(?: [0-9]{3})+)";
    private static final Pattern PLACED =
            Pattern.compile(
                    "^"
                            + DECORATION
                            + "(?:\\[[^]\\r\\n]+]"
                            + DECORATION
                            + ")*"
                            + "([A-Za-z0-9_]{1,16})"
                            + DECORATION
                            + " placed an "
                            + "(Arachne Crystal|Arachne's Calling)!"
                            + "(?: (Something is awakening!))?"
                            + "(?: \\(([1-4])/4\\))?$");
    private static final Pattern BOSS = Pattern.compile("^\\[BOSS] Arachne: .+$");
    private static final Pattern SPAWN =
            Pattern.compile(
                    "^\\[BOSS] Arachne: (?:"
                            + "A befitting welcome!|With your sacrifice\\.|"
                            + "The Era of Spiders begins now\\.|"
                            + "Ahhhh\\s*(?:\\.\\s*){3}A Calling\\s*(?:\\.\\s*){3})$");
    private static final Pattern DAMAGE =
            Pattern.compile(
                    "^"
                            + DECORATION
                            + "Your Damage: ("
                            + INTEGER
                            + ")(?: \\(Position #"
                            + INTEGER
                            + "\\))?$");

    public static String clean(String text) {
        return LocationDetection.clean(text);
    }

    /** Only server-style boss/summon lines are cues; ordinary player chat is excluded. */
    public static boolean isArachneCue(String text) {
        String message = clean(text);
        return message.equals("ARACHNE DOWN!")
                || BOSS.matcher(message).matches()
                || PLACED.matcher(message).matches();
    }

    /** Final summon cues belong to everyone; ownership only determines who pays the cost. */
    public static boolean isSummoning(String text) {
        return summon(text) != null;
    }

    /**
     * Identifies a placement after its ownership has been authenticated by {@link #parse}. Rank,
     * emblems, YOU/name substitutions and an added awakening phrase do not create another expense.
     * Each numbered Calling remains distinct so successive fragments can be paid for separately.
     */
    public static String placementIdentity(String text) {
        Matcher placement = PLACED.matcher(clean(text));
        if (!placement.matches()) {
            return null;
        }
        if (placement.group(2).equals("Arachne Crystal")) {
            return "ARACHNE_CRYSTAL";
        }
        String counter = placement.group(4);
        return "ARACHNE_KEEPER_FRAGMENT" + (counter == null ? "" : ":" + counter + "/4");
    }

    /** Return a complete ritual's kind, independent of the player who placed it. */
    public static Summon summon(String text) {
        Matcher match = PLACED.matcher(clean(text));
        if (!match.matches() || match.group(3) == null) {
            return null;
        }
        if (match.group(2).equals("Arachne Crystal") && match.group(4) == null)
            return Summon.CRYSTAL;
        return match.group(2).equals("Arachne's Calling") && "4".equals(match.group(4))
                ? Summon.CALLING
                : null;
    }

    public static Event parse(String text, String player) {
        String message = clean(text);
        if (message.equals("ARACHNE DOWN!")) {
            return Event.DOWN;
        }
        // The final boss welcome differs between Callings and Crystals. Keep this anchored
        // to known server dialogue so death lines and quoted player chat cannot start a fight.
        if (SPAWN.matcher(message).matches()) {
            return Event.SPAWN;
        }
        Matcher placement = PLACED.matcher(message);
        if (placement.matches()
                && (placement.group(1).equalsIgnoreCase(player)
                        || placement.group(1).equalsIgnoreCase("You"))) {
            return placement.group(2).equals("Arachne Crystal") ? Event.CRYSTAL : Event.CALLING;
        }
        if (BOSS.matcher(message).matches()
                && !message.equals("[BOSS] Arachne: No, this is impossible...")
                && !message.equals("[BOSS] Arachne: I will be back, even stronger!")) {
            return Event.ACTIVITY;
        }
        return Event.NONE;
    }

    public static OptionalLong damage(String text) {
        Matcher match = DAMAGE.matcher(clean(text));
        if (!match.matches()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(
                    Long.parseLong(match.group(1).replace(",", "").replace(" ", "")));
        } catch (NumberFormatException ex) {
            return OptionalLong.empty();
        }
    }

    private Messages() {}
}

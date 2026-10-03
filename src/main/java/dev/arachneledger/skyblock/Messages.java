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

    private static final Pattern PLACED =
            Pattern.compile(
                    "^[^A-Za-z0-9\\[]*" // Optional icon before the server's player/rank text.
                            + "(?:\\[[^]\\r\\n]+]\\s*)*"
                            + "([A-Za-z0-9_]{1,16}) placed an "
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
            Pattern.compile("^Your Damage: ([0-9,]+)(?: \\(Position #[0-9,]+\\))?$");

    public static String clean(String text) {
        return text.replaceAll("\u00a7.", "")
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u00a0', ' ')
                .replace('\u202f', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll(" +", " ")
                .trim();
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
        if (SPAWN.matcher(message.replace("\u2026", "...")).matches()) {
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
            return OptionalLong.of(Long.parseLong(match.group(1).replace(",", "")));
        } catch (NumberFormatException ex) {
            return OptionalLong.empty();
        }
    }

    private Messages() {}
}

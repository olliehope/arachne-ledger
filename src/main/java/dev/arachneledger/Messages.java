package dev.arachneledger;

import java.util.regex.*;
import java.util.OptionalLong;

public final class Messages {
    public enum Event { NONE, SPAWN, ACTIVITY, DOWN, CRYSTAL, CALLING }
    private static final Pattern PLACED = Pattern.compile("^[^A-Za-z0-9\\[]*(?:\\[[^]\\r\\n]+]\\s*)*([A-Za-z0-9_]{1,16}) placed an (Arachne Crystal|Arachne's Calling)!(?: (Something is awakening!))?(?: \\(([1-4])/4\\))?$");
    private static final Pattern BOSS = Pattern.compile("^\\[BOSS] Arachne: .+$");
    private static final Pattern SPAWN = Pattern.compile("^\\[BOSS] Arachne: (?:A befitting welcome!|With your sacrifice\\.|The Era of Spiders begins now\\.|Ahhhh\\s*(?:\\.\\s*){3}A Calling\\s*(?:\\.\\s*){3})$");
    private static final Pattern DAMAGE = Pattern.compile("^Your Damage: ([0-9,]+)(?: \\(Position #[0-9,]+\\))?$");
    public static String clean(String text) {
        return text.replaceAll("\u00a7.", "")
            .replace('\u2019', '\'').replace('\u2018', '\'')
            .replace('\u00a0', ' ').replace('\u202f', ' ')
            .replaceAll("[\\u200B-\\u200D\\uFEFF]", "").replaceAll(" +", " ").trim();
    }
    /** Only server-style boss/summon lines are cues; ordinary player chat is excluded. */
    public static boolean isArachneCue(String text) {
        String s = clean(text);
        return s.equals("ARACHNE DOWN!") || BOSS.matcher(s).matches() || PLACED.matcher(s).matches();
    }
    /** Final summon cues belong to everyone; ownership only determines who pays the cost. */
    public static boolean isSummoning(String text) {
        Matcher match = PLACED.matcher(clean(text));
        if (!match.matches() || match.group(3) == null) return false;
        return match.group(2).equals("Arachne Crystal") ? match.group(4) == null : "4".equals(match.group(4));
    }
    public static Event parse(String text, String player) {
        String s = clean(text);
        if (s.equals("ARACHNE DOWN!")) return Event.DOWN;
        // The final boss welcome differs between Callings and Crystals. Keep this anchored
        // to known server dialogue so death lines and quoted player chat cannot start a fight.
        if (SPAWN.matcher(s.replace("\u2026", "...")).matches()) return Event.SPAWN;
        Matcher m = PLACED.matcher(s);
        if (m.matches() && (m.group(1).equalsIgnoreCase(player) || m.group(1).equalsIgnoreCase("You")))
            return m.group(2).equals("Arachne Crystal") ? Event.CRYSTAL : Event.CALLING;
        if (BOSS.matcher(s).matches() && !s.equals("[BOSS] Arachne: No, this is impossible...")
                && !s.equals("[BOSS] Arachne: I will be back, even stronger!")) return Event.ACTIVITY;
        return Event.NONE;
    }
    public static OptionalLong damage(String text) {
        Matcher match = DAMAGE.matcher(clean(text));
        if (!match.matches()) return OptionalLong.empty();
        try { return OptionalLong.of(Long.parseLong(match.group(1).replace(",", ""))); }
        catch (NumberFormatException ex) { return OptionalLong.empty(); }
    }
    private Messages() {}
}

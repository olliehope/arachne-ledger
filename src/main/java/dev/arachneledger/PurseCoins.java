package dev.arachneledger;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Pairs the sidebar's yellow gain annotation with an actual purse increase. A display
 * is a snapshot, not an event: identical polls must never add the same coins again.
 * This cannot distinguish Scavenger from other marked coin rewards while farming.
 */
public final class PurseCoins {
    public static final String ITEM = "SCAVENGER_COINS";
    private static final long PAIR_MILLIS = 2_000;
    private static final Pattern LINE = Pattern.compile(
        "^(?:Purse|Piggy): ([0-9][0-9,]*(?:\\.[0-9]+)?)(?: \\(([+-])([0-9][0-9,]*(?:\\.[0-9]+)?)\\))?$");
    private double previous = Double.NaN, pending, annotationUsed;
    private long pendingAt;
    private boolean eligibleBefore;

    record Snapshot(double purse, double gain) {}
    static Snapshot parse(List<String> lines) {
        for (String line : lines) {
            var match = LINE.matcher(Messages.clean(line));
            if (!match.matches()) continue;
            try {
                double purse = Double.parseDouble(match.group(1).replace(",", ""));
                double gain = !"+".equals(match.group(2)) ? 0 : Double.parseDouble(match.group(3).replace(",", ""));
                if (Double.isFinite(purse) && Double.isFinite(gain) && purse >= 0 && gain >= 0)
                    return new Snapshot(purse, gain);
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    public double observe(List<String> lines, boolean eligible, long now) {
        Snapshot current = parse(lines);
        if (current == null) { reset(); return 0; }
        double delta = Double.isNaN(previous) ? 0 : current.purse - previous;
        previous = current.purse;
        boolean continuous = eligible && eligibleBefore;
        eligibleBefore = eligible;
        // Warps, menus, pauses and entering a fight establish a baseline first.
        if (!continuous) { pending = 0; annotationUsed = 0; return 0; }
        if (now < pendingAt || now - pendingAt > PAIR_MILLIS || delta < 0) { pending = 0; annotationUsed = 0; }
        if (delta > 0) { pending += delta; pendingAt = now; annotationUsed = 0; }
        if (current.gain <= 0 || pending <= 0) return 0;
        double accepted = Math.min(pending, Math.max(0, current.gain - annotationUsed));
        // Retain unmatched delta briefly: total and gain can arrive in separate packets.
        // Track the used annotation allowance so identical polls cannot consume it again.
        pending -= accepted; annotationUsed += accepted;
        return accepted <= 1_000_000_000_000.0 ? accepted : 0;
    }

    public void reset() { previous = Double.NaN; pending = 0; annotationUsed = 0; pendingAt = 0; eligibleBefore = false; }
}

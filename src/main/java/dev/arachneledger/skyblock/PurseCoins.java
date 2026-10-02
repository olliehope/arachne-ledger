package dev.arachneledger.skyblock;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Pairs the sidebar's yellow gain annotation with an actual purse increase. A display is a
 * snapshot, not an event: identical polls must never add the same coins again. This cannot
 * distinguish Scavenger from other marked coin rewards while farming.
 */
public final class PurseCoins {
    public static final String ITEM = "SCAVENGER_COINS";
    private static final long PAIR_MILLIS = 2_000;
    private static final Pattern LINE =
            Pattern.compile(
                    "^(?:Purse|Piggy): ([0-9][0-9,]*(?:\\.[0-9]+)?)(?: \\(([+-])([0-9][0-9,]*(?:\\.[0-9]+)?)\\))?$");
    private double previousPurse = Double.NaN;
    private double unmatchedPurseGain;
    private double consumedAnnotation;
    private long lastGainAt;
    private boolean previouslyEligible;

    record Snapshot(double purse, double gain) {}

    static Snapshot parse(List<String> lines) {
        for (String line : lines) {
            var match = LINE.matcher(Messages.clean(line));
            if (!match.matches()) {
                continue;
            }
            try {
                double purse = Double.parseDouble(match.group(1).replace(",", ""));
                double gain =
                        !"+".equals(match.group(2))
                                ? 0
                                : Double.parseDouble(match.group(3).replace(",", ""));
                if (Double.isFinite(purse) && Double.isFinite(gain) && purse >= 0 && gain >= 0) {
                    return new Snapshot(purse, gain);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public double observe(List<String> lines, boolean eligible, long now) {
        Snapshot current = parse(lines);
        if (current == null) {
            reset();
            return 0;
        }
        double delta = Double.isNaN(previousPurse) ? 0 : current.purse - previousPurse;
        previousPurse = current.purse;
        boolean continuous = eligible && previouslyEligible;
        previouslyEligible = eligible;
        // Warps, menus, pauses and entering a fight establish a baseline first.
        if (!continuous) {
            unmatchedPurseGain = 0;
            consumedAnnotation = 0;
            return 0;
        }
        if (now < lastGainAt || now - lastGainAt > PAIR_MILLIS || delta < 0) {
            unmatchedPurseGain = 0;
            consumedAnnotation = 0;
        }
        if (delta > 0) {
            unmatchedPurseGain += delta;
            lastGainAt = now;
            consumedAnnotation = 0;
        }
        if (current.gain <= 0 || unmatchedPurseGain <= 0) {
            return 0;
        }
        double accepted =
                Math.min(unmatchedPurseGain, Math.max(0, current.gain - consumedAnnotation));
        // Retain unmatched delta briefly: total and gain can arrive in separate packets.
        // Track the used annotation allowance so identical polls cannot consume it again.
        unmatchedPurseGain -= accepted;
        consumedAnnotation += accepted;
        return accepted <= 1_000_000_000_000.0 ? accepted : 0;
    }

    public void reset() {
        previousPurse = Double.NaN;
        unmatchedPurseGain = 0;
        consumedAnnotation = 0;
        lastGainAt = 0;
        previouslyEligible = false;
    }
}

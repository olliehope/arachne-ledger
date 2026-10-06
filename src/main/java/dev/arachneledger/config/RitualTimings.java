package dev.arachneledger.config;

import java.util.ArrayList;
import java.util.List;

/** Recent confirmed placement-to-spawn observations; estimates never become ledger events. */
public final class RitualTimings {
    public static final int MAX_SAMPLES = 9;
    public static final int MIN_SAMPLES = 3;
    public static final long MIN_MILLIS = 5_000;
    public static final long MAX_MILLIS = 60_000;
    public List<Long> crystal = new ArrayList<>();
    public List<Long> calling = new ArrayList<>();

    public record Estimate(long medianMillis, long minimumMillis, long maximumMillis, int samples) {
        public boolean learned() {
            return samples >= MIN_SAMPLES;
        }
    }

    /** Reject implausible durations and keep each tier's latest bounded set independently. */
    public boolean observe(boolean isCrystal, long millis) {
        if (!valid(millis)) return false;
        List<Long> observations = isCrystal ? crystal : calling;
        observations.add(millis);
        if (observations.size() > MAX_SAMPLES) observations.removeFirst();
        return true;
    }

    public Estimate estimate(boolean isCrystal) {
        List<Long> observations = isCrystal ? crystal : calling;
        if (observations.isEmpty()) return new Estimate(0, 0, 0, 0);
        long[] sorted = observations.stream().mapToLong(Long::longValue).sorted().toArray();
        int middle = sorted.length / 2;
        long median = sorted[middle];
        if (sorted.length % 2 == 0) median = (sorted[middle - 1] + median) / 2;
        return new Estimate(median, sorted[0], sorted[sorted.length - 1], sorted.length);
    }

    public void clear() {
        crystal.clear();
        calling.clear();
    }

    public RitualTimings copy() {
        RitualTimings copy = new RitualTimings();
        copy.crystal = new ArrayList<>(crystal);
        copy.calling = new ArrayList<>(calling);
        return copy;
    }

    public void validate() {
        crystal = sanitize(crystal);
        calling = sanitize(calling);
    }

    private static List<Long> sanitize(List<Long> observations) {
        List<Long> valid = new ArrayList<>();
        if (observations != null) {
            for (Long millis : observations) {
                if (millis != null && valid(millis)) valid.add(millis);
            }
        }
        return new ArrayList<>(
                valid.subList(Math.max(0, valid.size() - MAX_SAMPLES), valid.size()));
    }

    private static boolean valid(long millis) {
        return millis >= MIN_MILLIS && millis <= MAX_MILLIS;
    }
}

package dev.arachneledger.tracking;

import dev.arachneledger.config.FarmingPreferences;
import dev.arachneledger.skyblock.Messages;

import java.util.Locale;

/** A best-effort ritual estimate, kept completely separate from the recorded active clock. */
public final class PedestalTimer {
    private static final long SAMPLE_DELAY_MILLIS = 3_000;
    private static final long SAMPLE_BURST_MILLIS = 60;
    private static final long QUICK_CRYSTAL_REMAINING = 21_000;
    private static final long NORMAL_CRYSTAL_REMAINING = 37_000;
    private long placedAt, estimatedSpawnAt, expiresAt, burstAt;
    private Messages.Summon kind;
    private boolean adaptive, sampled;
    private int dustCount;

    public record View(String label, String detail, int color, double seconds, boolean fighting) {}

    /** Repeated relays during a ritual cannot restart or extend its countdown. */
    public boolean begin(Messages.Summon summon, long now, FarmingPreferences preferences) {
        if (summon == null || now <= 0 || (kind != null && now < expiresAt)) return false;
        kind = summon;
        placedAt = now;
        long duration =
                1_000L
                        * (kind == Messages.Summon.CRYSTAL
                                ? preferences.crystalSpawnSeconds
                                : preferences.callingSpawnSeconds);
        estimatedSpawnAt = now + duration;
        expiresAt = now + Math.max(60_000, duration + 15_000);
        adaptive = preferences.adaptiveCrystalTimer && kind == Messages.Summon.CRYSTAL;
        sampled = false;
        burstAt = 0;
        dustCount = 0;
        return true;
    }

    /** Only nearby ritual dust is forwarded here, on the client thread. */
    public void particles(long now) {
        if (!adaptive
                || kind == null
                || sampled
                || now < placedAt + SAMPLE_DELAY_MILLIS
                || now >= expiresAt) return;
        finishSample(now);
        if (sampled) return;
        if (burstAt == 0) burstAt = now;
        // The ritual variant is identified by dust events in its first burst, rather than the
        // packet's requested particle count. Ignore unrelated dust before the ritual starts.
        dustCount = Math.min(21, dustCount + 1);
    }

    public View view(long now) {
        if (kind == null || now < placedAt || now >= expiresAt) return null;
        finishSample(now);
        double remaining = Math.max(0, (estimatedSpawnAt - now) / 1_000.0);
        String label =
                remaining > 0
                        ? String.format(Locale.ROOT, "Arachne in ~%.1fs", remaining)
                        : "Arachne · awaiting spawn";
        String detail = kind == Messages.Summon.CRYSTAL ? "Crystal ritual" : "Calling ritual";
        return new View(label, detail, remaining <= 5 ? 0xFFFFAA00 : 0xFFFFFF55, remaining, false);
    }

    private void finishSample(long now) {
        if (!adaptive || sampled || burstAt == 0 || now < burstAt + SAMPLE_BURST_MILLIS) return;
        sampled = true;
        estimatedSpawnAt =
                burstAt + (dustCount <= 20 ? QUICK_CRYSTAL_REMAINING : NORMAL_CRYSTAL_REMAINING);
        // With no observed burst the configured fallback remains intact. A missing packet is
        // never evidence of a quick variant, and an estimate never confirms a real spawn.
    }

    public void clear() {
        kind = null;
        placedAt = estimatedSpawnAt = expiresAt = burstAt = 0;
        adaptive = sampled = false;
        dustCount = 0;
    }
}

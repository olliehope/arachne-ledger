package dev.arachneledger.tracking;

import dev.arachneledger.config.FarmingPreferences;
import dev.arachneledger.skyblock.Messages;

import java.util.Locale;

/** A bounded ritual estimate. Neither its expiry nor its particle sample can record a spawn. */
public final class PedestalTimer {
    private static final long SAMPLE_DELAY_MILLIS = 3_000;
    private static final long SAMPLE_BURST_MILLIS = 350;
    private static final long SAMPLE_IDLE_MILLIS = 150;
    private static final long SAMPLE_DEADLINE_MILLIS = 8_000;
    private static final int MIN_QUICK_SAMPLE = 8;
    private static final long QUICK_CRYSTAL_REMAINING = 21_000;
    private static final long NORMAL_CRYSTAL_REMAINING = 37_000;
    private long placedAt, estimatedSpawnAt, expiresAt, burstAt, lastDustAt;
    private Messages.Summon kind;
    private boolean adaptive, sampled, fragmented;
    private int dustCount;
    private long labelTenth = -1;
    private String cachedLabel;

    public record View(String label, String detail, int color, double seconds, boolean fighting) {}

    /** Repeated relays during a ritual cannot restart or extend its countdown. */
    public boolean begin(Messages.Summon summon, long now, FarmingPreferences preferences) {
        if (summon == null || now <= 0 || pending(now)) return false;
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
        sampled = fragmented = false;
        burstAt = lastDustAt = 0;
        dustCount = 0;
        labelTenth = -1;
        cachedLabel = null;
        return true;
    }

    public boolean pending(long now) {
        return kind != null && now >= placedAt && now < expiresAt;
    }

    /** Only nearby ritual dust is forwarded here, on the client thread. */
    public void particles(long now) {
        if (!adaptive || !pending(now) || sampled || now < placedAt + SAMPLE_DELAY_MILLIS) return;
        finishSample(now);
        if (sampled || now >= placedAt + SAMPLE_DEADLINE_MILLIS) return;
        if (burstAt == 0) burstAt = now;
        if (now < lastDustAt) return;
        if (lastDustAt > 0 && now - lastDustAt > SAMPLE_IDLE_MILLIS) fragmented = true;
        lastDustAt = now;
        dustCount = Math.min(21, dustCount + 1);
    }

    public View view(long now) {
        if (!pending(now)) return null;
        finishSample(now);
        double remaining = Math.max(0, (estimatedSpawnAt - now) / 1_000.0);
        long tenth = remaining > 0 ? Math.round(remaining * 10) : -1;
        if (cachedLabel == null || labelTenth != tenth) {
            cachedLabel =
                    remaining > 0
                            ? String.format(Locale.ROOT, "Arachne in ~%.1fs", remaining)
                            : "Arachne · awaiting spawn";
            labelTenth = tenth;
        }
        String detail = kind == Messages.Summon.CRYSTAL ? "Crystal ritual" : "Calling ritual";
        return new View(
                cachedLabel, detail, remaining <= 5 ? 0xFFFFAA00 : 0xFFFFFF55, remaining, false);
    }

    private void finishSample(long now) {
        if (!adaptive || sampled) return;
        if (burstAt == 0) {
            if (now >= placedAt + SAMPLE_DEADLINE_MILLIS) sampled = true;
            return;
        }
        if (now < burstAt + SAMPLE_BURST_MILLIS && now < placedAt + SAMPLE_DEADLINE_MILLIS) return;
        sampled = true;
        // Dense delivery is evidence of the normal ritual. Sparse or fragmented packets
        // cannot prove a quick variant; they retain the configured fallback.
        if (dustCount > 20) {
            estimatedSpawnAt = burstAt + NORMAL_CRYSTAL_REMAINING;
        } else if (!fragmented && dustCount >= MIN_QUICK_SAMPLE) {
            estimatedSpawnAt = burstAt + QUICK_CRYSTAL_REMAINING;
        }
    }

    public void clear() {
        kind = null;
        placedAt = estimatedSpawnAt = expiresAt = burstAt = lastDustAt = 0;
        adaptive = sampled = fragmented = false;
        dustCount = 0;
        labelTenth = -1;
        cachedLabel = null;
    }
}

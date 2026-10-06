package dev.arachneledger.tracking;

import dev.arachneledger.config.FarmingPreferences;
import dev.arachneledger.config.RitualTimings;
import dev.arachneledger.skyblock.Messages;

import java.util.Locale;

/** A bounded ritual estimate. Expiry and particles can never confirm a spawn or record a kill. */
public final class PedestalTimer {
    private long placedAt, estimatedSpawnAt, expiresAt, lastObservedAt;
    private Messages.Summon kind;
    private long labelTenth = -1;
    private String cachedLabel, detail;

    public record View(String label, String detail, int color, double seconds, boolean fighting) {}

    /** Repeated relays during a ritual cannot restart or extend its countdown. */
    public boolean begin(Messages.Summon summon, long now, FarmingPreferences preferences) {
        if (summon == null || now <= 0) return false;
        if (kind != null && now < lastObservedAt) {
            clear();
            return false;
        }
        if (pending(now)) return false;
        kind = summon;
        placedAt = lastObservedAt = now;
        boolean crystal = kind == Messages.Summon.CRYSTAL;
        long duration =
                1_000L
                        * (crystal
                                ? preferences.crystalSpawnSeconds
                                : preferences.callingSpawnSeconds);
        RitualTimings.Estimate estimate = preferences.ritualTimings.estimate(crystal);
        String ritual = crystal ? "Crystal ritual" : "Calling ritual";
        if (preferences.adaptiveCrystalTimer && estimate.learned()) {
            duration = estimate.medianMillis();
            detail =
                    String.format(
                            Locale.ROOT,
                            "%s · %d spawns · %.1f–%.1fs observed",
                            ritual,
                            estimate.samples(),
                            estimate.minimumMillis() / 1_000.0,
                            estimate.maximumMillis() / 1_000.0);
        } else {
            detail = ritual;
        }
        estimatedSpawnAt = now + duration;
        expiresAt = now + Math.max(60_000, duration + 15_000);
        labelTenth = -1;
        cachedLabel = null;
        return true;
    }

    public boolean pending(long now) {
        return kind != null && now >= placedAt && now < expiresAt;
    }

    /** Kept for older client integrations. Packet density is not a reliable spawn-time signal. */
    public void particles(long now) {}

    /** Only call after a genuinely new server-confirmed spawn, never for ordinary boss dialogue. */
    public boolean confirmSpawn(long now, FarmingPreferences preferences) {
        boolean learned =
                kind != null
                        && now >= lastObservedAt
                        && pending(now)
                        && preferences.ritualTimings.observe(
                                kind == Messages.Summon.CRYSTAL, now - placedAt);
        clear();
        return learned;
    }

    public View view(long now) {
        if (kind != null && now < lastObservedAt) {
            clear();
            return null;
        }
        if (!pending(now)) return null;
        lastObservedAt = now;
        double remaining = Math.max(0, (estimatedSpawnAt - now) / 1_000.0);
        long tenth = remaining > 0 ? Math.round(remaining * 10) : -1;
        if (cachedLabel == null || labelTenth != tenth) {
            cachedLabel =
                    remaining > 0
                            ? String.format(Locale.ROOT, "Arachne in ~%.1fs", remaining)
                            : "Arachne · awaiting spawn";
            labelTenth = tenth;
        }
        return new View(
                cachedLabel, detail, remaining <= 5 ? 0xFFFFAA00 : 0xFFFFFF55, remaining, false);
    }

    public void clear() {
        kind = null;
        placedAt = estimatedSpawnAt = expiresAt = lastObservedAt = 0;
        labelTenth = -1;
        cachedLabel = detail = null;
    }
}

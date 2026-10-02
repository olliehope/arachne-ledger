package dev.arachneledger.ui;

import java.util.Locale;

/** Locale-stable display formatting shared by chat, screens, and the HUD. */
public final class Format {
    public static String coins(double value) {
        double magnitude = Math.abs(value);
        if (magnitude >= 1_000_000_000) {
            return String.format(Locale.ROOT, "%.2fb", value / 1_000_000_000);
        }
        if (magnitude >= 1_000_000) {
            return String.format(Locale.ROOT, "%.2fm", value / 1_000_000);
        }
        if (magnitude >= 1000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000);
        }
        return String.format(Locale.ROOT, "%,.0f", value);
    }

    public static String time(long millis) {
        long seconds = millis / 1000;
        return String.format(
                Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private Format() {}
}

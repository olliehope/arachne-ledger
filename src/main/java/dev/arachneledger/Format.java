package dev.arachneledger;
import java.util.Locale;
public final class Format {
    public static String coins(double n) {
        double a = Math.abs(n);
        if (a >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fb", n / 1_000_000_000);
        if (a >= 1_000_000) return String.format(Locale.ROOT, "%.2fm", n / 1_000_000);
        if (a >= 1000) return String.format(Locale.ROOT, "%.1fk", n / 1000);
        return String.format(Locale.ROOT, "%,.0f", n);
    }
    public static String time(long millis) {
        long s = millis / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s/3600, s/60%60, s%60);
    }
    private Format() {}
}

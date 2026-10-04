package dev.arachneledger.config;

/** One optional personal target; it never changes the session or its recorded earnings. */
public final class SessionGoals {
    public enum Kind {
        NONE("Off"),
        KILLS("Kills"),
        ACTIVE_TIME("Active time"),
        PROFIT("Net profit");
        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public Kind kind = Kind.NONE;
    public long kills = 100;
    public long activeMillis = 3_600_000;
    public double profit = 1_000_000;
    public boolean notify = true;

    public SessionGoals copy() {
        SessionGoals copy = new SessionGoals();
        copy.kind = kind;
        copy.kills = kills;
        copy.activeMillis = activeMillis;
        copy.profit = profit;
        copy.notify = notify;
        return copy;
    }

    public void validate() {
        if (kind == null) kind = Kind.NONE;
        kills = Math.clamp(kills, 1, 1_000_000);
        activeMillis = Math.clamp(activeMillis, 60_000, 3_600_000_000L);
        if (!Double.isFinite(profit)) profit = 1_000_000;
        profit = Math.clamp(profit, 1, Config.MAX_MANUAL_PRICE);
    }
}

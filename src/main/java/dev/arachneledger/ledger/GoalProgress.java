package dev.arachneledger.ledger;

import dev.arachneledger.config.SessionGoals;

/** Session-only progress. The total/session display switch does not alter a personal target. */
public record GoalProgress(SessionGoals.Kind kind, double current, double target) {
    private static final GoalProgress OFF = new GoalProgress(SessionGoals.Kind.NONE, 0, 1);

    public static GoalProgress calculate(Ledger ledger, SessionGoals goals) {
        if (goals.kind == SessionGoals.Kind.NONE) return OFF;
        Ledger.Stats stats = ledger.stats(false);
        return switch (goals.kind) {
            case KILLS -> new GoalProgress(goals.kind, stats.kills(), goals.kills);
            case ACTIVE_TIME -> new GoalProgress(goals.kind, stats.elapsed(), goals.activeMillis);
            case PROFIT -> new GoalProgress(goals.kind, stats.profit(), goals.profit);
            case NONE -> OFF;
        };
    }

    public boolean enabled() {
        return kind != SessionGoals.Kind.NONE;
    }

    public boolean reached() {
        return enabled() && current >= target;
    }

    public double fraction() {
        return enabled() ? Math.clamp(current / target, 0, 1) : 0;
    }
}

package dev.arachneledger.ledger;

import dev.arachneledger.config.Config;
import dev.arachneledger.config.SessionGoals;

/** Local completion transitions: loading an already completed goal never replays its alert. */
public final class SessionGoalMonitor {
    /** Only the selected target identifies a goal; inactive values and muting are independent. */
    public record Acknowledgement(long session, SessionGoals.Kind kind, double target) {
        public boolean valid(long currentSession) {
            if (session < 1
                    || session > currentSession
                    || kind == null
                    || !Double.isFinite(target)) {
                return false;
            }
            return switch (kind) {
                case KILLS -> target >= 1 && target <= 1_000_000 && target == Math.floor(target);
                case ACTIVE_TIME ->
                        target >= 60_000
                                && target <= 3_600_000_000L
                                && target == Math.floor(target);
                case PROFIT -> target >= 1 && target <= Config.MAX_MANUAL_PRICE;
                case NONE -> false;
            };
        }
    }

    private Ledger owner;
    private Acknowledgement selection;
    private boolean completed;
    private boolean changed;

    /** The caller persists display metadata before it delivers any returned completion notice. */
    public boolean consumeChanged() {
        boolean result = changed;
        changed = false;
        return result;
    }

    public boolean update(Ledger ledger, SessionGoals goals, GoalProgress progress) {
        changed = false;
        if (!progress.enabled()) {
            owner = null;
            selection = null;
            completed = false;
            return false;
        }
        Acknowledgement next =
                new Acknowledgement(ledger.sessionId, progress.kind(), progress.target());
        if (owner != ledger || !next.equals(selection)) {
            owner = ledger;
            selection = next;
            completed = next.equals(ledger.sessionGoalAcknowledgement);
            if (!completed && progress.reached()) acknowledge(ledger, next);
            return false;
        }
        if (!completed && progress.reached()) {
            acknowledge(ledger, next);
            return goals.notify;
        }
        return false;
    }

    private void acknowledge(Ledger ledger, Acknowledgement target) {
        ledger.sessionGoalAcknowledgement = target;
        completed = true;
        changed = true;
    }
}

package dev.arachneledger.ui.screen;

import dev.arachneledger.tracking.Tracker;

/** All history pages share one saved scope and preserve it if storage refuses a change. */
final class ScopeSelection {
    static boolean select(Tracker tracker, boolean total) {
        if (!tracker.error.isEmpty()) return false;
        boolean previous = tracker.config.total;
        tracker.config.total = total;
        tracker.saveConfig();
        if (!tracker.error.isEmpty()) {
            tracker.config.total = previous;
            return false;
        }
        return true;
    }

    private ScopeSelection() {}
}

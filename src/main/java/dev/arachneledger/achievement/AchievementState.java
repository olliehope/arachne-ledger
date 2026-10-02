package dev.arachneledger.achievement;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Persistent unlocks and delivery acknowledgments, saved with the ledger rather than settings. */
public final class AchievementState {
    public boolean initialized;
    public int catalogVersion;
    public Map<String, Long> earnedAt = new LinkedHashMap<>();
    public Set<String> notified = new LinkedHashSet<>();
    private transient long revision;

    public long revision() {
        return revision;
    }

    public void validate() {
        if (earnedAt == null) {
            earnedAt = new LinkedHashMap<>();
        }
        if (notified == null) {
            notified = new LinkedHashSet<>();
        }
        earnedAt.entrySet()
                .removeIf(
                        entry ->
                                entry.getKey() == null
                                        || entry.getKey().isBlank()
                                        || entry.getValue() == null
                                        || entry.getValue() < 0);
        notified.removeIf(id -> id == null || id.isBlank() || !earnedAt.containsKey(id));
        catalogVersion = Math.max(0, catalogVersion);
        revision++;
    }

    void changed() {
        revision++;
    }
}

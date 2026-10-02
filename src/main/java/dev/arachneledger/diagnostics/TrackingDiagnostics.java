package dev.arachneledger.diagnostics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A bounded, local observation log. Repeated hologram scans occupy one row rather than flooding it.
 */
public final class TrackingDiagnostics {
    public static final int CAPACITY = 120;

    public enum Kind {
        LOCATION,
        SUMMON,
        SPAWN,
        DEATH,
        DAMAGE,
        LOOT,
        SCAVENGER,
        STORAGE,
        SESSION
    }

    public enum Result {
        ACCEPTED,
        IGNORED,
        INFO
    }

    public record Event(
            long firstAt,
            long at,
            Kind kind,
            Result result,
            String detail,
            String reason,
            long repeats) {}

    private final Deque<Event> events = new ArrayDeque<>();
    private final Set<String> seen = new LinkedHashSet<>();
    private long revision;

    public void record(long now, Kind kind, Result result, String detail, String reason) {
        String boundedDetail = text(detail), boundedReason = text(reason);
        Event last = events.peekLast();
        if (last != null
                && last.kind == kind
                && last.result == result
                && last.detail.equals(boundedDetail)
                && last.reason.equals(boundedReason)) {
            events.removeLast();
            events.addLast(
                    new Event(
                            last.firstAt,
                            now,
                            kind,
                            result,
                            boundedDetail,
                            boundedReason,
                            Math.min(Long.MAX_VALUE - 1, last.repeats) + 1));
        } else {
            if (events.size() == CAPACITY) events.removeFirst();
            events.addLast(new Event(now, now, kind, result, boundedDetail, boundedReason, 1));
        }
        revision++;
    }

    public List<Event> newestFirst() {
        return List.copyOf(events.reversed());
    }

    /** Entity identity keeps repeated scans from displacing useful fight events. */
    public void once(
            String identity, long now, Kind kind, Result result, String detail, String reason) {
        if (!seen.add(identity)) return;
        if (seen.size() > CAPACITY * 2) seen.remove(seen.iterator().next());
        record(now, kind, result, detail, reason);
    }

    public long revision() {
        return revision;
    }

    public void clear() {
        events.clear();
        seen.clear();
        revision++;
    }

    private static String text(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\p{Cntrl}]", " ");
        return clean.substring(0, Math.min(220, clean.length()));
    }
}

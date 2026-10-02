package dev.arachneledger.ui;

import dev.arachneledger.config.GraphPreferences;
import dev.arachneledger.ledger.Analytics;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Display-only graph calculations. Recorded prices remain authoritative for every series. */
public final class GraphData {
    private GraphData() {}

    /** A known spawn, positioned on the same active-time axis as journal entries. */
    public record Marker(long fightId, long elapsed) {}

    public record Snapshot(
            Map<GraphPreferences.Metric, List<Ledger.Point>> series,
            Map<GraphPreferences.Metric, List<Ledger.Point>> projections,
            List<Marker> spawns,
            Map<GraphPreferences.Metric, Double> values,
            Map<GraphPreferences.Metric, Double> hourly,
            Map<GraphPreferences.Metric, Double> projectedHourly,
            Map<GraphPreferences.Metric, Double> projectedTotals,
            long elapsed,
            long duration,
            boolean projectionReady,
            long windowMillis,
            long windowKills,
            GraphPreferences.Metric primary,
            long spawnCount) {
        public double value(GraphPreferences.Metric metric) {
            return values.getOrDefault(metric, 0.0);
        }

        public double hourly(GraphPreferences.Metric metric) {
            return hourly.getOrDefault(metric, 0.0);
        }

        public double projectedHourly(GraphPreferences.Metric metric) {
            return projectedHourly.getOrDefault(metric, 0.0);
        }

        public double projectedTotal(GraphPreferences.Metric metric) {
            return projectedTotals.getOrDefault(metric, 0.0);
        }
    }

    // A render reads this many times between journal changes. Keep the expensive journal
    // traversal separate from elapsed-time updates and display toggles.
    private static final Map<Ledger, Cache> CACHE = new WeakHashMap<>();

    private static final class Cache {
        Actual session, total;
        Recent recent;
        Snapshot snapshot;
        long revision = -1, activeMillis = -1;
        boolean scope, projection;
        Set<GraphPreferences.Metric> enabled;
        GraphPreferences.Metric primary;
    }

    private record Actual(
            long revision,
            Map<GraphPreferences.Metric, List<Ledger.Point>> series,
            Map<GraphPreferences.Metric, Double> values,
            List<Marker> spawns) {}

    private record Recent(
            long revision,
            long activeMillis,
            long windowMillis,
            long kills,
            boolean ready,
            Map<GraphPreferences.Metric, Double> hourly) {}

    public static synchronized Snapshot build(
            Ledger ledger, boolean total, GraphPreferences preferences) {
        Objects.requireNonNull(ledger, "ledger");
        GraphPreferences prefs = preferences == null ? new GraphPreferences() : preferences;
        Set<GraphPreferences.Metric> enabled = prefs.enabledSeries();
        GraphPreferences.Metric primary = prefs.effectiveMetric();
        long revision = ledger.revision();
        Cache cache = CACHE.computeIfAbsent(ledger, ignored -> new Cache());
        if (cache.snapshot != null
                && cache.revision == revision
                && cache.activeMillis == ledger.activeMillis
                && cache.scope == total
                && cache.projection == prefs.showProjection
                && Objects.equals(cache.enabled, enabled)
                && cache.primary == primary) {
            return cache.snapshot;
        }

        Actual actual = total ? cache.total : cache.session;
        if (actual == null || actual.revision != revision) {
            actual = calculateActual(ledger, total, revision);
            if (total) {
                cache.total = actual;
            } else {
                cache.session = actual;
            }
        }
        Recent recent = cache.recent;
        if (recent == null
                || recent.revision != revision
                || recent.activeMillis != ledger.activeMillis) {
            recent = calculateRecent(ledger, revision);
            cache.recent = recent;
        }

        long elapsed = Math.max(0, ledger.activeMillis - (total ? 0 : ledger.sessionMillis));
        Map<GraphPreferences.Metric, List<Ledger.Point>> selected =
                new EnumMap<>(GraphPreferences.Metric.class);
        Map<GraphPreferences.Metric, List<Ledger.Point>> projected =
                new EnumMap<>(GraphPreferences.Metric.class);
        Map<GraphPreferences.Metric, Double> hourly = zeroValues();
        Map<GraphPreferences.Metric, Double> futureTotals = zeroValues();
        for (GraphPreferences.Metric metric : GraphPreferences.Metric.values()) {
            hourly.put(metric, elapsed > 0 ? actual.values.get(metric) * 3_600_000.0 / elapsed : 0);
            futureTotals.put(
                    metric,
                    actual.values.get(metric)
                            + (recent.ready
                                    ? recent.hourly.get(metric)
                                            * Analytics.WINDOW_MILLIS
                                            / 3_600_000.0
                                    : 0));
        }
        boolean drawProjection = prefs.showProjection && recent.ready && !enabled.isEmpty();
        long duration = drawProjection ? elapsed + Analytics.WINDOW_MILLIS : elapsed;
        for (GraphPreferences.Metric metric : enabled) {
            selected.put(metric, actual.series.get(metric));
            if (drawProjection) {
                double current = actual.values.get(metric);
                double future = futureTotals.get(metric);
                projected.put(
                        metric,
                        List.of(
                                new Ledger.Point(elapsed, current),
                                new Ledger.Point(duration, future)));
            }
        }
        Snapshot snapshot =
                new Snapshot(
                        immutable(selected),
                        immutable(projected),
                        actual.spawns,
                        actual.values,
                        immutable(hourly),
                        recent.hourly,
                        immutable(futureTotals),
                        elapsed,
                        duration,
                        recent.ready,
                        recent.windowMillis,
                        recent.kills,
                        primary,
                        actual.spawns.size());
        cache.snapshot = snapshot;
        cache.revision = revision;
        cache.activeMillis = ledger.activeMillis;
        cache.scope = total;
        cache.projection = prefs.showProjection;
        cache.enabled = enabled;
        cache.primary = primary;
        return snapshot;
    }

    private static Actual calculateActual(Ledger ledger, boolean total, long revision) {
        Map<GraphPreferences.Metric, Double> values = zeroValues();
        Map<GraphPreferences.Metric, List<Ledger.Point>> series =
                new EnumMap<>(GraphPreferences.Metric.class);
        for (GraphPreferences.Metric metric : GraphPreferences.Metric.values()) {
            List<Ledger.Point> points = new ArrayList<>();
            points.add(new Ledger.Point(0, 0));
            series.put(metric, points);
        }
        long origin = total ? 0 : ledger.sessionMillis;
        for (int i = total ? 0 : ledger.sessionStart; i < ledger.entries.size(); i++) {
            Ledger.Entry entry = ledger.entries.get(i);
            accumulate(values, entry);
            long elapsed = Math.max(0, entry.elapsed() - origin);
            for (GraphPreferences.Metric metric : GraphPreferences.Metric.values()) {
                series.get(metric).add(new Ledger.Point(elapsed, values.get(metric)));
            }
        }
        for (GraphPreferences.Metric metric : GraphPreferences.Metric.values()) {
            series.put(metric, List.copyOf(series.get(metric)));
        }
        long elapsed = Math.max(0, ledger.activeMillis - origin);
        List<Marker> spawns = new ArrayList<>();
        for (FightRecord fight : ledger.fights) {
            long position =
                    (fight.spawnActiveMillis >= 0 ? fight.spawnActiveMillis : fight.activeStart)
                            - origin;
            if (fight.spawned > 0
                    && (total || fight.session == ledger.sessionId)
                    && position >= 0
                    && position <= elapsed) {
                spawns.add(new Marker(fight.id, position));
            }
        }
        spawns.sort(Comparator.comparingLong(Marker::elapsed).thenComparingLong(Marker::fightId));
        return new Actual(revision, immutable(series), immutable(values), List.copyOf(spawns));
    }

    private static Recent calculateRecent(Ledger ledger, long revision) {
        long sessionElapsed = Math.max(0, ledger.activeMillis - ledger.sessionMillis);
        long window = Math.min(Analytics.WINDOW_MILLIS, sessionElapsed);
        long start = ledger.activeMillis - window;
        boolean fromSessionStart = sessionElapsed <= Analytics.WINDOW_MILLIS;
        long kills = 0;
        Map<GraphPreferences.Metric, Double> values = zeroValues();
        for (int i = ledger.entries.size() - 1; i >= ledger.sessionStart; i--) {
            Ledger.Entry entry = ledger.entries.get(i);
            if (entry.elapsed() < start || (!fromSessionStart && entry.elapsed() == start)) {
                break;
            }
            accumulate(values, entry);
            if (entry.kind() == Ledger.Kind.KILL) {
                kills += entry.count();
            }
        }
        boolean ready = window >= Analytics.WARMUP_MILLIS && kills > 0;
        for (GraphPreferences.Metric metric : GraphPreferences.Metric.values()) {
            values.put(metric, ready ? values.get(metric) * 3_600_000.0 / window : 0);
        }
        return new Recent(revision, ledger.activeMillis, window, kills, ready, immutable(values));
    }

    private static void accumulate(
            Map<GraphPreferences.Metric, Double> values, Ledger.Entry entry) {
        values.merge(GraphPreferences.Metric.PROFIT, entry.income() - entry.cost(), Double::sum);
        if (entry.kind() == Ledger.Kind.LOOT) {
            values.merge(GraphPreferences.Metric.LOOT, entry.income(), Double::sum);
        }
        values.merge(GraphPreferences.Metric.COSTS, entry.cost(), Double::sum);
    }

    private static Map<GraphPreferences.Metric, Double> zeroValues() {
        Map<GraphPreferences.Metric, Double> values = new EnumMap<>(GraphPreferences.Metric.class);
        for (GraphPreferences.Metric metric : GraphPreferences.Metric.values()) {
            values.put(metric, 0.0);
        }
        return values;
    }

    private static <V> Map<GraphPreferences.Metric, V> immutable(
            Map<GraphPreferences.Metric, V> values) {
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }
}

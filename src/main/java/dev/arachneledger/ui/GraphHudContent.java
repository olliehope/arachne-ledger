package dev.arachneledger.ui;

import dev.arachneledger.config.GraphPreferences;
import dev.arachneledger.tracking.Tracker;

import java.util.ArrayList;
import java.util.List;

/** Graph overlay summaries share the graph snapshot's refresh rate instead of formatting at FPS. */
public final class GraphHudContent {
    private static final GraphPreferences.Metric[] METRICS = GraphPreferences.Metric.values();

    public record Row(String label, String value, int color) {}

    public record Snapshot(List<Row> metrics, List<Row> status) {
        public Snapshot {
            metrics = List.copyOf(metrics);
            status = List.copyOf(status);
        }
    }

    public static final class Cache {
        private GraphData.Snapshot data;
        private int flags = -1, status = -1;
        private Snapshot snapshot;

        public Snapshot get(Tracker tracker, GraphData.Snapshot current) {
            GraphPreferences preferences = tracker.config.graph;
            int currentFlags = flags(preferences);
            int currentStatus = status(tracker);
            if (data != current || flags != currentFlags || status != currentStatus) {
                data = current;
                flags = currentFlags;
                status = currentStatus;
                snapshot = build(current, preferences, tracker);
            }
            return snapshot;
        }
    }

    public static int metricRowCount(GraphPreferences preferences) {
        if (preferences.series == null) return 0;
        boolean enabled = false;
        for (GraphPreferences.Metric metric : METRICS) {
            if (preferences.series.contains(metric)) {
                enabled = true;
                break;
            }
        }
        return enabled
                ? (preferences.showTotal ? 1 : 0)
                        + (preferences.showHourly ? 1 : 0)
                        + (preferences.showProjectedHourly ? 1 : 0)
                        + (preferences.showProjectedTotal ? 1 : 0)
                : 0;
    }

    private static int flags(GraphPreferences preferences) {
        return (preferences.showTotal ? 1 : 0)
                | (preferences.showHourly ? 1 << 1 : 0)
                | (preferences.showProjectedHourly ? 1 << 2 : 0)
                | (preferences.showProjectedTotal ? 1 << 3 : 0)
                | (preferences.showActiveTime ? 1 << 4 : 0)
                | (preferences.showScope ? 1 << 5 : 0)
                | (preferences.showSpawnCount ? 1 << 6 : 0);
    }

    private static int status(Tracker tracker) {
        return (tracker.config.total ? 1 : 0)
                | (tracker.config.paused ? 1 << 1 : 0)
                | (tracker.inArena ? 1 << 2 : 0)
                | (tracker.isSummoning() ? 1 << 3 : 0)
                | (tracker.isAfk() ? 1 << 4 : 0)
                | (tracker.waitingForSpawn() ? 1 << 5 : 0);
    }

    private static Snapshot build(
            GraphData.Snapshot data, GraphPreferences preferences, Tracker tracker) {
        List<Row> metrics = new ArrayList<>(), status = new ArrayList<>();
        GraphPreferences.Metric metric = data.primary();
        if (!data.series().isEmpty()) {
            if (preferences.showTotal) {
                metrics.add(
                        row(
                                metric.totalLabel(),
                                Format.coins(data.value(metric)),
                                metric.color(data.value(metric))));
            }
            if (preferences.showHourly) {
                metrics.add(
                        row(
                                metric.hourlyLabel(),
                                data.elapsed() < 1000 ? "--" : Format.coins(data.hourly(metric)),
                                data.elapsed() < 1000
                                        ? Graph.MUTED
                                        : metric.color(data.hourly(metric))));
            }
            if (preferences.showProjectedHourly) {
                metrics.add(
                        row(
                                metric.projectedLabel(),
                                data.projectionReady()
                                        ? Format.coins(data.projectedHourly(metric))
                                        : "--",
                                data.projectionReady()
                                        ? metric.color(data.projectedHourly(metric))
                                        : Graph.MUTED));
            }
            if (preferences.showProjectedTotal) {
                metrics.add(
                        row(
                                metric.projectedTotalLabel(),
                                data.projectionReady()
                                        ? Format.coins(data.projectedTotal(metric))
                                        : "--",
                                data.projectionReady()
                                        ? metric.color(data.projectedTotal(metric))
                                        : Graph.MUTED));
            }
        }
        if (preferences.showSpawnCount) {
            status.add(row("Arachne spawns", Long.toString(data.spawnCount()), 0xFF7777FF));
        }
        if (preferences.showActiveTime) {
            status.add(row("Active time", Format.time(data.elapsed()), 0xFF55FFFF));
        }
        if (preferences.showScope) {
            String scope = tracker.config.total ? "Total" : "This session";
            if (tracker.config.paused) scope += " (paused)";
            else if (!tracker.inArena) scope += " (waiting)";
            else if (tracker.isSummoning()) scope += " (summoning)";
            else if (tracker.isAfk()) scope += " (AFK)";
            else if (tracker.waitingForSpawn()) scope += " (waiting)";
            status.add(row("Display", scope, Graph.MUTED));
        }
        return new Snapshot(metrics, status);
    }

    private static Row row(String label, String value, int color) {
        return new Row(label + ":", value, color);
    }

    private GraphHudContent() {}
}

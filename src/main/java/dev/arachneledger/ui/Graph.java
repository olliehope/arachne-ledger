package dev.arachneledger.ui;

import dev.arachneledger.config.GraphPreferences;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.Catalog;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Draws selected journal series on one shared active-time axis. */
public final class Graph {
    public static final int GREEN = 0xFF55FF55, RED = 0xFFFF5555, MUTED = 0xFFAAAAAA;

    private record ValueRange(double minimum, double maximum) {}

    private record RarePoint(GraphData.RareMarker marker, int x, int y) {}

    private record Geometry(
            PlotArea plot,
            Map<GraphPreferences.Metric, List<GraphSamples.Bucket>> series,
            Set<Integer> spawnColumns,
            List<RarePoint> rarePoints) {}

    private record CachedGeometry(
            GraphData.Snapshot data,
            int x,
            int y,
            int width,
            int height,
            boolean labels,
            boolean spawns,
            boolean rareDrops,
            Geometry geometry) {}

    // The HUD and open dashboard can use the same journal at different sizes. A tiny identity
    // cache avoids record/list hashing (which itself walks the entire journal).
    private static final CachedGeometry[] GEOMETRIES = new CachedGeometry[4];
    private static int nextGeometry;

    /** Pixel bounds exclude the legend and labels; duration includes any future projection. */
    private record PlotArea(
            int left, int right, int top, int bottom, long duration, ValueRange range) {
        int xAt(long elapsed) {
            return left
                    + (int)
                            Math.max(
                                    0,
                                    Math.min(
                                            right - left,
                                            (double) elapsed / duration * (right - left)));
        }

        int yAt(double value) {
            return bottom
                    - (int)
                            ((value - range.minimum())
                                    / (range.maximum() - range.minimum())
                                    * (bottom - top));
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
        }
    }

    /** Compatibility overload for callers that only have the original net-profit series. */
    public static void draw(
            GuiGraphicsExtractor graphics,
            Font font,
            Ledger.Stats stats,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int mouseX,
            int mouseY,
            boolean labels) {
        var metric = GraphPreferences.Metric.PROFIT;
        var data =
                new GraphData.Snapshot(
                        Map.of(metric, stats.graph()),
                        Map.of(),
                        List.of(),
                        Map.of(metric, stats.profit()),
                        Map.of(metric, stats.hourly()),
                        Map.of(),
                        Map.of(),
                        stats.elapsed(),
                        stats.elapsed(),
                        false,
                        0,
                        0,
                        metric,
                        0);
        draw(
                graphics,
                font,
                data,
                new GraphPreferences(),
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                mouseX,
                mouseY,
                labels);
    }

    public static void draw(
            GuiGraphicsExtractor graphics,
            Font font,
            GraphData.Snapshot data,
            GraphPreferences preferences,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int mouseX,
            int mouseY,
            boolean labels) {
        boolean emptyJournal =
                !data.series().isEmpty()
                        && data.series().values().stream().allMatch(points -> points.size() <= 1)
                        && data.elapsed() == 0;
        boolean showMoneyAxis = labels && !data.series().isEmpty() && !emptyJournal;
        Geometry geometry =
                geometry(data, preferences, panelX, panelY, panelWidth, panelHeight, labels);
        if (geometry == null) {
            return;
        }

        drawLegend(graphics, font, data, preferences, panelX + 2, panelY + 2, panelWidth - 4);
        PlotArea plot = geometry.plot();
        drawGrid(graphics, font, plot, showMoneyAxis, panelX + 2);
        if (preferences.showSpawns) {
            drawSpawnMarkers(graphics, geometry);
        }
        drawRecordedSeries(graphics, data, geometry);
        drawProjectedSeries(graphics, data, plot);
        drawRareMarkers(graphics, geometry);
        drawEmptyState(graphics, font, data, preferences, plot, emptyJournal);
        if (labels) {
            drawTimeAxis(graphics, font, data, plot);
            if (plot.contains(mouseX, mouseY)) {
                drawHoverTooltip(graphics, font, data, preferences, geometry, mouseX, mouseY);
            }
        }
    }

    private static ValueRange calculateValueRange(GraphData.Snapshot data) {
        // Keeping zero in the range makes income, costs and negative profit comparable.
        double minimum = 0, maximum = 0;
        for (var index : data.indexes().values()) {
            minimum = Math.min(minimum, index.minimum());
            maximum = Math.max(maximum, index.maximum());
        }
        for (var points : data.projections().values()) {
            for (var point : points) {
                minimum = Math.min(minimum, point.profit());
                maximum = Math.max(maximum, point.profit());
            }
        }
        if (maximum - minimum < 1) {
            minimum = -1;
            maximum = 1;
        }
        double padding = (maximum - minimum) * .1;
        return new ValueRange(minimum - padding, maximum + padding);
    }

    private static Geometry geometry(
            GraphData.Snapshot data,
            GraphPreferences preferences,
            int x,
            int y,
            int width,
            int height,
            boolean labels) {
        int replacement = -1;
        for (int index = 0; index < GEOMETRIES.length; index++) {
            CachedGeometry cached = GEOMETRIES[index];
            if (cached != null
                    && cached.x() == x
                    && cached.y() == y
                    && cached.width() == width
                    && cached.height() == height
                    && cached.labels() == labels
                    && cached.spawns() == preferences.showSpawns
                    && cached.rareDrops() == preferences.showRareDrops) {
                if (cached.data() == data) return cached.geometry();
                replacement = index;
            }
        }
        boolean empty =
                !data.series().isEmpty()
                        && data.series().values().stream().allMatch(points -> points.size() <= 1)
                        && data.elapsed() == 0;
        int left = x + (labels && !data.series().isEmpty() && !empty ? 48 : 2);
        int right = x + width - 8, top = y + 18, bottom = y + height - (labels ? 16 : 3);
        if (right <= left || bottom <= top) return null;
        PlotArea plot =
                new PlotArea(
                        left,
                        right,
                        top,
                        bottom,
                        Math.max(1, data.duration()),
                        calculateValueRange(data));
        Map<GraphPreferences.Metric, List<GraphSamples.Bucket>> samples =
                new EnumMap<>(GraphPreferences.Metric.class);
        data.indexes()
                .forEach(
                        (metric, index) ->
                                samples.put(metric, index.buckets(plot.duration(), right - left)));
        Set<Integer> spawns = new HashSet<>();
        if (preferences.showSpawns)
            for (var marker : data.spawns()) spawns.add(plot.xAt(marker.elapsed()));
        Map<Integer, GraphData.RareMarker> rareColumns = new LinkedHashMap<>();
        if (preferences.showRareDrops) {
            for (var marker : data.rareDrops()) {
                int rarity =
                        "ARACHNE_FANG".equals(marker.item())
                                ? 0
                                : marker.item().contains("LEGENDARY") ? 2 : 1;
                rareColumns.put(plot.xAt(marker.elapsed()) * 3 + rarity, marker);
            }
        }
        List<RarePoint> rares = new ArrayList<>(rareColumns.size());
        List<Ledger.Point> primary = data.series().get(data.primary());
        for (var marker : rareColumns.values()) {
            double value = primary == null ? 0 : valueAt(primary, marker.elapsed());
            rares.add(new RarePoint(marker, plot.xAt(marker.elapsed()), plot.yAt(value)));
        }
        Geometry geometry =
                new Geometry(plot, Map.copyOf(samples), Set.copyOf(spawns), List.copyOf(rares));
        int slot = replacement >= 0 ? replacement : nextGeometry;
        GEOMETRIES[slot] =
                new CachedGeometry(
                        data,
                        x,
                        y,
                        width,
                        height,
                        labels,
                        preferences.showSpawns,
                        preferences.showRareDrops,
                        geometry);
        if (replacement < 0) nextGeometry = (nextGeometry + 1) % GEOMETRIES.length;
        return geometry;
    }

    static int cachedViewCount() {
        int count = 0;
        for (CachedGeometry cached : GEOMETRIES) if (cached != null) count++;
        return count;
    }

    /** Bounded geometry counts, useful when diagnosing a large imported ledger. */
    public record RenderWork(int seriesColumns, int spawnColumns, int rarePoints) {}

    public static RenderWork renderWork(
            GraphData.Snapshot data,
            GraphPreferences preferences,
            int width,
            int height,
            boolean labels) {
        Geometry geometry = geometry(data, preferences, 0, 0, width, height, labels);
        if (geometry == null) return new RenderWork(0, 0, 0);
        int columns = geometry.series().values().stream().mapToInt(List::size).sum();
        return new RenderWork(
                columns, geometry.spawnColumns().size(), geometry.rarePoints().size());
    }

    /** Hit testing uses the exact cached geometry painted in the dashboard. */
    public static long rareFightAt(
            GraphData.Snapshot data,
            GraphPreferences preferences,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean labels) {
        Geometry geometry = geometry(data, preferences, x, y, width, height, labels);
        RarePoint point = geometry == null ? null : nearestRare(geometry, mouseX, mouseY);
        return point == null ? -1 : point.marker().fightId();
    }

    private static RarePoint nearestRare(Geometry geometry, int mouseX, int mouseY) {
        RarePoint nearest = null;
        int distance = 26;
        for (RarePoint point : geometry.rarePoints()) {
            int dx = point.x() - mouseX, dy = point.y() - mouseY;
            int squared = dx * dx + dy * dy;
            if (squared < distance) {
                nearest = point;
                distance = squared;
            }
        }
        return nearest;
    }

    private static void drawGrid(
            GuiGraphicsExtractor graphics,
            Font font,
            PlotArea plot,
            boolean showMoneyAxis,
            int labelX) {
        int divisions = plot.bottom() - plot.top() < 70 ? 2 : 4;
        for (int division = 0; division <= divisions; division++) {
            int gridY = plot.top() + (plot.bottom() - plot.top()) * division / divisions;
            graphics.horizontalLine(plot.left(), plot.right(), gridY, 0xFF303030);
            if (showMoneyAxis) {
                double value =
                        plot.range().maximum()
                                - (plot.range().maximum() - plot.range().minimum())
                                        * division
                                        / divisions;
                graphics.text(font, Format.coins(value), labelX, gridY - 3, MUTED, false);
            }
        }
        graphics.horizontalLine(plot.left(), plot.right(), plot.yAt(0), 0xFF666666);
    }

    private static void drawSpawnMarkers(GuiGraphicsExtractor graphics, Geometry geometry) {
        PlotArea plot = geometry.plot();
        // verticalLine excludes endpoints; explicit pixel bounds keep two-pixel dashes visible.
        for (int markerX : geometry.spawnColumns()) {
            if (geometry.spawnColumns().size() > 24) {
                graphics.verticalLine(markerX, plot.top() - 1, plot.bottom() + 1, 0x445555AA);
                continue;
            }
            for (int dashY = plot.top(); dashY <= plot.bottom(); dashY += 4) {
                graphics.fill(
                        markerX,
                        dashY,
                        markerX + 1,
                        Math.min(dashY + 2, plot.bottom() + 1),
                        0xFF5555AA);
            }
        }
    }

    private static void drawRecordedSeries(
            GuiGraphicsExtractor graphics, GraphData.Snapshot data, Geometry geometry) {
        PlotArea plot = geometry.plot();
        for (var series : geometry.series().entrySet()) {
            var points = series.getValue();
            int previousX = plot.left(), previousY = plot.yAt(0);
            for (var point : points) {
                int pointX = plot.left() + point.column();
                int pointY = plot.yAt(point.last());
                int minimumY = Math.min(previousY, plot.yAt(point.maximum()));
                int maximumY = Math.max(previousY, plot.yAt(point.minimum()));
                int color = series.getKey().color(point.last());
                // Consecutive unchanged columns form one horizontal segment. A quiet graph
                // should not emit hundreds of identical one-pixel draw commands.
                if (minimumY == maximumY && previousY == pointY) continue;
                if (pointX > previousX)
                    graphics.horizontalLine(previousX, pointX, previousY, color);
                if (minimumY != maximumY)
                    graphics.verticalLine(pointX, minimumY, maximumY + 1, color);
                previousX = pointX;
                previousY = pointY;
            }
            graphics.horizontalLine(
                    previousX,
                    plot.xAt(data.elapsed()),
                    previousY,
                    series.getKey().color(data.value(series.getKey())));
        }
    }

    private static void drawRareMarkers(GuiGraphicsExtractor graphics, Geometry geometry) {
        for (RarePoint point : geometry.rarePoints()) {
            graphics.fill(point.x() - 2, point.y() - 2, point.x() + 3, point.y() + 3, 0xFF151515);
            graphics.fill(
                    point.x() - 1,
                    point.y() - 1,
                    point.x() + 2,
                    point.y() + 2,
                    Hud.itemColor(point.marker().item()));
        }
    }

    private static void drawProjectedSeries(
            GuiGraphicsExtractor graphics, GraphData.Snapshot data, PlotArea plot) {
        for (var projection : data.projections().entrySet()) {
            var points = projection.getValue();
            var start = points.getFirst();
            var end = points.getLast();
            int startX = plot.xAt(start.elapsed()), endX = plot.xAt(end.elapsed());
            for (int dashX = startX; dashX <= endX; dashX += 4) {
                double value =
                        start.profit()
                                + (end.profit() - start.profit())
                                        * (dashX - startX)
                                        / Math.max(1.0, endX - startX);
                graphics.horizontalLine(
                        dashX,
                        Math.min(dashX + 1, endX),
                        plot.yAt(value),
                        projection.getKey().color(value));
            }
        }
    }

    private static void drawEmptyState(
            GuiGraphicsExtractor graphics,
            Font font,
            GraphData.Snapshot data,
            GraphPreferences preferences,
            PlotArea plot,
            boolean emptyJournal) {
        int centerX = (plot.left() + plot.right()) / 2;
        int messageY = (plot.top() + plot.bottom()) / 2 - 12;
        if (data.series().isEmpty() && !preferences.showSpawns) {
            graphics.centeredText(
                    font,
                    font.plainSubstrByWidth(
                            "Choose lines in Graph options", plot.right() - plot.left() - 8),
                    centerX,
                    messageY,
                    MUTED);
        } else if (emptyJournal) {
            graphics.centeredText(font, "No events recorded yet", centerX, messageY, MUTED);
        }
    }

    private static void drawTimeAxis(
            GuiGraphicsExtractor graphics, Font font, GraphData.Snapshot data, PlotArea plot) {
        graphics.text(font, Format.time(0), plot.left(), plot.bottom() + 5, MUTED, false);
        String endTime =
                Format.time(data.duration()) + (data.projections().isEmpty() ? "" : " projected");
        graphics.text(
                font, endTime, plot.right() - font.width(endTime), plot.bottom() + 5, MUTED, false);
    }

    private static void drawHoverTooltip(
            GuiGraphicsExtractor graphics,
            Font font,
            GraphData.Snapshot data,
            GraphPreferences preferences,
            Geometry geometry,
            int mouseX,
            int mouseY) {
        PlotArea plot = geometry.plot();
        RarePoint rare = nearestRare(geometry, mouseX, mouseY);
        if (rare != null) {
            GraphData.RareMarker marker = rare.marker();
            String text =
                    Catalog.name(marker.item())
                            + " x"
                            + marker.count()
                            + (marker.value() > 0
                                    ? " · " + Format.coins(marker.value()) + " coins"
                                    : "")
                            + "\nActive time "
                            + Format.time(marker.elapsed())
                            + (marker.fightId() > 0
                                    ? "\nClick to open Fight #" + marker.fightId()
                                    : "\nNo recorded fight association");
            Tooltips.show(graphics, font, text, mouseX, mouseY);
            return;
        }
        long elapsed =
                (long)
                        ((double) (mouseX - plot.left())
                                / (plot.right() - plot.left())
                                * plot.duration());
        StringBuilder tooltip = new StringBuilder("Active time ").append(Format.time(elapsed));
        boolean projected = elapsed > data.elapsed() && !data.projections().isEmpty();
        if (projected) {
            tooltip.append(" (projected)");
        }
        for (var series : data.series().entrySet()) {
            double value = valueAt(series.getValue(), elapsed);
            if (projected) {
                value =
                        data.value(series.getKey())
                                + data.projectedHourly(series.getKey())
                                        * (elapsed - data.elapsed())
                                        / 3_600_000.0;
            }
            tooltip.append('\n')
                    .append(series.getKey().label())
                    .append(": ")
                    .append(Format.coins(value))
                    .append(" coins");
        }
        if (preferences.showSpawns) {
            for (int column : geometry.spawnColumns()) {
                if (Math.abs(column - mouseX) <= 2) {
                    tooltip.append("\nArachne spawn");
                    break;
                }
            }
        }
        graphics.verticalLine(mouseX, plot.top(), plot.bottom(), 0xFFAAAAAA);
        Tooltips.show(graphics, font, tooltip.toString(), mouseX, mouseY);
    }

    /** Step lookup uses the last entry at or before this point on the active-time axis. */
    private static double valueAt(List<Ledger.Point> points, long elapsed) {
        int lowIndex = 0, highIndex = points.size() - 1;
        while (lowIndex <= highIndex) {
            int middleIndex = (lowIndex + highIndex) >>> 1;
            if (points.get(middleIndex).elapsed() <= elapsed) {
                lowIndex = middleIndex + 1;
            } else {
                highIndex = middleIndex - 1;
            }
        }
        return highIndex < 0 ? 0 : points.get(highIndex).profit();
    }

    private static void drawLegend(
            GuiGraphicsExtractor graphics,
            Font font,
            GraphData.Snapshot data,
            GraphPreferences preferences,
            int legendX,
            int legendY,
            int availableWidth) {
        int cursorX = legendX;
        for (var metric : data.series().keySet()) {
            String name =
                    switch (metric) {
                        case PROFIT -> "Profit";
                        case LOOT -> "Loot";
                        case COSTS -> "Costs";
                    };
            graphics.text(font, name, cursorX, legendY, metric.color(), false);
            cursorX += font.width(name) + 8;
        }
        if (preferences.showSpawns) {
            graphics.text(font, "Spawns", cursorX, legendY, 0xFF7777FF, false);
            cursorX += font.width("Spawns") + 8;
        }
        if (preferences.showRareDrops && !data.rareDrops().isEmpty()) {
            graphics.text(font, "RNG", cursorX, legendY, 0xFFFFAA00, false);
            cursorX += font.width("RNG") + 8;
        }
        if (!data.projections().isEmpty()) {
            int remainingWidth = Math.max(0, legendX + availableWidth - cursorX);
            String caption = font.width("+5m dashed") <= remainingWidth ? "+5m dashed" : "+5m";
            graphics.text(
                    font,
                    font.plainSubstrByWidth(caption, remainingWidth),
                    cursorX,
                    legendY,
                    MUTED,
                    false);
        }
    }

    private Graph() {}
}

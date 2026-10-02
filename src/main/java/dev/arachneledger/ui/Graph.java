package dev.arachneledger.ui;

import dev.arachneledger.config.GraphPreferences;
import dev.arachneledger.ledger.Ledger;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Draws selected journal series on one shared active-time axis. */
public final class Graph {
    public static final int GREEN = 0xFF55FF55, RED = 0xFFFF5555, MUTED = 0xFFAAAAAA;

    private record ValueRange(double minimum, double maximum) {}

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
        int left = panelX + (showMoneyAxis ? 48 : 2);
        int right = panelX + panelWidth - 8;
        int top = panelY + 18;
        int bottom = panelY + panelHeight - (labels ? 16 : 3);
        if (right <= left || bottom <= top) {
            return;
        }

        drawLegend(graphics, font, data, preferences, panelX + 2, panelY + 2, panelWidth - 4);
        PlotArea plot =
                new PlotArea(
                        left,
                        right,
                        top,
                        bottom,
                        Math.max(1, data.duration()),
                        calculateValueRange(data));
        drawGrid(graphics, font, plot, showMoneyAxis, panelX + 2);
        if (preferences.showSpawns) {
            drawSpawnMarkers(graphics, data, plot);
        }
        drawRecordedSeries(graphics, data, plot);
        drawProjectedSeries(graphics, data, plot);
        drawEmptyState(graphics, font, data, preferences, plot, emptyJournal);
        if (labels) {
            drawTimeAxis(graphics, font, data, plot);
            if (plot.contains(mouseX, mouseY)) {
                drawHoverTooltip(graphics, data, preferences, plot, mouseX, mouseY);
            }
        }
    }

    private static ValueRange calculateValueRange(GraphData.Snapshot data) {
        // Keeping zero in the range makes income, costs and negative profit comparable.
        double minimum = 0, maximum = 0;
        for (var points : data.series().values()) {
            for (var point : points) {
                minimum = Math.min(minimum, point.profit());
                maximum = Math.max(maximum, point.profit());
            }
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

    private static void drawSpawnMarkers(
            GuiGraphicsExtractor graphics, GraphData.Snapshot data, PlotArea plot) {
        Set<Integer> columns = new HashSet<>();
        for (var marker : data.spawns()) {
            columns.add(plot.xAt(marker.elapsed()));
        }
        // verticalLine excludes endpoints; explicit pixel bounds keep two-pixel dashes visible.
        for (int markerX : columns) {
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
            GuiGraphicsExtractor graphics, GraphData.Snapshot data, PlotArea plot) {
        for (var series : data.series().entrySet()) {
            var points = series.getValue();
            int previousX = plot.left(), previousY = plot.yAt(0);
            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                var point = points.get(pointIndex);
                int pointX = plot.xAt(point.elapsed());
                // Journal entries are steps. Entries sharing a pixel must retain the last value.
                while (pointIndex + 1 < points.size()
                        && plot.xAt(points.get(pointIndex + 1).elapsed()) == pointX) {
                    point = points.get(++pointIndex);
                }
                int pointY = plot.yAt(point.profit());
                int color = series.getKey().color(point.profit());
                graphics.horizontalLine(previousX, pointX, previousY, color);
                graphics.verticalLine(
                        pointX,
                        Math.min(previousY, pointY),
                        Math.max(previousY, pointY) + 1,
                        color);
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
        graphics.text(font, "0:00", plot.left(), plot.bottom() + 5, MUTED, false);
        String endTime =
                Format.time(data.duration()) + (data.projections().isEmpty() ? "" : " projected");
        graphics.text(
                font, endTime, plot.right() - font.width(endTime), plot.bottom() + 5, MUTED, false);
    }

    private static void drawHoverTooltip(
            GuiGraphicsExtractor graphics,
            GraphData.Snapshot data,
            GraphPreferences preferences,
            PlotArea plot,
            int mouseX,
            int mouseY) {
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
            for (var marker : data.spawns()) {
                if (Math.abs(plot.xAt(marker.elapsed()) - mouseX) <= 2) {
                    tooltip.append("\nArachne spawn · Fight #").append(marker.fightId());
                }
            }
        }
        graphics.verticalLine(mouseX, plot.top(), plot.bottom(), 0xFFAAAAAA);
        graphics.setTooltipForNextFrame(Component.literal(tooltip.toString()), mouseX, mouseY);
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

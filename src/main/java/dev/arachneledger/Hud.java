package dev.arachneledger;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The live overlay is independent of the dashboard's selected tab. */
public final class Hud {
    static final int WIDTH = 224, WHITE = 0xFFFFFFFF, GOLD = 0xFFFFAA00, TITLE = 0xFFFFFF55;

    public record Bounds(int x, int y, int width, int height, float scale) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        var minecraft = Minecraft.getInstance();
        var tracker = ArachneLedger.tracker;
        if (tracker == null
                || !tracker.config.hud
                || minecraft.player == null
                || minecraft.options.hideGui) {
            return;
        }
        if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) {
            return;
        }
        if (!tracker.inArena && !(tracker.config.hudAlwaysShow && tracker.inSkyblock)) {
            return;
        }
        drawPanel(
                graphics,
                tracker,
                bounds(tracker.config, graphics.guiWidth(), graphics.guiHeight()),
                false);
    }

    static int panelHeight(Config config) {
        if (config.hudView != Config.View.GRAPH) {
            return config.hudView == Config.View.COMPACT ? 82 : 180;
        }
        var preferences = config.graph;
        int metricRows = 0;
        if (!preferences.enabledSeries().isEmpty()) {
            metricRows =
                    (preferences.showTotal ? 1 : 0)
                            + (preferences.showHourly ? 1 : 0)
                            + (preferences.showProjectedHourly ? 1 : 0)
                            + (preferences.showProjectedTotal ? 1 : 0);
        }
        int statusRows =
                (preferences.showActiveTime ? 1 : 0)
                        + (preferences.showScope ? 1 : 0)
                        + (preferences.showSpawnCount ? 1 : 0);
        return 89 + (metricRows + statusRows) * 11 + (metricRows > 0 ? 3 : 0);
    }

    /** Resolve saved fractional positions against the free space in the current viewport. */
    static Bounds bounds(Config config, int width, int height) {
        int unscaledHeight = panelHeight(config);
        float scale =
                (float)
                        Math.min(
                                config.hudScale,
                                Math.min((width - 8.0) / WIDTH, (height - 8.0) / unscaledHeight));
        scale = Math.max(.25f, scale);
        int scaledWidth = (int) Math.ceil(WIDTH * scale),
                scaledHeight = (int) Math.ceil(unscaledHeight * scale);
        int maxX = Math.max(0, width - scaledWidth - 4),
                maxY = Math.max(0, height - scaledHeight - 4);
        int panelX =
                config.hudX < 0
                        ? (config.corner % 2 == 0 ? 8 : maxX - 4)
                        : (int) Math.round(config.hudX * maxX);
        int rowY =
                config.hudY < 0
                        ? (config.corner < 2 ? 8 : maxY - 28)
                        : (int) Math.round(config.hudY * maxY);
        return new Bounds(
                Math.max(4, Math.min(maxX, panelX)),
                Math.max(4, Math.min(maxY, rowY)),
                scaledWidth,
                scaledHeight,
                scale);
    }

    static void move(Config config, int width, int height, double x, double y) {
        Bounds hudBounds = bounds(config, width, height);
        config.hudX = Math.max(0, Math.min(1, x / Math.max(1, width - hudBounds.width() - 4)));
        config.hudY = Math.max(0, Math.min(1, y / Math.max(1, height - hudBounds.height() - 4)));
    }

    static void drawPanel(
            GuiGraphicsExtractor graphics, Tracker tracker, Bounds hudBounds, boolean editing) {
        var stats = tracker.ledger.stats(tracker.config.total);
        var analytics = tracker.ledger.analytics(tracker.config.total);
        var font = Minecraft.getInstance().font;
        int unscaledHeight = panelHeight(tracker.config);
        // Panel contents use unscaled local coordinates; only the outer transform moves/resizes
        // them.
        graphics.pose().pushMatrix();
        graphics.pose().translate(hudBounds.x(), hudBounds.y());
        graphics.pose().scale(hudBounds.scale());
        if (tracker.config.hudBackground) {
            graphics.fill(0, 0, WIDTH, unscaledHeight, 0x80000000);
        }
        graphics.text(
                font,
                Component.literal("Arachne Profit Tracker")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                4,
                4,
                WHITE,
                true);
        int rowY = 19;
        if (tracker.config.hudView == Config.View.GRAPH) {
            drawGraphPanel(graphics, tracker, font, rowY);
            if (editing) {
                graphics.outline(0, 0, WIDTH, unscaledHeight, 0x80666666);
            }
            graphics.pose().popMatrix();
            return;
        }
        if (tracker.config.hudView == Config.View.DETAILED) {
            rowY = drawDetailedRewards(graphics, tracker, font, stats, analytics, rowY);
        }
        drawProfitSummary(graphics, tracker, font, stats, analytics, rowY);
        if (editing) {
            graphics.outline(0, 0, WIDTH, unscaledHeight, 0x80666666);
        }
        graphics.pose().popMatrix();
    }

    private static void drawGraphPanel(
            GuiGraphicsExtractor graphics, Tracker tracker, Font font, int rowY) {
        var preferences = tracker.config.graph;
        var data = GraphData.build(tracker.ledger, tracker.config.total, preferences);
        int start = rowY;
        rowY = drawGraphMetrics(graphics, font, data, preferences, rowY);
        if (rowY > start) {
            rowY += 3;
        }
        Graph.draw(graphics, font, data, preferences, 4, rowY, WIDTH - 8, 62, -1, -1, false);
        rowY += 66;
        if (preferences.showSpawnCount) {
            row(
                    graphics,
                    font,
                    "Arachne spawns",
                    Long.toString(data.spawnCount()),
                    rowY,
                    0xFF7777FF);
            rowY += 11;
        }
        if (preferences.showActiveTime) {
            row(graphics, font, "Active time", shortTime(data.elapsed()), rowY, 0xFF55FFFF);
            rowY += 11;
        }
        if (preferences.showScope) {
            row(graphics, font, "Display", displayMode(tracker), rowY, Graph.MUTED);
        }
    }

    private static int drawDetailedRewards(
            GuiGraphicsExtractor graphics,
            Tracker tracker,
            Font font,
            Ledger.Stats stats,
            Analytics.Snapshot analytics,
            int rowY) {
        List<String> ids = sortedLoot(analytics, stats);
        for (String id : ids.stream().limit(3).toList()) {
            String count = String.format(Locale.ROOT, "%,d", stats.loot().get(id)) + "x ";
            double value = analytics.lootRevenue().getOrDefault(id, 0.0);
            String price = value == 0 ? "--" : Format.coins(value);
            int itemNameWidth = WIDTH - 18 - font.width(price) - font.width(count);
            graphics.text(font, count, 4, rowY, Graph.MUTED, true);
            graphics.text(
                    font,
                    font.plainSubstrByWidth(Catalog.name(id), Math.max(20, itemNameWidth)),
                    4 + font.width(count),
                    rowY,
                    itemColor(id),
                    true);
            right(graphics, font, price, WIDTH - 4, rowY, value == 0 ? Graph.MUTED : GOLD);
            rowY += 11;
        }
        if (ids.isEmpty()) {
            graphics.text(font, "No drops recorded yet", 4, rowY, Graph.MUTED, true);
            rowY += 11;
        }
        if (ids.size() > 3) {
            graphics.text(
                    font, "+ " + (ids.size() - 3) + " other drops", 4, rowY, Graph.MUTED, true);
            rowY += 11;
        }
        row(
                graphics,
                font,
                "Scavenger coins",
                Format.coins(tracker.ledger.scavengerCoins(tracker.config.total)),
                rowY,
                GOLD);
        rowY += 11;
        row(
                graphics,
                font,
                "Crystal costs (" + stats.crystals() + ")",
                Format.coins(analytics.crystalSpend()),
                rowY,
                Graph.RED);
        rowY += 11;
        row(
                graphics,
                font,
                "Calling costs (" + stats.callings() + ")",
                Format.coins(analytics.callingSpend()),
                rowY,
                Graph.RED);
        rowY += 11;
        row(graphics, font, "Bosses killed", Long.toString(stats.kills()), rowY, TITLE);
        rowY += 12;
        return rowY;
    }

    private static void drawProfitSummary(
            GuiGraphicsExtractor graphics,
            Tracker tracker,
            Font font,
            Ledger.Stats stats,
            Analytics.Snapshot analytics,
            int rowY) {
        row(
                graphics,
                font,
                "Total profit",
                Format.coins(stats.profit()),
                rowY,
                stats.profit() >= 0 ? Graph.GREEN : Graph.RED);
        rowY += 11;
        row(
                graphics,
                font,
                "Profit / hour",
                stats.elapsed() < 1000 ? "--" : Format.coins(stats.hourly()),
                rowY,
                GOLD);
        rowY += 11;
        row(
                graphics,
                font,
                "Projected / hour",
                analytics.projectionReady() ? Format.coins(analytics.projectedHourly()) : "--",
                rowY,
                GOLD);
        rowY += tracker.config.hudView == Config.View.DETAILED ? 10 : 14;
        row(graphics, font, "Active time", shortTime(stats.elapsed()), rowY, 0xFF55FFFF);
        rowY += 11;
        row(graphics, font, "Display", displayMode(tracker), rowY, Graph.MUTED);
        rowY += 11;
        if (tracker.config.hudView == Config.View.DETAILED && stats.unpriced() > 0) {
            graphics.text(
                    font,
                    stats.unpriced() + " unpriced drop" + (stats.unpriced() == 1 ? "" : "s"),
                    4,
                    rowY,
                    TITLE,
                    true);
        }
    }

    private static int drawGraphMetrics(
            GuiGraphicsExtractor graphics,
            Font font,
            GraphData.Snapshot data,
            GraphPreferences preferences,
            int rowY) {
        var metric = data.primary();
        if (!data.series().isEmpty()) {
            if (preferences.showTotal) {
                row(
                        graphics,
                        font,
                        metric.totalLabel(),
                        Format.coins(data.value(metric)),
                        rowY,
                        metric.color(data.value(metric)));
                rowY += 11;
            }
            if (preferences.showHourly) {
                row(
                        graphics,
                        font,
                        metric.hourlyLabel(),
                        data.elapsed() < 1000 ? "--" : Format.coins(data.hourly(metric)),
                        rowY,
                        data.elapsed() < 1000 ? Graph.MUTED : metric.color(data.hourly(metric)));
                rowY += 11;
            }
            if (preferences.showProjectedHourly) {
                row(
                        graphics,
                        font,
                        metric.projectedLabel(),
                        data.projectionReady() ? Format.coins(data.projectedHourly(metric)) : "--",
                        rowY,
                        data.projectionReady()
                                ? metric.color(data.projectedHourly(metric))
                                : Graph.MUTED);
                rowY += 11;
            }
            if (preferences.showProjectedTotal) {
                row(
                        graphics,
                        font,
                        metric.projectedTotalLabel(),
                        data.projectionReady() ? Format.coins(data.projectedTotal(metric)) : "--",
                        rowY,
                        data.projectionReady()
                                ? metric.color(data.projectedTotal(metric))
                                : Graph.MUTED);
                rowY += 11;
            }
        }
        return rowY;
    }

    private static String displayMode(Tracker tracker) {
        String mode = tracker.config.total ? "Total" : "This session";
        if (tracker.config.paused) {
            mode += " (paused)";
        } else if (!tracker.inArena) {
            mode += " (waiting)";
        } else if (tracker.isSummoning()) {
            mode += " (summoning)";
        } else if (tracker.isAfk()) {
            mode += " (AFK)";
        } else if (tracker.waitingForSpawn()) {
            mode += " (waiting)";
        }
        return mode;
    }

    static List<String> sortedLoot(Analytics.Snapshot analytics, Ledger.Stats stats) {
        List<String> ids = new ArrayList<>(stats.loot().keySet());
        ids.sort(
                Comparator.<String>comparingDouble(
                                id -> analytics.lootRevenue().getOrDefault(id, 0.0))
                        .reversed()
                        .thenComparing(id -> id));
        return ids;
    }

    static int itemColor(String id) {
        return switch (id) {
            case "ENCHANTED_STRING", "ENCHANTED_SPIDER_EYE", "LUXURIOUS_SPOOL", "ARACHNE_FANG" ->
                    0xFF55FF55;
            case "ARACHNE_FRAGMENT",
                    "ARACHNE_SHARD",
                    "ARACHNE_HELMET",
                    "ARACHNE_CHESTPLATE",
                    "ARACHNE_LEGGINGS",
                    "ARACHNE_BOOTS",
                    "ARACK" ->
                    0xFF5555FF;
            case "ESSENCE_SPIDER", "DARK_QUEENS_SOUL_DROP" -> 0xFFFF55FF;
            case "TARANTULA_EPIC" -> 0xFFAA00AA;
            case "TARANTULA_LEGENDARY" -> GOLD;
            default -> WHITE;
        };
    }

    static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    static String shortTime(long millis) {
        return millis >= 60_000
                ? (millis / 60_000) + "m " + (millis / 1000 % 60) + "s"
                : (millis / 1000) + "s";
    }

    static void row(
            GuiGraphicsExtractor graphics,
            Font font,
            String label,
            String value,
            int y,
            int color) {
        int labelWidth = Math.max(20, WIDTH - 14 - font.width(value));
        graphics.text(
                font, font.plainSubstrByWidth(label + ":", labelWidth), 4, y, Graph.MUTED, true);
        right(graphics, font, value, WIDTH - 4, y, color);
    }

    static void right(
            GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text), y, color, true);
    }

    private Hud() {}
}

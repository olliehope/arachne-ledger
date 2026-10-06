package dev.arachneledger.ui;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.config.HudPreferences;
import dev.arachneledger.ledger.Analytics;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.ItemRarity;
import dev.arachneledger.tracking.Tracker;

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
    public static final int WIDTH = 224, WHITE = 0xFFFFFFFF, GOLD = 0xFFFFAA00, TITLE = 0xFFFFFF55;
    private static final int LOOT_WIDTH = 300;
    private static final GraphHudContent.Cache GRAPH_CONTENT = new GraphHudContent.Cache();
    private static final Component TITLE_TEXT =
            Component.literal("Arachne Profit Tracker")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);

    public record Bounds(int x, int y, int width, int height, float scale) {
        public boolean contains(double mouseX, double mouseY) {
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

    public static int panelHeight(Config config) {
        if (config.hudView != Config.View.GRAPH) {
            int rewards = rewardRowCount(config.hudPreferences);
            int metrics = metricRowCount(config.hudPreferences);
            int rows =
                    splitColumns(config.hudPreferences)
                            ? Math.max(rewards, metrics)
                            : rewards + metrics;
            var preferences = config.hudPreferences;
            int lootRows = preferences.showLoot ? preferences.maxLootRows : 0;
            boolean ledgerGap =
                    preferences.layout == HudPreferences.Layout.LOOT
                            && lootRows > 0
                            && rewards + metrics > lootRows;
            int groupGap =
                    ledgerGap
                            ? 6
                            : !splitColumns(preferences) && rewards > 0 && metrics > 0 ? 3 : 0;
            return Math.max(
                    16,
                    (config.hudPreferences.showTitle ? 15 : 0)
                            + rows * 11
                            + groupGap
                            + 8
                            + (preferences.showCountdown ? 11 : 0));
        }
        var preferences = config.graph;
        int metricRows = GraphHudContent.metricRowCount(preferences);
        int statusRows =
                (preferences.showActiveTime ? 1 : 0)
                        + (preferences.showScope ? 1 : 0)
                        + (preferences.showSpawnCount ? 1 : 0);
        return 89
                + (metricRows + statusRows) * 11
                + (metricRows > 0 ? 3 : 0)
                + (config.hudPreferences.showCountdown ? 11 : 0);
    }

    /** Resolve saved fractional positions against the free space in the current viewport. */
    public static Bounds bounds(Config config, int width, int height) {
        int unscaledHeight = panelHeight(config);
        int unscaledWidth = panelWidth(config);
        float scale =
                (float)
                        Math.min(
                                config.hudScale,
                                Math.min(
                                        (width - 8.0) / unscaledWidth,
                                        (height - 8.0) / unscaledHeight));
        scale = Math.max(.25f, scale);
        int scaledWidth = (int) Math.ceil(unscaledWidth * scale),
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

    public static void move(Config config, int width, int height, double x, double y) {
        Bounds hudBounds = bounds(config, width, height);
        config.hudX = Math.max(0, Math.min(1, x / Math.max(1, width - hudBounds.width() - 4)));
        config.hudY = Math.max(0, Math.min(1, y / Math.max(1, height - hudBounds.height() - 4)));
    }

    public static void drawPanel(
            GuiGraphicsExtractor graphics, Tracker tracker, Bounds hudBounds, boolean editing) {
        drawPanel(graphics, tracker, tracker.config, hudBounds, editing);
    }

    /** An editor previews a detached display draft without changing live tracking preferences. */
    public static void drawPanel(
            GuiGraphicsExtractor graphics,
            Tracker tracker,
            Config display,
            Bounds hudBounds,
            boolean editing) {
        var font = Minecraft.getInstance().font;
        int unscaledHeight = panelHeight(display);
        int unscaledWidth = panelWidth(display);
        var preferences = display.hudPreferences;
        if (!editing
                && display.hudView != Config.View.GRAPH
                && !preferences.showTitle
                && rewardRowCount(preferences) + metricRowCount(preferences) == 0
                && !preferences.showCountdown) {
            return;
        }
        // Panel contents use unscaled local coordinates; only the outer transform moves/resizes
        // them.
        graphics.pose().pushMatrix();
        graphics.pose().translate(hudBounds.x(), hudBounds.y());
        graphics.pose().scale(hudBounds.scale());
        var style = preferences.resolvedStyle(display.hudBackground);
        if (style.backgroundColor() != 0) {
            graphics.fill(0, 0, unscaledWidth, unscaledHeight, style.backgroundColor());
        }
        if (style.borderColor() != 0) {
            graphics.outline(0, 0, unscaledWidth, unscaledHeight, style.borderColor());
        }
        boolean showTitle = display.hudView == Config.View.GRAPH || preferences.showTitle;
        if (showTitle) {
            graphics.text(font, TITLE_TEXT, 4, 4, WHITE, true);
        }
        int rowY = showTitle ? 19 : 4;
        if (preferences.showCountdown) {
            var timer = tracker.ritualTimer(System.currentTimeMillis());
            if (timer != null) {
                graphics.text(font, timer.label(), 4, rowY, timer.color(), true);
            } else if (editing) {
                graphics.text(font, "Arachne in ~40.0s", 4, rowY, TITLE, true);
            } else {
                graphics.text(
                        font, "Arachne · " + tracker.timerState(), 4, rowY, Graph.MUTED, true);
            }
            rowY += 11;
        }
        if (display.hudView == Config.View.GRAPH) {
            drawGraphPanel(graphics, tracker, font, rowY);
            if (editing) {
                graphics.outline(0, 0, WIDTH, unscaledHeight, 0x80666666);
            }
            graphics.pose().popMatrix();
            return;
        }
        drawContent(graphics, font, HudContent.build(tracker, preferences), preferences, rowY);
        if (editing) {
            graphics.outline(0, 0, unscaledWidth, unscaledHeight, 0x80666666);
        }
        graphics.pose().popMatrix();
    }

    private static void drawGraphPanel(
            GuiGraphicsExtractor graphics, Tracker tracker, Font font, int rowY) {
        var preferences = tracker.config.graph;
        var data = GraphData.build(tracker.ledger, tracker.config.total, preferences);
        var content = GRAPH_CONTENT.get(tracker, data);
        rowY = drawGraphRows(graphics, font, content.metrics(), rowY);
        if (!content.metrics().isEmpty()) {
            rowY += 3;
        }
        Graph.draw(graphics, font, data, preferences, 4, rowY, WIDTH - 8, 62, -1, -1, false);
        rowY += 66;
        drawGraphRows(graphics, font, content.status(), rowY);
    }

    /** Layout dimensions are derived from the same row switches used by HudContent. */
    public static int panelWidth(Config config) {
        if (config.hudView == Config.View.GRAPH) {
            return WIDTH;
        }
        if (splitColumns(config.hudPreferences)) {
            return WIDTH * 2 + 8;
        }
        if (config.hudPreferences.layout == HudPreferences.Layout.LOOT) {
            return LOOT_WIDTH;
        }
        return config.hudPreferences.layout == HudPreferences.Layout.MINIMAL ? 200 : WIDTH;
    }

    private static int rewardRowCount(HudPreferences preferences) {
        return (preferences.showLoot && preferences.maxLootRows > 0 ? preferences.maxLootRows : 0)
                + (preferences.showScavenger ? 1 : 0)
                + (preferences.showCrystalCosts ? 1 : 0)
                + (preferences.showCallingCosts ? 1 : 0)
                + (preferences.showKills ? 1 : 0);
    }

    private static int metricRowCount(HudPreferences preferences) {
        return (preferences.showTotalProfit ? 1 : 0)
                + (preferences.showRegularProfit ? 1 : 0)
                + (preferences.showRegularPerHour ? 1 : 0)
                + (preferences.showProfitPerHour
                                && !(preferences.layout == HudPreferences.Layout.LOOT
                                        && preferences.showTotalProfit)
                        ? 1
                        : 0)
                + (preferences.showProjectedPerHour ? 1 : 0)
                + (preferences.showActiveTime ? 1 : 0)
                + (preferences.showScope ? 1 : 0)
                + (preferences.showStatus ? 1 : 0)
                + (preferences.showUnpricedWarning ? 1 : 0)
                + (!preferences.pinnedAchievement.isEmpty() ? 1 : 0);
    }

    private static boolean splitColumns(HudPreferences preferences) {
        return preferences.layout == HudPreferences.Layout.SPLIT
                && rewardRowCount(preferences) > 0
                && metricRowCount(preferences) > 0;
    }

    private static void drawContent(
            GuiGraphicsExtractor graphics,
            Font font,
            HudContent.Snapshot content,
            HudPreferences preferences,
            int rowY) {
        if (preferences.layout == HudPreferences.Layout.LOOT) {
            drawLootLedger(graphics, font, content, rowY);
            return;
        }
        if (splitColumns(preferences)) {
            drawRows(graphics, font, content.rewards(), 0, WIDTH, rowY);
            drawRows(graphics, font, content.metrics(), WIDTH + 8, WIDTH, rowY);
            return;
        }
        int columnWidth = preferences.layout == HudPreferences.Layout.MINIMAL ? 200 : WIDTH;
        List<HudContent.Row> first =
                preferences.layout == HudPreferences.Layout.MINIMAL
                        ? content.metrics()
                        : content.rewards();
        List<HudContent.Row> second =
                preferences.layout == HudPreferences.Layout.MINIMAL
                        ? content.rewards()
                        : content.metrics();
        rowY = drawRows(graphics, font, first, 0, columnWidth, rowY);
        if (!first.isEmpty() && !second.isEmpty()) {
            rowY += 3;
        }
        drawRows(graphics, font, second, 0, columnWidth, rowY);
    }

    /** Align coin subtotals, then keep item names, counts and observed rare rates on one line. */
    private static void drawLootLedger(
            GuiGraphicsExtractor graphics, Font font, HudContent.Snapshot content, int rowY) {
        int valueWidth = 0;
        for (HudContent.Row row : content.rewards()) {
            if (row.id().startsWith("loot:"))
                valueWidth = Math.max(valueWidth, font.width(row.value()));
        }
        int itemX = valueWidth == 0 ? 4 : 4 + valueWidth + font.width(" | ");
        boolean previousLoot = false, previousRow = false;
        for (HudContent.Row row : content.rewards()) {
            boolean loot = row.id().startsWith("loot:") || row.id().equals("emptyLoot");
            if (previousRow && loot != previousLoot) {
                rowY += 3;
            }
            if (!loot) {
                drawLedgerSummary(graphics, font, row, rowY);
            } else if (row.parts().isEmpty()) {
                graphics.text(
                        font,
                        font.plainSubstrByWidth(row.label(), LOOT_WIDTH - 8),
                        4,
                        rowY,
                        row.labelColor(),
                        true);
            } else {
                if (!row.value().isEmpty()) {
                    right(graphics, font, row.value(), 4 + valueWidth, rowY, row.valueColor());
                    graphics.text(font, " | ", 4 + valueWidth, rowY, Graph.MUTED, true);
                }
                drawParts(graphics, font, row.parts(), itemX, rowY);
            }
            rowY += 11;
            previousLoot = loot;
            previousRow = true;
        }
        if (previousLoot && !content.metrics().isEmpty()) {
            rowY += 3;
        }
        for (HudContent.Row row : content.metrics()) {
            drawLedgerSummary(graphics, font, row, rowY);
            rowY += 11;
        }
    }

    private static void drawLedgerSummary(
            GuiGraphicsExtractor graphics, Font font, HudContent.Row row, int rowY) {
        drawParts(graphics, font, row.parts(), 4, rowY);
    }

    private static void drawParts(
            GuiGraphicsExtractor graphics, Font font, List<HudContent.Part> parts, int x, int y) {
        for (HudContent.Part part : parts) {
            int available = LOOT_WIDTH - 4 - x;
            if (available <= 0) {
                return;
            }
            String text = font.plainSubstrByWidth(part.text(), available);
            graphics.text(font, text, x, y, part.color(), true);
            x += font.width(text);
            if (text.length() < part.text().length()) {
                return;
            }
        }
    }

    private static int drawRows(
            GuiGraphicsExtractor graphics,
            Font font,
            List<HudContent.Row> rows,
            int columnX,
            int columnWidth,
            int rowY) {
        for (HudContent.Row row : rows) {
            String value = font.plainSubstrByWidth(row.value(), columnWidth - 40);
            String suffix = row.id().startsWith("loot:") || value.isEmpty() ? "" : ":";
            int labelWidth = Math.max(20, columnWidth - 14 - font.width(value));
            String label = font.plainSubstrByWidth(row.label() + suffix, labelWidth);
            graphics.text(font, label, columnX + 4, rowY, row.labelColor(), true);
            if (!value.isEmpty()) {
                right(graphics, font, value, columnX + columnWidth - 4, rowY, row.valueColor());
            }
            rowY += 11;
        }
        return rowY;
    }

    private static int drawGraphRows(
            GuiGraphicsExtractor graphics, Font font, List<GraphHudContent.Row> rows, int rowY) {
        for (GraphHudContent.Row row : rows) {
            int labelWidth = Math.max(20, WIDTH - 14 - font.width(row.value()));
            graphics.text(
                    font,
                    font.plainSubstrByWidth(row.label(), labelWidth),
                    4,
                    rowY,
                    Graph.MUTED,
                    true);
            right(graphics, font, row.value(), WIDTH - 4, rowY, row.color());
            rowY += 11;
        }
        return rowY;
    }

    public static List<String> sortedLoot(Analytics.Snapshot analytics, Ledger.Stats stats) {
        List<String> ids = new ArrayList<>(stats.loot().keySet());
        ids.sort(
                Comparator.<String>comparingDouble(
                                id -> analytics.lootRevenue().getOrDefault(id, 0.0))
                        .reversed()
                        .thenComparing(id -> id));
        return ids;
    }

    public static int itemColor(String id) {
        return ItemRarity.color(id);
    }

    public static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String shortTime(long millis) {
        return Format.time(millis);
    }

    static void right(
            GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text), y, color, true);
    }

    private Hud() {}
}

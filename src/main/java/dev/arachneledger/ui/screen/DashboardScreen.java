package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.ledger.Analytics;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.GraphData;
import dev.arachneledger.ui.Hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A compact text tracker with a larger view for details and history. */
public final class DashboardScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private int panelX, panelY, panelWidth, panelHeight, scrollOffset;
    private long resetConfirmationUntil, noteExpiresAt;
    private String note = "";

    private record Row(
            String label, String value, int labelColor, int valueColor, String explanation) {}

    public DashboardScreen(Screen parent) {
        super(Component.literal("Arachne Profit Tracker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(470, width - 24);
        panelHeight = Math.min(328, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        addScopeButtons();
        addDashboardTabs();
        addFooterButtons();
    }

    private void scope(boolean total) {
        tracker.config.total = total;
        tracker.saveConfig();
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void button(
            int buttonX,
            int rowY,
            int buttonWidth,
            int buttonHeight,
            String label,
            boolean selected,
            String tip,
            Runnable action) {
        var button =
                new FlatButton(buttonX, rowY, buttonWidth, buttonHeight, label, selected, action);
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        addRenderableWidget(button);
    }

    private void setNote(String text) {
        note =
                "".equals(tracker.error)
                        ? text
                        : "Storage error; changes were not saved. See Minecraft log.";
        noteExpiresAt = System.currentTimeMillis() + 6000;
    }

    @Override
    public void tick() {
        if (resetConfirmationUntil != 0 && System.currentTimeMillis() > resetConfirmationUntil) {
            resetConfirmationUntil = 0;
            rebuildWidgets();
        }
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        boolean graphView =
                !tracker.config.dashboardFights && tracker.config.view == Config.View.GRAPH;
        drawHeader(graphics, graphView);
        var stats = tracker.ledger.stats(tracker.config.total);
        var analytics = tracker.ledger.analytics(tracker.config.total);
        String projectionTip =
                "Current-session pace from the last "
                        + Hud.shortTime(analytics.windowMillis())
                        + " of active time. Includes costs and waiting; needs 60 active seconds and one recent kill.";
        GraphData.Snapshot graph =
                graphView
                        ? GraphData.build(
                                tracker.ledger, tracker.config.total, tracker.config.graph)
                        : null;
        int top;
        if (graphView) {
            top = drawGraphSummary(graphics, graph, mouseX, mouseY);
        } else {
            top = drawProfitSummary(graphics, stats, analytics, projectionTip, mouseX, mouseY);
        }
        int bottom = panelY + panelHeight - (graphView ? 47 : 52);
        if (tracker.config.dashboardFights) {
            drawFights(graphics, top, bottom, mouseX, mouseY);
        } else if (tracker.config.view == Config.View.GRAPH) {
            Graph.draw(
                    graphics,
                    font,
                    graph,
                    tracker.config.graph,
                    panelX + 10,
                    top - 4,
                    panelWidth - 20,
                    Math.max(24, bottom - top + 4),
                    mouseX,
                    mouseY,
                    true);
        } else if (tracker.config.view == Config.View.DETAILED) {
            drawDrops(graphics, stats, analytics, top, bottom, mouseX, mouseY);
        } else {
            drawRows(
                    graphics,
                    overviewRows(stats, analytics, projectionTip),
                    top,
                    bottom,
                    mouseX,
                    mouseY);
        }
        drawFooter(graphics, stats, analytics, graphView, graph, mouseX, mouseY);
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        }
    }

    private void addScopeButtons() {
        button(
                panelX + panelWidth - 122,
                panelY + 8,
                39,
                18,
                "Total",
                tracker.config.total,
                "Show lifetime totals.",
                () -> scope(true));
        button(
                panelX + panelWidth - 80,
                panelY + 8,
                68,
                18,
                "This session",
                !tracker.config.total,
                "Show this session's totals.",
                () -> scope(false));
    }

    private void addDashboardTabs() {
        String[] tabs = {"Overview", "Drops", "Graph", "Fights"};
        int[] sizes = {66, 48, 48, 50};
        int buttonX = panelX + 8;
        for (int i = 0; i < tabs.length; i++) {
            int selectedTab = i;
            button(
                    buttonX,
                    panelY + 36,
                    sizes[i],
                    18,
                    tabs[i],
                    i == 3
                            ? tracker.config.dashboardFights
                            : !tracker.config.dashboardFights && tracker.config.view.ordinal() == i,
                    "Change dashboard view.",
                    () -> selectDashboardTab(selectedTab));
            buttonX += sizes[i] + 4;
        }
        if (!tracker.config.dashboardFights && tracker.config.view == Config.View.GRAPH) {
            button(
                    panelX + panelWidth - 60,
                    panelY + 36,
                    52,
                    18,
                    "Options",
                    false,
                    "Choose graph lines, text and spawn markers.",
                    () -> minecraft.setScreen(new GraphOptionsScreen(this)));
        } else {
            button(
                    panelX + panelWidth - 60,
                    panelY + 36,
                    52,
                    18,
                    "Journal",
                    false,
                    "Achievements, session recaps, personal records and tracking diagnostics.",
                    () -> minecraft.setScreen(new JournalScreen(this)));
        }
    }

    private void selectDashboardTab(int selectedTab) {
        // The first three tabs share Config.View's order; Fights has its own persisted toggle.
        tracker.config.dashboardFights = selectedTab == 3;
        if (selectedTab < 3) {
            tracker.config.view = Config.View.values()[selectedTab];
        }
        tracker.saveConfig();
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void addFooterButtons() {
        int footerButtonWidth = (panelWidth - 16) / 6;
        String[] labels = {
            tracker.config.paused ? "Resume" : "Pause",
            "Settings",
            "Adjust",
            "HUD",
            resetConfirmationUntil > System.currentTimeMillis() ? "Confirm" : "Reset",
            "Close"
        };
        String[] tips = {
            "Pause automatic tracking.",
            "Configure the HUD, tracking, prices and graphs.",
            "Correct loot or coins, undo an entry, export CSV.",
            "Move or resize the overlay.",
            "Start a new session; confirm with a second click.",
            "Return to the game."
        };
        Runnable[] actions = {
            () -> {
                tracker.togglePause();
                rebuildWidgets();
            },
            () -> minecraft.setScreen(new SettingsScreen(this)),
            () -> minecraft.setScreen(new AdjustScreen(this)),
            () -> minecraft.setScreen(new HudEditorScreen(this)),
            this::resetSessionWithConfirmation,
            this::onClose
        };
        for (int i = 0; i < labels.length; i++) {
            button(
                    panelX + 8 + i * footerButtonWidth,
                    panelY + panelHeight - 24,
                    footerButtonWidth,
                    18,
                    labels[i],
                    false,
                    tips[i],
                    actions[i]);
        }
    }

    private void resetSessionWithConfirmation() {
        if (resetConfirmationUntil > System.currentTimeMillis()) {
            tracker.newSession();
            resetConfirmationUntil = 0;
            setNote("New session started.");
        } else {
            resetConfirmationUntil = System.currentTimeMillis() + 5000;
            setNote("Click Confirm to start a new session.");
        }
        scrollOffset = 0;
        rebuildWidgets();
    }

    private int drawProfitSummary(
            GuiGraphicsExtractor graphics,
            Ledger.Stats stats,
            Analytics.Snapshot analytics,
            String projectionTip,
            int mouseX,
            int mouseY) {
        line(
                graphics,
                "Total profit",
                Format.coins(stats.profit()) + " coins",
                panelY + 69,
                stats.profit() >= 0 ? Graph.GREEN : Graph.RED,
                "Recorded income minus summoning costs and other expenses.",
                mouseX,
                mouseY);
        line(
                graphics,
                "Profit / hour",
                stats.elapsed() < 1000 ? "--" : Format.coins(stats.hourly()) + " coins",
                panelY + 82,
                Hud.GOLD,
                "Net profit divided by active time in the selected scope.",
                mouseX,
                mouseY);
        line(
                graphics,
                "Projected / hour",
                analytics.projectionReady()
                        ? Format.coins(analytics.projectedHourly()) + " coins"
                        : "Warming up",
                panelY + 95,
                Hud.GOLD,
                projectionTip,
                mouseX,
                mouseY);
        graphics.horizontalLine(panelX + 12, panelX + panelWidth - 12, panelY + 110, 0xFF333333);
        return panelY + 121;
    }

    private void drawDrops(
            GuiGraphicsExtractor graphics,
            Ledger.Stats stats,
            Analytics.Snapshot analytics,
            int top,
            int bottom,
            int mouseX,
            int mouseY) {
        List<Row> rows = new ArrayList<>();
        for (String id : Hud.sortedLoot(analytics, stats)) {
            double value = analytics.lootRevenue().getOrDefault(id, 0.0);
            rows.add(
                    new Row(
                            String.format(Locale.ROOT, "%,d", stats.loot().get(id))
                                    + "x "
                                    + Catalog.name(id),
                            value == 0 ? "Unpriced" : Format.coins(value) + " coins",
                            Hud.itemColor(id),
                            value == 0 ? Graph.MUTED : Hud.GOLD,
                            Catalog.name(id)
                                    + ": "
                                    + stats.loot().get(id)
                                    + " items, valued at "
                                    + Format.coins(value)
                                    + " recorded coins."));
        }
        if (rows.isEmpty()) {
            graphics.text(font, "No drops recorded yet.", panelX + 12, top, Graph.MUTED, true);
        } else {
            drawRows(graphics, rows, top, bottom, mouseX, mouseY);
        }
    }

    private List<Row> overviewRows(
            Ledger.Stats stats, Analytics.Snapshot analytics, String projectionTip) {
        List<Row> rows = new ArrayList<>();
        rows.add(
                row(
                        "Rewards + other income",
                        Format.coins(stats.revenue()) + " coins",
                        Hud.GOLD,
                        "Item values, scoreboard coins and manual income recorded in this scope."));
        rows.add(
                row(
                        "Scavenger coins",
                        Format.coins(tracker.ledger.scavengerCoins(tracker.config.total))
                                + " coins",
                        Hud.GOLD,
                        "Already included in profit. Positive purse changes paired with the yellow (+coins) display during fights or within 10 seconds of death. Other marked coin rewards cannot be distinguished from Scavenger."));
        rows.add(
                row(
                        "Crystal costs (" + stats.crystals() + ")",
                        Format.coins(analytics.crystalSpend()) + " coins",
                        Graph.RED,
                        "Your own crystal placements and their recorded costs."));
        rows.add(
                row(
                        "Calling costs (" + stats.callings() + ")",
                        Format.coins(analytics.callingSpend()) + " coins",
                        Graph.RED,
                        "Your own Calling placements and their recorded costs."));
        rows.add(
                row(
                        "All costs",
                        Format.coins(stats.costs()) + " coins",
                        Graph.RED,
                        "Crystals, Callings and manual expenses."));
        rows.add(
                row(
                        "Bosses killed",
                        Long.toString(stats.kills()),
                        Hud.TITLE,
                        "Arachne kills with at least "
                                + tracker.config.minimumDamage
                                + " reported damage. Use /arachne mindamage to change the threshold."));
        rows.add(
                row(
                        "Kills / hour",
                        Hud.decimal(analytics.killsPerHour()),
                        Hud.TITLE,
                        "Kills divided by active time."));
        rows.add(
                row(
                        "Average profit / kill",
                        stats.kills() == 0
                                ? "--"
                                : Format.coins(analytics.averageNetPerKill()) + " coins",
                        Hud.GOLD,
                        "Net profit divided by participating kills."));
        rows.add(
                row(
                        "Average time / kill",
                        stats.kills() == 0
                                ? "--"
                                : Hud.shortTime((long) analytics.averageMillisPerKill()),
                        0xFF55FFFF,
                        "Active time per kill, including waits."));
        rows.add(
                row(
                        "Active time",
                        Hud.shortTime(stats.elapsed()),
                        0xFF55FFFF,
                        "Runs from Arachne's spawn until 60 seconds after death. Another spawn resumes it. Pauses and time outside the arena are excluded."));
        rows.add(
                row(
                        "Projection sample",
                        Hud.shortTime(analytics.windowMillis())
                                + " / "
                                + analytics.windowKills()
                                + " kills",
                        Graph.MUTED,
                        projectionTip));
        rows.add(
                row(
                        "Next crystal",
                        Format.coins(tracker.config.effectiveCrystalCost()) + " coins",
                        Graph.RED,
                        "Current crystal cost for future placements."));
        rows.add(
                row(
                        "Unpriced drops",
                        Long.toString(stats.unpriced()),
                        stats.unpriced() > 0 ? Hud.TITLE : Graph.MUTED,
                        "Set prices and use Reprice session to value these drops."));
        return rows;
    }

    private void drawHeader(GuiGraphicsExtractor graphics, boolean graphView) {
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDF101010);
        graphics.text(
                font,
                Component.literal("Arachne Profit Tracker")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 12,
                panelY + 12,
                Hud.WHITE,
                true);
        String state = tracker.timerState();
        if (!graphView || tracker.config.graph.showScope) {
            graphics.text(
                    font,
                    font.plainSubstrByWidth(
                            state + " · " + tracker.config.profile, panelWidth - 24),
                    panelX + 12,
                    panelY + 26,
                    Graph.MUTED,
                    true);
        }
        graphics.horizontalLine(panelX + 12, panelX + panelWidth - 12, panelY + 59, 0xFF444444);
    }

    private void drawFooter(
            GuiGraphicsExtractor graphics,
            Ledger.Stats stats,
            Analytics.Snapshot analytics,
            boolean graphView,
            GraphData.Snapshot graph,
            int mouseX,
            int mouseY) {
        String footer = defaultFooterText(stats, analytics);
        if (graphView && System.currentTimeMillis() >= noteExpiresAt && "".equals(tracker.error)) {
            List<String> parts = new ArrayList<>();
            if (tracker.config.graph.showActiveTime) {
                parts.add("Active " + Hud.shortTime(graph.elapsed()));
            }
            if (tracker.config.graph.showSpawnCount) {
                parts.add("Spawns " + graph.spawnCount());
            }
            if (tracker.config.graph.showScope) {
                parts.add(
                        (tracker.config.total ? "Total" : "This session")
                                + " · "
                                + tracker.status());
            }
            footer = String.join(" · ", parts);
        }
        graphics.text(
                font,
                font.plainSubstrByWidth(footer, panelWidth - 24),
                panelX + 12,
                panelY + panelHeight - 42,
                Graph.MUTED,
                true);
        if (mouseX >= panelX + 12
                && mouseX < panelX + panelWidth - 12
                && mouseY >= panelY + panelHeight - 44
                && mouseY < panelY + panelHeight - 31) {
            graphics.setTooltipForNextFrame(Component.literal(footer), mouseX, mouseY);
        }
        graphics.horizontalLine(
                panelX + 12, panelX + panelWidth - 12, panelY + panelHeight - 29, 0xFF333333);
    }

    private String defaultFooterText(Ledger.Stats stats, Analytics.Snapshot analytics) {
        // A storage failure must take precedence over a recent success note.
        if (!"".equals(tracker.error)) {
            return "Storage error - check logs";
        }
        if (System.currentTimeMillis() < noteExpiresAt) {
            return note;
        }
        if (stats.unpriced() > 0) {
            return stats.unpriced()
                    + " unpriced drop"
                    + (stats.unpriced() == 1 ? "" : "s")
                    + " · set Prices";
        }
        if (!analytics.projectionReady()) {
            return "Projection: waiting for 60s and a kill";
        }
        if (analytics.windowKills() < 3) {
            return "Projection: small sample";
        }
        return tracker.status();
    }

    private int drawGraphSummary(
            GuiGraphicsExtractor graphics, GraphData.Snapshot data, int mouseX, int mouseY) {
        var graphPreferences = tracker.config.graph;
        var metric = data.primary();
        int rowY = panelY + 65;
        if (!data.series().isEmpty()) {
            String description =
                    switch (metric) {
                        case PROFIT -> "All recorded income minus all costs.";
                        case LOOT ->
                                "Recorded item-drop value only; excludes Scavenger and manual coin income.";
                        case COSTS -> "All recorded summoning costs and other expenses.";
                    };
            if (graphPreferences.showTotal) {
                line(
                        graphics,
                        metric.totalLabel(),
                        Format.coins(data.value(metric)) + " coins",
                        rowY,
                        metric.color(data.value(metric)),
                        description,
                        mouseX,
                        mouseY);
                rowY += 11;
            }
            if (graphPreferences.showHourly) {
                line(
                        graphics,
                        metric.hourlyLabel(),
                        data.elapsed() < 1000 ? "--" : Format.coins(data.hourly(metric)) + " coins",
                        rowY,
                        data.elapsed() < 1000 ? Graph.MUTED : metric.color(data.hourly(metric)),
                        description + " Divided by active time in this scope.",
                        mouseX,
                        mouseY);
                rowY += 11;
            }
            if (graphPreferences.showProjectedHourly) {
                line(
                        graphics,
                        metric.projectedLabel(),
                        data.projectionReady()
                                ? Format.coins(data.projectedHourly(metric)) + " coins"
                                : "Warming up",
                        rowY,
                        data.projectionReady()
                                ? metric.color(data.projectedHourly(metric))
                                : Graph.MUTED,
                        "Observed current-session pace over up to five active minutes; needs 60 seconds and a recent qualifying kill.",
                        mouseX,
                        mouseY);
                rowY += 11;
            }
            if (graphPreferences.showProjectedTotal) {
                line(
                        graphics,
                        metric.projectedTotalLabel(),
                        data.projectionReady()
                                ? Format.coins(data.projectedTotal(metric)) + " coins"
                                : "Warming up",
                        rowY,
                        data.projectionReady()
                                ? metric.color(data.projectedTotal(metric))
                                : Graph.MUTED,
                        "This scope's current value plus five more active minutes at the observed recent pace. An estimate, not a guaranteed reward.",
                        mouseX,
                        mouseY);
                rowY += 11;
            }
        }
        graphics.horizontalLine(panelX + 12, panelX + panelWidth - 12, rowY + 4, 0xFF333333);
        return rowY + 15;
    }

    private void drawFights(
            GuiGraphicsExtractor graphics, int top, int bottom, int mouseX, int mouseY) {
        var fights = tracker.ledger.recentFights(tracker.config.total);
        if (fights.isEmpty()) {
            graphics.text(font, "No fights recorded yet.", panelX + 12, top, Graph.MUTED, true);
            return;
        }
        int visibleRows = Math.max(1, (bottom - top) / 25);
        scrollOffset =
                Math.max(0, Math.min(scrollOffset, Math.max(0, fights.size() - visibleRows)));
        for (int i = scrollOffset; i < Math.min(fights.size(), scrollOffset + visibleRows); i++) {
            FightRecord fight = fights.get(i);
            var stats = tracker.ledger.fightStats(fight.id);
            int rowY = top + (i - scrollOffset) * 25;
            boolean hovered =
                    mouseX >= panelX + 10
                            && mouseX < panelX + panelWidth - 10
                            && mouseY >= rowY - 2
                            && mouseY < rowY + 22;
            if (hovered) {
                graphics.fill(
                        panelX + 10, rowY - 2, panelX + panelWidth - 10, rowY + 22, 0x30666666);
            }
            String value = Format.coins(stats.profit()) + " coins";
            int valueX = panelX + panelWidth - 16 - font.width(value);
            String label = "Fight #" + fight.id + " · " + FightDetailsScreen.duration(fight);
            graphics.text(
                    font,
                    font.plainSubstrByWidth(label, Math.max(25, valueX - panelX - 24)),
                    panelX + 12,
                    rowY,
                    Hud.TITLE,
                    true);
            graphics.text(
                    font, value, valueX, rowY, stats.profit() >= 0 ? Graph.GREEN : Graph.RED, true);
            String detail =
                    fight.reason(stats.kills() > 0)
                            + (fight.damage >= 0
                                    ? " · "
                                            + String.format(Locale.ROOT, "%,d", fight.damage)
                                            + " damage"
                                    : "");
            graphics.text(
                    font,
                    font.plainSubstrByWidth(detail, panelWidth - 28),
                    panelX + 12,
                    rowY + 11,
                    Graph.MUTED,
                    true);
            if (hovered) {
                graphics.setTooltipForNextFrame(
                        Component.literal(
                                detail
                                        + "\n"
                                        + FightDetailsScreen.date(fight)
                                        + "\nClick for drops, costs and corrections."),
                        mouseX,
                        mouseY);
            }
        }
        if (fights.size() > visibleRows) {
            int scrollTrackHeight = bottom - top,
                    scrollThumbHeight =
                            Math.max(6, scrollTrackHeight * visibleRows / fights.size());
            int scrollThumbOffset =
                    (scrollTrackHeight - scrollThumbHeight)
                            * scrollOffset
                            / Math.max(1, fights.size() - visibleRows);
            graphics.fill(
                    panelX + panelWidth - 8, top, panelX + panelWidth - 7, bottom, 0xFF333333);
            graphics.fill(
                    panelX + panelWidth - 8,
                    top + scrollThumbOffset,
                    panelX + panelWidth - 7,
                    top + scrollThumbOffset + scrollThumbHeight,
                    0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(
            net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (tracker.config.dashboardFights
                && event.button() == 0
                && event.x() >= panelX + 10
                && event.x() < panelX + panelWidth - 10
                && event.y() >= panelY + 119
                && event.y() < panelY + panelHeight - 52) {
            var fights = tracker.ledger.recentFights(tracker.config.total);
            int top = panelY + 121,
                    visibleRows = Math.max(1, (panelY + panelHeight - 52 - top) / 25);
            scrollOffset =
                    Math.max(0, Math.min(scrollOffset, Math.max(0, fights.size() - visibleRows)));
            int row = (int) Math.floor((event.y() - (top - 2)) / 25);
            // Match the rendered row bounds, excluding unused space below the last row.
            if (row >= 0
                    && row < visibleRows
                    && scrollOffset + row < fights.size()
                    && event.y() < top + row * 25 + 22) {
                minecraft.setScreen(
                        new FightDetailsScreen(this, fights.get(scrollOffset + row).id));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Row row(String label, String value, int color, String tip) {
        return new Row(label, value, Graph.MUTED, color, tip);
    }

    private void line(
            GuiGraphicsExtractor graphics,
            String label,
            String value,
            int rowY,
            int color,
            String tip,
            int mouseX,
            int mouseY) {
        int valueX = panelX + panelWidth - 12 - font.width(value);
        graphics.text(
                font,
                font.plainSubstrByWidth(label + ":", Math.max(25, valueX - panelX - 24)),
                panelX + 12,
                rowY,
                Graph.MUTED,
                true);
        graphics.text(font, value, valueX, rowY, color, true);
        if (mouseX >= panelX + 12
                && mouseX < panelX + panelWidth - 12
                && mouseY >= rowY - 1
                && mouseY < rowY + 10) {
            graphics.setTooltipForNextFrame(Component.literal(tip), mouseX, mouseY);
        }
    }

    private void drawRows(
            GuiGraphicsExtractor graphics,
            List<Row> rows,
            int top,
            int bottom,
            int mouseX,
            int mouseY) {
        int visibleRows = Math.max(1, (bottom - top) / 13);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visibleRows)));
        graphics.enableScissor(panelX + 10, top - 1, panelX + panelWidth - 10, bottom);
        for (int i = scrollOffset; i < Math.min(rows.size(), scrollOffset + visibleRows); i++) {
            Row row = rows.get(i);
            int rowY = top + (i - scrollOffset) * 13;
            int valueX = panelX + panelWidth - 16 - font.width(row.value());
            graphics.text(
                    font,
                    font.plainSubstrByWidth(row.label(), Math.max(25, valueX - panelX - 24)),
                    panelX + 12,
                    rowY,
                    row.labelColor(),
                    true);
            graphics.text(font, row.value(), valueX, rowY, row.valueColor(), true);
            if (mouseX >= panelX + 10
                    && mouseX < panelX + panelWidth - 10
                    && mouseY >= rowY - 1
                    && mouseY < rowY + 11) {
                graphics.setTooltipForNextFrame(
                        Component.literal(row.explanation()), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        if (rows.size() > visibleRows) {
            int scrollTrackHeight = bottom - top,
                    scrollThumbHeight = Math.max(6, scrollTrackHeight * visibleRows / rows.size());
            int scrollThumbOffset =
                    (scrollTrackHeight - scrollThumbHeight)
                            * scrollOffset
                            / Math.max(1, rows.size() - visibleRows);
            graphics.fill(
                    panelX + panelWidth - 8, top, panelX + panelWidth - 7, bottom, 0xFF333333);
            graphics.fill(
                    panelX + panelWidth - 8,
                    top + scrollThumbOffset,
                    panelX + panelWidth - 7,
                    top + scrollThumbOffset + scrollThumbHeight,
                    0xFF888888);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(vertical));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        tracker.save();
        minecraft.setScreen(parent);
    }
}

package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.GraphPreferences;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Graph display choices are shared by the dashboard and HUD, without changing tracking. */
public final class GraphOptionsScreen extends Screen {
    private record Row(
            String label,
            String value,
            boolean selected,
            boolean active,
            String tooltip,
            Runnable action) {}

    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    // Switching between line and text options must not inherit the other tab's scroll position.
    private final int[] tabScrollOffsets = new int[2];
    private int selectedTab, panelX, panelY, panelWidth, panelHeight, visibleRows;
    private List<Row> rows = List.of();

    public GraphOptionsScreen(Screen parent) {
        super(Component.literal("Graph options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(460, width - 20);
        panelHeight = Math.min(228, height - 12);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        rows = selectedTab == 0 ? lineRows() : textRows();
        visibleRows = Math.max(1, Math.min(6, (panelHeight - 90) / 22));
        tabScrollOffsets[selectedTab] =
                Math.max(0, Math.min(tabScrollOffsets[selectedTab], rows.size() - visibleRows));

        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + 43,
                        88,
                        18,
                        "Lines",
                        selectedTab == 0,
                        () -> selectTab(0)));
        addRenderableWidget(
                new FlatButton(
                        panelX + 108,
                        panelY + 43,
                        88,
                        18,
                        "Text",
                        selectedTab == 1,
                        () -> selectTab(1)));
        if (rows.size() > visibleRows) {
            var up =
                    new FlatButton(
                            panelX + panelWidth - 60,
                            panelY + 43,
                            20,
                            18,
                            "^",
                            false,
                            () -> move(-1));
            up.active = tabScrollOffsets[selectedTab] > 0;
            up.setTooltip(Tooltip.create(Component.literal("Scroll up")));
            addRenderableWidget(up);
            var down =
                    new FlatButton(
                            panelX + panelWidth - 36,
                            panelY + 43,
                            20,
                            18,
                            "v",
                            false,
                            () -> move(1));
            down.active = tabScrollOffsets[selectedTab] < rows.size() - visibleRows;
            down.setTooltip(Tooltip.create(Component.literal("Scroll down")));
            addRenderableWidget(down);
        }

        for (int i = tabScrollOffsets[selectedTab];
                i < Math.min(rows.size(), tabScrollOffsets[selectedTab] + visibleRows);
                i++) {
            Row row = rows.get(i);
            var button =
                    new FlatButton(
                            panelX + panelWidth - 152,
                            panelY + 67 + (i - tabScrollOffsets[selectedTab]) * 22,
                            136,
                            20,
                            row.value(),
                            row.selected(),
                            row.action());
            button.active = row.active();
            button.setTooltip(Tooltip.create(Component.literal(row.tooltip())));
            addRenderableWidget(button);
        }
        addRenderableWidget(
                new FlatButton(
                        panelX + 16,
                        panelY + panelHeight - 24,
                        panelWidth - 32,
                        20,
                        "Done",
                        false,
                        this::onClose));
    }

    private List<Row> lineRows() {
        GraphPreferences preferences = tracker.config.graph;
        boolean hasMetric = !preferences.enabledSeries().isEmpty();
        return List.of(
                metricRow(
                        GraphPreferences.Metric.PROFIT,
                        "Recorded income minus all recorded costs, including crystals and callings."),
                metricRow(
                        GraphPreferences.Metric.LOOT,
                        "Recorded loot value only. Scavenger coins and other coin income are excluded."),
                metricRow(
                        GraphPreferences.Metric.COSTS,
                        "All recorded costs, including crystals, callings and manual costs."),
                toggle(
                        "Projection",
                        preferences.showProjection,
                        "Extend the current session's observed five-minute pace for five more active minutes. This uses recorded values; it does not predict RNG drops.",
                        () -> preferences.showProjection = !preferences.showProjection),
                toggle(
                        "Spawn markers",
                        preferences.showSpawns,
                        "Mark known Arachne spawns, including fights skipped for low damage. Fights joined after their spawn have no marker.",
                        () -> preferences.showSpawns = !preferences.showSpawns),
                new Row(
                        "Text metric",
                        hasMetric ? preferences.effectiveMetric().label() : "None selected",
                        hasMetric,
                        hasMetric,
                        "Choose which enabled line supplies the totals and hourly text. Disabling its line selects another enabled metric.",
                        () -> {
                            preferences.cyclePrimary();
                            changed();
                        }));
    }

    private Row metricRow(GraphPreferences.Metric metric, String tooltip) {
        GraphPreferences preferences = tracker.config.graph;
        boolean enabled = preferences.enabledSeries().contains(metric);
        return new Row(
                metric.label(),
                enabled ? "Shown" : "Hidden",
                enabled,
                true,
                tooltip,
                () -> {
                    preferences.setVisible(metric, !enabled);
                    changed();
                });
    }

    private List<Row> textRows() {
        GraphPreferences preferences = tracker.config.graph;
        return List.of(
                toggle(
                        "Total value",
                        preferences.showTotal,
                        "Show the selected metric's total for the current session or all-time scope.",
                        () -> preferences.showTotal = !preferences.showTotal),
                toggle(
                        "Projected total",
                        preferences.showProjectedTotal,
                        "Show the selected metric's total after five more active minutes, starting from its current total. This extrapolates recorded pace and is not guaranteed.",
                        () -> preferences.showProjectedTotal = !preferences.showProjectedTotal),
                toggle(
                        "Hourly rate",
                        preferences.showHourly,
                        "Show the selected metric per active hour. The label follows your chosen text metric.",
                        () -> preferences.showHourly = !preferences.showHourly),
                toggle(
                        "Projected hourly",
                        preferences.showProjectedHourly,
                        "Show the selected metric's hourly rate estimated from the current session's recent recorded pace.",
                        () -> preferences.showProjectedHourly = !preferences.showProjectedHourly),
                toggle(
                        "Arachne spawns",
                        preferences.showSpawnCount,
                        "Count known Arachne spawns, including fights skipped for low damage. Fights joined after their spawn are excluded.",
                        () -> preferences.showSpawnCount = !preferences.showSpawnCount),
                toggle(
                        "Active time",
                        preferences.showActiveTime,
                        "Show active time in the graph text. Hiding it does not stop time tracking or change the graph's time axis.",
                        () -> preferences.showActiveTime = !preferences.showActiveTime),
                toggle(
                        "Session / total",
                        preferences.showScope,
                        "Show the current session or all-time scope in the graph text. This does not switch your selected scope.",
                        () -> preferences.showScope = !preferences.showScope));
    }

    private Row toggle(String label, boolean enabled, String tooltip, Runnable action) {
        return new Row(
                label,
                enabled ? "On" : "Off",
                enabled,
                true,
                tooltip,
                () -> {
                    action.run();
                    changed();
                });
    }

    private void changed() {
        tracker.saveConfig();
        rebuildWidgets();
    }

    private void selectTab(int selected) {
        selectedTab = selected;
        rebuildWidgets();
    }

    private void move(int direction) {
        int next =
                Math.max(
                        0,
                        Math.min(
                                tabScrollOffsets[selectedTab] + direction,
                                rows.size() - visibleRows));
        if (next != tabScrollOffsets[selectedTab]) {
            tabScrollOffsets[selectedTab] = next;
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= panelX
                && mouseX < panelX + panelWidth
                && mouseY >= panelY + 43
                && mouseY < panelY + panelHeight - 24
                && vertical != 0) {
            move(-(int) Math.signum(vertical));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xDF101010);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(
                font,
                Component.literal("Graph options")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 16,
                panelY + 12,
                Hud.WHITE,
                true);
        String hint = "Applies to the graph dashboard and HUD.";
        graphics.text(
                font,
                font.plainSubstrByWidth(hint, panelWidth - 32),
                panelX + 16,
                panelY + 30,
                Graph.MUTED,
                false);
        for (int i = tabScrollOffsets[selectedTab];
                i < Math.min(rows.size(), tabScrollOffsets[selectedTab] + visibleRows);
                i++) {
            Row row = rows.get(i);
            int rowY = panelY + 73 + (i - tabScrollOffsets[selectedTab]) * 22;
            graphics.text(
                    font,
                    font.plainSubstrByWidth(row.label(), panelWidth - 184),
                    panelX + 16,
                    rowY,
                    Hud.WHITE,
                    false);
            if (mouseX >= panelX + 16
                    && mouseX < panelX + panelWidth - 156
                    && mouseY >= rowY - 6
                    && mouseY < rowY + 14) {
                graphics.setTooltipForNextFrame(Component.literal(row.tooltip()), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

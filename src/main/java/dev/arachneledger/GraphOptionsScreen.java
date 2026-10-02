package dev.arachneledger;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Graph display choices are shared by the dashboard and HUD, without changing tracking. */
public final class GraphOptionsScreen extends Screen {
    private record Row(String label, String value, boolean selected, boolean active,
                       String tooltip, Runnable action) {}

    private final Screen parent;
    private final Tracker t = ArachneLedger.tracker;
    private final int[] scroll = new int[2];
    private int tab, x, y, w, h, visible;
    private List<Row> rows = List.of();

    public GraphOptionsScreen(Screen parent) {
        super(Component.literal("Graph options"));
        this.parent = parent;
    }

    @Override protected void init() {
        w = Math.min(460, width - 20);
        h = Math.min(228, height - 12);
        x = (width - w) / 2;
        y = (height - h) / 2;
        rows = tab == 0 ? lineRows() : textRows();
        visible = Math.max(1, Math.min(6, (h - 90) / 22));
        scroll[tab] = Math.max(0, Math.min(scroll[tab], rows.size() - visible));

        addRenderableWidget(new FlatButton(x + 16, y + 43, 88, 18, "Lines", tab == 0,
            () -> selectTab(0)));
        addRenderableWidget(new FlatButton(x + 108, y + 43, 88, 18, "Text", tab == 1,
            () -> selectTab(1)));
        if (rows.size() > visible) {
            var up = new FlatButton(x + w - 60, y + 43, 20, 18, "^", false, () -> move(-1));
            up.active = scroll[tab] > 0;
            up.setTooltip(Tooltip.create(Component.literal("Scroll up")));
            addRenderableWidget(up);
            var down = new FlatButton(x + w - 36, y + 43, 20, 18, "v", false, () -> move(1));
            down.active = scroll[tab] < rows.size() - visible;
            down.setTooltip(Tooltip.create(Component.literal("Scroll down")));
            addRenderableWidget(down);
        }

        for (int i = scroll[tab]; i < Math.min(rows.size(), scroll[tab] + visible); i++) {
            Row row = rows.get(i);
            var button = new FlatButton(x + w - 152, y + 67 + (i - scroll[tab]) * 22,
                136, 20, row.value(), row.selected(), row.action());
            button.active = row.active();
            button.setTooltip(Tooltip.create(Component.literal(row.tooltip())));
            addRenderableWidget(button);
        }
        addRenderableWidget(new FlatButton(x + 16, y + h - 24, w - 32, 20,
            "Done", false, this::onClose));
    }

    private List<Row> lineRows() {
        GraphPreferences p = t.config.graph;
        boolean hasMetric = !p.enabledSeries().isEmpty();
        return List.of(
            metricRow(GraphPreferences.Metric.PROFIT,
                "Recorded income minus all recorded costs, including crystals and callings."),
            metricRow(GraphPreferences.Metric.LOOT,
                "Recorded loot value only. Scavenger coins and other coin income are excluded."),
            metricRow(GraphPreferences.Metric.COSTS,
                "All recorded costs, including crystals, callings and manual costs."),
            toggle("Projection", p.showProjection,
                "Extend the current session's observed five-minute pace for five more active minutes. This uses recorded values; it does not predict RNG drops.",
                () -> p.showProjection = !p.showProjection),
            toggle("Spawn markers", p.showSpawns,
                "Mark known Arachne spawns, including fights skipped for low damage. Fights joined after their spawn have no marker.",
                () -> p.showSpawns = !p.showSpawns),
            new Row("Text metric", hasMetric ? p.effectiveMetric().label() : "None selected",
                hasMetric, hasMetric,
                "Choose which enabled line supplies the totals and hourly text. Disabling its line selects another enabled metric.",
                () -> { p.cyclePrimary(); changed(); })
        );
    }

    private Row metricRow(GraphPreferences.Metric metric, String tooltip) {
        GraphPreferences p = t.config.graph;
        boolean enabled = p.enabledSeries().contains(metric);
        return new Row(metric.label(), enabled ? "Shown" : "Hidden", enabled, true, tooltip,
            () -> { p.setVisible(metric, !enabled); changed(); });
    }

    private List<Row> textRows() {
        GraphPreferences p = t.config.graph;
        return List.of(
            toggle("Total value", p.showTotal,
                "Show the selected metric's total for the current session or all-time scope.",
                () -> p.showTotal = !p.showTotal),
            toggle("Projected total", p.showProjectedTotal,
                "Show the selected metric's total after five more active minutes, starting from its current total. This extrapolates recorded pace and is not guaranteed.",
                () -> p.showProjectedTotal = !p.showProjectedTotal),
            toggle("Hourly rate", p.showHourly,
                "Show the selected metric per active hour. The label follows your chosen text metric.",
                () -> p.showHourly = !p.showHourly),
            toggle("Projected hourly", p.showProjectedHourly,
                "Show the selected metric's hourly rate estimated from the current session's recent recorded pace.",
                () -> p.showProjectedHourly = !p.showProjectedHourly),
            toggle("Arachne spawns", p.showSpawnCount,
                "Count known Arachne spawns, including fights skipped for low damage. Fights joined after their spawn are excluded.",
                () -> p.showSpawnCount = !p.showSpawnCount),
            toggle("Active time", p.showActiveTime,
                "Show active time in the graph text. Hiding it does not stop time tracking or change the graph's time axis.",
                () -> p.showActiveTime = !p.showActiveTime),
            toggle("Session / total", p.showScope,
                "Show the current session or all-time scope in the graph text. This does not switch your selected scope.",
                () -> p.showScope = !p.showScope)
        );
    }

    private Row toggle(String label, boolean enabled, String tooltip, Runnable action) {
        return new Row(label, enabled ? "On" : "Off", enabled, true, tooltip,
            () -> { action.run(); changed(); });
    }

    private void changed() { t.saveConfig(); rebuildWidgets(); }
    private void selectTab(int selected) { tab = selected; rebuildWidgets(); }
    private void move(int direction) {
        int next = Math.max(0, Math.min(scroll[tab] + direction, rows.size() - visible));
        if (next != scroll[tab]) { scroll[tab] = next; rebuildWidgets(); }
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx >= x && mx < x + w && my >= y + 43 && my < y + h - 24 && vertical != 0) {
            move(-(int)Math.signum(vertical));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xDF101010);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(font, Component.literal("Graph options").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            x + 16, y + 12, Hud.WHITE, true);
        String hint = "Applies to the graph dashboard and HUD.";
        g.text(font, font.plainSubstrByWidth(hint, w - 32), x + 16, y + 30, Graph.MUTED, false);
        for (int i = scroll[tab]; i < Math.min(rows.size(), scroll[tab] + visible); i++) {
            Row row = rows.get(i);
            int yy = y + 73 + (i - scroll[tab]) * 22;
            g.text(font, font.plainSubstrByWidth(row.label(), w - 184), x + 16, yy,
                Hud.WHITE, false);
            if (mx >= x + 16 && mx < x + w - 156 && my >= yy - 6 && my < yy + 14)
                g.setTooltipForNextFrame(Component.literal(row.tooltip()), mx, my);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

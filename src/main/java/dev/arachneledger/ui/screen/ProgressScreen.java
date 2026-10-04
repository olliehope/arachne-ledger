package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Direct navigation to progression and optional details, without crowding the live overlay. */
public class ProgressScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private int x, y, panelWidth, panelHeight;

    public ProgressScreen(Screen parent) {
        super(Component.literal("Arachne progress"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(400, width - 24);
        panelHeight = Math.min(238, height - 12);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        button(
                x + panelWidth - 120,
                y + 8,
                62,
                18,
                "Session",
                !tracker.config.total,
                "Show the current session in history and comparisons.",
                () -> scope(false));
        button(
                x + panelWidth - 54,
                y + 8,
                42,
                18,
                "Total",
                tracker.config.total,
                "Show lifetime history and comparisons.",
                () -> scope(true));
        route(
                0,
                "Achievements",
                "Lifetime milestones and optional challenges; pin one goal to your HUD.",
                () -> minecraft.setScreen(new AchievementsScreen(this)));
        route(
                1,
                "RNG history",
                "Kills and active time since pets and Fangs, plus observed rare-drop rates.",
                () -> minecraft.setScreen(new RngStatsScreen(this)));
        route(
                2,
                "T1 / T2 comparison",
                "Compare Calling and Crystal fights, recorded rewards, costs and fight pace.",
                () -> minecraft.setScreen(new SummonStatsScreen(this)));
        route(
                3,
                "Recaps & records",
                "Current and previous session summaries, fastest fights and personal records.",
                () -> minecraft.setScreen(new RecapScreen(this)));
        route(
                4,
                "Session target",
                "Choose a kill, active-time or profit target for the current session.",
                () -> minecraft.setScreen(new GoalsScreen(this)));
        route(
                5,
                "Supplies",
                "Crystals and Callings in your inventory, with estimated time before restocking.",
                () -> minecraft.setScreen(new SuppliesScreen(this)));
        int step = Math.max(23, Math.min(29, (panelHeight - 116) / 3));
        button(
                x + 12,
                y + 53 + step * 3,
                panelWidth - 24,
                22,
                "Tracking diagnostics",
                false,
                "Inspect detection and copy a report when something fails.",
                () -> minecraft.setScreen(new DiagnosticsScreen(this)));
        button(
                x + 12,
                y + panelHeight - 24,
                panelWidth - 24,
                20,
                "Back",
                false,
                "Return to the dashboard.",
                this::onClose);
    }

    private void route(int index, String label, String tooltip, Runnable action) {
        int columnWidth = (panelWidth - 30) / 2;
        int step = Math.max(23, Math.min(29, (panelHeight - 116) / 3));
        button(
                x + 12 + index % 2 * (columnWidth + 6),
                y + 53 + index / 2 * step,
                columnWidth,
                22,
                label,
                false,
                tooltip,
                action);
    }

    private void button(
            int x,
            int y,
            int width,
            int height,
            String label,
            boolean selected,
            String tooltip,
            Runnable action) {
        var button = new FlatButton(x, y, width, height, label, selected, action);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(button);
    }

    private void scope(boolean total) {
        ScopeSelection.select(tracker, total);
        rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        extractBackground(graphics, mx, my, delta);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xDF101010);
        graphics.text(font, "Progress", x + 12, y + 12, Hud.TITLE, true);
        graphics.text(
                font, "Goals, rewards and session history.", x + 12, y + 32, Graph.MUTED, false);
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
                renderable.extractRenderState(graphics, mx, my, delta);
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

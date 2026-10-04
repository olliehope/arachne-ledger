package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.ledger.SummonStats;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;
import dev.arachneledger.ui.Tooltips;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Tier comparison rates are explicitly fight-only, avoiding invented attribution of waiting time.
 */
public final class SummonStatsScreen extends Screen {
    private record Stat(
            String label, Function<SummonStats.Row, String> value, String explanation) {}

    private static final List<Stat> STATS =
            List.of(
                    new Stat(
                            "Qualified kills",
                            row -> Long.toString(row.qualifiedKills()),
                            "Only counted kills with a retained server kill receipt and sufficient reported damage."),
                    new Stat(
                            "Loot value",
                            row -> Format.coins(row.detectedLootRevenue()),
                            "Detected loot at its recorded sale value. Manual income is shown in other income below."),
                    new Stat(
                            "Scavenger coins",
                            row -> Format.coins(row.scavengerCoins()),
                            "Recorded coin rewards associated with qualifying fights."),
                    new Stat(
                            "Costs",
                            row -> Format.coins(row.costs()),
                            "Your recorded costs associated with qualifying fights. Skipped and unassociated costs remain in the footer."),
                    new Stat(
                            "Net profit",
                            row -> Format.coins(row.profit()),
                            "Detected loot and coins minus associated costs."),
                    new Stat(
                            "Without RNG",
                            row -> Format.coins(row.ordinaryProfit()),
                            "Net profit excluding Tarantula pet and Arachne Fang value, retaining all associated costs."),
                    new Stat(
                            "Profit / kill",
                            row ->
                                    row.qualifiedKills() == 0
                                            ? "--"
                                            : Format.coins(row.profit() / row.qualifiedKills()),
                            "Net profit divided by qualifying kills of this tier."),
                    new Stat(
                            "Avg. fight time",
                            row ->
                                    row.timedKills() == 0
                                            ? "--"
                                            : Format.time((long) row.averageFightMillis()),
                            "Average confirmed spawn-to-death time. Uses the timed subset; compare the Timed fights row."),
                    new Stat(
                            "Fight profit / hr",
                            row -> row.hasTiming() ? Format.coins(row.fightHourly()) : "--",
                            "Fight-only rate: net profit divided by confirmed fight durations. Ritual, waiting and AFK time are excluded. Unavailable if any qualifying fight lacks a confirmed duration."),
                    new Stat(
                            "Without RNG / hr",
                            row -> row.hasTiming() ? Format.coins(row.ordinaryFightHourly()) : "--",
                            "Fight-only profit per hour excluding pet and Fang value. This is not your whole-session earning rate."),
                    new Stat(
                            "Timed fights",
                            row -> row.timedKills() + " / " + row.qualifiedKills(),
                            "Fights with both a confirmed spawn and death. Legacy or recovered fights stay Unknown when their tier cannot be established."));
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private int x, y, panelWidth, panelHeight, scrollOffset;
    private SummonStats.Snapshot cached;
    private List<List<String>> values = List.of();

    public SummonStatsScreen(Screen parent) {
        super(Component.literal("Arachne T1 / T2 comparison"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(480, width - 24);
        panelHeight = Math.min(310, height - 20);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        button(
                x + panelWidth - 120,
                y + 8,
                62,
                "Session",
                !tracker.config.total,
                () -> scope(false));
        button(x + panelWidth - 54, y + 8, 42, "Total", tracker.config.total, () -> scope(true));
        button(x + 12, y + panelHeight - 24, 24, "^", false, () -> move(-1));
        button(x + 40, y + panelHeight - 24, 24, "v", false, () -> move(1));
        button(x + panelWidth - 80, y + panelHeight - 24, 68, "Back", false, this::onClose);
    }

    private void button(int x, int y, int size, String text, boolean selected, Runnable action) {
        addRenderableWidget(new FlatButton(x, y, size, 18, text, selected, action));
    }

    private void scope(boolean total) {
        ScopeSelection.select(tracker, total);
        scrollOffset = 0;
        rebuildWidgets();
    }

    private int visibleRows() {
        return Math.max(1, (panelHeight - 126) / 17);
    }

    private void move(int step) {
        scrollOffset = Math.max(0, Math.min(STATS.size() - visibleRows(), scrollOffset + step));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        SummonStats.Snapshot data = tracker.ledger.summonStats(tracker.config.total);
        if (cached != data) {
            values =
                    STATS.stream()
                            .map(stat -> data.rows().stream().map(stat.value()).toList())
                            .toList();
            cached = data;
        }
        extractBackground(graphics, mx, my, delta);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xDF101010);
        graphics.text(font, "T1 / T2 comparison", x + 12, y + 12, Hud.TITLE, true);
        graphics.text(
                font,
                font.plainSubstrByWidth(
                        "Rates use fight time; waiting is excluded.", panelWidth - 24),
                x + 12,
                y + 32,
                Graph.MUTED,
                false);
        int labelWidth = Math.min(138, (panelWidth - 24) / 3);
        int tableLeft = x + 12 + labelWidth, columnWidth = (panelWidth - 24 - labelWidth) / 3;
        String[] headers = {"T1 Calling", "T2 Crystal", "Unknown"};
        for (int column = 0; column < 3; column++)
            graphics.centeredText(
                    font,
                    font.plainSubstrByWidth(headers[column], columnWidth - 4),
                    tableLeft + columnWidth * column + columnWidth / 2,
                    y + 55,
                    Hud.GOLD);
        graphics.horizontalLine(x + 12, x + panelWidth - 12, y + 69, 0xFF333333);
        int count = visibleRows();
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, STATS.size() - count)));
        for (int index = scrollOffset;
                index < Math.min(STATS.size(), scrollOffset + count);
                index++) {
            int rowY = y + 78 + (index - scrollOffset) * 17;
            Stat stat = STATS.get(index);
            graphics.text(
                    font,
                    font.plainSubstrByWidth(stat.label(), labelWidth - 6),
                    x + 12,
                    rowY,
                    Graph.MUTED,
                    false);
            for (int column = 0; column < 3; column++) {
                String value = values.get(index).get(column);
                double amount =
                        index == 5
                                ? data.rows().get(column).ordinaryProfit()
                                : data.rows().get(column).profit();
                int color =
                        index == 4 || index == 5
                                ? (amount < 0 ? Graph.RED : Graph.GREEN)
                                : Hud.WHITE;
                String fitted = font.plainSubstrByWidth(value, columnWidth - 6);
                graphics.text(
                        font,
                        fitted,
                        tableLeft + columnWidth * (column + 1) - 4 - font.width(fitted),
                        rowY,
                        color,
                        false);
            }
            if (mx >= x + 12 && mx < x + panelWidth - 12 && my >= rowY - 2 && my < rowY + 12)
                Tooltips.show(graphics, font, stat.explanation(), mx, my);
        }
        String other =
                "Other / skipped: +"
                        + Format.coins(data.unassignedRevenue())
                        + " income · -"
                        + Format.coins(data.unassignedCosts())
                        + " costs";
        graphics.text(
                font,
                font.plainSubstrByWidth(other, panelWidth - 24),
                x + 12,
                y + panelHeight - 43,
                Graph.MUTED,
                false);
        if (mx >= x + 12
                && mx < x + panelWidth - 12
                && my >= y + panelHeight - 46
                && my < y + panelHeight - 31)
            Tooltips.show(
                    graphics,
                    font,
                    other
                            + "\nThese values remain in the main dashboard totals. Legacy fights are never guessed to be T1 or T2.",
                    mx,
                    my);
        for (var child : children())
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
                renderable.extractRenderState(graphics, mx, my, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (vertical != 0) {
            move(-(int) Math.signum(vertical));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
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

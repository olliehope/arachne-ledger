package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.PersonalRecords;
import dev.arachneledger.ledger.SessionSummary;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A compact session summary and reversible records derived from the receipt journal. */
public final class RecapScreen extends Screen {
    private enum Tab {
        CURRENT,
        LAST,
        RECORDS
    }

    private record Row(String label, String value, int color, String explanation) {}

    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private Tab tab;
    private boolean total;
    private int panelX, panelY, panelWidth, panelHeight, scrollOffset;
    private Ledger cachedLedger;
    private long cachedRevision = -1, cachedSecond = -1;
    private boolean cachedTotal;
    private SessionSummary.Snapshot current;
    private PersonalRecords.Snapshot records;

    public RecapScreen(Screen parent) {
        this(parent, false);
    }

    public RecapScreen(Screen parent, boolean showLastSession) {
        super(Component.literal("Session recap"));
        this.parent = parent;
        total = tracker.config.total;
        tab = showLastSession ? Tab.LAST : Tab.CURRENT;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(470, width - 24);
        panelHeight = Math.min(328, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        FlatButton totalButton =
                button(
                        panelX + panelWidth - 120,
                        panelY + 8,
                        43,
                        "Total",
                        total,
                        () -> scope(true));
        FlatButton sessionButton =
                button(
                        panelX + panelWidth - 74,
                        panelY + 8,
                        62,
                        "Session",
                        !total,
                        () -> scope(false));
        totalButton.active = tab != Tab.LAST;
        sessionButton.active = tab != Tab.LAST;
        button(
                panelX + 12,
                panelY + 32,
                72,
                "Current",
                tab == Tab.CURRENT,
                () -> select(Tab.CURRENT));
        button(
                panelX + 88,
                panelY + 32,
                92,
                "Last session",
                tab == Tab.LAST,
                () -> select(Tab.LAST));
        button(
                panelX + 184,
                panelY + 32,
                72,
                "Records",
                tab == Tab.RECORDS,
                () -> select(Tab.RECORDS));
        button(
                panelX + panelWidth - 80,
                panelY + panelHeight - 24,
                68,
                "Back",
                false,
                this::onClose);
    }

    private FlatButton button(
            int x, int y, int size, String label, boolean selected, Runnable action) {
        FlatButton button = new FlatButton(x, y, size, 18, label, selected, action);
        button.setTooltip(Tooltip.create(Component.literal(label)));
        addRenderableWidget(button);
        return button;
    }

    private void select(Tab selected) {
        tab = selected;
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void scope(boolean selected) {
        total = selected;
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void refresh() {
        Ledger ledger = tracker.ledger;
        long second = ledger.activeMillis / 1000;
        boolean journalChanged =
                cachedLedger != ledger
                        || cachedRevision != ledger.revision()
                        || cachedTotal != total;
        if (journalChanged || cachedSecond != second || current == null) {
            current = SessionSummary.capture(ledger, total);
        }
        if (journalChanged || records == null) {
            records = PersonalRecords.calculate(ledger, total);
        }
        cachedLedger = ledger;
        cachedRevision = ledger.revision();
        cachedSecond = second;
        cachedTotal = total;
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        refresh();
        extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDF101010);
        graphics.text(font, "Session recap", panelX + 12, panelY + 12, Hud.TITLE, true);
        graphics.horizontalLine(panelX + 12, panelX + panelWidth - 12, panelY + 54, 0xFF333333);
        SessionSummary.Snapshot selected =
                tab == Tab.LAST
                        ? (tracker.ledger.sessionRecaps.isEmpty()
                                ? null
                                : tracker.ledger.sessionRecaps.getLast())
                        : current;
        List<Row> rows = tab == Tab.RECORDS ? recordRows(records) : summaryRows(selected);
        drawRows(graphics, rows, mouseX, mouseY);
        String footer =
                tab == Tab.RECORDS
                        ? "Detected rewards only · manual additions excluded"
                        : tab == Tab.LAST
                                ? "Captured before the session reset"
                                : "Recorded values · active time excludes AFK";
        graphics.text(
                font,
                font.plainSubstrByWidth(footer, panelWidth - 24),
                panelX + 12,
                panelY + panelHeight - 42,
                Graph.MUTED,
                true);
        if (mouseY >= panelY + panelHeight - 44 && mouseY < panelY + panelHeight - 30) {
            graphics.setTooltipForNextFrame(Component.literal(footer), mouseX, mouseY);
        }
        graphics.horizontalLine(
                panelX + 12, panelX + panelWidth - 12, panelY + panelHeight - 29, 0xFF333333);
        for (var child : children()) {
            if (child instanceof Renderable renderable) {
                renderable.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        }
    }

    private List<Row> summaryRows(SessionSummary.Snapshot summary) {
        if (summary == null) {
            return List.of(
                    new Row(
                            "Last session",
                            "None yet",
                            Graph.MUTED,
                            "Start a new session to save its recap."));
        }
        var profit = summary.profit();
        List<Row> rows = new ArrayList<>();
        rows.add(
                new Row(
                        "Scope",
                        summary.total() ? "Lifetime" : "Session " + summary.sessionId(),
                        Graph.MUTED,
                        "Financial totals include manual adjustments, as recorded in your journal."));
        rows.add(
                money(
                        "Net profit",
                        profit.net(),
                        "All loot and coin income, less all recorded costs."));
        rows.add(
                money(
                        "Without RNG",
                        profit.ordinaryNet(),
                        "Tarantula pets and Arachne Fangs excluded. Scavenger coins and all costs remain."));
        rows.add(
                money(
                        "Profit / hour",
                        profit.hourly(),
                        "Net profit divided by tracked active time."));
        rows.add(
                money(
                        "Without RNG / hour",
                        profit.ordinaryHourly(),
                        "The same active-time rate, excluding Tarantula pets and Arachne Fangs."));
        rows.add(
                new Row(
                        "Active time",
                        Format.time(profit.elapsed()),
                        Hud.WHITE,
                        "Includes spawning, fighting and the post-fight grace period. AFK time is excluded."));
        rows.add(
                new Row(
                        "Qualifying kills",
                        Long.toString(summary.qualifiedKills()),
                        Hud.WHITE,
                        "Confirmed server kills meeting each fight's saved damage threshold."));
        rows.add(
                new Row(
                        "Average kill time",
                        summary.timedKills() > 0 ? seconds(summary.averageKillMillis()) : "Unknown",
                        Hud.WHITE,
                        summary.timedKills()
                                + " confirmed timed kills. Unknown spawn times and zero-length fights are excluded."));
        rows.add(
                new Row(
                        "Fastest kill",
                        summary.fastestKillMillis() > 0
                                ? seconds(summary.fastestKillMillis())
                                : "Unknown",
                        Hud.WHITE,
                        "Fastest qualifying fight with a confirmed spawn and death."));
        rows.add(
                new Row(
                        "Crystals placed",
                        Long.toString(summary.crystals()),
                        Hud.WHITE,
                        "Your recorded Crystal placements."));
        rows.add(
                new Row(
                        "Callings placed",
                        Long.toString(summary.callings()),
                        Hud.WHITE,
                        "Your recorded Calling placements."));
        rows.add(
                money(
                        "Crystal spend",
                        -summary.crystalSpend(),
                        "Recorded Crystal costs, including unsuccessful summons."));
        rows.add(
                money(
                        "Calling spend",
                        -summary.callingSpend(),
                        "Recorded Calling costs, including unsuccessful summons."));
        rows.add(money("Other spend", -summary.otherSpend(), "Other recorded expenses."));
        rows.add(
                money(
                        "Scavenger coins",
                        summary.scavengerCoins(),
                        "Included in profit once; shown here as a subtotal."));
        rows.add(
                money(
                        "RNG value",
                        profit.rngRevenue(),
                        "Recorded Tarantula pet and Arachne Fang value."));
        if (summary.unpriced() > 0) {
            rows.add(
                    new Row(
                            "Unpriced quantity",
                            Long.toString(summary.unpriced()),
                            Hud.GOLD,
                            "These item units contributed zero coins. Set prices and explicitly reprice the session to change recorded values."));
        }
        return rows;
    }

    private List<Row> recordRows(PersonalRecords.Snapshot snapshot) {
        List<Row> rows = new ArrayList<>();
        rows.add(
                new Row(
                        "Scope",
                        snapshot.total() ? "Lifetime" : "Current session",
                        Graph.MUTED,
                        "Records are rebuilt from qualifying detected fights. Late drops, edits and repricing are reflected."));
        var fastest = snapshot.fastestKill();
        rows.add(
                new Row(
                        "Fastest kill",
                        fastest == null ? "None yet" : seconds(fastest.durationMillis()),
                        Hud.WHITE,
                        fastest == null
                                ? "Needs a qualifying kill with confirmed spawn and death times."
                                : "Fight #"
                                        + fastest.fightId()
                                        + " · Session "
                                        + fastest.sessionId()));
        var bestFight = snapshot.bestFight();
        rows.add(
                new Row(
                        "Best fight",
                        bestFight == null ? "None yet" : Format.coins(bestFight.net()),
                        bestFight == null ? Graph.MUTED : profitColor(bestFight.net()),
                        bestFight == null
                                ? "Needs a confirmed qualifying server kill."
                                : "Fight #"
                                        + bestFight.fightId()
                                        + " · Session "
                                        + bestFight.sessionId()
                                        + ". Detected rewards minus associated costs."));
        if (bestFight != null) {
            rows.add(
                    money(
                            "Its profit without RNG",
                            bestFight.ordinaryNet(),
                            "Excludes pets and Fangs from the most profitable fight; this is not a separate record."));
        }
        var bestSession = snapshot.bestSession();
        rows.add(
                new Row(
                        "Best tracked session",
                        bestSession == null ? "None yet" : Format.coins(bestSession.net()),
                        bestSession == null ? Graph.MUTED : profitColor(bestSession.net()),
                        bestSession == null
                                ? "Needs at least one qualifying fight."
                                : "Session "
                                        + bestSession.sessionId()
                                        + " · "
                                        + bestSession.qualifiedKills()
                                        + " qualifying kills. Includes associated costs from failed attempts. Unassociated receipts cannot be assigned to historical sessions."));
        rows.add(
                new Row(
                        "Qualifying kills",
                        Long.toString(snapshot.qualifiedKills()),
                        Hud.WHITE,
                        "Saved damage threshold met, Counted outcome, and a retained server kill receipt."));
        rows.add(
                new Row(
                        "Rewards",
                        "Detected only",
                        Graph.MUTED,
                        "Manual loot, manual coin adjustments and manually replaced fight loot never increase a record. Associated cost corrections still subtract."));
        return rows;
    }

    private void drawRows(GuiGraphicsExtractor graphics, List<Row> rows, int mouseX, int mouseY) {
        int top = panelY + 65, bottom = panelY + panelHeight - 52;
        int visible = Math.max(1, (bottom - top) / 15);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visible)));
        graphics.enableScissor(panelX + 10, top - 1, panelX + panelWidth - 10, bottom);
        for (int i = scrollOffset; i < Math.min(rows.size(), scrollOffset + visible); i++) {
            Row row = rows.get(i);
            int y = top + (i - scrollOffset) * 15;
            int valueWidth = Math.min(font.width(row.value()), Math.max(50, panelWidth / 2));
            String value = font.plainSubstrByWidth(row.value(), valueWidth);
            int right = panelX + panelWidth - 16 - font.width(value);
            graphics.text(
                    font,
                    font.plainSubstrByWidth(row.label(), Math.max(20, right - panelX - 24)),
                    panelX + 12,
                    y,
                    Graph.MUTED,
                    true);
            graphics.text(font, value, right, y, row.color(), true);
            if (mouseY >= y - 1 && mouseY < y + 13) {
                graphics.setTooltipForNextFrame(
                        Component.literal(
                                row.label() + ": " + row.value() + "\n" + row.explanation()),
                        mouseX,
                        mouseY);
            }
        }
        graphics.disableScissor();
        if (rows.size() > visible) {
            int track = bottom - top;
            int thumb = Math.max(6, track * visible / rows.size());
            int offset = (track - thumb) * scrollOffset / (rows.size() - visible);
            graphics.fill(
                    panelX + panelWidth - 8, top, panelX + panelWidth - 7, bottom, 0xFF333333);
            graphics.fill(
                    panelX + panelWidth - 8,
                    top + offset,
                    panelX + panelWidth - 7,
                    top + offset + thumb,
                    0xFF888888);
        }
    }

    private static Row money(String label, double amount, String explanation) {
        return new Row(label, Format.coins(amount), profitColor(amount), explanation);
    }

    private static int profitColor(double amount) {
        return amount < 0 ? Graph.RED : Graph.GREEN;
    }

    private static String seconds(double millis) {
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
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
        minecraft.setScreen(parent);
    }
}

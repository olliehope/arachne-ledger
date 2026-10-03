package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.RngSince;
import dev.arachneledger.ledger.RngSince.Reward;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Optional RNG detail page; the live HUD stays focused on the player's selected profit stats. */
public final class RngStatsScreen extends Screen {
    private record Row(String label, String value, String explanation) {}

    private static final DateTimeFormatter DROP_TIME =
            DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private Reward reward = Reward.ANY_PET;
    private boolean total;
    private int panelX, panelY, panelWidth, panelHeight, scrollOffset;
    private RngSince.Snapshot snapshot;
    private Ledger cachedLedger;
    private long cachedRevision = -1, cachedSecond = -1;
    private boolean cachedTotal;

    public RngStatsScreen(Screen parent) {
        super(Component.literal("Arachne RNG history"));
        this.parent = parent;
        total = tracker.config.total;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(440, width - 24);
        panelHeight = Math.min(310, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        button(panelX + panelWidth - 120, panelY + 8, 43, "Total", total, () -> scope(true));
        button(panelX + panelWidth - 74, panelY + 8, 62, "Session", !total, () -> scope(false));
        int tabWidth = (panelWidth - 30) / 2;
        String[] labels = {"Any pet", "Epic pet", "Legendary pet", "Arachne's Fang"};
        for (int index = 0; index < Reward.values().length; index++) {
            Reward selected = Reward.values()[index];
            button(
                    panelX + 12 + (index % 2) * (tabWidth + 6),
                    panelY + 36 + (index / 2) * 22,
                    tabWidth,
                    labels[index],
                    reward == selected,
                    () -> {
                        reward = selected;
                        scrollOffset = 0;
                        rebuildWidgets();
                    });
        }
        button(panelX + 12, panelY + panelHeight - 24, 22, "^", false, () -> move(-1));
        button(panelX + 38, panelY + panelHeight - 24, 22, "v", false, () -> move(1));
        button(
                panelX + panelWidth - 80,
                panelY + panelHeight - 24,
                68,
                "Back",
                false,
                this::onClose);
    }

    private void button(int x, int y, int size, String label, boolean selected, Runnable action) {
        addRenderableWidget(new FlatButton(x, y, size, 18, label, selected, action));
    }

    private void scope(boolean selected) {
        total = selected;
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void move(int direction) {
        scrollOffset = Math.max(0, scrollOffset + direction);
    }

    private void refresh() {
        Ledger ledger = tracker.ledger;
        long second = ledger.activeMillis / 1000;
        if (snapshot == null
                || cachedLedger != ledger
                || cachedRevision != ledger.revision()
                || cachedSecond != second
                || cachedTotal != total) {
            snapshot = tracker.rngSince(total);
            cachedLedger = ledger;
            cachedRevision = ledger.revision();
            cachedSecond = second;
            cachedTotal = total;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        refresh();
        extractBackground(graphics, mx, my, delta);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xDF101010);
        graphics.text(font, "RNG history", panelX + 12, panelY + 12, Hud.TITLE, true);
        graphics.horizontalLine(panelX + 12, panelX + panelWidth - 12, panelY + 84, 0xFF333333);
        drawRows(graphics, rows(snapshot.row(reward)), mx, my);
        String footer = "Detected rewards · qualifying kills only";
        graphics.text(
                font,
                font.plainSubstrByWidth(footer, panelWidth - 24),
                panelX + 12,
                panelY + panelHeight - 42,
                Graph.MUTED,
                true);
        if (my >= panelY + panelHeight - 44 && my < panelY + panelHeight - 30) {
            graphics.setTooltipForNextFrame(
                    Component.literal(
                            "Only detected loot from confirmed kills meeting their saved damage threshold is counted.\nManual loot and manually replaced rewards are excluded. These are observed quantities, not drop odds."),
                    mx,
                    my);
        }
        graphics.horizontalLine(
                panelX + 12, panelX + panelWidth - 12, panelY + panelHeight - 29, 0xFF333333);
        for (var child : children()) {
            if (child instanceof Renderable renderable) {
                renderable.extractRenderState(graphics, mx, my, delta);
            }
        }
    }

    private List<Row> rows(RngSince.Streak streak) {
        RngSince.Drop last = streak.lastDrop();
        return List.of(
                new Row(
                        "Qualifying kills",
                        Long.toString(streak.qualifiedKills()),
                        "Confirmed server kills meeting each fight's saved damage threshold."),
                new Row(
                        "Detected drops",
                        Long.toString(streak.drops()),
                        "Total detected quantity of " + reward.label() + " in this scope."),
                new Row(
                        streak.hasDrop() ? "Kills since drop" : "Kills tracked",
                        Long.toString(streak.killsSinceDrop()),
                        streak.hasDrop()
                                ? "Qualifying kills after the most recent fight containing this reward. Its own kill is excluded."
                                : "No drop in this scope. Counts qualifying kills since this scope began."),
                new Row(
                        streak.hasDrop() ? "Active since drop" : "Active tracked",
                        Format.time(streak.activeMillisSinceDrop()),
                        streak.hasDrop()
                                ? "Tracked active time after the original reward fight ended. AFK time is excluded."
                                : "Tracked active time since this scope began. AFK time is excluded."),
                new Row(
                        "Last drop",
                        last == null
                                ? "No drop in this scope"
                                : DROP_TIME.format(Instant.ofEpochMilli(last.at())),
                        last == null
                                ? "No qualifying detected reward exists in this scope. A session does not inherit drops from earlier sessions."
                                : "Fight #"
                                        + last.fightId()
                                        + " · "
                                        + reward.label()
                                        + " x"
                                        + last.count()),
                new Row(
                        "Last drop quantity",
                        last == null ? "—" : Long.toString(last.count()),
                        "Detected quantity in the latest qualifying fight containing this reward."),
                new Row(
                        "Longest dry streak",
                        Long.toString(streak.longestDryStreak()),
                        "Longest run of qualifying kills without this reward, including the current run and the run before the first drop in this scope."),
                new Row(
                        "Drops / 100 kills",
                        streak.qualifiedKills() == 0
                                ? "—"
                                : String.format(Locale.ROOT, "%.2f", streak.dropsPer100Kills()),
                        "Observed drop quantity per 100 qualifying kills. This does not predict the next kill."));
    }

    private void drawRows(GuiGraphicsExtractor graphics, List<Row> rows, int mx, int my) {
        int top = panelY + 94, bottom = panelY + panelHeight - 52;
        int visible = Math.max(1, (bottom - top) / 15);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visible)));
        graphics.enableScissor(panelX + 10, top - 1, panelX + panelWidth - 10, bottom);
        for (int index = scrollOffset;
                index < Math.min(rows.size(), scrollOffset + visible);
                index++) {
            Row row = rows.get(index);
            int y = top + (index - scrollOffset) * 15;
            String value = font.plainSubstrByWidth(row.value(), Math.max(80, panelWidth / 2));
            int valueX = panelX + panelWidth - 16 - font.width(value);
            graphics.text(
                    font,
                    font.plainSubstrByWidth(row.label(), Math.max(20, valueX - panelX - 24)),
                    panelX + 12,
                    y,
                    Graph.MUTED,
                    true);
            graphics.text(font, value, valueX, y, Hud.WHITE, true);
            if (my >= y - 1 && my < y + 13) {
                graphics.setTooltipForNextFrame(
                        Component.literal(
                                row.label() + ": " + row.value() + "\n" + row.explanation()),
                        mx,
                        my);
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

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        move(-(int) Math.signum(vertical));
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

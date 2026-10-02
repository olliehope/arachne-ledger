package dev.arachneledger.ui.screen;

import dev.arachneledger.achievement.AchievementDefinition.Category;
import dev.arachneledger.achievement.AchievementDefinition.Metric;
import dev.arachneledger.achievement.Achievements;
import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** A lifetime achievement book. Filters and scrolling only select rows, never mutate progress. */
public final class AchievementsScreen extends Screen {
    private enum Filter {
        ALL("All"),
        LOCKED("In progress"),
        EARNED("Unlocked");

        final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private static final int ROW_HEIGHT = 46;
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private Filter filter = Filter.ALL;
    private Category category;
    private int panelX, panelY, panelWidth, panelHeight, scrollOffset, visibleRows;
    private Achievements.Snapshot snapshot;
    private Ledger cachedLedger;
    private long ledgerRevision = -1, stateRevision = -1;
    private FlatButton previousButton, nextButton;

    public AchievementsScreen(Screen parent) {
        super(Component.literal("Arachne achievements"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(480, width - 20);
        panelHeight = Math.min(338, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        visibleRows = Math.max(1, (panelHeight - 103) / ROW_HEIGHT);
        refreshSnapshot();
        int filterWidth = Math.max(56, (panelWidth - 36) / 3);
        for (int index = 0; index < Filter.values().length; index++) {
            Filter option = Filter.values()[index];
            addRenderableWidget(
                    new FlatButton(
                            panelX + 12 + index * (filterWidth + 6),
                            panelY + 42,
                            filterWidth,
                            18,
                            option.label,
                            option == filter,
                            () -> {
                                filter = option;
                                scrollOffset = 0;
                                rebuildWidgets();
                            }));
        }
        addRenderableWidget(
                new FlatButton(
                        panelX + 12,
                        panelY + panelHeight - 24,
                        Math.max(92, panelWidth - 158),
                        18,
                        category == null ? "All categories" : category.label(),
                        false,
                        () -> {
                            category =
                                    category == null
                                            ? Category.values()[0]
                                            : category.ordinal() + 1 < Category.values().length
                                                    ? Category.values()[category.ordinal() + 1]
                                                    : null;
                            scrollOffset = 0;
                            rebuildWidgets();
                        }));
        previousButton =
                new FlatButton(
                        panelX + panelWidth - 134,
                        panelY + panelHeight - 24,
                        22,
                        18,
                        "^",
                        false,
                        () -> move(-1));
        nextButton =
                new FlatButton(
                        panelX + panelWidth - 108,
                        panelY + panelHeight - 24,
                        22,
                        18,
                        "v",
                        false,
                        () -> move(1));
        addRenderableWidget(previousButton);
        addRenderableWidget(nextButton);
        addRenderableWidget(
                new FlatButton(
                        panelX + panelWidth - 80,
                        panelY + panelHeight - 24,
                        68,
                        18,
                        "Back",
                        false,
                        this::onClose));
        updateScrollButtons(rows().size());
    }

    private void refreshSnapshot() {
        if (snapshot == null
                || cachedLedger != tracker.ledger
                || ledgerRevision != tracker.ledger.revision()
                || stateRevision != tracker.ledger.achievements.revision()) {
            snapshot = Achievements.snapshot(tracker.ledger, tracker.ledger.achievements);
            cachedLedger = tracker.ledger;
            ledgerRevision = tracker.ledger.revision();
            stateRevision = tracker.ledger.achievements.revision();
        }
    }

    private List<Achievements.Progress> rows() {
        return snapshot.progress().stream()
                .filter(
                        progress ->
                                category == null || progress.definition().category() == category)
                .filter(
                        progress ->
                                filter == Filter.ALL
                                        || progress.earned() == (filter == Filter.EARNED))
                .toList();
    }

    private void updateScrollButtons(int size) {
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, size - visibleRows)));
        previousButton.active = scrollOffset > 0;
        nextButton.active = scrollOffset + visibleRows < size;
    }

    private void move(int direction) {
        refreshSnapshot();
        scrollOffset += direction;
        updateScrollButtons(rows().size());
    }

    @Override
    public void tick() {
        refreshSnapshot();
        updateScrollButtons(rows().size());
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
        refreshSnapshot();
        List<Achievements.Progress> rows = rows();
        updateScrollButtons(rows.size());
        graphics.text(
                font,
                getTitle().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 12,
                panelY + 12,
                Hud.WHITE,
                true);
        String count = snapshot.earned() + " / " + snapshot.total() + " unlocked";
        graphics.text(
                font,
                count,
                panelX + panelWidth - 12 - font.width(count),
                panelY + 29,
                Graph.MUTED,
                false);
        graphics.text(font, "Lifetime progress", panelX + 12, panelY + 29, Graph.MUTED, false);
        int top = panelY + 70;
        graphics.enableScissor(
                panelX + 10, top, panelX + panelWidth - 10, top + visibleRows * ROW_HEIGHT);
        if (rows.isEmpty()) {
            graphics.text(
                    font,
                    filter == Filter.EARNED
                            ? "No achievements unlocked here yet."
                            : "No achievements in this selection.",
                    panelX + 12,
                    top + 8,
                    Graph.MUTED,
                    false);
        }
        for (int index = scrollOffset;
                index < Math.min(rows.size(), scrollOffset + visibleRows);
                index++) {
            drawRow(
                    graphics,
                    rows.get(index),
                    top + (index - scrollOffset) * ROW_HEIGHT,
                    mouseX,
                    mouseY);
        }
        graphics.disableScissor();
        if (rows.size() > visibleRows) {
            int trackHeight = visibleRows * ROW_HEIGHT;
            int thumbHeight = Math.max(8, trackHeight * visibleRows / rows.size());
            int thumbY =
                    top + (trackHeight - thumbHeight) * scrollOffset / (rows.size() - visibleRows);
            graphics.fill(
                    panelX + panelWidth - 7,
                    top,
                    panelX + panelWidth - 6,
                    top + trackHeight,
                    0xFF333333);
            graphics.fill(
                    panelX + panelWidth - 7,
                    thumbY,
                    panelX + panelWidth - 6,
                    thumbY + thumbHeight,
                    0xFF888888);
        }
    }

    private void drawRow(
            GuiGraphicsExtractor graphics,
            Achievements.Progress progress,
            int top,
            int mouseX,
            int mouseY) {
        boolean revealed = progress.revealed();
        String name = revealed ? progress.definition().title() : "Hidden achievement";
        String description =
                revealed
                        ? progress.definition().description()
                        : "Discover a rare milestone to reveal it.";
        String value = revealed ? progressText(progress) : "?";
        int left = panelX + 12, right = panelX + panelWidth - 14;
        int valueX = right - font.width(value);
        graphics.text(
                font,
                font.plainSubstrByWidth(name, Math.max(40, valueX - left - 10)),
                left,
                top + 3,
                progress.earned() ? Graph.GREEN : Hud.WHITE,
                false);
        graphics.text(
                font, value, valueX, top + 3, progress.earned() ? Graph.GREEN : Graph.MUTED, false);
        graphics.text(
                font,
                font.plainSubstrByWidth(description, right - left),
                left,
                top + 17,
                Graph.MUTED,
                false);
        graphics.fill(left, top + 32, right, top + 35, 0xFF333333);
        int fill = (int) Math.round((right - left) * (revealed ? progress.fraction() : 0));
        if (fill > 0) {
            graphics.fill(
                    left,
                    top + 32,
                    left + fill,
                    top + 35,
                    progress.earned() ? Graph.GREEN : Hud.GOLD);
        }
        if (mouseX >= left && mouseX <= right && mouseY >= top && mouseY < top + ROW_HEIGHT) {
            String tooltip = name + "\n" + description;
            if (revealed) {
                tooltip += "\n" + progressText(progress);
            }
            if (progress.earned()) {
                tooltip +=
                        "\n"
                                + (progress.earnedAt() == 0
                                        ? "Imported from saved history"
                                        : "Unlocked "
                                                + DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
                                                        .withZone(ZoneId.systemDefault())
                                                        .format(
                                                                Instant.ofEpochMilli(
                                                                        progress.earnedAt())));
            }
            graphics.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY);
        }
    }

    private static String progressText(Achievements.Progress progress) {
        if (progress.earned()) {
            return "Unlocked";
        }
        if (progress.definition().metric() == Metric.FASTEST_KILL) {
            return progress.value() <= 0
                    ? "No timed kill"
                    : String.format(
                            Locale.ROOT,
                            "%.1fs / %.0fs",
                            progress.value() / 1_000.0,
                            progress.definition().target() / 1_000.0);
        }
        return String.format(
                Locale.ROOT, "%,d / %,d", progress.value(), progress.definition().target());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= panelX
                && mouseX < panelX + panelWidth
                && mouseY >= panelY + 66
                && mouseY < panelY + panelHeight - 28
                && vertical != 0) {
            move(-(int) Math.signum(vertical));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
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

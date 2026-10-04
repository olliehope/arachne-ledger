package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.config.SessionGoals;
import dev.arachneledger.ledger.GoalProgress;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A personal session target, edited as one draft with no change to journal data. */
public final class GoalsScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private final SessionGoals draft = tracker.config.goals.copy();
    private int x, y, panelWidth, panelHeight, tabY, inputY, toggleY;
    private EditBox input;
    private String raw, note = "";

    public GoalsScreen(Screen parent) {
        super(Component.literal("Session goal"));
        this.parent = parent;
        raw = targetText();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(380, width - 24);
        panelHeight = Math.min(224, height - 20);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        boolean compact = panelHeight < 205;
        tabY = compact ? 35 : 43;
        inputY = compact ? 70 : 84;
        toggleY = compact ? 96 : 111;
        String[] labels = {"Off", "Kills", "Time", "Profit"};
        int tabWidth = (panelWidth - 36) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            SessionGoals.Kind kind = SessionGoals.Kind.values()[i];
            addRenderableWidget(
                    new FlatButton(
                            x + 12 + i * (tabWidth + 4),
                            y + tabY,
                            tabWidth,
                            18,
                            labels[i],
                            draft.kind == kind,
                            () -> select(kind)));
        }
        input =
                new EditBox(
                        font, x + 12, y + inputY, panelWidth - 24, 20, Component.literal("Target"));
        input.setMaxLength(24);
        input.setValue(raw);
        input.setResponder(value -> raw = value);
        input.active = draft.kind != SessionGoals.Kind.NONE;
        addRenderableWidget(input);
        addRenderableWidget(
                new FlatButton(
                        x + 12,
                        y + toggleY,
                        panelWidth - 24,
                        18,
                        "Completion chat: " + (draft.notify ? "On" : "Off"),
                        draft.notify,
                        () -> {
                            draft.notify = !draft.notify;
                            rebuildWidgets();
                        }));
        int size = (panelWidth - 28) / 2;
        addRenderableWidget(
                new FlatButton(
                        x + 12, y + panelHeight - 24, size, 18, "Save", true, this::saveDraft));
        addRenderableWidget(
                new FlatButton(
                        x + 16 + size,
                        y + panelHeight - 24,
                        size,
                        18,
                        "Cancel",
                        false,
                        this::onClose));
    }

    private String targetText() {
        return switch (draft.kind) {
            case KILLS -> Long.toString(draft.kills);
            case ACTIVE_TIME -> Format.time(draft.activeMillis);
            case PROFIT ->
                    java.math.BigDecimal.valueOf(draft.profit).stripTrailingZeros().toPlainString();
            case NONE -> "";
        };
    }

    private void select(SessionGoals.Kind kind) {
        try {
            captureTarget();
        } catch (RuntimeException ignored) {
        }
        draft.kind = kind;
        raw = targetText();
        note = "";
        rebuildWidgets();
    }

    private void captureTarget() {
        switch (draft.kind) {
            case KILLS -> {
                long value = Long.parseLong(raw.trim().replace(",", ""));
                if (value < 1 || value > 1_000_000)
                    throw new IllegalArgumentException("Use 1–1,000,000 kills.");
                draft.kills = value;
            }
            case ACTIVE_TIME -> draft.activeMillis = parseTime(raw);
            case PROFIT -> {
                double value = Config.amount(raw);
                if (value < 1) throw new IllegalArgumentException("Use at least 1 coin.");
                draft.profit = value;
            }
            case NONE -> {}
        }
    }

    public static long parseTime(String value) {
        try {
            String[] parts = value.trim().split(":", -1);
            if (parts.length != 3 || !value.trim().matches("\\d{1,4}:\\d{2}:\\d{2}"))
                throw new IllegalArgumentException();
            long hours = Long.parseLong(parts[0]),
                    minutes = Long.parseLong(parts[1]),
                    seconds = Long.parseLong(parts[2]);
            long result = (hours * 3600 + minutes * 60 + seconds) * 1000;
            if (minutes > 59 || seconds > 59 || result < 60_000 || result > 3_600_000_000L)
                throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Use HH:MM:SS, from 00:01:00 to 1000:00:00.");
        }
    }

    private void saveDraft() {
        if (!tracker.error.isEmpty()) {
            note = "Storage error; see the Minecraft log.";
            return;
        }
        try {
            captureTarget();
            draft.validate();
            SessionGoals previous = tracker.config.goals;
            tracker.config.goals = draft.copy();
            tracker.saveConfig();
            if (!tracker.error.isEmpty()) {
                tracker.config.goals = previous;
                note = "Goal could not be saved.";
            } else onClose();
        } catch (RuntimeException invalid) {
            note = invalid.getMessage();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        extractBackground(graphics, mx, my, delta);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xDF101010);
        // Draw controls after the panel fill, using the same child extraction as other screens.
        graphics.text(font, "Session goal", x + 12, y + 12, Hud.TITLE, true);
        if (panelHeight >= 205)
            graphics.text(
                    font,
                    "This session · Save applies your target",
                    x + 12,
                    y + 29,
                    Graph.MUTED,
                    false);
        String unit =
                draft.kind == SessionGoals.Kind.ACTIVE_TIME
                        ? "Target · HH:MM:SS"
                        : draft.kind == SessionGoals.Kind.PROFIT
                                ? "Target · coins net profit"
                                : "Target · tracked kills";
        graphics.text(font, unit, x + 12, y + inputY - 14, Graph.MUTED, false);
        GoalProgress current = GoalProgress.calculate(tracker.ledger, tracker.config.goals);
        String status =
                current.enabled()
                        ? "Saved goal: " + Math.round(current.fraction() * 100) + "%"
                        : "No goal active";
        if (panelHeight >= 205)
            graphics.text(
                    font,
                    status,
                    x + 12,
                    y + 141,
                    current.reached() ? Graph.GREEN : Graph.MUTED,
                    false);
        graphics.text(
                font,
                font.plainSubstrByWidth(note, panelWidth - 24),
                x + 12,
                y + panelHeight - 42,
                Hud.TITLE,
                false);
        for (var child : children())
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable)
                renderable.extractRenderState(graphics, mx, my, delta);
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

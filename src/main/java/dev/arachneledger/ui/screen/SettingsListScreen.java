package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
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

/** Shared scrolling rows for preference pages; each page supplies its own settings and actions. */
abstract class SettingsListScreen extends Screen {
    protected record AdditionalAction(
            String value, boolean active, String tooltip, Runnable action) {}

    protected record Option(
            String label,
            String value,
            boolean selected,
            boolean active,
            String tooltip,
            Runnable action,
            AdditionalAction additionalAction) {
        protected Option(
                String label,
                String value,
                boolean selected,
                boolean active,
                String tooltip,
                Runnable action) {
            this(label, value, selected, active, tooltip, action, null);
        }
    }

    protected final Screen parent;
    protected final Tracker tracker = ArachneLedger.tracker;
    protected int panelX, panelY, panelWidth, panelHeight;
    protected String note = "";
    private final String hint;
    private int scrollOffset, visibleRows;
    private List<Option> rows = List.of();

    protected SettingsListScreen(Screen parent, String title, String hint) {
        super(Component.literal(title));
        this.parent = parent;
        this.hint = hint;
    }

    protected abstract List<Option> options();

    protected void captureDrafts() {}

    protected void addHeaderControls() {}

    @Override
    protected final void init() {
        captureDrafts();
        panelWidth = Math.min(460, width - 20);
        panelHeight = Math.min(250, height - 12);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        rows = options();
        visibleRows = Math.max(1, Math.min(6, (panelHeight - 112) / 22));
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visibleRows)));
        addHeaderControls();
        addScrollButtons();
        for (int index = scrollOffset;
                index < Math.min(rows.size(), scrollOffset + visibleRows);
                index++) {
            Option row = rows.get(index);
            var button =
                    new FlatButton(
                            panelX + panelWidth - 120,
                            panelY + 67 + (index - scrollOffset) * 22,
                            row.additionalAction() == null ? 104 : 50,
                            20,
                            row.value(),
                            row.selected(),
                            row.action());
            button.active = row.active();
            button.setTooltip(Tooltip.create(Component.literal(row.tooltip())));
            addRenderableWidget(button);
            if (row.additionalAction() != null) {
                AdditionalAction action = row.additionalAction();
                var additional =
                        new FlatButton(
                                panelX + panelWidth - 66,
                                panelY + 67 + (index - scrollOffset) * 22,
                                50,
                                20,
                                action.value(),
                                false,
                                action.action());
                additional.active = action.active();
                additional.setTooltip(Tooltip.create(Component.literal(action.tooltip())));
                addRenderableWidget(additional);
            }
        }
        addFooterControls();
    }

    protected void addFooterControls() {
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

    private void addScrollButtons() {
        if (rows.size() <= visibleRows) {
            return;
        }
        var up =
                new FlatButton(
                        panelX + panelWidth - 60, panelY + 43, 20, 18, "^", false, () -> move(-1));
        up.active = scrollOffset > 0;
        up.setTooltip(Tooltip.create(Component.literal("Scroll up")));
        addRenderableWidget(up);
        var down =
                new FlatButton(
                        panelX + panelWidth - 36, panelY + 43, 20, 18, "v", false, () -> move(1));
        down.active = scrollOffset < rows.size() - visibleRows;
        down.setTooltip(Tooltip.create(Component.literal("Scroll down")));
        addRenderableWidget(down);
    }

    protected final Option toggle(String label, boolean enabled, String tip, Runnable action) {
        return new Option(
                label,
                enabled ? "On" : "Off",
                enabled,
                tracker.error.isEmpty(),
                tip,
                () -> {
                    action.run();
                    changed();
                });
    }

    protected void changed() {
        tracker.saveConfig();
        if (!tracker.error.isEmpty()) {
            note = "Storage error; settings were not saved. See the Minecraft log.";
        }
        rebuildWidgets();
    }

    protected final void resetScroll() {
        scrollOffset = 0;
    }

    private void move(int direction) {
        int next = Math.max(0, Math.min(scrollOffset + direction, rows.size() - visibleRows));
        if (next != scrollOffset) {
            scrollOffset = next;
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= panelX
                && mouseX < panelX + panelWidth
                && mouseY >= panelY + 43
                && mouseY < panelY + panelHeight - 40
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
                getTitle().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                panelX + 16,
                panelY + 12,
                Hud.WHITE,
                true);
        graphics.text(
                font,
                font.plainSubstrByWidth(hint, panelWidth - 32),
                panelX + 16,
                panelY + 30,
                Graph.MUTED,
                false);
        for (int index = scrollOffset;
                index < Math.min(rows.size(), scrollOffset + visibleRows);
                index++) {
            Option row = rows.get(index);
            int rowY = panelY + 73 + (index - scrollOffset) * 22;
            graphics.text(
                    font,
                    font.plainSubstrByWidth(row.label(), panelWidth - 148),
                    panelX + 16,
                    rowY,
                    Hud.WHITE,
                    false);
            if (mouseX >= panelX + 16
                    && mouseX < panelX + panelWidth - 124
                    && mouseY >= rowY - 6
                    && mouseY < rowY + 14) {
                graphics.setTooltipForNextFrame(Component.literal(row.tooltip()), mouseX, mouseY);
            }
        }
        String feedback = tracker.error.isEmpty() ? note : "Storage error; see the Minecraft log.";
        graphics.text(
                font,
                font.plainSubstrByWidth(feedback, panelWidth - 32),
                panelX + 16,
                panelY + panelHeight - 37,
                Hud.TITLE,
                false);
    }

    @Override
    public final boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

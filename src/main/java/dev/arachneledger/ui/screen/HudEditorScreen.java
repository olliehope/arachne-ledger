package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
import dev.arachneledger.config.HudPreferences;
import dev.arachneledger.config.HudPreset;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Edits the real viewport-relative HUD position, including from the title screen. */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private final Tracker tracker = ArachneLedger.tracker;
    private final Config draft = new Config();
    private final Config target;
    private boolean dragging;
    private double dragOffsetX, dragOffsetY;
    private int toolbarX, toolbarY, toolbarWidth;
    private FlatButton hudOptions, cancel;
    private String resizeHint;

    public HudEditorScreen(Screen parent) {
        this(parent, ArachneLedger.tracker.config);
    }

    HudEditorScreen(Screen parent, Config target) {
        super(Component.literal("Edit Arachne HUD"));
        this.parent = parent;
        this.target = target;
        HudPreset.capture("Draft", target).apply(draft);
        draft.hudPresets = HudPreset.normalize(target.hudPresets);
    }

    @Override
    protected void init() {
        toolbarWidth = Math.min(440, width - 12);
        toolbarX = (width - toolbarWidth) / 2;
        toolbarY = height - 52;
        resizeHint = Math.round(draft.hudScale * 100) + "% · drag to move · scroll to resize";
        int buttonWidth = (toolbarWidth - 28) / 4;
        button(
                0,
                0,
                buttonWidth,
                draft.hud ? "HUD: on" : "HUD: off",
                "Show or hide the live overlay.",
                () -> {
                    draft.hud = !draft.hud;
                    changed();
                });
        button(
                1,
                0,
                buttonWidth,
                draft.hudView == Config.View.GRAPH ? "Graph" : draft.hudPreferences.layout.label(),
                "Cycle Minimal, Classic, Split, Loot ledger and Graph. Save applies the draft.",
                () -> {
                    cycleView();
                    rebuildWidgets();
                });
        button(
                2,
                0,
                buttonWidth,
                draft.hudAlwaysShow ? "SkyBlock" : "Arena only",
                "Show while in any SkyBlock area, or only when Arachne tracking is active.",
                () -> {
                    draft.hudAlwaysShow = !draft.hudAlwaysShow;
                    changed();
                });
        button(
                3,
                0,
                buttonWidth,
                draft.hudPreferences.resolvedStyle(draft.hudBackground).label(),
                "Cycle Text only, Soft panel and Border without changing your rows.",
                () -> {
                    draft.hudPreferences.cycleStyle(draft.hudBackground);
                    draft.hudBackground =
                            draft.hudPreferences.style != HudPreferences.Style.TEXT_ONLY;
                    changed();
                });
        button(
                0,
                1,
                buttonWidth,
                "Smaller",
                "Decrease overlay size. You can also scroll over the overlay.",
                () -> scale(-.05));
        button(
                1,
                1,
                buttonWidth,
                "Larger",
                "Increase overlay size. It automatically fits the screen.",
                () -> scale(.05));
        button(
                2,
                1,
                buttonWidth,
                "Reset",
                "Reset HUD position and size.",
                () -> {
                    draft.corner = 0;
                    draft.hudX = -1;
                    draft.hudY = -1;
                    draft.hudScale = 1;
                    changed();
                });
        button(3, 1, buttonWidth, "Save", "Save this HUD layout and return.", this::saveDraft);
        hudOptions =
                new FlatButton(
                        toolbarX + 8,
                        toolbarY - 22,
                        84,
                        18,
                        "HUD options",
                        false,
                        () -> minecraft.setScreen(new HudOptionsScreen(this)));
        hudOptions.setTooltip(
                Tooltip.create(Component.literal("Choose visible rows, items and text layouts.")));
        addRenderableWidget(hudOptions);
        cancel =
                new FlatButton(
                        toolbarX + toolbarWidth - 92,
                        toolbarY - 22,
                        84,
                        18,
                        "Cancel",
                        false,
                        this::onClose);
        addRenderableWidget(cancel);
    }

    private void button(
            int column, int row, int buttonWidth, String label, String tip, Runnable action) {
        var button =
                new FlatButton(
                        toolbarX + 8 + column * (buttonWidth + 4),
                        toolbarY + 14 + row * 18,
                        buttonWidth,
                        17,
                        label,
                        false,
                        action);
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        addRenderableWidget(button);
    }

    private void changed() {
        rebuildWidgets();
    }

    private void scale(double amount) {
        draft.hudScale = Math.max(.65, Math.min(1.6, draft.hudScale + amount));
        changed();
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        Hud.drawPanel(graphics, tracker, draft, Hud.bounds(draft, width, height), true);
        graphics.fill(toolbarX, toolbarY, toolbarX + toolbarWidth, height - 2, 0xDF101010);
        graphics.text(
                font,
                font.plainSubstrByWidth(resizeHint, toolbarWidth - 16),
                toolbarX + 8,
                toolbarY + 3,
                Graph.MUTED,
                true);
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (hudOptions != null && hudOptions.isMouseOver(event.x(), event.y())) {
            return super.mouseClicked(event, doubleClick);
        }
        if (cancel != null && cancel.isMouseOver(event.x(), event.y())) {
            return super.mouseClicked(event, doubleClick);
        }
        if (event.y() >= toolbarY && event.x() >= toolbarX && event.x() < toolbarX + toolbarWidth) {
            return super.mouseClicked(event, doubleClick);
        }
        Hud.Bounds hudBounds = Hud.bounds(draft, width, height);
        if (event.button() == 0 && hudBounds.contains(event.x(), event.y())) {
            dragging = true;
            // Keep the grabbed point under the cursor instead of snapping the HUD's corner to it.
            dragOffsetX = event.x() - hudBounds.x();
            dragOffsetY = event.y() - hudBounds.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            Hud.move(draft, width, height, event.x() - dragOffsetX, event.y() - dragOffsetY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (Hud.bounds(draft, width, height).contains(mouseX, mouseY)) {
            scale(Math.signum(vertical) * .05);
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

    Config draftConfig() {
        return draft;
    }

    private void cycleView() {
        if (draft.hudView == Config.View.GRAPH) {
            draft.hudView = Config.View.DETAILED;
            draft.hudPreferences.layout = HudPreferences.Layout.MINIMAL;
        } else {
            switch (draft.hudPreferences.layout) {
                case MINIMAL -> draft.hudPreferences.layout = HudPreferences.Layout.CLASSIC;
                case CLASSIC -> draft.hudPreferences.layout = HudPreferences.Layout.SPLIT;
                case SPLIT -> draft.hudPreferences.layout = HudPreferences.Layout.LOOT;
                case LOOT -> draft.hudView = Config.View.GRAPH;
            }
        }
    }

    private void saveDraft() {
        if (!tracker.error.isEmpty()) return;
        HudPreset previous = HudPreset.capture("Previous", target);
        var previousPresets = target.hudPresets;
        HudPreset.capture("Draft", draft).apply(target);
        target.hudPresets = HudPreset.normalize(draft.hudPresets);
        if (target == tracker.config) tracker.saveConfig();
        if (!tracker.error.isEmpty()) {
            previous.apply(target);
            target.hudPresets = previousPresets;
            rebuildWidgets();
        } else {
            onClose();
        }
    }
}

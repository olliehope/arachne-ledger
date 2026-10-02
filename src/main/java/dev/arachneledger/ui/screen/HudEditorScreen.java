package dev.arachneledger.ui.screen;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.config.Config;
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
    private boolean dragging;
    private double dragOffsetX, dragOffsetY;
    private int toolbarX, toolbarY, toolbarWidth;
    private FlatButton hudOptions, graphOptions;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Edit Arachne HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        toolbarWidth = Math.min(440, width - 12);
        toolbarX = (width - toolbarWidth) / 2;
        toolbarY = height - 52;
        int buttonWidth = (toolbarWidth - 28) / 4;
        button(
                0,
                0,
                buttonWidth,
                tracker.config.hud ? "HUD: on" : "HUD: off",
                "Show or hide the live overlay.",
                () -> {
                    tracker.config.hud = !tracker.config.hud;
                    changed();
                });
        button(
                1,
                0,
                buttonWidth,
                tracker.config.hudView == Config.View.GRAPH
                        ? "Graph"
                        : tracker.config.hudPreferences.layout.label(),
                "Cycle Minimal, Classic, Split and Graph while preserving individual choices. The dashboard tab stays unchanged.",
                () -> {
                    tracker.cycleView();
                    rebuildWidgets();
                });
        button(
                2,
                0,
                buttonWidth,
                tracker.config.hudAlwaysShow ? "SkyBlock" : "Arena only",
                "Show while in any SkyBlock area, or only when Arachne tracking is active.",
                () -> {
                    tracker.config.hudAlwaysShow = !tracker.config.hudAlwaysShow;
                    changed();
                });
        button(
                3,
                0,
                buttonWidth,
                tracker.config.hudBackground ? "Background" : "Text only",
                "Toggle a subtle black background behind the overlay.",
                () -> {
                    tracker.config.hudBackground = !tracker.config.hudBackground;
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
                    tracker.config.corner = 0;
                    tracker.config.hudX = -1;
                    tracker.config.hudY = -1;
                    tracker.config.hudScale = 1;
                    changed();
                });
        button(3, 1, buttonWidth, "Done", "Save the HUD layout and return.", this::onClose);
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
        graphOptions = null;
        if (tracker.config.hudView == Config.View.GRAPH) {
            graphOptions =
                    new FlatButton(
                            toolbarX + toolbarWidth - 92,
                            toolbarY - 22,
                            84,
                            18,
                            "Graph options",
                            false,
                            () -> minecraft.setScreen(new GraphOptionsScreen(this)));
            addRenderableWidget(graphOptions);
        }
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
        tracker.saveConfig();
        rebuildWidgets();
    }

    private void scale(double amount) {
        tracker.config.hudScale = Math.max(.65, Math.min(1.6, tracker.config.hudScale + amount));
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
        Hud.drawPanel(graphics, tracker, Hud.bounds(tracker.config, width, height), true);
        graphics.fill(toolbarX, toolbarY, toolbarX + toolbarWidth, height - 2, 0xDF101010);
        String hint =
                "Drag to move · scroll to resize · "
                        + Math.round(tracker.config.hudScale * 100)
                        + "%";
        graphics.text(
                font,
                font.plainSubstrByWidth(hint, toolbarWidth - 16),
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
        if (graphOptions != null && graphOptions.isMouseOver(event.x(), event.y())) {
            return super.mouseClicked(event, doubleClick);
        }
        if (event.y() >= toolbarY && event.x() >= toolbarX && event.x() < toolbarX + toolbarWidth) {
            return super.mouseClicked(event, doubleClick);
        }
        Hud.Bounds hudBounds = Hud.bounds(tracker.config, width, height);
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
            Hud.move(
                    tracker.config,
                    width,
                    height,
                    event.x() - dragOffsetX,
                    event.y() - dragOffsetY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            tracker.saveConfig();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (Hud.bounds(tracker.config, width, height).contains(mouseX, mouseY)) {
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
        tracker.saveConfig();
        minecraft.setScreen(parent);
    }
}

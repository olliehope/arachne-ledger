package dev.arachneledger.ui.screen;

import dev.arachneledger.config.HudPreset;
import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Graph;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Small naming dialog; its callback edits the parent draft rather than live configuration. */
final class HudPresetNameScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> accept;
    private String name;
    private EditBox input;
    private int x, y, panelWidth;
    private String note = "";

    HudPresetNameScreen(Screen parent, String initialName, Consumer<String> accept) {
        super(Component.literal("Name this HUD"));
        this.parent = parent;
        this.accept = accept;
        this.name = initialName;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(300, width - 24);
        x = (width - panelWidth) / 2;
        y = (height - 112) / 2;
        input =
                new EditBox(
                        font,
                        x + 12,
                        y + 33,
                        panelWidth - 24,
                        20,
                        Component.literal("Preset name"));
        input.setMaxLength(24);
        input.setValue(name);
        input.setResponder(value -> name = value);
        addRenderableWidget(input);
        setInitialFocus(input);
        int buttonWidth = (panelWidth - 28) / 2;
        addRenderableWidget(
                new FlatButton(
                        x + 12,
                        y + 78,
                        buttonWidth,
                        20,
                        "Add",
                        true,
                        () -> {
                            String clean = HudPreset.cleanName(name);
                            if (clean.isBlank()) {
                                note = "Enter a name first.";
                                return;
                            }
                            minecraft.setScreen(parent);
                            accept.accept(clean);
                        }));
        addRenderableWidget(
                new FlatButton(
                        x + 16 + buttonWidth,
                        y + 78,
                        buttonWidth,
                        20,
                        "Cancel",
                        false,
                        this::onClose));
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
        graphics.text(font, getTitle(), x + 12, y + 12, Hud.TITLE, false);
        graphics.text(font, note, x + 12, y + 61, Graph.MUTED, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

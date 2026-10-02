package dev.arachneledger.ui.screen;

import dev.arachneledger.ui.FlatButton;
import dev.arachneledger.ui.Hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Validated tracking preferences. Drafts are independent of widgets and recorded fights. */
public final class BehaviorSettingsScreen extends SettingsListScreen {
    private String damageInput;
    private EditBox minimumDamageField;
    private boolean killChat, rngTitles, rngValue, scavengerCoins;

    public BehaviorSettingsScreen(Screen parent) {
        super(parent, "Tracking settings", "Changes apply to future fights and rewards.");
        damageInput = Long.toString(tracker.config.minimumDamage);
        killChat = tracker.config.killChat;
        rngTitles = tracker.config.rngTitles;
        rngValue = tracker.config.rngValue;
        scavengerCoins = tracker.config.scavengerCoins;
    }

    @Override
    protected void captureDrafts() {
        if (minimumDamageField != null) {
            damageInput = minimumDamageField.getValue();
        }
    }

    @Override
    protected void addHeaderControls() {
        minimumDamageField =
                new EditBox(
                        font,
                        panelX + panelWidth - 172,
                        panelY + 43,
                        96,
                        20,
                        Component.literal("Minimum damage"));
        minimumDamageField.setMaxLength(24);
        minimumDamageField.setValue(damageInput);
        minimumDamageField.setResponder(
                value -> {
                    damageInput = value;
                    note = "Unsaved changes.";
                });
        minimumDamageField.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Count a kill only after your reported damage reaches this threshold. Use a whole number from 1 to 1 trillion.")));
        addRenderableWidget(minimumDamageField);
    }

    @Override
    protected List<Option> options() {
        return List.of(
                toggle(
                        "Kill chat summary",
                        killChat,
                        "Print kill time, loot, costs and net profit after a counted kill.",
                        () -> killChat = !killChat),
                toggle(
                        "Rare drop titles",
                        rngTitles,
                        "Show a rarity-colored title for Tarantula pets and Arachne Fangs.",
                        () -> rngTitles = !rngTitles),
                toggle(
                        "Rare drop value",
                        rngValue,
                        "Show the recorded value beside a priced rare drop. Unpriced drops show only the item name.",
                        () -> rngValue = !rngValue),
                toggle(
                        "Scavenger coins",
                        scavengerCoins,
                        "Record the yellow purse gain shown during Arachne tracking. Turning this off does not remove previously recorded coins.",
                        () -> scavengerCoins = !scavengerCoins));
    }

    @Override
    protected void changed() {
        // A toggle is still a draft: invalid damage must not cause a partial settings save.
        note = "Unsaved changes.";
        rebuildWidgets();
    }

    @Override
    protected void addFooterControls() {
        int buttonWidth = (panelWidth - 36) / 2;
        var save =
                new FlatButton(
                        panelX + 16,
                        panelY + panelHeight - 24,
                        buttonWidth,
                        20,
                        "Save",
                        true,
                        this::saveDraft);
        save.active = tracker.error.isEmpty();
        addRenderableWidget(save);
        addRenderableWidget(
                new FlatButton(
                        panelX + 20 + buttonWidth,
                        panelY + panelHeight - 24,
                        buttonWidth,
                        20,
                        "Back",
                        false,
                        this::onClose));
    }

    private void saveDraft() {
        captureDrafts();
        long damage;
        try {
            damage = Long.parseLong(damageInput.trim().replace(",", ""));
            if (damage < 1 || damage > 1_000_000_000_000L) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            note = "Damage must be a whole number from 1 to 1 trillion.";
            return;
        }
        if (!tracker.error.isEmpty()) {
            note = "Storage error; settings were not saved. See the Minecraft log.";
            return;
        }
        tracker.config.minimumDamage = damage;
        tracker.config.killChat = killChat;
        tracker.config.rngTitles = rngTitles;
        tracker.config.rngValue = rngValue;
        tracker.config.scavengerCoins = scavengerCoins;
        tracker.saveConfig();
        note =
                tracker.error.isEmpty()
                        ? "Saved. Applies to future fights and rewards."
                        : "Storage error; settings were not saved. See the Minecraft log.";
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(
                font,
                font.plainSubstrByWidth("Minimum damage", panelWidth - 196),
                panelX + 16,
                panelY + 49,
                Hud.WHITE,
                false);
    }
}

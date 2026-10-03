package dev.arachneledger.ui.screen;

import dev.arachneledger.config.FarmingPreferences;
import dev.arachneledger.ui.FlatButton;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Farming cues are an independent draft; changing their presentation never edits the ledger. */
public final class FarmingSettingsScreen extends SettingsListScreen {
    private final FarmingPreferences draft;
    private int selectedTab;

    public FarmingSettingsScreen(Screen parent) {
        super(parent, "Farming cues", "Save applies all tabs. Back discards your changes.");
        draft = tracker.config.farming.copy();
        draft.validate();
    }

    @Override
    protected void addHeaderControls() {
        String[] tabs = {"Pedestal", "Alerts", "Chat"};
        int tabWidth = Math.min(70, (panelWidth - 96) / tabs.length);
        for (int index = 0; index < tabs.length; index++) {
            int tab = index;
            addRenderableWidget(
                    new FlatButton(
                            panelX + 16 + index * (tabWidth + 3),
                            panelY + 43,
                            tabWidth,
                            18,
                            tabs[index],
                            selectedTab == index,
                            () -> {
                                selectedTab = tab;
                                resetScroll();
                                rebuildWidgets();
                            }));
        }
    }

    @Override
    protected List<Option> options() {
        return switch (selectedTab) {
            case 1 -> alertOptions();
            case 2 -> chatOptions();
            default -> pedestalOptions();
        };
    }

    private List<Option> pedestalOptions() {
        return List.of(
                toggle(
                        "Spawn countdown",
                        draft.pedestalTimer,
                        "Show an estimated spawn countdown above Arachne's pedestal after a Crystal or Calling placement. Actual spawn chat ends the estimate; reaching zero does not record a spawn.",
                        () -> draft.pedestalTimer = !draft.pedestalTimer),
                toggle(
                        "Fight duration",
                        draft.pedestalFightTime,
                        "Show elapsed fight time at the pedestal after the server confirms Arachne's spawn. This is display only.",
                        () -> draft.pedestalFightTime = !draft.pedestalFightTime),
                toggle(
                        "Through walls",
                        draft.pedestalThroughWalls,
                        "Draw pedestal text through blocks. Turning this off uses normal world visibility.",
                        () -> draft.pedestalThroughWalls = !draft.pedestalThroughWalls),
                toggle(
                        "Adaptive Crystal timing",
                        draft.adaptiveCrystalTimer,
                        "Sample the first nearby ritual dust burst after three seconds to estimate the Crystal delay. Without a sample, use your Crystal fallback. Calling timing uses its own fallback.",
                        () -> draft.adaptiveCrystalTimer = !draft.adaptiveCrystalTimer),
                stepper(
                        "Crystal fallback · " + draft.crystalSpawnSeconds + "s",
                        draft.crystalSpawnSeconds,
                        10,
                        60,
                        1,
                        "Estimated Crystal delay, in seconds. Used when adaptive timing is off or no nearby ritual particles were observed. It never changes spawn detection.",
                        value -> draft.crystalSpawnSeconds = value),
                stepper(
                        "Calling fallback · " + draft.callingSpawnSeconds + "s",
                        draft.callingSpawnSeconds,
                        10,
                        60,
                        1,
                        "Estimated Calling delay, in seconds. The completed 4/4 Calling placement starts the countdown; repeated relays do not restart it.",
                        value -> draft.callingSpawnSeconds = value),
                stepper(
                        "Label scale · "
                                + String.format(Locale.ROOT, "%.0f%%", draft.pedestalScale * 100),
                        (int) Math.round(draft.pedestalScale * 10),
                        5,
                        20,
                        1,
                        "Size of the world label, from 50% to 200%. The regular profit HUD has its own scale.",
                        value -> draft.pedestalScale = value / 10.0),
                stepper(
                        "Label range · " + draft.pedestalRange + " blocks",
                        draft.pedestalRange,
                        16,
                        128,
                        8,
                        "Maximum distance from the pedestal for drawing its label, from 16 to 128 blocks.",
                        value -> draft.pedestalRange = value));
    }

    private List<Option> alertOptions() {
        return List.of(
                toggle(
                        "Spawn title",
                        draft.spawnTitle,
                        "Show a local spawn notification after Arachne's actual spawn is confirmed. An estimated countdown reaching zero does not trigger it.",
                        () -> draft.spawnTitle = !draft.spawnTitle),
                toggle(
                        "Spawn sound",
                        draft.spawnSound,
                        "Play a local sound on a confirmed Arachne spawn.",
                        () -> draft.spawnSound = !draft.spawnSound),
                toggle(
                        "Rare drop sound",
                        draft.rngSound,
                        "Play a local sound for a newly recorded Tarantula Pet or Arachne Fang. Repeated labels do not replay it.",
                        () -> draft.rngSound = !draft.rngSound),
                toggle(
                        "Achievement title",
                        draft.achievementTitle,
                        "Show a local overlay for newly earned achievements. Existing history and upgrades backfill silently.",
                        () -> draft.achievementTitle = !draft.achievementTitle),
                toggle(
                        "Achievement sound",
                        draft.achievementSound,
                        "Play a local sound for newly earned achievements. Previously earned milestones are not replayed.",
                        () -> draft.achievementSound = !draft.achievementSound));
    }

    private List<Option> chatOptions() {
        return List.of(
                toggle(
                        "Rare drop chat",
                        draft.rngChat,
                        "Print a rarity-colored pet or fang notification with its recorded value and trusted kill/time interval. The Rare drop value setting under Tracking also applies here.",
                        () -> draft.rngChat = !draft.rngChat),
                toggle(
                        "Compact kill summary",
                        draft.compactKillChat,
                        "Keep kill chat to the kill number, fight time and profit. Hover it for income, costs, damage, Scavenger coins and missing values. Enable Kill chat summary under Tracking.",
                        () -> draft.compactKillChat = !draft.compactKillChat));
    }

    private Option stepper(
            String label,
            int value,
            int minimum,
            int maximum,
            int step,
            String tooltip,
            IntConsumer change) {
        boolean healthy = tracker.error.isEmpty();
        return new Option(
                label,
                "-",
                false,
                healthy && value > minimum,
                "Decrease. " + tooltip,
                () -> {
                    change.accept(Math.max(minimum, value - step));
                    changed();
                },
                new AdditionalAction(
                        "+",
                        healthy && value < maximum,
                        "Increase. " + tooltip,
                        () -> {
                            change.accept(Math.min(maximum, value + step));
                            changed();
                        }));
    }

    @Override
    protected void changed() {
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
        if (!tracker.error.isEmpty()) {
            note = "Storage error; settings were not saved. See the Minecraft log.";
            return;
        }
        FarmingPreferences saved = tracker.config.farming;
        FarmingPreferences replacement = draft.copy();
        replacement.validate();
        tracker.config.farming = replacement;
        tracker.saveConfig();
        if (!tracker.error.isEmpty()) {
            tracker.config.farming = saved;
            note = "Storage error; settings were not saved. See the Minecraft log.";
        } else {
            note = "Saved farming cues.";
        }
        rebuildWidgets();
    }
}

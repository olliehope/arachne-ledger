package dev.arachneledger.ui.screen;

import dev.arachneledger.config.Config;
import dev.arachneledger.config.HudPreferences;
import dev.arachneledger.config.HudPreset;
import dev.arachneledger.config.HudRowOrder;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.ui.FlatButton;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Individual text-HUD choices. Visibility filters never alter the recorded ledger. */
public final class HudOptionsScreen extends SettingsListScreen {
    private int selectedTab;
    private final Config draft = new Config();
    private final Config target;

    public HudOptionsScreen(Screen parent) {
        super(parent, "HUD options", "Save applies all tabs. Cancel discards changes.");
        target = parent instanceof HudEditorScreen editor ? editor.draftConfig() : tracker.config;
        HudPreset.capture("Draft", target).apply(draft);
        draft.hudPresets = HudPreset.normalize(target.hudPresets);
    }

    @Override
    protected void addHeaderControls() {
        String[] labels = {"Rows", "Items", "Display", "Order", "Saved"};
        int tabWidth = Math.min(58, (panelWidth - 96) / labels.length);
        for (int index = 0; index < labels.length; index++) {
            int tab = index;
            addRenderableWidget(
                    new FlatButton(
                            panelX + 16 + index * (tabWidth + 3),
                            panelY + 43,
                            tabWidth,
                            18,
                            labels[index],
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
            case 1 -> itemOptions();
            case 2 -> displayOptions();
            case 3 -> orderOptions();
            case 4 -> savedOptions();
            default -> rowOptions();
        };
    }

    private List<Option> rowOptions() {
        HudPreferences preferences = draft.hudPreferences;
        return List.of(
                toggle(
                        "Title",
                        preferences.showTitle,
                        "Show the text HUD's Arachne title.",
                        () -> preferences.showTitle = !preferences.showTitle),
                toggle(
                        "Total profit",
                        preferences.showTotalProfit,
                        "Recorded income minus recorded costs for the selected scope.",
                        () -> preferences.showTotalProfit = !preferences.showTotalProfit),
                toggle(
                        "Profit / hour",
                        preferences.showProfitPerHour,
                        "Net profit per active hour.",
                        () -> preferences.showProfitPerHour = !preferences.showProfitPerHour),
                toggle(
                        "Projected / hour",
                        preferences.showProjectedPerHour,
                        "The current session's recent net-profit pace. Needs enough active time and a recent kill.",
                        () -> preferences.showProjectedPerHour = !preferences.showProjectedPerHour),
                toggle(
                        "Profit without RNG",
                        preferences.showRegularProfit,
                        "Net profit excluding Tarantula Pets and Arachne Fangs. Scavenger income and all recorded costs are kept.",
                        () -> preferences.showRegularProfit = !preferences.showRegularProfit),
                toggle(
                        "Without RNG / hour",
                        preferences.showRegularPerHour,
                        "Net profit per active hour excluding Tarantula Pets and Arachne Fangs.",
                        () -> preferences.showRegularPerHour = !preferences.showRegularPerHour),
                toggle(
                        "Tracking status",
                        preferences.showStatus,
                        "Show whether tracking is active, summoning, waiting, paused or AFK.",
                        () -> preferences.showStatus = !preferences.showStatus),
                toggle(
                        "Loot items",
                        preferences.showLoot,
                        "Show individually recorded item quantities. Choose visible items and a row limit in Items.",
                        () -> preferences.showLoot = !preferences.showLoot),
                toggle(
                        "Loot values",
                        preferences.showLootValues,
                        "Show the recorded value beside each visible item quantity.",
                        () -> preferences.showLootValues = !preferences.showLootValues),
                toggle(
                        "Observed rare rates",
                        preferences.showRareRates,
                        "Loot ledger only: show detected Epic Pet, Legendary Pet and Fang quantities per 100 qualifying kills. These are observed results, not the chance of the next drop.",
                        () -> preferences.showRareRates = !preferences.showRareRates),
                toggle(
                        "Scavenger coins",
                        preferences.showScavenger,
                        "Show recorded Scavenger coin income. This display toggle does not enable or disable collection.",
                        () -> preferences.showScavenger = !preferences.showScavenger),
                toggle(
                        "Crystal costs",
                        preferences.showCrystalCosts,
                        "Show recorded Crystal spend and placement count.",
                        () -> preferences.showCrystalCosts = !preferences.showCrystalCosts),
                toggle(
                        "Calling costs",
                        preferences.showCallingCosts,
                        "Show recorded Calling spend and placement count.",
                        () -> preferences.showCallingCosts = !preferences.showCallingCosts),
                toggle(
                        "Bosses killed",
                        preferences.showKills,
                        "Show counted kills that meet the participation threshold.",
                        () -> preferences.showKills = !preferences.showKills),
                toggle(
                        "Bosses / hour",
                        preferences.showKillsPerHour,
                        "Loot ledger only: append kills per active hour to the total bosses row.",
                        () -> preferences.showKillsPerHour = !preferences.showKillsPerHour),
                toggle(
                        "Active time",
                        preferences.showActiveTime,
                        "Show active time without changing how time is tracked.",
                        () -> preferences.showActiveTime = !preferences.showActiveTime),
                toggle(
                        "Session / total",
                        preferences.showScope,
                        "Show the selected scope. Switch scope in the dashboard or with /arachne session and /arachne total.",
                        () -> preferences.showScope = !preferences.showScope),
                toggle(
                        "Spawn countdown",
                        preferences.showCountdown,
                        "Show an estimated countdown on the HUD independently of pedestal visibility.",
                        () -> preferences.showCountdown = !preferences.showCountdown),
                new Option(
                        "Pinned achievement",
                        preferences.pinnedAchievement.isEmpty() ? "None" : "Clear",
                        false,
                        tracker.error.isEmpty(),
                        "Click an achievement in Progress to pin it. Clear removes the goal from the HUD.",
                        () -> {
                            preferences.pinnedAchievement = "";
                            changed();
                        }),
                toggle(
                        "Unpriced warning",
                        preferences.showUnpricedWarning,
                        "Show how many recorded drops have no saved value.",
                        () -> preferences.showUnpricedWarning = !preferences.showUnpricedWarning));
    }

    private List<Option> orderOptions() {
        HudPreferences preferences = draft.hudPreferences;
        List<Option> rows = new ArrayList<>();
        rows.add(
                new Option(
                        "Reset row order",
                        "Reset",
                        false,
                        tracker.error.isEmpty(),
                        "Restore the default stat order. Row visibility, loot sorting and item filters are kept.",
                        () -> {
                            preferences.rowOrder =
                                    preferences.layout == HudPreferences.Layout.LOOT
                                            ? HudRowOrder.lootLedgerOrder()
                                            : HudRowOrder.defaultOrder();
                            changed();
                        }));
        for (HudRowOrder.Group group : HudRowOrder.Group.values()) {
            for (HudRowOrder.Definition row : HudRowOrder.rows(preferences.rowOrder, group)) {
                String tip =
                        row.label()
                                + ": reorder within "
                                + group.label().toLowerCase(java.util.Locale.ROOT)
                                + ". Split keeps rewards on the left and summary on the right. Hidden rows keep their order; loot moves as one block.";
                rows.add(
                        new Option(
                                group.label() + ": " + row.label(),
                                "Up",
                                false,
                                tracker.error.isEmpty()
                                        && HudRowOrder.canMove(preferences.rowOrder, row.id(), -1),
                                "Move up. " + tip,
                                () -> moveRow(row.id(), -1),
                                new AdditionalAction(
                                        "Down",
                                        tracker.error.isEmpty()
                                                && HudRowOrder.canMove(
                                                        preferences.rowOrder, row.id(), 1),
                                        "Move down. " + tip,
                                        () -> moveRow(row.id(), 1))));
            }
        }
        return rows;
    }

    private void moveRow(String id, int direction) {
        HudPreferences preferences = draft.hudPreferences;
        preferences.rowOrder = HudRowOrder.move(preferences.rowOrder, id, direction);
        changed();
    }

    private List<Option> itemOptions() {
        HudPreferences preferences = draft.hudPreferences;
        List<Option> rows = new ArrayList<>();
        rows.add(
                new Option(
                        "Maximum loot rows",
                        Integer.toString(preferences.maxLootRows),
                        false,
                        tracker.error.isEmpty(),
                        "Choose 0 to 8 item rows. Zero hides all item rows; the Loot items toggle must also be enabled.",
                        () -> {
                            preferences.maxLootRows = (preferences.maxLootRows + 1) % 9;
                            changed();
                        }));
        rows.add(
                new Option(
                        "Sort loot by",
                        preferences.sort.label(),
                        false,
                        tracker.error.isEmpty(),
                        "Sort visible loot by recorded value, recorded quantity or item name.",
                        () -> {
                            HudPreferences.Sort[] choices = HudPreferences.Sort.values();
                            preferences.sort =
                                    choices[(preferences.sort.ordinal() + 1) % choices.length];
                            changed();
                        }));
        rows.add(
                new Option(
                        "Reset item filter",
                        "Show all",
                        false,
                        tracker.error.isEmpty(),
                        "Make every item eligible for a HUD line. This does not change the loot-row limit or enable loot rows.",
                        () -> {
                            preferences.hiddenItems.clear();
                            changed();
                        }));
        for (var item : Catalog.ITEMS.entrySet()) {
            String itemId = item.getKey();
            boolean shown = !preferences.hiddenItems.contains(itemId);
            rows.add(
                    new Option(
                            item.getValue(),
                            shown ? "Shown" : "Hidden",
                            shown,
                            tracker.error.isEmpty(),
                            item.getValue()
                                    + ": show or hide this HUD item line. Its quantity and profit remain recorded.",
                            () -> {
                                if (shown) {
                                    preferences.hiddenItems.add(itemId);
                                } else {
                                    preferences.hiddenItems.remove(itemId);
                                }
                                changed();
                            }));
        }
        return rows;
    }

    private List<Option> displayOptions() {
        HudPreferences preferences = draft.hudPreferences;
        List<Option> rows = new ArrayList<>();
        rows.add(
                toggle(
                        "Overlay",
                        draft.hud,
                        "Show or hide the live HUD.",
                        () -> draft.hud = !draft.hud));
        rows.add(
                new Option(
                        "HUD type",
                        draft.hudView == Config.View.GRAPH ? "Graph" : "Text",
                        false,
                        tracker.error.isEmpty(),
                        "Switch between the text tracker and graph. Graph text is configured in Graph options.",
                        () -> {
                            draft.hudView =
                                    draft.hudView == Config.View.GRAPH
                                            ? Config.View.DETAILED
                                            : Config.View.GRAPH;
                            changed();
                        }));
        rows.add(
                new Option(
                        "Text layout",
                        preferences.layout.label(),
                        false,
                        tracker.error.isEmpty(),
                        "Cycle Loot ledger, Minimal, Classic and Split while preserving every individual selection. This selects the text HUD.",
                        () -> {
                            HudPreferences.Layout[] choices = HudPreferences.Layout.values();
                            preferences.layout =
                                    choices[(preferences.layout.ordinal() + 1) % choices.length];
                            draft.hudView = Config.View.DETAILED;
                            changed();
                        }));
        rows.add(
                new Option(
                        "Presentation style",
                        preferences.resolvedStyle(draft.hudBackground).label(),
                        false,
                        tracker.error.isEmpty(),
                        "Cycle Text only, Soft panel and Border. Style is independent of layout, rows and graph settings.",
                        () -> {
                            preferences.cycleStyle(draft.hudBackground);
                            draft.hudBackground =
                                    preferences.style != HudPreferences.Style.TEXT_ONLY;
                            changed();
                        }));
        for (HudPreferences.Layout preset :
                new HudPreferences.Layout[] {
                    HudPreferences.Layout.LOOT,
                    HudPreferences.Layout.MINIMAL,
                    HudPreferences.Layout.CLASSIC,
                    HudPreferences.Layout.SPLIT
                }) {
            rows.add(
                    new Option(
                            "Apply preset",
                            preset.label(),
                            false,
                            tracker.error.isEmpty(),
                            "Apply "
                                    + preset.label()
                                    + ": resets row toggles, row order, item filters, sorting and the loot-row limit. Position, size and presentation style are kept.",
                            () -> {
                                preferences.applyPreset(preset);
                                draft.hudView = Config.View.DETAILED;
                                changed();
                            }));
        }
        rows.add(
                new Option(
                        "Show in",
                        draft.hudAlwaysShow ? "SkyBlock" : "Arena only",
                        draft.hudAlwaysShow,
                        tracker.error.isEmpty(),
                        "Show throughout SkyBlock, or only while Arachne tracking is active.",
                        () -> {
                            draft.hudAlwaysShow = !draft.hudAlwaysShow;
                            changed();
                        }));
        rows.add(
                new Option(
                        "Preview, move & resize",
                        "Preview",
                        false,
                        true,
                        "Preview your draft, drag the actual overlay and scroll to resize it. Save applies the draft; Cancel keeps your current HUD.",
                        () -> minecraft.setScreen(new HudEditorScreen(this, draft))));
        return rows;
    }

    private List<Option> savedOptions() {
        List<Option> rows = new ArrayList<>();
        rows.add(
                new Option(
                        "Save this HUD as a named preset",
                        "New preset",
                        false,
                        tracker.error.isEmpty() && draft.hudPresets.size() < HudPreset.LIMIT,
                        "Save all HUD rows, item filters, position, size and background. Up to 12 presets.",
                        () ->
                                minecraft.setScreen(
                                        new HudPresetNameScreen(
                                                this,
                                                "My HUD",
                                                name -> {
                                                    if (draft.hudPresets.stream()
                                                            .anyMatch(
                                                                    p ->
                                                                            p.name.equalsIgnoreCase(
                                                                                    name))) {
                                                        note =
                                                                "That name is already saved. Delete it before replacing it.";
                                                    } else {
                                                        draft.hudPresets.add(
                                                                HudPreset.capture(name, draft));
                                                        note =
                                                                "Preset added to your draft. Save to keep it.";
                                                    }
                                                    rebuildWidgets();
                                                }))));
        for (HudPreset saved : draft.hudPresets) {
            rows.add(
                    new Option(
                            saved.name,
                            "Apply",
                            false,
                            tracker.error.isEmpty(),
                            "Apply this saved HUD to the draft. Save commits it; Cancel keeps your live HUD.",
                            () -> {
                                saved.apply(draft);
                                changed();
                            },
                            new AdditionalAction(
                                    "Delete",
                                    tracker.error.isEmpty(),
                                    "Remove this named preset when you Save.",
                                    () -> {
                                        draft.hudPresets.remove(saved);
                                        changed();
                                    })));
        }
        return rows;
    }

    @Override
    protected void changed() {
        note = "Unsaved changes.";
        rebuildWidgets();
    }

    @Override
    protected void addFooterControls() {
        int buttonWidth = (panelWidth - 36) / 2;
        FlatButton save =
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
                        "Cancel",
                        false,
                        this::onClose));
    }

    private void saveDraft() {
        if (!tracker.error.isEmpty()) return;
        HudPreset previous = HudPreset.capture("Previous", target);
        List<HudPreset> previousPresets = target.hudPresets;
        HudPreset.capture("Draft", draft).apply(target);
        target.hudPresets = HudPreset.normalize(draft.hudPresets);
        if (target == tracker.config) tracker.saveConfig();
        if (!tracker.error.isEmpty()) {
            previous.apply(target);
            target.hudPresets = previousPresets;
            note = "Storage error; settings were not saved.";
            rebuildWidgets();
        } else {
            onClose();
        }
    }
}

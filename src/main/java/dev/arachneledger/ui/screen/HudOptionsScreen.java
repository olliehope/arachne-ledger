package dev.arachneledger.ui.screen;

import dev.arachneledger.config.Config;
import dev.arachneledger.config.HudPreferences;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.ui.FlatButton;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Individual text-HUD choices. Visibility filters never alter the recorded ledger. */
public final class HudOptionsScreen extends SettingsListScreen {
    private int selectedTab;

    public HudOptionsScreen(Screen parent) {
        super(parent, "HUD options", "Display only; recorded totals stay unchanged.");
    }

    @Override
    protected void addHeaderControls() {
        String[] labels = {"Rows", "Items", "Display"};
        for (int index = 0; index < labels.length; index++) {
            int tab = index;
            addRenderableWidget(
                    new FlatButton(
                            panelX + 16 + index * 62,
                            panelY + 43,
                            58,
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
            default -> rowOptions();
        };
    }

    private List<Option> rowOptions() {
        HudPreferences preferences = tracker.config.hudPreferences;
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
                        "Unpriced warning",
                        preferences.showUnpricedWarning,
                        "Show how many recorded drops have no saved value.",
                        () -> preferences.showUnpricedWarning = !preferences.showUnpricedWarning));
    }

    private List<Option> itemOptions() {
        HudPreferences preferences = tracker.config.hudPreferences;
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
        HudPreferences preferences = tracker.config.hudPreferences;
        List<Option> rows = new ArrayList<>();
        rows.add(
                toggle(
                        "Overlay",
                        tracker.config.hud,
                        "Show or hide the live HUD.",
                        () -> tracker.config.hud = !tracker.config.hud));
        rows.add(
                new Option(
                        "HUD type",
                        tracker.config.hudView == Config.View.GRAPH ? "Graph" : "Text",
                        false,
                        tracker.error.isEmpty(),
                        "Switch between the text tracker and graph. Graph text is configured in Graph options.",
                        () -> {
                            tracker.config.hudView =
                                    tracker.config.hudView == Config.View.GRAPH
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
                        "Cycle Minimal, Classic and Split while preserving every individual selection. This selects the text HUD.",
                        () -> {
                            HudPreferences.Layout[] choices = HudPreferences.Layout.values();
                            preferences.layout =
                                    choices[(preferences.layout.ordinal() + 1) % choices.length];
                            tracker.config.hudView = Config.View.DETAILED;
                            changed();
                        }));
        for (HudPreferences.Layout preset :
                new HudPreferences.Layout[] {
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
                                    + ": resets row toggles, item filters, sorting and the loot-row limit. Position and scale are kept.",
                            () -> {
                                preferences.applyPreset(preset);
                                tracker.config.hudView = Config.View.DETAILED;
                                changed();
                            }));
        }
        rows.add(
                new Option(
                        "Show in",
                        tracker.config.hudAlwaysShow ? "SkyBlock" : "Arena only",
                        tracker.config.hudAlwaysShow,
                        tracker.error.isEmpty(),
                        "Show throughout SkyBlock, or only while Arachne tracking is active.",
                        () -> {
                            tracker.config.hudAlwaysShow = !tracker.config.hudAlwaysShow;
                            changed();
                        }));
        rows.add(
                toggle(
                        "Background",
                        tracker.config.hudBackground,
                        "Add a subtle background behind the overlay.",
                        () -> tracker.config.hudBackground = !tracker.config.hudBackground));
        rows.add(
                new Option(
                        "Move / resize",
                        "Edit HUD",
                        false,
                        true,
                        "Drag the actual overlay and scroll to resize it.",
                        () -> minecraft.setScreen(new HudEditorScreen(this))));
        rows.add(
                new Option(
                        "Graph rows / lines",
                        "Graph options",
                        false,
                        true,
                        "Choose each graph metric, text row, projection and spawn marker.",
                        () -> minecraft.setScreen(new GraphOptionsScreen(this))));
        return rows;
    }
}

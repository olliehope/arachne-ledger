package dev.arachneledger.config;

import dev.arachneledger.skyblock.Catalog;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Display-only choices for the text HUD. Graph preferences, journal values, and saved HUD position
 * remain independent. A preset is an explicit reset; validation preserves individual row choices.
 */
public final class HudPreferences {
    public enum Style {
        INHERIT("Saved style", 0, 0),
        TEXT_ONLY("Text only", 0, 0),
        SOFT_PANEL("Soft panel", 0x80000000, 0),
        BORDER("Border", 0x600B0D10, 0x806B7078);

        private final String label;
        private final int backgroundColor, borderColor;

        Style(String label, int backgroundColor, int borderColor) {
            this.label = label;
            this.backgroundColor = backgroundColor;
            this.borderColor = borderColor;
        }

        public String label() {
            return label;
        }

        public int backgroundColor() {
            return backgroundColor;
        }

        public int borderColor() {
            return borderColor;
        }
    }

    public enum Layout {
        CLASSIC("Classic"),
        MINIMAL("Minimal"),
        SPLIT("Split"),
        LOOT("Loot ledger");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Sort {
        VALUE("Value"),
        QUANTITY("Quantity"),
        NAME("Name");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public Layout layout = Layout.MINIMAL;
    public Sort sort = Sort.VALUE;

    // Older saves keep their exact background choice until a presentation style is selected.
    public Style style = Style.INHERIT;

    // The constructor supplies the fresh preset; Gson restores existing individual choices.
    public boolean showTitle = true;
    public boolean showLoot = false;
    public boolean showLootValues = true;
    public boolean showRareRates = true;
    public boolean showKillsPerHour = true;
    public boolean showScavenger = false;
    public boolean showCrystalCosts = false;
    public boolean showCallingCosts = false;
    public boolean showKills = false;
    public boolean showTotalProfit = true;
    public boolean showProfitPerHour = true;
    public boolean showProjectedPerHour = true;
    public boolean showRegularProfit = false;
    public boolean showRegularPerHour = false;
    public boolean showActiveTime = false;
    public boolean showScope = false;
    public boolean showStatus = true;
    public boolean showUnpricedWarning = false;
    public boolean showCountdown = false;
    public String pinnedAchievement = "";

    // Hiding loot does not discard the user's row limit or preferred value display.
    public int maxLootRows = 3;

    /** Item IDs excluded from the HUD only; their recorded quantities and income remain intact. */
    public Set<String> hiddenItems = new LinkedHashSet<>();

    /** Stable stat keys; the loot list moves as one block and preserves its own sort order. */
    public List<String> rowOrder = HudRowOrder.defaultOrder();

    public HudPreferences() {
        applyPreset(Layout.LOOT);
    }

    public Style resolvedStyle(boolean savedBackground) {
        return style == null || style == Style.INHERIT
                ? savedBackground ? Style.SOFT_PANEL : Style.TEXT_ONLY
                : style;
    }

    /** Presentation choices remain independent of content layout and row presets. */
    public void cycleStyle(boolean savedBackground) {
        style =
                switch (resolvedStyle(savedBackground)) {
                    case TEXT_ONLY -> Style.SOFT_PANEL;
                    case SOFT_PANEL -> Style.BORDER;
                    default -> Style.TEXT_ONLY;
                };
    }

    /** Restore a complete preset, including loot sorting and individual item visibility. */
    public void applyPreset(Layout preset) {
        layout = preset == null ? Layout.MINIMAL : preset;
        boolean classic = layout == Layout.CLASSIC;
        boolean lootLedger = layout == Layout.LOOT;
        boolean expanded = layout != Layout.MINIMAL;

        showTitle = true;
        showLoot = expanded;
        showLootValues = true;
        showRareRates = true;
        showKillsPerHour = true;
        showScavenger = classic || lootLedger;
        showCrystalCosts = classic;
        showCallingCosts = classic;
        showKills = expanded;
        showTotalProfit = true;
        showProfitPerHour = true;
        showProjectedPerHour = !lootLedger;
        showRegularProfit = false;
        showRegularPerHour = false;
        showActiveTime = expanded;
        showScope = classic;
        showStatus = !lootLedger;
        showUnpricedWarning = classic;
        showCountdown = false;
        pinnedAchievement = "";
        sort = Sort.VALUE;
        maxLootRows = lootLedger ? 8 : 3;
        hiddenItems = new LinkedHashSet<>();
        rowOrder = lootLedger ? HudRowOrder.lootLedgerOrder() : HudRowOrder.defaultOrder();
    }

    /** Normalize damaged or older display settings without resetting custom row selections. */
    public void validate() {
        if (pinnedAchievement == null
                || dev.arachneledger.achievement.Achievements.DEFINITIONS.stream()
                        .noneMatch(definition -> definition.id().equals(pinnedAchievement))) {
            pinnedAchievement = "";
        }
        if (layout == null) {
            layout = Layout.MINIMAL;
        }
        if (sort == null) {
            sort = Sort.VALUE;
        }
        if (style == null) {
            style = Style.INHERIT;
        }
        maxLootRows = Math.max(0, Math.min(8, maxLootRows));
        rowOrder = HudRowOrder.normalize(rowOrder);
        Set<String> supportedHiddenItems = new LinkedHashSet<>();
        if (hiddenItems != null) {
            for (String itemId : hiddenItems) {
                if (itemId != null && Catalog.ITEMS.containsKey(itemId)) {
                    supportedHiddenItems.add(itemId);
                }
            }
        }
        hiddenItems = supportedHiddenItems;
    }

    /** A detached copy makes settings drafts and named presets independent of live choices. */
    public HudPreferences copy() {
        HudPreferences copy = new HudPreferences();
        copy.layout = layout;
        copy.sort = sort;
        copy.style = style;
        copy.showTitle = showTitle;
        copy.showLoot = showLoot;
        copy.showLootValues = showLootValues;
        copy.showRareRates = showRareRates;
        copy.showKillsPerHour = showKillsPerHour;
        copy.showScavenger = showScavenger;
        copy.showCrystalCosts = showCrystalCosts;
        copy.showCallingCosts = showCallingCosts;
        copy.showKills = showKills;
        copy.showTotalProfit = showTotalProfit;
        copy.showProfitPerHour = showProfitPerHour;
        copy.showProjectedPerHour = showProjectedPerHour;
        copy.showRegularProfit = showRegularProfit;
        copy.showRegularPerHour = showRegularPerHour;
        copy.showActiveTime = showActiveTime;
        copy.showScope = showScope;
        copy.showStatus = showStatus;
        copy.showUnpricedWarning = showUnpricedWarning;
        copy.showCountdown = showCountdown;
        copy.pinnedAchievement = pinnedAchievement;
        copy.maxLootRows = maxLootRows;
        copy.hiddenItems =
                hiddenItems == null ? new LinkedHashSet<>() : new LinkedHashSet<>(hiddenItems);
        copy.rowOrder = HudRowOrder.normalize(rowOrder);
        return copy;
    }

    /** Exact comparisons avoid allocations and hash collisions in mutable display settings. */
    public boolean sameDisplay(HudPreferences other) {
        return other != null
                && displayFlags() == other.displayFlags()
                && layout == other.layout
                && sort == other.sort
                && style == other.style
                && maxLootRows == other.maxLootRows
                && hiddenItems.equals(other.hiddenItems)
                && rowOrder.equals(other.rowOrder)
                && pinnedAchievement.equals(other.pinnedAchievement);
    }

    private int displayFlags() {
        return (showTitle ? 1 : 0)
                | (showLoot ? 1 << 1 : 0)
                | (showLootValues ? 1 << 2 : 0)
                | (showRareRates ? 1 << 3 : 0)
                | (showKillsPerHour ? 1 << 4 : 0)
                | (showScavenger ? 1 << 5 : 0)
                | (showCrystalCosts ? 1 << 6 : 0)
                | (showCallingCosts ? 1 << 7 : 0)
                | (showKills ? 1 << 8 : 0)
                | (showTotalProfit ? 1 << 9 : 0)
                | (showProfitPerHour ? 1 << 10 : 0)
                | (showProjectedPerHour ? 1 << 11 : 0)
                | (showRegularProfit ? 1 << 12 : 0)
                | (showRegularPerHour ? 1 << 13 : 0)
                | (showActiveTime ? 1 << 14 : 0)
                | (showScope ? 1 << 15 : 0)
                | (showStatus ? 1 << 16 : 0)
                | (showUnpricedWarning ? 1 << 17 : 0)
                | (showCountdown ? 1 << 18 : 0);
    }
}

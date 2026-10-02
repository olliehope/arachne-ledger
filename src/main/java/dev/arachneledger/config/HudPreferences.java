package dev.arachneledger.config;

import dev.arachneledger.skyblock.Catalog;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Display-only choices for the text HUD. Graph preferences, journal values, and saved HUD position
 * remain independent. A preset is an explicit reset; validation preserves individual row choices.
 */
public final class HudPreferences {
    public enum Layout {
        CLASSIC("Classic"),
        MINIMAL("Minimal"),
        SPLIT("Split");

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

    // Minimal keeps the money summary and tracking state visible. Every row remains optional.
    public boolean showTitle = true;
    public boolean showLoot = false;
    public boolean showLootValues = true;
    public boolean showScavenger = false;
    public boolean showCrystalCosts = false;
    public boolean showCallingCosts = false;
    public boolean showKills = false;
    public boolean showTotalProfit = true;
    public boolean showProfitPerHour = true;
    public boolean showProjectedPerHour = true;
    public boolean showActiveTime = false;
    public boolean showScope = false;
    public boolean showStatus = true;
    public boolean showUnpricedWarning = false;

    // Hiding loot does not discard the user's row limit or preferred value display.
    public int maxLootRows = 3;

    /** Item IDs excluded from the HUD only; their recorded quantities and income remain intact. */
    public Set<String> hiddenItems = new LinkedHashSet<>();

    /** Restore a complete preset, including loot sorting and individual item visibility. */
    public void applyPreset(Layout preset) {
        layout = preset == null ? Layout.MINIMAL : preset;
        boolean classic = layout == Layout.CLASSIC;
        boolean expanded = layout != Layout.MINIMAL;

        showTitle = true;
        showLoot = expanded;
        showLootValues = true;
        showScavenger = classic;
        showCrystalCosts = classic;
        showCallingCosts = classic;
        showKills = expanded;
        showTotalProfit = true;
        showProfitPerHour = true;
        showProjectedPerHour = true;
        showActiveTime = expanded;
        showScope = classic;
        showStatus = true;
        showUnpricedWarning = classic;
        sort = Sort.VALUE;
        maxLootRows = 3;
        hiddenItems = new LinkedHashSet<>();
    }

    /** Normalize damaged or older display settings without resetting custom row selections. */
    public void validate() {
        if (layout == null) {
            layout = Layout.MINIMAL;
        }
        if (sort == null) {
            sort = Sort.VALUE;
        }
        maxLootRows = Math.max(0, Math.min(8, maxLootRows));
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
}

package dev.arachneledger.config;

import dev.arachneledger.ledger.Ledger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** HUD migrations and display changes must preserve financial and graph preferences. */
public final class HudPreferencesChecks {
    private static int checks;

    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) {
            throw new AssertionError(why);
        }
    }

    private static void same(Object expected, Object actual, String why) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static Config read(Path file) throws Exception {
        return Store.read(file, Config.class, Config::new, Config::validate);
    }

    public static void main(String[] args) throws Exception {
        Path root =
                Files.createTempDirectory(
                        Path.of(System.getProperty("test.root", "build")),
                        "arachne-hud-preferences-");
        defaults();
        legacyMigration(root.resolve("legacy.json"));
        customPersistence(root.resolve("custom.json"));
        normalization();
        presetsAndIsolation();
        System.out.println("PASS: " + checks + " HUD preference, migration and isolation checks.");
    }

    private static void defaults() {
        Config fresh = new Config();
        fresh.validate();
        minimal(fresh.hudPreferences, "Fresh settings");
        same(
                Config.View.DETAILED,
                fresh.hudView,
                "New row preferences do not change the existing view setting");
        same(
                3,
                fresh.hudPreferences.maxLootRows,
                "Enabling loot later has a useful default row limit");
        yes(fresh.hudPreferences.hiddenItems.isEmpty(), "New settings do not hide specific items");
    }

    private static void minimal(HudPreferences preferences, String context) {
        same(HudPreferences.Layout.MINIMAL, preferences.layout, context + " use Minimal");
        yes(
                preferences.showTitle
                        && preferences.showTotalProfit
                        && preferences.showProfitPerHour
                        && preferences.showProjectedPerHour
                        && preferences.showStatus,
                context + " display the money summary and tracking status");
        yes(
                !preferences.showLoot
                        && !preferences.showScavenger
                        && !preferences.showCrystalCosts
                        && !preferences.showCallingCosts
                        && !preferences.showKills
                        && !preferences.showActiveTime
                        && !preferences.showScope
                        && !preferences.showUnpricedWarning,
                context + " omit the detailed rows");
        same(HudPreferences.Sort.VALUE, preferences.sort, context + " retain value sorting");
    }

    private static void legacyMigration(Path file) throws Exception {
        Files.writeString(
                file,
                """
                {"view":"GRAPH","hudView":"GRAPH","hudX":0.25,"hudY":0.75,"hudScale":1.25,
                 "crystalConfigured":true,"crystalCost":12345,"callingCost":678,"profile":"saved",
                 "prices":{"SOUL_STRING":7123,"ARACHNE_BOOTS":0},
                 "graph":{"showSpawns":true,"showActiveTime":false}}
                """);
        Config migrated = read(file);
        minimal(migrated.hudPreferences, "Legacy settings without HUD choices");
        same(Config.View.GRAPH, migrated.view, "Migration keeps the dashboard graph view");
        same(Config.View.GRAPH, migrated.hudView, "Migration keeps the HUD graph view");
        same(0.25, migrated.hudX, "Migration keeps the HUD horizontal position");
        same(0.75, migrated.hudY, "Migration keeps the HUD vertical position");
        same(1.25, migrated.hudScale, "Migration keeps the HUD scale");
        same(12345.0, migrated.effectiveCrystalCost(), "Migration keeps the fixed Crystal cost");
        same(678.0, migrated.callingCost, "Migration keeps the Calling cost");
        same("saved", migrated.profile, "Migration keeps the selected ledger profile");
        same(
                7123.0,
                migrated.price("SOUL_STRING"),
                "Migration keeps historical manual preferences");
        same(
                0.0,
                migrated.lootPrice("ARACHNE_BOOTS"),
                "Migration keeps an intentional manual zero");
        yes(
                migrated.graph.showSpawns && !migrated.graph.showActiveTime,
                "Migration does not copy HUD choices into graph preferences");
        Store.write(file, migrated);
        Config reopened = read(file);
        minimal(reopened.hudPreferences, "Reopened migrated settings");
        same(Config.View.GRAPH, reopened.hudView, "The graph view survives migration and save");

        Files.writeString(file, "{\"hudPreferences\":null,\"hudView\":\"COMPACT\"}");
        Config explicitNull = read(file);
        minimal(explicitNull.hudPreferences, "Explicitly null HUD choices");
        same(
                Config.View.COMPACT,
                explicitNull.hudView,
                "A null preference repair keeps Compact view");
    }

    private static void customPersistence(Path file) throws Exception {
        Config custom = new Config();
        custom.hudPreferences.applyPreset(HudPreferences.Layout.SPLIT);
        HudPreferences preferences = custom.hudPreferences;
        preferences.showTitle = false;
        preferences.showLoot = true;
        preferences.showLootValues = false;
        preferences.showScavenger = true;
        preferences.showCrystalCosts = true;
        preferences.showCallingCosts = false;
        preferences.showKills = false;
        preferences.showTotalProfit = false;
        preferences.showProfitPerHour = true;
        preferences.showProjectedPerHour = false;
        preferences.showActiveTime = false;
        preferences.showScope = true;
        preferences.showStatus = false;
        preferences.showUnpricedWarning = true;
        preferences.maxLootRows = 8;
        preferences.sort = HudPreferences.Sort.QUANTITY;
        preferences.hiddenItems.add("STRING");
        preferences.hiddenItems.add("ARACHNE_FANG");
        custom.validate();
        Store.write(file, custom);
        HudPreferences saved = read(file).hudPreferences;
        same(HudPreferences.Layout.SPLIT, saved.layout, "Custom layout persists");
        same(HudPreferences.Sort.QUANTITY, saved.sort, "Custom quantity sorting persists");
        same(8, saved.maxLootRows, "Custom maximum row count persists");
        same(
                List.of("STRING", "ARACHNE_FANG"),
                List.copyOf(saved.hiddenItems),
                "Per-item exclusions persist in their saved order");
        same(rowFlags(preferences), rowFlags(saved), "Every individual row selection persists");

        saved.maxLootRows = 0;
        saved.sort = HudPreferences.Sort.NAME;
        Config zeroRows = new Config();
        zeroRows.hudPreferences = saved;
        Store.write(file, zeroRows);
        HudPreferences reopened = read(file).hudPreferences;
        same(0, reopened.maxLootRows, "Zero loot rows is an intentional saved choice");
        same(HudPreferences.Sort.NAME, reopened.sort, "Name sorting survives reload");
        same(
                rowFlags(saved),
                rowFlags(reopened),
                "Validation does not reset custom rows to a preset");
    }

    private static List<Boolean> rowFlags(HudPreferences preferences) {
        return List.of(
                preferences.showTitle,
                preferences.showLoot,
                preferences.showLootValues,
                preferences.showScavenger,
                preferences.showCrystalCosts,
                preferences.showCallingCosts,
                preferences.showKills,
                preferences.showTotalProfit,
                preferences.showProfitPerHour,
                preferences.showProjectedPerHour,
                preferences.showActiveTime,
                preferences.showScope,
                preferences.showStatus,
                preferences.showUnpricedWarning);
    }

    private static void normalization() {
        HudPreferences preferences = new HudPreferences();
        preferences.layout = null;
        preferences.sort = null;
        preferences.maxLootRows = 100;
        preferences.showTotalProfit = false;
        preferences.hiddenItems = new LinkedHashSet<>();
        preferences.hiddenItems.add(null);
        preferences.hiddenItems.add("UNKNOWN_ITEM");
        preferences.hiddenItems.add("SOUL_STRING");
        preferences.validate();
        same(
                HudPreferences.Layout.MINIMAL,
                preferences.layout,
                "Unknown layouts fall back to Minimal");
        same(HudPreferences.Sort.VALUE, preferences.sort, "Unknown sorting falls back to Value");
        same(8, preferences.maxLootRows, "Oversized loot limits clamp to eight");
        same(
                List.of("SOUL_STRING"),
                List.copyOf(preferences.hiddenItems),
                "Unsupported item exclusions cannot block loading financial settings");
        yes(
                !preferences.showTotalProfit,
                "Repairing display metadata preserves explicit hidden rows");
        preferences.maxLootRows = -1;
        preferences.hiddenItems = null;
        preferences.validate();
        same(0, preferences.maxLootRows, "Negative loot limits clamp to zero");
        yes(preferences.hiddenItems.isEmpty(), "Null item exclusions become an editable set");
        preferences.hiddenItems.add("STRING");
        yes(
                preferences.hiddenItems.contains("STRING"),
                "Normalized item exclusions remain mutable");
    }

    private static void presetsAndIsolation() {
        Config config = new Config();
        config.validate();
        config.hudView = Config.View.GRAPH;
        config.hudX = 0.2;
        config.hudScale = 1.3;
        config.graph.showHourly = false;
        config.manualSet("SOUL_STRING", 9876);
        Ledger ledger = new Ledger();
        ledger.add(Ledger.Kind.LOOT, "SOUL_STRING", 2, 5000, "test", 1);
        double recordedIncome = ledger.stats(true).revenue();

        config.hudPreferences.applyPreset(HudPreferences.Layout.CLASSIC);
        yes(
                rowFlags(config.hudPreferences).stream().allMatch(Boolean::booleanValue),
                "Classic enables the full set of optional rows");
        config.hudPreferences.hiddenItems.add("SOUL_STRING");
        config.hudPreferences.maxLootRows = 8;
        config.hudPreferences.sort = HudPreferences.Sort.NAME;
        config.hudPreferences.applyPreset(HudPreferences.Layout.MINIMAL);
        minimal(config.hudPreferences, "An explicit Minimal preset");
        same(3, config.hudPreferences.maxLootRows, "A preset resets the loot limit");
        yes(config.hudPreferences.hiddenItems.isEmpty(), "A preset resets item exclusions");
        config.hudPreferences.applyPreset(HudPreferences.Layout.SPLIT);
        yes(
                config.hudPreferences.showLoot
                        && config.hudPreferences.showKills
                        && config.hudPreferences.showActiveTime,
                "Split combines a money summary with useful loot and activity rows");
        yes(
                !config.hudPreferences.showCrystalCosts
                        && !config.hudPreferences.showCallingCosts
                        && !config.hudPreferences.showScavenger
                        && !config.hudPreferences.showScope,
                "Split omits the separate detailed cost and scope rows by default");
        config.validate();
        same(
                Config.View.GRAPH,
                config.hudView,
                "Text presets never replace an existing graph view");
        same(0.2, config.hudX, "Text presets never move the HUD");
        same(1.3, config.hudScale, "Text presets never resize the HUD");
        yes(!config.graph.showHourly, "Text presets never reset graph row choices");
        same(9876.0, config.price("SOUL_STRING"), "Text presets never change future valuations");
        same(
                recordedIncome,
                ledger.stats(true).revenue(),
                "Text presets never reprice journal receipts");
        same(
                2L,
                ledger.stats(true).loot().get("SOUL_STRING"),
                "Item visibility never deletes recorded quantities");
    }
}

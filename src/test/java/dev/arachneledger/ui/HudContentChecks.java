package dev.arachneledger.ui;

import dev.arachneledger.config.Config;
import dev.arachneledger.config.HudPreferences;
import dev.arachneledger.config.HudRowOrder;
import dev.arachneledger.config.Store;
import dev.arachneledger.ledger.FightRecord;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.skyblock.PurseCoins;
import dev.arachneledger.tracking.Tracker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Text HUD selections and layout bounds are presentation choices, never financial mutations. */
public final class HudContentChecks {
    private static int checks;
    private static final long BASE = 1_000_000;

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) {
            throw new AssertionError(why);
        }
    }

    private static void same(Object expected, Object actual, String why) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
        }
    }

    private static void immutable(Runnable mutation, String why) {
        checks++;
        try {
            mutation.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(why);
    }

    private static Tracker emptyTracker(Path directory) {
        Tracker tracker = new Tracker(directory);
        tracker.config.hudPreferences.applyPreset(HudPreferences.Layout.MINIMAL);
        tracker.account("hud-fixture");
        tracker.updateLocation(true, true, true, "Arachne's Sanctuary", "fixture", BASE);
        tracker.tick(BASE, true);
        return tracker;
    }

    private static Tracker fixture(Path directory) {
        Tracker tracker = emptyTracker(directory);
        add(tracker.ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 1_000);
        add(tracker.ledger, Ledger.Kind.CALLING, "ARACHNE_KEEPER_FRAGMENT", 2, 100);
        add(tracker.ledger, Ledger.Kind.LOOT, "SOUL_STRING", 2, 5_000);
        add(tracker.ledger, Ledger.Kind.LOOT, "STRING", 12, 3);
        add(tracker.ledger, Ledger.Kind.LOOT, "SPIDER_EYE", 4, 20);
        add(tracker.ledger, Ledger.Kind.LOOT, "LUXURIOUS_SPOOL", 1, 500);
        add(tracker.ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 1, 0);
        add(tracker.ledger, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 317);
        advance(tracker.ledger, 60_000);
        add(tracker.ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        return tracker;
    }

    private static void add(Ledger ledger, Ledger.Kind kind, String item, long count, double unit) {
        ledger.add(kind, item, count, unit, "hud_fixture", BASE + ledger.activeMillis);
    }

    private static void advance(Ledger ledger, long millis) {
        while (millis > 0) {
            long delta = Math.min(5_000, millis);
            ledger.tick(delta);
            millis -= delta;
        }
    }

    private static List<HudContent.Row> allRows(HudContent.Snapshot content) {
        List<HudContent.Row> rows = new ArrayList<>(content.rewards());
        rows.addAll(content.metrics());
        return rows;
    }

    private static HudContent.Row row(HudContent.Snapshot content, String id) {
        return allRows(content).stream()
                .filter(row -> row.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing HUD row " + id));
    }

    private static List<String> visibleLoot(Tracker tracker) {
        return HudContent.visibleLoot(
                tracker.config.hudPreferences,
                tracker.ledger.stats(tracker.config.total),
                tracker.ledger.analytics(tracker.config.total));
    }

    public static void main(String[] args) throws Exception {
        Path root =
                Files.createTempDirectory(
                        Path.of(System.getProperty("test.root", "build")), "arachne-hud-content-");
        summariesAndSelections(root.resolve("summary"));
        lootVisibilityAndSorting(root.resolve("loot"));
        scopeAndProjection(root.resolve("scope"));
        dimensionsAndHiddenRows(root.resolve("dimensions"));
        cyclingPreservesChoices(root.resolve("cycling"));
        customRowOrder(root.resolve("order"));
        profitWithoutRng(root.resolve("without-rng"));
        lootLedgerContent(root.resolve("loot-ledger"));
        System.out.println("PASS: " + checks + " HUD content, selection, scope and layout checks.");
    }

    private record RowToggle(String id, Consumer<HudPreferences> hide) {}

    private static void summariesAndSelections(Path directory) {
        Tracker tracker = fixture(directory);
        HudContent.Snapshot minimal = HudContent.build(tracker);
        yes(minimal.rewards().isEmpty(), "Minimal omits loot and cost details by default");
        same(
                List.of("profit", "hourly", "projectedHourly", "status"),
                minimal.metrics().stream().map(HudContent.Row::id).toList(),
                "Minimal contains the selected money rows and tracking state");
        same(
                9_733.0,
                tracker.ledger.stats(false).profit(),
                "Fixture profit includes Scavenger once");
        same(
                Format.coins(9_733),
                row(minimal, "profit").value(),
                "Money row uses journal net profit");
        same(
                Format.coins(583_980),
                row(minimal, "hourly").value(),
                "Actual rate uses active elapsed time");
        same(
                Format.coins(583_980),
                row(minimal, "projectedHourly").value(),
                "Warmed projection uses the observed current-session rate");
        same(
                "Waiting for spawn",
                row(minimal, "status").value(),
                "Status describes the live timer state");
        immutable(() -> minimal.metrics().clear(), "HUD metrics snapshots are immutable");
        immutable(() -> minimal.rewards().clear(), "HUD reward snapshots are immutable");

        HudPreferences preferences = tracker.config.hudPreferences;
        preferences.applyPreset(HudPreferences.Layout.CLASSIC);
        HudContent.Snapshot classic = HudContent.build(tracker);
        same("317", row(classic, "scavenger").value(), "Scavenger row is a revenue subtotal");
        same(
                "Crystal costs (1)",
                row(classic, "crystalCosts").label(),
                "Crystal row includes quantity");
        same("1.0k", row(classic, "crystalCosts").value(), "Crystal row uses recorded cost");
        same(
                "Calling costs (2)",
                row(classic, "callingCosts").label(),
                "Calling row includes quantity");
        same("200", row(classic, "callingCosts").value(), "Calling row includes every placement");
        same("1", row(classic, "kills").value(), "Kill row uses journal kills");
        same(
                "1m 0s",
                row(classic, "activeTime").value(),
                "Active row uses the selected time scope");
        same(
                "This session",
                row(classic, "scope").value(),
                "Scope row identifies current-session totals");
        same(
                "1 unpriced drop",
                row(classic, "unpriced").label(),
                "Warning counts recorded unpriced quantities");

        List<Ledger.Entry> receipts = List.copyOf(tracker.ledger.entries);
        long revision = tracker.ledger.revision();
        List<RowToggle> toggles =
                List.of(
                        new RowToggle("scavenger", prefs -> prefs.showScavenger = false),
                        new RowToggle("crystalCosts", prefs -> prefs.showCrystalCosts = false),
                        new RowToggle("callingCosts", prefs -> prefs.showCallingCosts = false),
                        new RowToggle("kills", prefs -> prefs.showKills = false),
                        new RowToggle("profit", prefs -> prefs.showTotalProfit = false),
                        new RowToggle("hourly", prefs -> prefs.showProfitPerHour = false),
                        new RowToggle(
                                "projectedHourly", prefs -> prefs.showProjectedPerHour = false),
                        new RowToggle("activeTime", prefs -> prefs.showActiveTime = false),
                        new RowToggle("scope", prefs -> prefs.showScope = false),
                        new RowToggle("status", prefs -> prefs.showStatus = false),
                        new RowToggle("unpriced", prefs -> prefs.showUnpricedWarning = false));
        for (RowToggle toggle : toggles) {
            preferences.applyPreset(HudPreferences.Layout.CLASSIC);
            toggle.hide().accept(preferences);
            HudContent.Snapshot selected = HudContent.build(tracker);
            same(
                    allRows(classic).stream().filter(row -> !row.id().equals(toggle.id())).toList(),
                    allRows(selected),
                    "Hiding " + toggle.id() + " preserves every other displayed value");
            same(
                    receipts,
                    tracker.ledger.entries,
                    "Hiding " + toggle.id() + " never edits receipts");
        }
        same(
                revision,
                tracker.ledger.revision(),
                "Row visibility never invalidates financial history");
        preferences.applyPreset(HudPreferences.Layout.MINIMAL);
        int titledHeight = Hud.panelHeight(tracker.config);
        preferences.showTitle = false;
        yes(
                Hud.panelHeight(tracker.config) < titledHeight,
                "Hiding the title removes its reserved height");

        tracker.config.paused = true;
        same(
                "Paused",
                row(HudContent.build(tracker), "status").value(),
                "Paused status takes precedence over arena activity");
        tracker.config.paused = false;
        tracker.inArena = false;
        same(
                "Waiting",
                row(HudContent.build(tracker), "status").value(),
                "Outside-arena status is Waiting");
        add(tracker.ledger, Ledger.Kind.EXPENSE, "MANUAL", 1, 20_000);
        same(
                Graph.RED,
                row(HudContent.build(tracker), "profit").valueColor(),
                "A negative net profit uses loss coloring");
    }

    private static void lootVisibilityAndSorting(Path directory) {
        Tracker tracker = fixture(directory);
        HudPreferences preferences = tracker.config.hudPreferences;
        preferences.applyPreset(HudPreferences.Layout.CLASSIC);
        preferences.maxLootRows = 8;
        List<Ledger.Entry> receipts = List.copyOf(tracker.ledger.entries);
        Ledger.Stats stats = tracker.ledger.stats(false);

        same(
                List.of("SOUL_STRING", "LUXURIOUS_SPOOL", "SPIDER_EYE", "STRING", "ARACHNE_FANG"),
                visibleLoot(tracker),
                "Value sorting ranks the full recorded reward values");
        HudContent.Snapshot content = HudContent.build(tracker);
        same(
                "2x Soul String",
                row(content, "loot:SOUL_STRING").label(),
                "Loot row contains quantity and display name");
        same("10.0k", row(content, "loot:SOUL_STRING").value(), "Loot values use recorded income");
        same(
                "",
                row(content, "loot:ARACHNE_FANG").value(),
                "Unpriced loot has no misleading value text");
        preferences.showLootValues = false;
        content = HudContent.build(tracker);
        yes(
                content.rewards().stream()
                        .filter(row -> row.id().startsWith("loot:"))
                        .allMatch(row -> row.value().isEmpty()),
                "Disabling loot values keeps names and counts without any price text");
        same(
                "2x Soul String",
                row(content, "loot:SOUL_STRING").label(),
                "Value visibility never hides item quantity");

        preferences.sort = HudPreferences.Sort.QUANTITY;
        same(
                List.of("STRING", "SPIDER_EYE", "SOUL_STRING", "ARACHNE_FANG", "LUXURIOUS_SPOOL"),
                visibleLoot(tracker),
                "Quantity sorting uses counts with stable item-ID ties");
        preferences.sort = HudPreferences.Sort.NAME;
        same(
                List.of("ARACHNE_FANG", "LUXURIOUS_SPOOL", "SOUL_STRING", "SPIDER_EYE", "STRING"),
                visibleLoot(tracker),
                "Name sorting uses readable catalog names");

        preferences.sort = HudPreferences.Sort.VALUE;
        preferences.hiddenItems.add("SOUL_STRING");
        preferences.hiddenItems.add("STRING");
        preferences.maxLootRows = 2;
        same(
                List.of("LUXURIOUS_SPOOL", "SPIDER_EYE"),
                visibleLoot(tracker),
                "Individual exclusions are applied before the row limit");
        preferences.maxLootRows = 1;
        same(
                List.of("LUXURIOUS_SPOOL"),
                visibleLoot(tracker),
                "The one-row limit keeps the highest visible item");
        preferences.maxLootRows = 0;
        yes(
                HudContent.build(tracker).rewards().stream()
                        .noneMatch(
                                row ->
                                        row.id().startsWith("loot:")
                                                || row.id().equals("emptyLoot")),
                "Zero loot rows suppresses both rewards and an empty-list placeholder");
        preferences.maxLootRows = 8;
        preferences.hiddenItems.addAll(Catalog.ITEMS.keySet());
        same(
                "No visible drops",
                row(HudContent.build(tracker), "emptyLoot").label(),
                "A fully filtered reward list explains that existing drops are hidden");
        preferences.showLoot = false;
        yes(
                visibleLoot(tracker).isEmpty(),
                "Disabling the loot group removes its selected item list");
        same(
                receipts,
                tracker.ledger.entries,
                "Loot sorting and exclusions preserve every journal receipt");
        same(
                stats,
                tracker.ledger.stats(false),
                "Loot display choices preserve revenue, costs, counts and graphs");

        Tracker empty = emptyTracker(directory.resolve("empty"));
        empty.config.hudPreferences.showLoot = true;
        same(
                "No drops recorded yet",
                row(HudContent.build(empty), "emptyLoot").label(),
                "An empty ledger is distinguished from a filtered ledger");
        add(empty.ledger, Ledger.Kind.LOOT, "STRING", 5, 10);
        add(empty.ledger, Ledger.Kind.LOOT, "SPIDER_EYE", 5, 10);
        same(
                List.of("SPIDER_EYE", "STRING"),
                visibleLoot(empty),
                "Equal-value rewards have a deterministic item-ID order");
    }

    private static void scopeAndProjection(Path directory) {
        Tracker tracker = fixture(directory);
        tracker.ledger.newSession();
        add(tracker.ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 50);
        add(tracker.ledger, Ledger.Kind.LOOT, "ARACHNE_FRAGMENT", 3, 100);
        advance(tracker.ledger, 60_000);
        add(tracker.ledger, Ledger.Kind.KILL, "ARACHNE", 1, 0);
        tracker.config.hudPreferences.applyPreset(HudPreferences.Layout.CLASSIC);

        HudContent.Snapshot session = HudContent.build(tracker);
        same(
                "250",
                row(session, "profit").value(),
                "Current-session profit excludes earlier receipts");
        same("1", row(session, "kills").value(), "Current-session kills exclude earlier receipts");
        same(
                "15.0k",
                row(session, "hourly").value(),
                "Session hourly rate uses its own one-minute time origin");
        same(
                "15.0k",
                row(session, "projectedHourly").value(),
                "Projection samples only the current session");
        same(
                "3x Arachne Fragment",
                row(session, "loot:ARACHNE_FRAGMENT").label(),
                "The selected item list follows session scope");
        yes(
                allRows(session).stream().noneMatch(row -> row.id().equals("unpriced")),
                "An earlier unpriced reward does not create a current-session warning");

        tracker.config.total = true;
        HudContent.Snapshot total = HudContent.build(tracker);
        same(
                Format.coins(9_983),
                row(total, "profit").value(),
                "Lifetime profit combines both sessions");
        same("2", row(total, "kills").value(), "Lifetime kills combine both sessions");
        same(
                "2m 0s",
                row(total, "activeTime").value(),
                "Lifetime active time combines both session clocks");
        same("Total", row(total, "scope").value(), "The selected scope is identified correctly");
        same(
                row(session, "projectedHourly").value(),
                row(total, "projectedHourly").value(),
                "Lifetime totals use the same current-session projected pace");
        same(
                Format.coins(9_983 * 30.0),
                row(total, "hourly").value(),
                "Lifetime actual hourly rate uses total financial values and total active time");
        same(
                "1 unpriced drop",
                row(total, "unpriced").label(),
                "Lifetime warning includes earlier unpriced receipts");

        tracker.ledger.newSession();
        same(
                "--",
                row(HudContent.build(tracker), "projectedHourly").value(),
                "A new session makes projection unavailable even in lifetime scope");
        tracker.config.total = false;
        same(
                "--",
                row(HudContent.build(tracker), "hourly").value(),
                "A zero-time session does not show an infinite hourly rate");
    }

    private static void hideAll(HudPreferences preferences) {
        preferences.showTitle = false;
        preferences.showLoot = false;
        preferences.showLootValues = false;
        preferences.showScavenger = false;
        preferences.showCrystalCosts = false;
        preferences.showCallingCosts = false;
        preferences.showKills = false;
        preferences.showTotalProfit = false;
        preferences.showProfitPerHour = false;
        preferences.showProjectedPerHour = false;
        preferences.showRegularProfit = false;
        preferences.showRegularPerHour = false;
        preferences.showActiveTime = false;
        preferences.showScope = false;
        preferences.showStatus = false;
        preferences.showUnpricedWarning = false;
    }

    private static void dimensionsAndHiddenRows(Path directory) {
        Tracker tracker = fixture(directory);
        add(tracker.ledger, Ledger.Kind.LOOT, "ARACHNE_HELMET", 1, 2_000);
        add(tracker.ledger, Ledger.Kind.LOOT, "ARACHNE_BOOTS", 1, 2_000);
        add(tracker.ledger, Ledger.Kind.LOOT, "ARACHNE_FRAGMENT", 1, 500);
        Config config = tracker.config;
        config.hudView = Config.View.DETAILED;
        config.hudPreferences.applyPreset(HudPreferences.Layout.CLASSIC);
        config.hudPreferences.maxLootRows = 8;
        HudContent.Snapshot content = HudContent.build(tracker);
        same(
                12,
                content.rewards().size(),
                "Eight loot rows fit alongside four optional reward statistics");
        same(
                7,
                content.metrics().size(),
                "Every selected summary metric and warning is represented");
        same(224, Hud.panelWidth(config), "Classic uses a single tracker column");
        yes(
                Hud.panelHeight(config) >= 19 + (12 + 7) * 11 + 3,
                "Classic bounds cover all rows and the group separation");

        config.hudPreferences.layout = HudPreferences.Layout.SPLIT;
        same(456, Hud.panelWidth(config), "Split provides two full columns with a gap");
        yes(
                Hud.panelHeight(config) >= 19 + 12 * 11,
                "Split bounds cover the longer selected column");
        int splitHeight = Hud.panelHeight(config);
        config.hudPreferences.layout = HudPreferences.Layout.CLASSIC;
        yes(splitHeight < Hud.panelHeight(config), "Split shortens a fully populated tracker");
        config.hudPreferences.layout = HudPreferences.Layout.MINIMAL;
        same(200, Hud.panelWidth(config), "Minimal uses the narrower summary column");

        int[][] viewports = {{320, 180}, {427, 240}, {640, 360}, {1280, 720}};
        for (HudPreferences.Layout layout : HudPreferences.Layout.values()) {
            config.hudPreferences.layout = layout;
            config.hudScale = 1.6;
            for (int[] viewport : viewports) {
                for (double position : new double[] {-1, 0, 0.5, 1}) {
                    config.hudX = position;
                    config.hudY = position;
                    Hud.Bounds bounds = Hud.bounds(config, viewport[0], viewport[1]);
                    yes(
                            bounds.x() >= 4 && bounds.y() >= 4,
                            layout + " preserves the viewport's leading margin");
                    yes(
                            bounds.x() + bounds.width() <= viewport[0] - 4
                                    && bounds.y() + bounds.height() <= viewport[1] - 4,
                            layout + " fits all rows at scale and position " + position);
                    yes(
                            bounds.contains(bounds.x(), bounds.y())
                                    && !bounds.contains(bounds.x() + bounds.width(), bounds.y()),
                            layout
                                    + " editor bounds include the leading edge and exclude the trailing edge");
                }
            }
        }

        config.hudPreferences.layout = HudPreferences.Layout.SPLIT;
        hideAll(config.hudPreferences);
        config.hudPreferences.showTotalProfit = true;
        same(
                224,
                Hud.panelWidth(config),
                "Split collapses unused rewards into a single metric column");
        hideAll(config.hudPreferences);
        config.hudPreferences.showKills = true;
        same(
                224,
                Hud.panelWidth(config),
                "Split collapses unused metrics into a single reward column");
        hideAll(config.hudPreferences);
        content = HudContent.build(tracker);
        yes(
                content.metrics().isEmpty() && content.rewards().isEmpty(),
                "All-hidden settings create no display rows");
        same(16, Hud.panelHeight(config), "All-hidden settings retain only a small editor target");

        config.hudView = Config.View.GRAPH;
        int graphHeight = Hud.panelHeight(config);
        config.hudPreferences.applyPreset(HudPreferences.Layout.CLASSIC);
        config.hudPreferences.maxLootRows = 8;
        same(
                graphHeight,
                Hud.panelHeight(config),
                "Text row switches never change graph HUD dimensions");
        same(224, Hud.panelWidth(config), "Graph width remains independent of the text layout");
    }

    private static void cyclingPreservesChoices(Path directory) throws Exception {
        Tracker tracker = fixture(directory);
        Config config = tracker.config;
        HudPreferences preferences = config.hudPreferences;
        preferences.showLoot = true;
        preferences.showProfitPerHour = false;
        preferences.showStatus = false;
        preferences.maxLootRows = 8;
        preferences.sort = HudPreferences.Sort.NAME;
        preferences.hiddenItems.add("SOUL_STRING");
        preferences.rowOrder = HudRowOrder.move(preferences.rowOrder, "profit", 1);
        preferences.showRegularProfit = true;
        config.graph.showSpawns = true;
        config.hudX = 0.3;
        config.hudY = 0.7;
        config.hudScale = 1.25;
        List<Ledger.Entry> receipts = List.copyOf(tracker.ledger.entries);
        HudContent.Snapshot initial = HudContent.build(tracker);
        boolean initialHourly = preferences.showProfitPerHour;
        List<String> initialOrder = List.copyOf(preferences.rowOrder);

        List<HudPreferences.Layout> expectedLayouts =
                List.of(
                        HudPreferences.Layout.CLASSIC,
                        HudPreferences.Layout.SPLIT,
                        HudPreferences.Layout.LOOT,
                        HudPreferences.Layout.LOOT,
                        HudPreferences.Layout.MINIMAL);
        for (int cycle = 0; cycle < expectedLayouts.size(); cycle++) {
            tracker.cycleView();
            same(
                    expectedLayouts.get(cycle),
                    preferences.layout,
                    "HUD cycling selects its expected arrangement");
            same(
                    cycle == 3 ? Config.View.GRAPH : Config.View.DETAILED,
                    config.hudView,
                    "HUD cycling includes graph and returns to text");
            if (preferences.layout != HudPreferences.Layout.LOOT) {
                same(
                        initial,
                        HudContent.build(tracker),
                        "Arrangement cycling preserves every selected text row and item");
            } else {
                same(
                        initial.rewards().stream().map(HudContent.Row::id).toList(),
                        HudContent.build(tracker).rewards().stream()
                                .map(HudContent.Row::id)
                                .toList(),
                        "Loot arrangement preserves selected rewards and their order");
                same(
                        initialHourly,
                        preferences.showProfitPerHour,
                        "Loot arrangement preserves the hourly visibility choice");
            }
            same(
                    HudPreferences.Sort.NAME,
                    preferences.sort,
                    "Arrangement cycling preserves loot sorting");
            same(8, preferences.maxLootRows, "Arrangement cycling preserves the loot row limit");
            yes(
                    preferences.hiddenItems.contains("SOUL_STRING"),
                    "Arrangement cycling preserves item exclusions");
            same(
                    receipts,
                    tracker.ledger.entries,
                    "Arrangement cycling never rewrites financial entries");
            yes(config.graph.showSpawns, "Arrangement cycling preserves graph configuration");
            same(
                    initialOrder,
                    preferences.rowOrder,
                    "Arrangement cycling preserves custom stat order");
        }
        same(0.3, config.hudX, "Arrangement cycling preserves horizontal position");
        same(0.7, config.hudY, "Arrangement cycling preserves vertical position");
        same(1.25, config.hudScale, "Arrangement cycling preserves HUD scale");
        Config reopened =
                Store.read(
                        directory.resolve("settings.json"),
                        Config.class,
                        Config::new,
                        Config::validate);
        same(
                HudPreferences.Layout.MINIMAL,
                reopened.hudPreferences.layout,
                "The final arrangement persists");
        yes(
                reopened.hudPreferences.showLoot
                        && !reopened.hudPreferences.showProfitPerHour
                        && !reopened.hudPreferences.showStatus,
                "Customized row flags survive cycling and reload");
        yes(
                reopened.hudPreferences.hiddenItems.contains("SOUL_STRING"),
                "Individual item exclusions survive cycling and reload");
        same(
                initialOrder,
                reopened.hudPreferences.rowOrder,
                "Custom stat order survives cycling and reload");
        yes(
                reopened.hudPreferences.showRegularProfit,
                "Optional profit-without-RNG row survives cycling and reload");
    }

    private static void customRowOrder(Path directory) {
        Tracker tracker = fixture(directory);
        HudPreferences preferences = tracker.config.hudPreferences;
        preferences.applyPreset(HudPreferences.Layout.CLASSIC);
        preferences.maxLootRows = 8;
        preferences.rowOrder = List.of("kills", "status", "scavenger", "profit", "loot", "hourly");
        List<Ledger.Entry> receipts = List.copyOf(tracker.ledger.entries);
        Ledger.Stats stats = tracker.ledger.stats(false);
        long revision = tracker.ledger.revision();
        HudContent.Snapshot ordered = HudContent.build(tracker);
        same(
                List.of(
                        "kills",
                        "scavenger",
                        "loot:SOUL_STRING",
                        "loot:LUXURIOUS_SPOOL",
                        "loot:SPIDER_EYE",
                        "loot:STRING",
                        "loot:ARACHNE_FANG",
                        "crystalCosts",
                        "callingCosts"),
                ordered.rewards().stream().map(HudContent.Row::id).toList(),
                "Custom reward order moves the loot block while preserving its item sort order");
        same(
                List.of(
                        "status",
                        "profit",
                        "hourly",
                        "projectedHourly",
                        "activeTime",
                        "scope",
                        "unpriced"),
                ordered.metrics().stream().map(HudContent.Row::id).toList(),
                "Custom summary order stays separate from reward keys in the same saved list");
        for (HudPreferences.Layout layout : HudPreferences.Layout.values()) {
            preferences.layout = layout;
            HudContent.Snapshot actual = HudContent.build(tracker);
            same(
                    ordered.rewards().stream().map(HudContent.Row::id).toList(),
                    actual.rewards().stream().map(HudContent.Row::id).toList(),
                    "Changing to " + layout + " preserves the selected reward row order");
            same(
                    ordered.metrics().stream()
                            .map(HudContent.Row::id)
                            .filter(
                                    id ->
                                            layout != HudPreferences.Layout.LOOT
                                                    || !id.equals("hourly"))
                            .toList(),
                    actual.metrics().stream().map(HudContent.Row::id).toList(),
                    "Changing to "
                            + layout
                            + " preserves summary order while combining hourly profit");
        }
        preferences.layout = HudPreferences.Layout.CLASSIC;
        preferences.showKills = false;
        preferences.hiddenItems.add("SOUL_STRING");
        same(
                "scavenger",
                HudContent.build(tracker).rewards().getFirst().id(),
                "Hiding the first row lets the next ordered row lead");
        preferences.showKills = true;
        preferences.hiddenItems.clear();
        same(
                ordered,
                HudContent.build(tracker),
                "Re-enabling hidden rows restores their saved positions");
        preferences.rowOrder = List.of("emptyLoot", "unknown", "status", "status", "profit");
        same(
                "status",
                HudContent.build(tracker).metrics().getFirst().id(),
                "Damaged unsaved order cannot duplicate or suppress rows");
        same(
                receipts,
                tracker.ledger.entries,
                "Ordering and hiding blocks never alter journal receipts");
        same(
                stats,
                tracker.ledger.stats(false),
                "Custom row order leaves accounting totals unchanged");
        same(
                revision,
                tracker.ledger.revision(),
                "Custom row order does not invalidate accounting history");

        Tracker empty = emptyTracker(directory.resolve("empty"));
        empty.config.hudPreferences.showLoot = true;
        empty.config.hudPreferences.showKills = true;
        empty.config.hudPreferences.rowOrder = List.of("kills", "loot");
        same(
                List.of("kills", "emptyLoot"),
                HudContent.build(empty).rewards().stream().map(HudContent.Row::id).toList(),
                "The empty-loot placeholder follows the same movable block order");
    }

    private static String compactText(HudContent.Row row) {
        return row.parts().stream().map(HudContent.Part::text).reduce("", String::concat);
    }

    private static FightRecord countedFight(Ledger ledger) {
        FightRecord fight = ledger.beginFight(BASE + ledger.activeMillis, 10_000);
        advance(ledger, 10_000);
        fight.died = BASE + ledger.activeMillis;
        fight.activeEnd = ledger.activeMillis;
        fight.damage = 100_000;
        fight.outcome = FightRecord.Outcome.COUNTED;
        ledger.add(Ledger.Kind.KILL, "ARACHNE", 1, 0, "server", fight.died, fight.id);
        return fight;
    }

    private static void lootLedgerContent(Path directory) {
        Tracker tracker = emptyTracker(directory);
        HudPreferences preferences = tracker.config.hudPreferences;
        preferences.applyPreset(HudPreferences.Layout.LOOT);
        FightRecord epic = countedFight(tracker.ledger);
        tracker.ledger.add(
                Ledger.Kind.LOOT, "TARANTULA_EPIC", 2, 2_000, "pet_claim", epic.died, epic.id);
        FightRecord legendary = countedFight(tracker.ledger);
        tracker.ledger.add(
                Ledger.Kind.LOOT,
                "TARANTULA_LEGENDARY",
                1,
                100_000,
                "armor_stand",
                legendary.died,
                legendary.id);
        FightRecord fang = countedFight(tracker.ledger);
        tracker.ledger.add(
                Ledger.Kind.LOOT, "ARACHNE_FANG", 3, 500, "armor_stand", fang.died, fang.id);
        add(tracker.ledger, Ledger.Kind.LOOT, "SOUL_STRING", 2, 5_000);
        add(tracker.ledger, Ledger.Kind.INCOME, PurseCoins.ITEM, 1, 317);
        tracker.ledger.add(
                Ledger.Kind.LOOT,
                "ESSENCE_SPIDER",
                8,
                0,
                "armor_stand",
                BASE + tracker.ledger.activeMillis,
                0,
                true);
        List<Ledger.Entry> receipts = List.copyOf(tracker.ledger.entries);
        Ledger.Stats totals = tracker.ledger.stats(false);
        HudContent.Snapshot content = HudContent.build(tracker);
        HudContent.Row pet = row(content, "loot:TARANTULA_EPIC");
        same(
                "4.0k",
                pet.value(),
                "The leading item amount is total recorded revenue, not unit price");
        same(
                "Tarantula Pet (Epic): 2 (66.67%)",
                compactText(pet), "Epic quantity has its own observed rate over qualifying kills");
        same(
                "Tarantula Pet (Legendary): 1 (33.33%)",
                compactText(row(content, "loot:TARANTULA_LEGENDARY")),
                "Legendary rarity has an independent observed rate");
        same(
                "Arachne's Fang: 3 (100.00%)",
                compactText(row(content, "loot:ARACHNE_FANG")),
                "Fang rates use detected quantities");
        same(
                Hud.itemColor("TARANTULA_EPIC"),
                pet.parts().getFirst().color(),
                "Pet names use rarity color");
        same(0xFF55FFFF, pet.parts().get(1).color(), "Quantities have a separate cyan color");
        immutable(() -> pet.parts().clear(), "Colored row segments are immutable");
        same(
                "Soul String: 2",
                compactText(row(content, "loot:SOUL_STRING")),
                "Ordinary materials have no observed rare-rate suffix");
        same(
                "",
                row(content, "loot:ESSENCE_SPIDER").value(),
                "Excluded essence never invents a coin value");
        same(
                "Spider Essence: 8",
                compactText(row(content, "loot:ESSENCE_SPIDER")),
                "Zero-valued loot retains its quantity");
        same(
                "3 [360.0/hr]",
                row(content, "kills").value(),
                "Boss totals combine their active hourly rate");
        same(
                "Coins",
                row(content, "scavenger").label(),
                "The compact coins row is Scavenger income");
        same("317", row(content, "scavenger").value(), "Coins retain their recorded subtotal");
        same(
                "Playtime",
                row(content, "activeTime").label(),
                "Compact playtime still means active time");
        yes(
                row(content, "profit").value().endsWith("/hr]"),
                "Profit and its hourly rate share a compact line");
        yes(
                content.metrics().stream().noneMatch(metric -> metric.id().equals("hourly")),
                "An inline hourly rate does not create a duplicate summary row");
        same(
                300,
                Hud.panelWidth(tracker.config),
                "The ledger accommodates a leading subtotal and rare suffix");

        preferences.showTotalProfit = false;
        same(
                Format.coins(totals.hourly()),
                row(HudContent.build(tracker), "hourly").value(),
                "Hiding profit keeps independently enabled hourly profit");
        preferences.showTotalProfit = true;
        preferences.showProfitPerHour = false;
        same(
                Format.coins(totals.profit()),
                row(HudContent.build(tracker), "profit").value(),
                "Hourly profit can be hidden independently");
        preferences.showKillsPerHour = false;
        same(
                "3",
                row(HudContent.build(tracker), "kills").value(),
                "Boss hourly rate can be hidden independently");
        preferences.showRareRates = false;
        same(
                "Tarantula Pet (Epic): 2",
                compactText(row(HudContent.build(tracker), "loot:TARANTULA_EPIC")),
                "Rare percentages can be hidden without hiding quantities");
        preferences.showLootValues = false;
        same(
                "",
                row(HudContent.build(tracker), "loot:TARANTULA_EPIC").value(),
                "Item coin subtotals can be hidden independently");
        same(
                receipts,
                tracker.ledger.entries,
                "The ledger layout never changes financial receipts");
        same(totals, tracker.ledger.stats(false), "The ledger layout preserves recorded totals");

        Tracker noKills = emptyTracker(directory.resolve("no-kills"));
        noKills.config.hudPreferences.applyPreset(HudPreferences.Layout.LOOT);
        add(noKills.ledger, Ledger.Kind.LOOT, "TARANTULA_EPIC", 1, 2_000);
        same(
                "Tarantula Pet (Epic): 1",
                compactText(row(HudContent.build(noKills), "loot:TARANTULA_EPIC")),
                "An unknown denominator never displays an invented percentage");
        same(
                "0 [--/hr]",
                row(HudContent.build(noKills), "kills").value(),
                "An empty active clock never displays an infinite boss rate");
    }

    private static void profitWithoutRng(Path directory) {
        Tracker tracker = fixture(directory);
        add(tracker.ledger, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 1_000_000);
        add(tracker.ledger, Ledger.Kind.LOOT, "TARANTULA_EPIC", 1, 100_000);
        add(tracker.ledger, Ledger.Kind.LOOT, "ARACHNE_FANG", 2, 5_000);
        HudPreferences preferences = tracker.config.hudPreferences;
        int baseHeight = Hud.panelHeight(tracker.config);
        preferences.showRegularProfit = true;
        preferences.showRegularPerHour = true;
        preferences.rowOrder = List.of("regularProfit", "regularHourly", "profit", "hourly");
        List<Ledger.Entry> receipts = List.copyOf(tracker.ledger.entries);
        Ledger.Stats stats = tracker.ledger.stats(false);
        HudContent.Snapshot content = HudContent.build(tracker);
        same(
                "Profit without RNG",
                row(content, "regularProfit").label(),
                "Ordinary-profit label explains the excluded income");
        same(
                Format.coins(9_733),
                row(content, "regularProfit").value(),
                "Ordinary profit excludes both pets and Fangs, retaining Scavenger and all costs");
        same(
                Format.coins(583_980),
                row(content, "regularHourly").value(),
                "Ordinary hourly rate uses the same selected active time");
        same(
                Format.coins(1_119_733),
                row(content, "profit").value(),
                "Total profit still includes every recorded RNG drop");
        same(
                List.of("regularProfit", "regularHourly", "profit", "hourly"),
                content.metrics().stream().map(HudContent.Row::id).limit(4).toList(),
                "Ordinary-profit rows obey the user's summary order");
        same(
                baseHeight + 22,
                Hud.panelHeight(tracker.config),
                "Both new metrics reserve exactly two additional HUD rows");
        preferences.hiddenItems.add("TARANTULA_LEGENDARY");
        preferences.hiddenItems.add("SOUL_STRING");
        same(
                content,
                HudContent.build(tracker),
                "Item display filters do not change either profit summary");
        same(
                receipts,
                tracker.ledger.entries,
                "Displaying profit without RNG never rewrites receipts");
        same(
                stats,
                tracker.ledger.stats(false),
                "Displaying profit without RNG never changes total profit");
        preferences.showRegularProfit = false;
        yes(
                allRows(HudContent.build(tracker)).stream()
                        .noneMatch(row -> row.id().equals("regularProfit")),
                "Ordinary total can be hidden independently of its rate");
        same(
                Format.coins(583_980),
                row(HudContent.build(tracker), "regularHourly").value(),
                "Hiding ordinary total keeps ordinary hourly visible");

        tracker.ledger.newSession();
        preferences.showRegularProfit = true;
        same(
                "0",
                row(HudContent.build(tracker), "regularProfit").value(),
                "A new session clears ordinary profit in session scope");
        same(
                "--",
                row(HudContent.build(tracker), "regularHourly").value(),
                "A zero-time session shows no ordinary hourly rate");
        add(tracker.ledger, Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 1, 1_000);
        add(tracker.ledger, Ledger.Kind.LOOT, "TARANTULA_LEGENDARY", 1, 2_000_000);
        advance(tracker.ledger, 60_000);
        content = HudContent.build(tracker);
        same(
                Format.coins(-1_000),
                row(content, "regularProfit").value(),
                "An RNG-only session still includes its summon cost as an ordinary loss");
        same(
                Graph.RED,
                row(content, "regularProfit").valueColor(),
                "Ordinary losses use loss coloring");
        tracker.config.total = true;
        same(
                Format.coins(8_733),
                row(HudContent.build(tracker), "regularProfit").value(),
                "Lifetime ordinary profit includes earlier ordinary revenue and both sessions' costs");
        same(
                Format.coins(261_990),
                row(HudContent.build(tracker), "regularHourly").value(),
                "Lifetime ordinary rate uses both sessions' active time");
    }
}

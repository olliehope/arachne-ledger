package dev.arachneledger.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import dev.arachneledger.config.Config;
import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.pricing.BazaarPrices;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.Format;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builds the client command tree around an explicit tracker dependency. Commands execute on the
 * client thread; screen requests are callbacks so navigation remains a lifecycle concern. Add
 * commands to the relevant group below rather than adding UI or packet logic here.
 */
public final class LedgerCommands {
    public record Actions(
            Runnable dashboard,
            Runnable hudEditor,
            Runnable refreshContext,
            Runnable diagnostics,
            Consumer<String> message,
            Runnable settings) {
        /** Retains the existing embedding API while clients adopt the separate settings route. */
        public Actions(
                Runnable dashboard,
                Runnable hudEditor,
                Runnable refreshContext,
                Runnable diagnostics,
                Consumer<String> message) {
            this(dashboard, hudEditor, refreshContext, diagnostics, message, dashboard);
        }
    }

    private final Tracker tracker;
    private final Actions actions;
    private List<String> pendingFeedback;

    private LedgerCommands(Tracker tracker, Actions actions) {
        this.tracker = Objects.requireNonNull(tracker);
        this.actions = Objects.requireNonNull(actions);
    }

    /** Register once at startup; Fabric rebuilds this tree when its command callback fires. */
    public static void register(Tracker tracker, Actions actions) {
        new LedgerCommands(tracker, actions).register();
    }

    private void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, access) -> {
                    var root =
                            literal("arachne")
                                    .executes(
                                            c -> {
                                                actions.dashboard().run();
                                                return 1;
                                            });
                    displayCommands(root);
                    trackingCommands(root);
                    notificationCommands(root);
                    priceCommands(root);
                    adjustmentCommands(root);
                    storageCommands(root);
                    helpCommand(root);
                    dispatcher.register(root);
                });
    }

    private void displayCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(
                literal("settings")
                        .executes(
                                c -> {
                                    actions.settings().run();
                                    return 1;
                                }));
        root.then(
                literal("dashboard")
                        .executes(
                                c -> {
                                    actions.dashboard().run();
                                    return 1;
                                }));
        root.then(
                literal("session")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.config.total = false;
                                                    tracker.saveConfig();
                                                    say("Showing session.");
                                                })));
        root.then(
                literal("total")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.config.total = true;
                                                    tracker.saveConfig();
                                                    say("Showing lifetime totals.");
                                                })));
        root.then(literal("view").executes(c -> run(this::cycleView)));

        var hud = literal("hud").executes(c -> run(() -> setHud(!tracker.config.hud)));
        hud.then(
                literal("edit")
                        .executes(
                                c -> {
                                    actions.hudEditor().run();
                                    return 1;
                                }));
        hud.then(literal("view").executes(c -> run(this::cycleView)));
        hud.then(
                literal("always")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.config.hudAlwaysShow =
                                                            !tracker.config.hudAlwaysShow;
                                                    tracker.saveConfig();
                                                    say(
                                                            tracker.config.hudAlwaysShow
                                                                    ? "HUD visible throughout SkyBlock, including waiting status."
                                                                    : "HUD visible only while tracking Arachne.");
                                                })));
        for (String option : new String[] {"on", "off"}) {
            hud.then(literal(option).executes(c -> run(() -> setHud(option.equals("on")))));
        }
        root.then(hud);
    }

    private void setHud(boolean enabled) {
        tracker.config.hud = enabled;
        tracker.saveConfig();
        say("HUD " + (enabled ? "enabled" : "hidden"));
    }

    private void cycleView() {
        tracker.cycleView();
        say(
                "HUD: "
                        + (tracker.config.hudView == Config.View.GRAPH
                                ? "Graph"
                                : tracker.config.hudPreferences.layout.label()));
    }

    private void trackingCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(
                literal("pause")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.togglePause();
                                                    say(tracker.status());
                                                })));
        var track = literal("track");
        for (String option : new String[] {"auto", "manual"}) {
            track.then(
                    literal(option)
                            .executes(
                                    c ->
                                            run(
                                                    () -> {
                                                        tracker.config.manualTracking =
                                                                option.equals("manual");
                                                        tracker.saveConfig();
                                                        actions.refreshContext().run();
                                                        say(
                                                                tracker.config.manualTracking
                                                                        ? "Manual tracking enabled anywhere in SkyBlock. Use /arachne track auto when finished."
                                                                        : "Automatic Sanctuary detection enabled.");
                                                    })));
        }
        root.then(track);
        root.then(literal("debug").executes(c -> runReadOnly(actions.diagnostics())));
        root.then(
                literal("mindamage")
                        .then(
                                argument("damage", LongArgumentType.longArg(1, 1_000_000_000_000L))
                                        .executes(
                                                c ->
                                                        run(
                                                                () -> {
                                                                    tracker.config.minimumDamage =
                                                                            LongArgumentType
                                                                                    .getLong(
                                                                                            c,
                                                                                            "damage");
                                                                    tracker.saveConfig();
                                                                    say(
                                                                            "Kills require at least "
                                                                                    + String.format(
                                                                                            Locale
                                                                                                    .ROOT,
                                                                                            "%,d",
                                                                                            tracker.config
                                                                                                    .minimumDamage)
                                                                                    + " damage.");
                                                                }))));
    }

    private void notificationCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var chat =
                literal("chat")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.config.killChat =
                                                            !tracker.config.killChat;
                                                    tracker.saveConfig();
                                                    say(
                                                            "Kill summaries "
                                                                    + (tracker.config.killChat
                                                                            ? "on"
                                                                            : "off")
                                                                    + ".");
                                                }));
        for (String option : new String[] {"on", "off"}) {
            chat.then(
                    literal(option)
                            .executes(
                                    c ->
                                            run(
                                                    () -> {
                                                        tracker.config.killChat =
                                                                option.equals("on");
                                                        tracker.saveConfig();
                                                        say("Kill summaries " + option + ".");
                                                    })));
        }
        root.then(chat);

        var rng =
                literal("rng")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.config.rngTitles =
                                                            !tracker.config.rngTitles;
                                                    tracker.saveConfig();
                                                    say(
                                                            "Rare drop titles "
                                                                    + (tracker.config.rngTitles
                                                                            ? "on"
                                                                            : "off")
                                                                    + ".");
                                                }));
        for (String option : new String[] {"on", "off"}) {
            rng.then(
                    literal(option)
                            .executes(
                                    c ->
                                            run(
                                                    () -> {
                                                        tracker.config.rngTitles =
                                                                option.equals("on");
                                                        tracker.saveConfig();
                                                        say("Rare drop titles " + option + ".");
                                                    })));
        }
        rng.then(
                literal("value")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.config.rngValue =
                                                            !tracker.config.rngValue;
                                                    tracker.saveConfig();
                                                    say(
                                                            "Rare drop title values "
                                                                    + (tracker.config.rngValue
                                                                            ? "shown"
                                                                            : "hidden")
                                                                    + ".");
                                                })));
        var preview =
                literal("test")
                        .executes(c -> runReadOnly(() -> previewRareDrop("TARANTULA_LEGENDARY")));
        for (String name : new String[] {"epic", "legendary", "fang"}) {
            String item =
                    name.equals("fang")
                            ? "ARACHNE_FANG"
                            : "TARANTULA_" + name.toUpperCase(Locale.ROOT);
            preview.then(literal(name).executes(c -> runReadOnly(() -> previewRareDrop(item))));
        }
        rng.then(preview);
        root.then(rng);
    }

    private void previewRareDrop(String item) {
        if (!tracker.config.rngTitles) {
            say("Rare drop titles are off. Enable them with /arachne rng on first.");
            return;
        }
        boolean queued =
                tracker.rng.notice(
                        item, 1, tracker.config.lootPrice(item), System.currentTimeMillis());
        say(
                queued
                        ? "Rare drop title preview; no loot recorded."
                        : "Title queue is full; try again shortly.");
    }

    private void priceCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var bazaar =
                literal("bazaar")
                        .executes(
                                c ->
                                        runReadOnly(
                                                () ->
                                                        say(
                                                                BazaarPrices.GLOBAL.status(
                                                                        tracker.config,
                                                                        System
                                                                                .currentTimeMillis()))));
        for (String option : new String[] {"on", "off"}) {
            bazaar.then(
                    literal(option)
                            .executes(
                                    c ->
                                            run(
                                                    () -> {
                                                        tracker.config.autoBazaar =
                                                                option.equals("on");
                                                        tracker.saveConfig();
                                                        say(
                                                                "Automatic Bazaar prices "
                                                                        + option
                                                                        + "; manual overrides remain in use.");
                                                    })));
        }
        bazaar.then(
                literal("defaults")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.config.useBazaarItems();
                                                    tracker.saveConfig();
                                                    say(
                                                            "Bazaar items now use automatic prices; other values stay manual.");
                                                })));
        bazaar.then(
                literal("refresh")
                        .executes(
                                c ->
                                        run(
                                                () ->
                                                        say(
                                                                BazaarPrices.GLOBAL.requestRefresh(
                                                                                tracker.config,
                                                                                System
                                                                                        .currentTimeMillis())
                                                                        ? "Refreshing Bazaar prices."
                                                                        : "Enable Bazaar prices first; refreshes are limited to once every five minutes."))));
        root.then(bazaar);

        root.then(
                literal("crystal")
                        .then(
                                argument("cost", DoubleArgumentType.doubleArg(0, 1e12))
                                        .executes(
                                                c ->
                                                        run(
                                                                () -> {
                                                                    tracker.config.crystalCost =
                                                                            DoubleArgumentType
                                                                                    .getDouble(
                                                                                            c,
                                                                                            "cost");
                                                                    tracker.config
                                                                                    .crystalConfigured =
                                                                            true;
                                                                    tracker.saveConfig();
                                                                    say(
                                                                            "Future crystals: "
                                                                                    + Format.coins(
                                                                                            tracker.config
                                                                                                    .crystalCost)
                                                                                    + " coins each.");
                                                                }))));
        root.then(
                literal("recipe")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.config.crystalConfigured = false;
                                                    tracker.saveConfig();
                                                    say(
                                                            "Future crystal costs use the Shaggy recipe.");
                                                })));
        root.then(
                literal("price")
                        .then(
                                argument("item", StringArgumentType.word())
                                        .suggests(
                                                (c, builder) -> {
                                                    Catalog.ITEMS
                                                            .keySet()
                                                            .forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                        .then(
                                                argument(
                                                                "coins",
                                                                DoubleArgumentType.doubleArg(
                                                                        0, 1e12))
                                                        .executes(
                                                                c ->
                                                                        run(
                                                                                () -> {
                                                                                    String id =
                                                                                            knownItem(
                                                                                                    StringArgumentType
                                                                                                            .getString(
                                                                                                                    c,
                                                                                                                    "item"));
                                                                                    tracker.config
                                                                                            .manualSet(
                                                                                                    id,
                                                                                                    DoubleArgumentType
                                                                                                            .getDouble(
                                                                                                                    c,
                                                                                                                    "coins"));
                                                                                    tracker
                                                                                            .saveConfig();
                                                                                    say(
                                                                                            "Future "
                                                                                                    + Catalog
                                                                                                            .name(
                                                                                                                    id)
                                                                                                    + " manual price saved.");
                                                                                })))));
    }

    private void adjustmentCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(
                literal("add")
                        .then(
                                argument("item", StringArgumentType.word())
                                        .suggests(
                                                (c, builder) -> {
                                                    Catalog.ITEMS
                                                            .keySet()
                                                            .forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                        .then(
                                                argument(
                                                                "count",
                                                                IntegerArgumentType.integer(
                                                                        1, 1_000_000))
                                                        .executes(
                                                                c ->
                                                                        run(
                                                                                () -> {
                                                                                    String id =
                                                                                            knownItem(
                                                                                                    StringArgumentType
                                                                                                            .getString(
                                                                                                                    c,
                                                                                                                    "item"));
                                                                                    tracker.record(
                                                                                            Ledger
                                                                                                    .Kind
                                                                                                    .LOOT,
                                                                                            id,
                                                                                            IntegerArgumentType
                                                                                                    .getInteger(
                                                                                                            c,
                                                                                                            "count"),
                                                                                            tracker
                                                                                                    .config
                                                                                                    .lootPrice(
                                                                                                            id),
                                                                                            "manual",
                                                                                            System
                                                                                                    .currentTimeMillis());
                                                                                    tracker.save();
                                                                                    say(
                                                                                            "Loot adjustment recorded.");
                                                                                })))));
        for (String kind : new String[] {"income", "expense"}) {
            root.then(
                    literal(kind)
                            .then(
                                    argument("coins", DoubleArgumentType.doubleArg(0, 1e12))
                                            .executes(
                                                    c ->
                                                            run(
                                                                    () -> {
                                                                        tracker.record(
                                                                                kind.equals(
                                                                                                "income")
                                                                                        ? Ledger
                                                                                                .Kind
                                                                                                .INCOME
                                                                                        : Ledger
                                                                                                .Kind
                                                                                                .EXPENSE,
                                                                                "MANUAL",
                                                                                1,
                                                                                DoubleArgumentType
                                                                                        .getDouble(
                                                                                                c,
                                                                                                "coins"),
                                                                                "manual",
                                                                                System
                                                                                        .currentTimeMillis());
                                                                        tracker.save();
                                                                        say("Adjustment recorded.");
                                                                    }))));
        }
    }

    private static String knownItem(String supplied) {
        String id = supplied.toUpperCase(Locale.ROOT);
        if (!Catalog.ITEMS.containsKey(id)) {
            throw new IllegalArgumentException("Unknown item ID.");
        }
        return id;
    }

    private void storageCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(
                literal("new")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    tracker.newSession();
                                                    say(
                                                            "New session started. Lifetime totals preserved.");
                                                })));
        root.then(
                literal("undo")
                        .executes(
                                c ->
                                        run(
                                                () ->
                                                        say(
                                                                tracker.undo()
                                                                        ? "Last session entry removed."
                                                                        : "No session entry to undo."))));
        root.then(
                literal("profile")
                        .then(
                                argument("name", StringArgumentType.word())
                                        .executes(
                                                c ->
                                                        run(
                                                                () -> {
                                                                    tracker.profile(
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "name"));
                                                                    say(
                                                                            "Ledger: "
                                                                                    + tracker.config
                                                                                            .profile);
                                                                }))));
        root.then(
                literal("export")
                        .executes(
                                c ->
                                        run(
                                                () -> {
                                                    try {
                                                        say("Saved " + tracker.export());
                                                    } catch (java.io.IOException ex) {
                                                        throw new IllegalStateException(
                                                                ex.getMessage());
                                                    }
                                                })));
    }

    private void helpCommand(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(
                literal("help")
                        .executes(
                                c ->
                                        runReadOnly(
                                                () -> {
                                                    say(
                                                            "/arachne opens the dashboard. O is the default key.");
                                                    say(
                                                            "settings | session | total | view | pause | new | undo | export | profile <name>");
                                                    say(
                                                            "hud [on|off|edit|view|always] | track auto/manual | debug");
                                                    say(
                                                            "chat [on|off] | mindamage <damage> (default 10,000)");
                                                    say(
                                                            "bazaar [on|off|defaults|refresh] | rng [on|off|value|test [epic|legendary|fang]]");
                                                    say(
                                                            "crystal <cost> | recipe | price <ITEM_ID> <coins> | add <ITEM_ID> <count> | income/expense <coins>");
                                                })));
    }

    /** Expected user errors become local chat feedback instead of escaping Brigadier. */
    private int run(Runnable action) {
        if (!"".equals(tracker.error)) {
            storageError();
            return 0;
        }
        pendingFeedback = new ArrayList<>();
        try {
            action.run();
            if (!"".equals(tracker.error)) {
                storageError();
                return 0;
            }
            pendingFeedback.forEach(actions.message());
            return 1;
        } catch (Exception ex) {
            actions.message().accept(ex.getMessage());
            return 0;
        } finally {
            pendingFeedback = null;
        }
    }

    private int runReadOnly(Runnable action) {
        try {
            action.run();
            return 1;
        } catch (Exception ex) {
            actions.message().accept(ex.getMessage());
            return 0;
        }
    }

    private void storageError() {
        actions.message().accept("Storage error; changes were not saved. See Minecraft log.");
    }

    private void say(String text) {
        // Persistence catches I/O errors inside Tracker. Publish success only after it stayed
        // healthy.
        if (pendingFeedback == null) {
            actions.message().accept(text);
        } else {
            pendingFeedback.add(text);
        }
    }
}

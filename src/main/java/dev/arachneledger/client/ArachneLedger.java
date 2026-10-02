package dev.arachneledger.client;

import dev.arachneledger.pricing.BazaarPrices;
import dev.arachneledger.skyblock.LootLabels;
import dev.arachneledger.skyblock.Messages;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.Hud;
import dev.arachneledger.ui.screen.DashboardScreen;
import dev.arachneledger.ui.screen.HudEditorScreen;
import dev.arachneledger.ui.screen.SettingsScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.ArmorStand;

import org.lwjgl.glfw.GLFW;

/**
 * Fabric entry point and client lifecycle wiring. Tracker mutations happen on the client thread,
 * including packet observations after vanilla has re-dispatched their handlers. Commands, chat
 * formatting and domain decisions live in their focused modules.
 */
public final class ArachneLedger implements ClientModInitializer {
    private static final int CONTEXT_REFRESH_TICKS = 10;
    private static final int REWARD_SCAN_RADIUS = 48;

    /** Shared with render hooks and the pickup mixin; created before those hooks register. */
    public static Tracker tracker;

    private static boolean openRequested;
    private static boolean editRequested;
    private static boolean settingsRequested;
    private static Object lastLevel;
    private int clientTicks;

    @Override
    public void onInitializeClient() {
        tracker = new Tracker(FabricLoader.getInstance().getConfigDir().resolve("arachneledger"));
        var category =
                KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath("arachneledger", "controls"));
        var openDashboardKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping("key.arachneledger.open", GLFW.GLFW_KEY_O, category));
        var toggleScopeKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping("key.arachneledger.scope", GLFW.GLFW_KEY_UNKNOWN, category));
        var cycleHudViewKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping("key.arachneledger.view", GLFW.GLFW_KEY_UNKNOWN, category));
        ClientTickEvents.END_CLIENT_TICK.register(
                client -> tick(client, openDashboardKey, toggleScopeKey, cycleHudViewKey));
        ClientReceiveMessageEvents.GAME.register(ArachneLedger::receive);
        ClientReceiveMessageEvents.GAME_CANCELED.register(ArachneLedger::receive);
        registerConnectionLifecycle();
        registerOverlays();
        registerCommands();
    }

    private static void registerConnectionLifecycle() {
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    tracker.resetContext();
                    tracker.save();
                });
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> {
                    tracker.resetContext();
                    tracker.save();
                    tracker.saveConfig();
                });
    }

    private static void registerCommands() {
        LedgerCommands.register(
                tracker,
                new LedgerCommands.Actions(
                        () -> openRequested = true,
                        () -> editRequested = true,
                        () -> updateContext(Minecraft.getInstance(), System.currentTimeMillis()),
                        ArachneLedger::debug,
                        ArachneLedger::say,
                        () -> settingsRequested = true));
    }

    private void tick(
            Minecraft client,
            KeyMapping openDashboardKey,
            KeyMapping toggleScopeKey,
            KeyMapping cycleHudViewKey) {
        long now = System.currentTimeMillis();
        prepareContext(client, now);
        if (++clientTicks % CONTEXT_REFRESH_TICKS == 0 || client.player == null) {
            updateContext(client, now);
        }
        tracker.tick(now, tracker.refreshArea(now));
        updatePricesAndAlerts(client, now);
        observeRewardLabels(client, now);
        publishKillSummaries(client);
        handleKeybindings(openDashboardKey, toggleScopeKey, cycleHudViewKey);
        openRequestedScreen(client);
    }

    private static void updatePricesAndAlerts(Minecraft client, long now) {
        // Background fetches publish their results here before prices are persisted or used.
        if (BazaarPrices.GLOBAL.tick(tracker.config, now)) {
            tracker.saveConfig();
        }
        if (!tracker.config.rngTitles) {
            tracker.rng.clear();
        }
        tracker.rng.setVisible(canShowRareTitles(client), now);
    }

    private static void publishKillSummaries(Minecraft client) {
        for (var summary : tracker.drainKillSummaries()) {
            if (client.player != null) {
                client.gui.getChat().addClientSystemMessage(killSummaryMessage(summary));
            }
        }
    }

    private static void handleKeybindings(
            KeyMapping openDashboardKey, KeyMapping toggleScopeKey, KeyMapping cycleHudViewKey) {
        while (openDashboardKey.consumeClick()) {
            openRequested = true;
        }
        while (toggleScopeKey.consumeClick()) {
            tracker.toggleScope();
        }
        while (cycleHudViewKey.consumeClick()) {
            tracker.cycleView();
        }
    }

    private static void openRequestedScreen(Minecraft client) {
        // Defer screen changes to a tick so command/message callbacks do not replace a screen
        // mid-event.
        if (settingsRequested) {
            settingsRequested = false;
            editRequested = false;
            openRequested = false;
            client.setScreen(new SettingsScreen(null));
        } else if (editRequested) {
            editRequested = false;
            openRequested = false;
            client.setScreen(new HudEditorScreen(null));
        } else if (openRequested) {
            openRequested = false;
            client.setScreen(new DashboardScreen(null));
        }
    }

    private static void observeRewardLabels(Minecraft client, long now) {
        if (client.player == null || client.level == null || !tracker.acceptsStandLoot(now)) {
            return;
        }
        for (ArmorStand stand :
                client.level.getEntitiesOfClass(
                        ArmorStand.class,
                        client.player.getBoundingBox().inflate(REWARD_SCAN_RADIUS))) {
            Component label = stand.getCustomName();
            if (label != null) {
                tracker.observeLootStand(stand.getUUID(), LootLabels.formatted(label), now);
            }
        }
    }

    private static void registerOverlays() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("arachneledger", "hud"),
                (graphics, delta) -> Hud.draw(graphics));
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("arachneledger", "rng"),
                (graphics, delta) -> {
                    var client = Minecraft.getInstance();
                    if (tracker.config.rngTitles && canShowRareTitles(client)) {
                        tracker.rng.draw(
                                graphics, System.currentTimeMillis(), tracker.config.rngValue);
                    }
                });
    }

    private static boolean canShowRareTitles(Minecraft client) {
        return client.player != null
                && !client.options.hideGui
                && (client.screen == null || client.screen instanceof ChatScreen);
    }

    private static void receive(Component message, boolean overlay) {
        Minecraft client = Minecraft.getInstance();
        if (!overlay && client.player != null) {
            long now = System.currentTimeMillis();
            prepareContext(client, now);
            if (Messages.isArachneCue(message.getString())) {
                updateContext(client, now);
            }
            // Pet receipts carry rarity in their Component colour. getString() erases it.
            tracker.message(
                    LootLabels.formatted(message), client.player.getName().getString(), now);
        }
    }

    /**
     * Packets may arrive before the first tick after a warp. Clear old fight/reward state before
     * recording the new world's events, and load the correct account ledger first. PickupMixin
     * calls this only after vanilla dispatches its packet to the client thread.
     */
    public static void prepareContext(Minecraft client, long now) {
        boolean changed = client.level != lastLevel;
        boolean unloaded = !tracker.ready();
        if (changed) {
            tracker.resetContext();
            tracker.save();
            lastLevel = client.level;
        }
        if (client.player != null) {
            tracker.account(client.player.getUUID().toString());
        }
        // Sidebar refreshes are throttled; container eligibility cannot be, because an
        // NPC menu may open and close before the next purse snapshot is read.
        tracker.observeMenu(client.screen instanceof AbstractContainerScreen<?>, now);
        if (changed || unloaded) {
            updateContext(client, now);
        }
        tracker.refreshArea(now);
    }

    private static GameContext.Snapshot updateContext(Minecraft client, long now) {
        var snapshot = GameContext.snapshot(client);
        tracker.updateLocation(
                snapshot.onHypixel(),
                snapshot.skyBlock(),
                snapshot.sanctuary(),
                snapshot.location(),
                snapshot.detectionReason(),
                now);
        tracker.observePurse(
                snapshot.sidebarLines(), client.screen instanceof AbstractContainerScreen<?>, now);
        return snapshot;
    }

    private static void debug() {
        var snapshot = updateContext(Minecraft.getInstance(), System.currentTimeMillis());
        var file =
                FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve("arachneledger/detection-debug.txt");
        ClientMessages.writeDiagnostics(tracker, snapshot, file);
    }

    /** Compatibility entry point used by screens and integrations for local feedback. */
    public static void say(String text) {
        ClientMessages.say(text);
    }

    /** Preserve the public formatter used by checks and client preview fixtures. */
    public static Component killSummaryMessage(Tracker.KillSummary summary) {
        return ClientMessages.killSummary(summary);
    }
}

package dev.arachneledger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric entry point and client lifecycle wiring. Tracker mutations happen on the client
 * thread, including packet observations after vanilla has re-dispatched their handlers.
 * Commands, chat formatting and domain decisions live in their focused modules.
 */
public final class ArachneLedger implements ClientModInitializer {
    /** Shared with render hooks and the pickup mixin; created before those hooks register. */
    public static Tracker tracker;
    private static boolean openRequested;
    private static boolean editRequested;
    private static Object lastLevel;
    private int ticks;

    @Override public void onInitializeClient() {
        tracker = new Tracker(FabricLoader.getInstance().getConfigDir().resolve("arachneledger"));
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("arachneledger", "controls"));
        var open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arachneledger.open", GLFW.GLFW_KEY_O, category));
        var scope = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arachneledger.scope", GLFW.GLFW_KEY_UNKNOWN, category));
        var view = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.arachneledger.view", GLFW.GLFW_KEY_UNKNOWN, category));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc, open, scope, view));
        ClientReceiveMessageEvents.GAME.register(ArachneLedger::receive);
        ClientReceiveMessageEvents.GAME_CANCELED.register(ArachneLedger::receive);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            tracker.resetContext();
            tracker.save();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            tracker.resetContext();
            tracker.save();
            tracker.saveConfig();
        });
        registerOverlays();
        LedgerCommands.register(tracker, new LedgerCommands.Actions(
            () -> openRequested = true,
            () -> editRequested = true,
            () -> updateContext(Minecraft.getInstance(), System.currentTimeMillis()),
            ArachneLedger::debug,
            ArachneLedger::say));
    }

    private void tick(Minecraft mc, KeyMapping open, KeyMapping scope, KeyMapping view) {
        long now = System.currentTimeMillis();
        prepareContext(mc, now);
        if (++ticks % 10 == 0 || mc.player == null) updateContext(mc, now);
        tracker.tick(now, tracker.refreshArea(now));
        // Background fetches publish their results here before prices are persisted or used.
        if (BazaarPrices.GLOBAL.tick(tracker.config, now)) tracker.saveConfig();
        if (!tracker.config.rngTitles) tracker.rng.clear();
        observeRewardLabels(mc, now);
        for (var summary : tracker.drainKillSummaries()) {
            if (mc.player != null) mc.gui.getChat().addClientSystemMessage(killSummaryMessage(summary));
        }
        while (open.consumeClick()) openRequested = true;
        while (scope.consumeClick()) tracker.toggleScope();
        while (view.consumeClick()) tracker.cycleView();
        // Defer screen changes to a tick so command/message callbacks do not replace a screen mid-event.
        if (editRequested) {
            editRequested = false;
            openRequested = false;
            mc.setScreen(new HudEditorScreen(null));
        } else if (openRequested) {
            openRequested = false;
            mc.setScreen(new DashboardScreen(null));
        }
    }

    private static void observeRewardLabels(Minecraft mc, long now) {
        if (mc.player == null || mc.level == null || !tracker.acceptsStandLoot(now)) return;
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48))) {
            Component label = stand.getCustomName();
            if (label != null) tracker.observeLootStand(stand.getUUID(), LootLabels.formatted(label), now);
        }
    }

    private static void registerOverlays() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("arachneledger", "hud"), (graphics, delta) -> Hud.draw(graphics));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("arachneledger", "rng"), (graphics, delta) -> {
                var mc = Minecraft.getInstance();
                if (tracker.config.rngTitles && mc.player != null && !mc.options.hideGui
                    && (mc.screen == null || mc.screen instanceof ChatScreen)) {
                    tracker.rng.draw(graphics, System.currentTimeMillis(), tracker.config.rngValue);
                }
            });
    }

    private static void receive(Component message, boolean overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (!overlay && mc.player != null) {
            long now = System.currentTimeMillis();
            prepareContext(mc, now);
            if (Messages.isArachneCue(message.getString())) updateContext(mc, now);
            // Pet receipts carry rarity in their Component colour. getString() erases it.
            tracker.message(LootLabels.formatted(message), mc.player.getName().getString(), now);
        }
    }

    /**
     * Packets may arrive before the first tick after a warp. Clear old fight/reward state
     * before recording the new world's events, and load the correct account ledger first.
     * PickupMixin calls this only after vanilla dispatches its packet to the client thread.
     */
    public static void prepareContext(Minecraft mc, long now) {
        boolean changed = mc.level != lastLevel;
        boolean unloaded = !tracker.ready();
        if (changed) {
            tracker.resetContext();
            tracker.save();
            lastLevel = mc.level;
        }
        if (mc.player != null) tracker.account(mc.player.getUUID().toString());
        if (changed || unloaded) updateContext(mc, now);
        tracker.refreshArea(now);
    }

    private static GameContext.Snapshot updateContext(Minecraft mc, long now) {
        var snapshot = GameContext.snapshot(mc);
        tracker.updateLocation(snapshot.onHypixel(), snapshot.skyBlock(), snapshot.sanctuary(),
            snapshot.location(), snapshot.detectionReason(), now);
        tracker.observePurse(snapshot.sidebarLines(), mc.screen instanceof AbstractContainerScreen<?>, now);
        return snapshot;
    }

    private static void debug() {
        var snapshot = updateContext(Minecraft.getInstance(), System.currentTimeMillis());
        var file = FabricLoader.getInstance().getConfigDir().resolve("arachneledger/detection-debug.txt");
        ClientMessages.writeDiagnostics(tracker, snapshot, file);
    }

    /** Compatibility entry point used by screens and integrations for local feedback. */
    public static void say(String text) { ClientMessages.say(text); }

    /** Preserve the public formatter used by checks and client preview fixtures. */
    public static Component killSummaryMessage(Tracker.KillSummary summary) {
        return ClientMessages.killSummary(summary);
    }
}

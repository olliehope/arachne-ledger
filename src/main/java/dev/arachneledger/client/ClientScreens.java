package dev.arachneledger.client;

import dev.arachneledger.ui.screen.AchievementsScreen;
import dev.arachneledger.ui.screen.DashboardScreen;
import dev.arachneledger.ui.screen.DiagnosticsScreen;
import dev.arachneledger.ui.screen.HudEditorScreen;
import dev.arachneledger.ui.screen.RecapScreen;
import dev.arachneledger.ui.screen.SettingsScreen;

import net.minecraft.client.Minecraft;

/** A single deferred request prevents command callbacks from replacing a screen mid-event. */
final class ClientScreens {
    enum Page {
        DASHBOARD,
        HUD,
        SETTINGS,
        ACHIEVEMENTS,
        RECAP,
        DIAGNOSTICS
    }

    private static Page requested;

    static void request(Page page) {
        requested = page;
    }

    static void openRequested(Minecraft client) {
        if (requested == null) return;
        Page page = requested;
        requested = null;
        client.setScreen(
                switch (page) {
                    case DASHBOARD -> new DashboardScreen(null);
                    case HUD -> new HudEditorScreen(null);
                    case SETTINGS -> new SettingsScreen(null);
                    case ACHIEVEMENTS -> new AchievementsScreen(null);
                    case RECAP -> new RecapScreen(null);
                    case DIAGNOSTICS -> new DiagnosticsScreen(null);
                });
    }

    private ClientScreens() {}
}

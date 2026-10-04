package dev.arachneledger.client;

import dev.arachneledger.ui.screen.AchievementsScreen;
import dev.arachneledger.ui.screen.DashboardScreen;
import dev.arachneledger.ui.screen.DiagnosticsScreen;
import dev.arachneledger.ui.screen.GoalsScreen;
import dev.arachneledger.ui.screen.HudEditorScreen;
import dev.arachneledger.ui.screen.ProgressScreen;
import dev.arachneledger.ui.screen.RecapScreen;
import dev.arachneledger.ui.screen.SettingsScreen;
import dev.arachneledger.ui.screen.SuppliesScreen;

import net.minecraft.client.Minecraft;

/** A single deferred request prevents command callbacks from replacing a screen mid-event. */
final class ClientScreens {
    enum Page {
        DASHBOARD,
        HUD,
        SETTINGS,
        ACHIEVEMENTS,
        RECAP,
        DIAGNOSTICS,
        PROGRESS,
        GOALS,
        SUPPLIES
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
                    case PROGRESS -> new ProgressScreen(null);
                    case GOALS -> new GoalsScreen(null);
                    case SUPPLIES -> new SuppliesScreen(null);
                });
    }

    private ClientScreens() {}
}

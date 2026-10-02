package dev.arachneledger.client;

import dev.arachneledger.tracking.Tracker;

import net.minecraft.client.Minecraft;

/** All notifications are local; delivery is separate from saved eligibility and accounting. */
final class ClientNotifications {
    static void publish(Minecraft client, Tracker tracker) {
        if (client.player == null) return;
        for (var summary : tracker.drainKillSummaries())
            client.gui.getChat().addClientSystemMessage(ClientMessages.killSummary(summary));
        for (var unlock : tracker.drainAchievements())
            client.gui.getChat().addClientSystemMessage(ClientMessages.achievement(unlock));
        for (var summary : tracker.drainSessionRecaps())
            client.gui.getChat().addClientSystemMessage(ClientMessages.sessionRecap(summary));
    }

    private ClientNotifications() {}
}

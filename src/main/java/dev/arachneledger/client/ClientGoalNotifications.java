package dev.arachneledger.client;

import dev.arachneledger.config.SessionGoals;
import dev.arachneledger.ledger.GoalProgress;
import dev.arachneledger.ledger.SessionGoalMonitor;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.Format;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Personal goals are local and do not send a message or action to the server. */
final class ClientGoalNotifications {
    private static final SessionGoalMonitor MONITOR = new SessionGoalMonitor();

    static void publish(Minecraft client, Tracker tracker) {
        if (client.player == null || !tracker.ready()) return;
        SessionGoals goals = tracker.config.goals;
        GoalProgress progress = GoalProgress.calculate(tracker.ledger, goals);
        boolean announce = MONITOR.update(tracker.ledger, goals, progress);
        if (MONITOR.consumeChanged()) {
            tracker.saveJournalMetadata();
            if (!tracker.ready() || !tracker.error.isEmpty()) return;
        }
        if (!announce) return;
        String target =
                switch (progress.kind()) {
                    case KILLS -> (long) progress.target() + " kills";
                    case ACTIVE_TIME -> Format.time((long) progress.target()) + " active time";
                    case PROFIT -> Format.coins(progress.target()) + " coins net profit";
                    case NONE -> "";
                };
        client.gui
                .getChat()
                .addClientSystemMessage(
                        Component.literal("[Arachne] ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(
                                        Component.literal("Session goal complete >> ")
                                                .withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal(target).withStyle(ChatFormatting.GREEN)));
    }

    private ClientGoalNotifications() {}
}

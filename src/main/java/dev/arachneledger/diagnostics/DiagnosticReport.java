package dev.arachneledger.diagnostics;

import java.time.Instant;
import java.util.List;

/**
 * Plain-text reports contain only relevant local observations, never credentials or account saves.
 */
public final class DiagnosticReport {
    public static String create(
            String version,
            DetectionSnapshot state,
            List<TrackingDiagnostics.Event> events,
            List<String> sidebar,
            List<String> tab,
            long now) {
        StringBuilder text =
                new StringBuilder("Arachne Ledger ")
                        .append(version)
                        .append(" tracking diagnostics\n")
                        .append(Instant.ofEpochMilli(now))
                        .append("\nStatus: ")
                        .append(state.status())
                        .append("\nReady: ")
                        .append(state.ready())
                        .append(" | SkyBlock: ")
                        .append(state.skyblock())
                        .append(" | Sanctuary: ")
                        .append(state.sanctuary())
                        .append(" | Paused: ")
                        .append(state.paused())
                        .append("\nLocation: ")
                        .append(state.location())
                        .append("\nReason: ")
                        .append(state.reason())
                        .append("\nTimer: ")
                        .append(state.timer())
                        .append("\nFight: ")
                        .append(state.fightId())
                        .append(" / ")
                        .append(state.outcome())
                        .append("\nDamage: ")
                        .append(state.damage())
                        .append(" / ")
                        .append(state.minimumDamage())
                        .append("\nPickup window: ")
                        .append(state.pickups())
                        .append(" | Name-tag window: ")
                        .append(state.labels())
                        .append(" | Reward milliseconds left: ")
                        .append(state.rewardMillis())
                        .append("\n\nRecent observations (newest first):\n");
        for (TrackingDiagnostics.Event event : events) {
            text.append(Instant.ofEpochMilli(event.at()))
                    .append(" | ")
                    .append(event.kind())
                    .append(" | ")
                    .append(event.result())
                    .append(" | ")
                    .append(event.detail())
                    .append(" | ")
                    .append(event.reason());
            if (event.repeats() > 1) text.append(" (x").append(event.repeats()).append(')');
            text.append('\n');
        }
        text.append("\nVisible sidebar:\n")
                .append(String.join("\n", sidebar))
                .append("\n\nVisible tab list:\n")
                .append(String.join("\n", tab))
                .append('\n');
        return text.toString();
    }

    private DiagnosticReport() {}
}

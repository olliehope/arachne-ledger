package dev.arachneledger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Presentation and diagnostics for local client messages. These messages never send chat to
 * the server. Keep component construction separate from delivery so it can be tested without a world.
 */
public final class ClientMessages {
    public static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Arachne] " + text));
    }

    public static Component killSummary(Tracker.KillSummary summary) {
        String time = summary.fightMillis() < 0 ? "Time unknown"
            : String.format(Locale.ROOT, "%.1fs", summary.fightMillis() / 1000.0);
        double profit = summary.profit();
        var result = Component.literal("[Arachne] ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("Kill #" + summary.killNumber()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" · " + time + " · Profit ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal((profit >= 0 ? "+" : "") + Format.coins(profit) + " coins")
                .withStyle(profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
            .append(Component.literal(" (rewards " + Format.coins(summary.income()) + ", cost "
                + Format.coins(summary.costs()) + ")").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" · Damage " + Format.coins(summary.damage())).withStyle(ChatFormatting.AQUA));
        if (summary.scavengerCoins() > 0)
            result.append(Component.literal(" · Scavenger " + Format.coins(summary.scavengerCoins()))
                .withStyle(ChatFormatting.GOLD));
        if (summary.unpriced() > 0) {
            result.append(Component.literal(" · " + summary.unpriced() + " unpriced").withStyle(ChatFormatting.YELLOW));
        }
        return result;
    }

    /** Use a fresh context snapshot; writing diagnostics must not alter tracking decisions. */
    public static void writeDiagnostics(Tracker tracker, GameContext.Snapshot snapshot, Path file) {
        say("Hypixel: " + snapshot.onHypixel() + " | SkyBlock: " + snapshot.skyBlock()
            + " | Sanctuary: " + snapshot.sanctuary());
        say(tracker.detectionReason + " | " + tracker.status());
        String content = "Arachne Ledger location diagnostics\n" + Instant.now()
            + "\nHypixel: " + snapshot.onHypixel() + "\nSkyBlock: " + snapshot.skyBlock()
            + "\nSanctuary: " + snapshot.sanctuary() + "\nLocation: " + snapshot.location()
            + "\nReason: " + tracker.detectionReason + "\n\nVisible sidebar:\n"
            + String.join("\n", snapshot.sidebarLines()) + "\n\nVisible tab list:\n"
            + String.join("\n", snapshot.tabLines()) + "\n";
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            say("Visible HUD diagnostics saved to config/arachneledger/detection-debug.txt");
        } catch (IOException ex) { say("Could not save diagnostics: " + ex.getMessage()); }
    }

    private ClientMessages() {}
}

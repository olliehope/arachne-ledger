package dev.arachneledger.client;

import dev.arachneledger.achievement.Achievements;
import dev.arachneledger.diagnostics.DiagnosticReport;
import dev.arachneledger.ledger.SessionSummary;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.Format;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Presentation and diagnostics for local client messages. These messages never send chat to the
 * server. Keep component construction separate from delivery so it can be tested without a world.
 */
public final class ClientMessages {
    public static Component achievement(Achievements.Unlock unlock) {
        return Component.literal("[Arachne] ")
                .withStyle(ChatFormatting.GRAY)
                .append(
                        Component.literal("Achievement unlocked: ")
                                .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(unlock.title()).withStyle(ChatFormatting.GOLD))
                .append(
                        Component.literal(" · " + unlock.definition().description())
                                .withStyle(ChatFormatting.GRAY));
    }

    public static Component sessionRecap(SessionSummary.Snapshot summary) {
        var profit = summary.profit();
        var text =
                Component.literal("[Arachne] ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(
                                Component.literal(
                                                "Session "
                                                        + summary.sessionId()
                                                        + " · "
                                                        + summary.qualifiedKills()
                                                        + " kills · "
                                                        + dev.arachneledger.ui.Hud.shortTime(
                                                                profit.elapsed())
                                                        + " · Profit ")
                                        .withStyle(ChatFormatting.YELLOW))
                        .append(
                                Component.literal(Format.coins(profit.net()) + " coins")
                                        .withStyle(
                                                profit.net() >= 0
                                                        ? ChatFormatting.GREEN
                                                        : ChatFormatting.RED));
        if (profit.rngRevenue() > 0)
            text.append(
                    Component.literal(" · Without RNG " + Format.coins(profit.ordinaryNet()))
                            .withStyle(ChatFormatting.GRAY));
        text.append(
                Component.literal(
                                " · Costs "
                                        + Format.coins(profit.costs())
                                        + " · Scavenger "
                                        + Format.coins(summary.scavengerCoins()))
                        .withStyle(ChatFormatting.GRAY));
        return text;
    }

    public static void say(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Arachne] " + text));
        }
    }

    public static Component killSummary(Tracker.KillSummary summary) {
        String time =
                summary.fightMillis() < 0
                        ? "Time unknown"
                        : String.format(Locale.ROOT, "%.1fs", summary.fightMillis() / 1000.0);
        double profit = summary.profit();
        var result =
                Component.literal("[Arachne] ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(
                                Component.literal("Kill #" + summary.killNumber())
                                        .withStyle(ChatFormatting.YELLOW))
                        .append(
                                Component.literal(" · " + time + " · Profit ")
                                        .withStyle(ChatFormatting.GRAY))
                        .append(
                                Component.literal(
                                                (profit >= 0 ? "+" : "")
                                                        + Format.coins(profit)
                                                        + " coins")
                                        .withStyle(
                                                profit >= 0
                                                        ? ChatFormatting.GREEN
                                                        : ChatFormatting.RED))
                        .append(
                                Component.literal(
                                                " (rewards "
                                                        + Format.coins(summary.income())
                                                        + ", cost "
                                                        + Format.coins(summary.costs())
                                                        + ")")
                                        .withStyle(ChatFormatting.GRAY))
                        .append(
                                Component.literal(" · Damage " + Format.coins(summary.damage()))
                                        .withStyle(ChatFormatting.AQUA));
        if (summary.scavengerCoins() > 0) {
            result.append(
                    Component.literal(" · Scavenger " + Format.coins(summary.scavengerCoins()))
                            .withStyle(ChatFormatting.GOLD));
        }
        if (summary.unpriced() > 0) {
            result.append(
                    Component.literal(" · " + summary.unpriced() + " unpriced")
                            .withStyle(ChatFormatting.YELLOW));
        }
        return result;
    }

    /** Use a fresh context snapshot; writing diagnostics must not alter tracking decisions. */
    public static void writeDiagnostics(Tracker tracker, GameContext.Snapshot snapshot, Path file) {
        say(
                "Hypixel: "
                        + snapshot.onHypixel()
                        + " | SkyBlock: "
                        + snapshot.skyBlock()
                        + " | Sanctuary: "
                        + snapshot.sanctuary());
        say(tracker.detectionReason + " | " + tracker.status());
        String version =
                FabricLoader.getInstance()
                        .getModContainer("arachneledger")
                        .orElseThrow()
                        .getMetadata()
                        .getVersion()
                        .getFriendlyString();
        String content =
                DiagnosticReport.create(
                        version,
                        tracker.detectionSnapshot(System.currentTimeMillis()),
                        tracker.diagnostics.newestFirst(),
                        snapshot.sidebarLines(),
                        snapshot.tabLines(),
                        System.currentTimeMillis());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            say("Visible HUD diagnostics saved to config/arachneledger/detection-debug.txt");
        } catch (IOException ex) {
            say("Could not save diagnostics: " + ex.getMessage());
        }
    }

    private ClientMessages() {}
}

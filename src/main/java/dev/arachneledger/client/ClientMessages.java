package dev.arachneledger.client;

import dev.arachneledger.achievement.Achievements;
import dev.arachneledger.diagnostics.DiagnosticReport;
import dev.arachneledger.ledger.SessionSummary;
import dev.arachneledger.skyblock.Catalog;
import dev.arachneledger.tracking.Tracker;
import dev.arachneledger.ui.Format;
import dev.arachneledger.ui.RngAlerts;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

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
        var definition = unlock.definition();
        ChatFormatting titleColor =
                switch (definition.metric()) {
                    case PETS -> ChatFormatting.DARK_PURPLE;
                    case FANGS -> ChatFormatting.GREEN;
                    default -> ChatFormatting.GOLD;
                };
        var tooltip =
                Component.literal(unlock.title())
                        .withStyle(titleColor)
                        .append(
                                Component.literal("\n" + definition.category().label())
                                        .withStyle(ChatFormatting.GRAY))
                        .append(
                                Component.literal("\n" + definition.description())
                                        .withStyle(ChatFormatting.WHITE))
                        .append(
                                Component.literal("\n\nClick to view achievements.")
                                        .withStyle(ChatFormatting.YELLOW));
        return Component.literal("[Arachne] ")
                .withStyle(ChatFormatting.GRAY)
                .withStyle(
                        style ->
                                style.withHoverEvent(new HoverEvent.ShowText(tooltip))
                                        .withClickEvent(
                                                new ClickEvent.RunCommand("/arachne achievements")))
                .append(
                        Component.literal("Achievement Unlocked ")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(">> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(unlock.title()).withStyle(titleColor));
    }

    /** The interval describes counted fights and active time, never wall-clock offline time. */
    public static Component rareDrop(
            String item,
            long quantity,
            double unitValue,
            long killsSince,
            long activeMillisSince,
            boolean hasPrevious) {
        return rareDrop(
                item, quantity, unitValue, killsSince, activeMillisSince, hasPrevious, true);
    }

    public static Component rareDrop(
            String item,
            long quantity,
            double unitValue,
            long killsSince,
            long activeMillisSince,
            boolean hasPrevious,
            boolean showValue) {
        String name = Catalog.name(item);
        long count = Math.max(1, quantity);
        boolean intervalKnown = killsSince >= 0 && activeMillisSince >= 0;
        String time = intervalKnown ? Format.time(activeMillisSince) : "";
        String interval = hasPrevious ? "Since the previous drop" : "Since tracking began";
        var tooltip =
                Component.literal(
                                intervalKnown
                                        ? interval
                                                + ": "
                                                + killsSince
                                                + " counted kills, "
                                                + time
                                                + " active"
                                        : "Recorded reward; no qualifying fight interval.")
                        .withStyle(ChatFormatting.GRAY)
                        .append(
                                Component.literal("\nClick to open the dashboard.")
                                        .withStyle(ChatFormatting.YELLOW));
        var result =
                Component.literal("[Arachne] ")
                        .withStyle(ChatFormatting.GRAY)
                        .withStyle(
                                style ->
                                        style.withHoverEvent(new HoverEvent.ShowText(tooltip))
                                                .withClickEvent(
                                                        new ClickEvent.RunCommand(
                                                                "/arachne dashboard")))
                        .append(
                                Component.literal("Rare Drop! ")
                                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.literal(">> ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(
                                Component.literal((count > 1 ? count + "x " : "") + name)
                                        .withStyle(
                                                style -> {
                                                    int rarity = RngAlerts.rarityColor(item);
                                                    return style.withColor(
                                                            rarity == 0
                                                                    ? 0xFFFFFF
                                                                    : rarity & 0xFFFFFF);
                                                }));
        double value = unitValue * count;
        if (showValue && value > 0 && Double.isFinite(value)) {
            result.append(
                    Component.literal(" · +" + Format.coins(value) + " coins")
                            .withStyle(ChatFormatting.GREEN));
        }
        if (intervalKnown) {
            result.append(
                    Component.literal(" · " + killsSince + " kills · " + time)
                            .withStyle(ChatFormatting.GRAY));
        }
        return result;
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

    /** Compact chat keeps the income, costs, damage, and Scavenger details available on hover. */
    public static Component killSummary(Tracker.KillSummary summary, boolean compact) {
        if (!compact) {
            return killSummary(summary);
        }
        String time =
                summary.fightMillis() < 0
                        ? "Time unknown"
                        : String.format(Locale.ROOT, "%.1fs", summary.fightMillis() / 1000.0);
        double profit = summary.profit();
        return Component.literal("[Arachne] ")
                .withStyle(ChatFormatting.GRAY)
                .withStyle(
                        style ->
                                style.withHoverEvent(new HoverEvent.ShowText(killSummary(summary))))
                .append(
                        Component.literal("#" + summary.killNumber())
                                .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" · " + time + " · ").withStyle(ChatFormatting.GRAY))
                .append(
                        Component.literal(
                                        (profit >= 0 ? "+" : "") + Format.coins(profit) + " coins")
                                .withStyle(
                                        profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
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

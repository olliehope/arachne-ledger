package dev.arachneledger.client;

import dev.arachneledger.achievement.Achievements;
import dev.arachneledger.tracking.Tracker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Chat presentation can be checked without a player, network connection, or world. */
public final class ClientMessageChecks {
    private static int checks;

    public static void main(String[] args) {
        achievementInteractions();
        rareDropValuesAndIntervals();
        compactFightDetails();
        System.out.println("Client messages: " + checks + " checks passed");
    }

    private static void achievementInteractions() {
        var definition = Achievements.DEFINITIONS.getFirst();
        var message = ClientMessages.achievement(new Achievements.Unlock(definition, 1_000));
        check(
                message.getString()
                        .equals("[Arachne] Achievement Unlocked >> " + definition.title()),
                "An achievement stays a single compact chat line with the mod's own name");
        check(
                !message.getString().contains(definition.description()),
                "Long descriptions do not clutter the visible notification");
        var hover = hover(message).getString();
        check(hover.contains(definition.description()), "Hover retains the achievement objective");
        check(hover.contains(definition.category().label()), "Hover identifies the category");
        check(
                command(message).equals("/arachne achievements"),
                "Click opens the local achievements command");
        check(
                textPart(message, definition.title()).getStyle().getColor().getValue()
                        == ChatFormatting.GOLD.getColor(),
                "The achievement title has its own gold emphasis");
    }

    private static void rareDropValuesAndIntervals() {
        var legendary =
                ClientMessages.rareDrop("TARANTULA_LEGENDARY", 1, 100_000, 42, 125_000, true);
        check(legendary.getString().contains("Tarantula Pet (Legendary)"), "Pet rarity is named");
        check(legendary.getString().contains("+100.0k coins"), "Recorded positive value is shown");
        check(
                legendary.getString().contains("42 kills · 00:02:05"),
                "The notification reports the counted kill and active time interval");
        check(
                hover(legendary).getString().contains("Since the previous drop"),
                "An established interval is identified on hover");
        check(
                command(legendary).equals("/arachne dashboard"),
                "Rare-drop chat opens the local dashboard");
        check(
                textPart(legendary, "Tarantula Pet (Legendary)").getStyle().getColor().getValue()
                        == ChatFormatting.GOLD.getColor(),
                "Legendary rarity is gold");
        var first = ClientMessages.rareDrop("TARANTULA_EPIC", 1, 0, 7, 8_000, false);
        check(
                hover(first).getString().contains("Since tracking began"),
                "The first drop does not imply a known earlier drop");
        check(!first.getString().contains("coins"), "An unknown or excluded value is omitted");
        check(
                !first.getString().toLowerCase(java.util.Locale.ROOT).contains("unpriced"),
                "An unknown value does not produce an unpriced alert");
        check(
                textPart(first, "Tarantula Pet (Epic)").getStyle().getColor().getValue()
                        == ChatFormatting.DARK_PURPLE.getColor(),
                "Epic rarity is purple");
        var stack = ClientMessages.rareDrop("ARACHNE_FANG", 2, 500, 3, 4_000, true);
        check(stack.getString().contains("2x Arachne's Fang"), "A stacked reward retains quantity");
        check(stack.getString().contains("+1.0k coins"), "The value covers the whole stack");
        var hidden = ClientMessages.rareDrop("ARACHNE_FANG", 1, 500, 3, 4_000, true, false);
        check(!hidden.getString().contains("coins"), "Value privacy applies to rare-drop chat");
        check(!hover(hidden).getString().contains("coins"), "Hidden values are absent on hover");
        var invalid =
                ClientMessages.rareDrop("ARACHNE_FANG", 1, Double.POSITIVE_INFINITY, 0, 0, false);
        check(!invalid.getString().contains("Infinity"), "A nonfinite value is never displayed");
        var unqualified = ClientMessages.rareDrop("ARACHNE_FANG", 1, 500, -1, -1, false);
        check(
                !unqualified.getString().contains("kills"),
                "A real reward from an unqualified fight does not invent a tracked interval");
        check(
                !unqualified.getString().contains("00:00:00"),
                "An unavailable interval does not appear to be zero active time");
    }

    private static void compactFightDetails() {
        var summary = new Tracker.KillSummary(29_600, 25_000, 5_000, 2_000, 2, 18, 300);
        var compact = ClientMessages.killSummary(summary, true);
        check(
                compact.getString().equals("[Arachne] #18 · 29.6s · +3.0k coins"),
                "Compact fight chat presents only number, duration, and net profit");
        var details = hover(compact).getString();
        check(details.contains("rewards 5.0k, cost 2.0k"), "Hover explains the profit calculation");
        check(details.contains("Damage 25.0k"), "Damage remains available on hover");
        check(details.contains("Scavenger 300"), "Scavenger remains available on hover");
        check(details.contains("2 unpriced"), "Incomplete valuation remains visible on hover");
        check(
                ClientMessages.killSummary(summary, false)
                        .getString()
                        .equals(ClientMessages.killSummary(summary).getString()),
                "Existing detailed chat is retained when compact mode is off");
        var loss = new Tracker.KillSummary(-1, 10_000, 500, 1_000, 0, 1);
        var lossChat = ClientMessages.killSummary(loss, true);
        check(lossChat.getString().contains("Time unknown"), "Unknown duration stays explicit");
        check(lossChat.getString().contains("-500 coins"), "Losses retain their minus sign");
        check(
                textPart(lossChat, "-500 coins").getStyle().getColor().getValue()
                        == ChatFormatting.RED.getColor(),
                "A loss uses the same red as detailed chat");
    }

    private static Component hover(Component component) {
        if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText event) {
            return event.value();
        }
        throw new AssertionError("Missing local text hover");
    }

    private static String command(Component component) {
        if (component.getStyle().getClickEvent() instanceof ClickEvent.RunCommand event) {
            return event.command();
        }
        throw new AssertionError("Missing local command action");
    }

    private static Component textPart(Component message, String text) {
        return message.getSiblings().stream()
                .filter(part -> part.getString().equals(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing colored text part: " + text));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private ClientMessageChecks() {}
}

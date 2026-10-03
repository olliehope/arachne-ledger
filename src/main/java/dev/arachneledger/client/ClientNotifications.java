package dev.arachneledger.client;

import dev.arachneledger.ledger.RngSince;
import dev.arachneledger.tracking.FarmingEvents;
import dev.arachneledger.tracking.Tracker;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** All notifications are local; delivery is separate from saved eligibility and accounting. */
final class ClientNotifications {
    static void publish(Minecraft client, Tracker tracker) {
        if (client.player == null) return;
        var preferences = tracker.config.farming;
        for (var summary : tracker.drainKillSummaries())
            client.gui
                    .getChat()
                    .addClientSystemMessage(
                            ClientMessages.killSummary(summary, preferences.compactKillChat));
        for (var spawn : tracker.drainSpawnNotices()) {
            if (preferences.spawnSound) sound(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f);
            if (preferences.spawnTitle)
                title(
                        client,
                        Component.literal("Arachne has spawned!").withStyle(ChatFormatting.YELLOW),
                        Component.literal("Arachne Ledger").withStyle(ChatFormatting.GRAY));
        }
        for (var drop : tracker.drainRareDrops()) {
            // An undo before delivery removes the notification as well as the receipt.
            if (tracker.ledger.entries.stream().noneMatch(entry -> entry.id() == drop.entryId()))
                continue;
            if (preferences.rngChat) {
                Interval interval = rareInterval(tracker, drop);
                client.gui
                        .getChat()
                        .addClientSystemMessage(
                                ClientMessages.rareDrop(
                                        drop.itemId(),
                                        drop.quantity(),
                                        drop.unitValue(),
                                        interval.kills(),
                                        interval.activeMillis(),
                                        interval.hasPrevious(),
                                        tracker.config.rngValue));
            }
            if (preferences.rngSound) sound(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f);
        }
        for (var unlock : tracker.drainAchievements()) {
            if (tracker.config.achievementNotifications)
                client.gui.getChat().addClientSystemMessage(ClientMessages.achievement(unlock));
            if (preferences.achievementSound)
                sound(client, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f);
            if (preferences.achievementTitle)
                title(
                        client,
                        Component.literal("Achievement Unlocked").withStyle(ChatFormatting.YELLOW),
                        Component.literal(unlock.title()).withStyle(ChatFormatting.GOLD));
        }
        for (var summary : tracker.drainSessionRecaps())
            client.gui.getChat().addClientSystemMessage(ClientMessages.sessionRecap(summary));
    }

    private record Interval(long kills, long activeMillis, boolean hasPrevious) {
        private static final Interval UNKNOWN = new Interval(-1, -1, false);
    }

    private static Interval rareInterval(Tracker tracker, FarmingEvents.RareDrop drop) {
        var interval =
                RngSince.intervalForDrop(tracker.ledger, tracker.config.total, drop.entryId());
        return interval == null
                ? Interval.UNKNOWN
                : new Interval(interval.kills(), interval.activeMillis(), interval.hasPrevious());
    }

    private static void sound(Minecraft client, SoundEvent event, float pitch) {
        client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, 0.65f));
    }

    private static void title(Minecraft client, Component title, Component subtitle) {
        if (client.options.hideGui) return;
        client.gui.setTimes(5, 35, 10);
        client.gui.setTitle(title);
        client.gui.setSubtitle(subtitle);
    }

    private ClientNotifications() {}
}

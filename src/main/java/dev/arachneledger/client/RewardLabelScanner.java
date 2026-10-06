package dev.arachneledger.client;

import dev.arachneledger.skyblock.LootLabels;
import dev.arachneledger.tracking.Tracker;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Reads nearby reward holograms only while their death window is open. Accepted UUIDs are filtered
 * before building the result list or formatting styled text; a new/changed unknown name is still
 * reconsidered on the next tick. Pickup packets remain an independent observation path.
 */
final class RewardLabelScanner {
    private static final int RADIUS = 48;

    static void observe(Minecraft client, Tracker tracker, long now) {
        if (client.player == null || client.level == null || !tracker.acceptsStandLoot(now)) return;
        var nearby =
                client.level.getEntitiesOfClass(
                        ArmorStand.class,
                        client.player.getBoundingBox().inflate(RADIUS),
                        stand ->
                                stand.hasCustomName()
                                        && !tracker.hasSeenLootStand(stand.getUUID()));
        for (ArmorStand stand : nearby) {
            var label = stand.getCustomName();
            if (label != null) {
                tracker.observeLootStand(stand.getUUID(), LootLabels.formatted(label), now);
            }
        }
    }

    private RewardLabelScanner() {}
}

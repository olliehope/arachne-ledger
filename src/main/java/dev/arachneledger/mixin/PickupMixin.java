package dev.arachneledger.mixin;

import dev.arachneledger.client.ArachneLedger;
import dev.arachneledger.skyblock.ItemIds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PickupMixin {
    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void arachneLedger$pickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // Vanilla re-dispatches this handler to the render thread. Only observe that invocation.
        if (!mc.isSameThread()
                || mc.player == null
                || mc.level == null
                || ArachneLedger.tracker == null) {
            return;
        }
        if (packet.getPlayerId() != mc.player.getId()) {
            return;
        }
        long now = System.currentTimeMillis();
        ArachneLedger.prepareContext(mc, now);
        if (mc.level.getEntity(packet.getItemId()) instanceof ItemEntity item) {
            int count = Math.min(packet.getAmount(), item.getItem().getCount());
            ArachneLedger.tracker.pickup(ItemIds.of(item.getItem()), count, now);
        }
    }
}

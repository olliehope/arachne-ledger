package dev.arachneledger.mixin;

import dev.arachneledger.client.ArachneLedger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes altar dust packets without creating, suppressing, or changing server particles. */
@Mixin(ClientPacketListener.class)
public abstract class RitualParticlesMixin {
    private static final double ALTAR_X = -282.5;
    private static final double ALTAR_Z = -178.5;
    private static final double ALTAR_Y = 51;
    private static final double RITUAL_RADIUS_SQUARED = 30 * 30;

    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void arachneLedger$ritualParticles(
            ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()
                || client.player == null
                || client.level == null
                || ArachneLedger.tracker == null
                || !ArachneLedger.tracker.ready()
                || packet.getParticle().getType() != ParticleTypes.DUST
                || packet.getMaxSpeed() != 1f) {
            return;
        }
        double x = packet.getX() - ALTAR_X;
        double z = packet.getZ() - ALTAR_Z;
        double y = packet.getY() - ALTAR_Y;
        if (x * x + y * y + z * z > RITUAL_RADIUS_SQUARED) {
            return;
        }
        long now = System.currentTimeMillis();
        ArachneLedger.prepareContext(client, now);
        // The tracker owns the sampling window and rejects particles outside a Crystal ritual.
        ArachneLedger.tracker.observeRitualParticles(now);
    }
}

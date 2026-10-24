package dev.sleeptrails.mixin;

import dev.sleeptrails.Observation;
import dev.sleeptrails.Presentation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClockObservationMixin {
    // After ensureRunningOnSameThread, before either authoritative clock is updated.
    @Inject(method = "handleSetTime", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;setTimeFromServer(J)V"))
    private void sleeptrails$before(ClientboundSetTimePacket packet, CallbackInfo ci) {
        Observation.time("before", packet);
        Presentation.beforeTime(packet);
    }

    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void sleeptrails$after(ClientboundSetTimePacket packet, CallbackInfo ci) {
        Observation.time("after", packet);
    }
}

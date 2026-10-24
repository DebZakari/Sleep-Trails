package dev.sleeptrails.mixin;

import dev.sleeptrails.Observation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class SleepObservationMixin {
    @Inject(method = "startSleeping", at = @At("RETURN"))
    private void sleeptrails$enter(BlockPos bed, CallbackInfoReturnable<Boolean> cir) {
        Observation.sleep((LivingEntity)(Object)this, "sleep_enter");
    }

    @Inject(method = "stopSleeping", at = @At("HEAD"))
    private void sleeptrails$wakeBefore(CallbackInfo ci) {
        Observation.sleep((LivingEntity)(Object)this, "wake_before");
    }

    @Inject(method = "stopSleeping", at = @At("TAIL"))
    private void sleeptrails$wakeAfter(CallbackInfo ci) {
        Observation.sleep((LivingEntity)(Object)this, "wake_after");
    }
}

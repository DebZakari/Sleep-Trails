package dev.sleeptrails.mixin;

import dev.sleeptrails.StarExposure;
import dev.vitrail.uniform.UniformCatalog;
import dev.vitrail.uniform.UniformShape;
import dev.vitrail.uniform.values.TimeValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = TimeValues.class, remap = false)
abstract class VitrailUniformMixin {
    @Inject(method = "register", at = @At("TAIL"))
    private static void sleeptrails$uniforms(UniformCatalog.Builder builder, CallbackInfo ci) {
        builder.add("sleeptrailsExposureTicks", UniformShape.FLOAT, (world, out) -> out.set(StarExposure.sample().ticks()));
        builder.add("sleeptrailsClockMod", UniformShape.INT, (world, out) -> out.set(StarExposure.sample().clockMod()));
    }
}

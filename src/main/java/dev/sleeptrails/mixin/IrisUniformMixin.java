package dev.sleeptrails.mixin;

import dev.sleeptrails.Presentation;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = CommonUniforms.class, remap = false)
abstract class IrisUniformMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"))
    private static void sleeptrails$uniforms(UniformHolder holder, IdMap ids, PackDirectives directives,
                                            FrameUpdateNotifier notifier, CallbackInfo ci) {
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sleeptrailsExposureTicks",
            (FloatSupplier)() -> Presentation.exposure().ticks());
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "sleeptrailsClockMod",
            () -> Presentation.exposure().clockMod());
    }
}

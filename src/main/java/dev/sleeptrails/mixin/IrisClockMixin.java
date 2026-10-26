package dev.sleeptrails.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sleeptrails.Presentation;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.WorldTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps Iris's original dimension rules and suppliers; only the visual clock and update frequency change. */
@Pseudo
@Mixin(value = WorldTimeUniforms.class, remap = false)
abstract class IrisClockMixin {
    @ModifyExpressionValue(method = {"getWorldDayTime", "getWorldDay"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;getDefaultClockTime()J"), require = 2)
    private static long sleeptrails$clock(long authoritative) { return Presentation.shaderTime(authoritative); }

    // moonPhase samples the same camera probe as the sun/moon positions, and must update with them too.
    @ModifyExpressionValue(method = "addWorldTimeUniforms", at = @At(value = "FIELD",
        target = "Lnet/irisshaders/iris/gl/uniform/UniformUpdateFrequency;PER_TICK:Lnet/irisshaders/iris/gl/uniform/UniformUpdateFrequency;"), require = 3)
    private static UniformUpdateFrequency sleeptrails$perFrame(UniformUpdateFrequency original) {
        return UniformUpdateFrequency.PER_FRAME;
    }
}

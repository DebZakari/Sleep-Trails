package dev.sleeptrails.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sleeptrails.Presentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.vitrail.render.FrameState", remap = false)
abstract class VitrailClockMixin {
    @ModifyExpressionValue(method = "readWorld", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;getDefaultClockTime()J"), require = 1)
    private long sleeptrails$clock(long authoritative) { return Presentation.shaderTime(authoritative); }
}

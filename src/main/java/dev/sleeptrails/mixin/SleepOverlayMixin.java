package dev.sleeptrails.mixin;

import org.spongepowered.asm.mixin.injection.ModifyArg;
import dev.sleeptrails.Presentation;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
abstract class SleepOverlayMixin {
    @ModifyArg(method = "extractSleepOverlay", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), index = 4)
    private int sleeptrails$visibleNight(int color) {
        // Keep the ordinary bed camera, chat and Leave Bed button; only bound the sleep fade while presenting.
        return Presentation.frame().active() ? (Math.min(color >>> 24, 56) << 24) | (color & 0xffffff) : color;
    }
}

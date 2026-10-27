package dev.sleeptrails.mixin;

import dev.sleeptrails.iris.IrisStarSource;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.file.Path;

@Pseudo
@Mixin(value = IncludeGraph.class, remap = false)
abstract class IrisSourceMixin {
    @Inject(method = "readFile", at = @At("RETURN"), cancellable = true)
    private static void sleeptrails$source(Path file, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(IrisStarSource.read(file, cir.getReturnValue()));
    }
}

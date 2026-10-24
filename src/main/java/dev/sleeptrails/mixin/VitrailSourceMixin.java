package dev.sleeptrails.mixin;

import dev.sleeptrails.vitrail.StarSourcePatch;
import dev.vitrail.pack.source.ShaderPackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.file.Path;
import java.util.List;

@Pseudo
@Mixin(value = ShaderPackSource.class, remap = false)
abstract class VitrailSourceMixin {
    @Inject(method = "open", at = @At("RETURN"))
    private static void sleeptrails$open(Path archive, CallbackInfoReturnable<ShaderPackSource> cir) {
        StarSourcePatch.opened(cir.getReturnValue(), archive);
    }

    @Inject(method = "readLines", at = @At("RETURN"), cancellable = true)
    private void sleeptrails$source(Path file, CallbackInfoReturnable<List<String>> cir) {
        cir.setReturnValue(StarSourcePatch.read((ShaderPackSource)(Object)this, file, cir.getReturnValue()));
    }
}

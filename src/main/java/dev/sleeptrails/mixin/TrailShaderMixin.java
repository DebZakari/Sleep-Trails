package dev.sleeptrails.mixin;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.sleeptrails.VanillaTrails;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Without Fabric API the game loads no shaders from a mod's namespace, so the trail's vertex shader comes from this JAR. */
@Mixin(ShaderManager.Configs.class)
abstract class TrailShaderMixin {
    @Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
    private void sleeptrails$trailShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() == null) {
            String source = VanillaTrails.shader(id, type);
            if (source != null) cir.setReturnValue(source);
        }
    }
}

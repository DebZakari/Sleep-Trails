package dev.sleeptrails.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sleeptrails.iris.IrisStarSource;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.ShaderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.file.Path;
import java.util.Map;

@Pseudo
@Mixin(value = Iris.class, remap = false)
abstract class IrisPackMixin {
    @WrapOperation(method = "loadExternalShaderpack", at = @At(value = "NEW",
        target = "(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)Lnet/irisshaders/iris/shaderpack/ShaderPack;"))
    private static ShaderPack sleeptrails$pack(Path root, Map<String, String> options, ImmutableList<StringPair> defines,
                                               boolean fallback, Operation<ShaderPack> original) {
        try (var scope = IrisStarSource.open(root)) {
            return original.call(root, options, defines, fallback);
        }
    }
}

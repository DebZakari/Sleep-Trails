package dev.sleeptrails;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Exact-artifact admission, before any optional renderer class is loaded. */
public final class RendererCompatibility implements IMixinConfigPlugin {
    private static boolean checked, vitrail, iris, supported;

    private static synchronized void check() {
        if (checked) return;
        checked = true;
        var loader = FabricLoader.getInstance();
        vitrail = loader.isModLoaded("vitrail");
        iris = loader.isModLoaded("iris");
        if (vitrail && iris) return;
        try {
            supported = vitrail
                ? matches("vitrail", "0.13.0-beta.vultrum.4", "ebabdea37f4a520de8436901d28cfd72d58fed02cce0ba1770fe34bff0a71e8f")
                : !iris || (matches("iris", "1.11.7+mc26.3", "83ab4d3013dd56ac05ae503a2f596968911001c4ef3d82c59c5c79e64c34dd98")
                    && matches("sodium", "0.9.2+mc26.3", "87a5bb39f7e6e41106e77f122f82fc93f9d3bc0b6ebcb746b314172a446a287a"));
        } catch (Exception error) {
            LoggerFactory.getLogger("sleep_trails").warn("Renderer clock adapter unavailable", error);
        }
    }

    private static boolean matches(String id, String version, String expectedSha) throws Exception {
        var mod = FabricLoader.getInstance().getModContainer(id).orElse(null);
        if (mod == null || !mod.getMetadata().getVersion().getFriendlyString().equals(version)) return false;
        var paths = mod.getOrigin().getPaths();
        if (paths.size() != 1 || !Files.isRegularFile(paths.getFirst())) return false;
        try (var input = Files.newInputStream(paths.getFirst())) {
            var sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) sha.update(buffer, 0, count);
            return HexFormat.of().formatHex(sha.digest()).equals(expectedSha);
        }
    }

    public static boolean supportedRenderer() { check(); return supported; }
    public static boolean vitrailPresent() { check(); return vitrail; }
    public static boolean irisPresent() { check(); return iris; }
    public void onLoad(String mixinPackage) { check(); }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (targetClassName.startsWith("dev.vitrail.")) return vitrail && supported;
        if (targetClassName.startsWith("net.irisshaders.iris.")) return iris && supported;
        return true;
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

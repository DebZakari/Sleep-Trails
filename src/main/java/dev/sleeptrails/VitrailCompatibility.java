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

/** Fail closed for unknown shader renderers; the optional mixin never loads absent Vitrail classes. */
public final class VitrailCompatibility implements IMixinConfigPlugin {
    private static final String EXPECTED_SHA = "ebabdea37f4a520de8436901d28cfd72d58fed02cce0ba1770fe34bff0a71e8f";
    private static boolean checked;
    private static boolean present;
    private static boolean supported;

    private static synchronized void check() {
        if (checked) return;
        checked = true;
        var loader = FabricLoader.getInstance();
        present = loader.isModLoaded("vitrail");
        if (!present) { supported = !loader.isModLoaded("iris"); return; }
        try {
            var mod = loader.getModContainer("vitrail").orElseThrow();
            if (!mod.getMetadata().getVersion().getFriendlyString().equals("0.13.0-beta.vultrum.4")) return;
            var paths = mod.getOrigin().getPaths();
            if (paths.size() != 1 || !Files.isRegularFile(paths.getFirst())) return;
            try (var input = Files.newInputStream(paths.getFirst())) {
                var sha = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) sha.update(buffer, 0, count);
                supported = HexFormat.of().formatHex(sha.digest()).equals(EXPECTED_SHA);
            }
        } catch (Exception error) {
            LoggerFactory.getLogger("sleep_trails").warn("Vitrail clock adapter unavailable", error);
        }
    }

    public static boolean supportedRenderer() { check(); return supported; }
    /** Vitrail is loaded. With {@link #supportedRenderer()} true, it is the exact build. */
    public static boolean vitrailPresent() { check(); return present; }
    public void onLoad(String mixinPackage) { check(); }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !targetClassName.startsWith("dev.vitrail.") || (present && supported);
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

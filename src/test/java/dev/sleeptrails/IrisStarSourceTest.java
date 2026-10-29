package dev.sleeptrails;

import dev.sleeptrails.iris.IrisStarSource;
import dev.sleeptrails.shader.StarSource;
import org.junit.jupiter.api.Test;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import static org.junit.jupiter.api.Assertions.*;

class IrisStarSourceTest {
    // Exact shader output from the image-validated and measured early-exit candidate.
    private static final String OPTIMIZED_SHA = "03d5ceedf6053ebdc5fa3b22f35c35ed89e8f313aeb055d80b3db5865baebdba";
    private static Path pack() { return Path.of(System.getProperty("sleeptrails.referencePack")); }

    private static void assertOptimized(String patched) {
        assertEquals(OPTIMIZED_SHA, StarSource.sha(patched.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test void admittedIrisShaderMatchesTheValidatedOptimizationCandidate() throws Exception {
        try (var fs = FileSystems.newFileSystem(pack()); var scope = IrisStarSource.open(fs.getPath("/shaders"))) {
            var member = fs.getPath("/shaders").resolve(StarSource.MEMBER);
            String patched = IrisStarSource.read(member, Files.readString(member));
            assertOptimized(patched);
        }
    }

    @Test void referenceRetainsTheUnoptimizedOriginalStarFunction() throws Exception {
        String previous = System.getProperty("sleeptrails.reference");
        try {
            System.setProperty("sleeptrails.reference", "true");
            try (var fs = FileSystems.newFileSystem(pack()); var scope = IrisStarSource.open(fs.getPath("/shaders"))) {
                var member = fs.getPath("/shaders").resolve(StarSource.MEMBER);
                String original = Files.readString(member);
                assertEquals(StarSource.transform(original), IrisStarSource.read(member, original));
            }
        } finally {
            if (previous == null) System.clearProperty("sleeptrails.reference");
            else System.setProperty("sleeptrails.reference", previous);
        }
    }

    @Test void exactArchiveAndMemberArePatchedOnlyInsideTheirConstructionScope() throws Exception {
        try (var fs = FileSystems.newFileSystem(pack())) {
            var root = fs.getPath("/shaders");
            var member = root.resolve(StarSource.MEMBER);
            var original = Files.readString(member);
            assertEquals(original, IrisStarSource.read(member, original));
            try (var scope = IrisStarSource.open(root)) {
                assertOptimized(IrisStarSource.read(member, original));
                assertEquals(original, IrisStarSource.read(root.resolve("other/stars.glsl"), original));
                assertEquals(original + "\n", IrisStarSource.read(member, original + "\n"));
                assertEquals(original, CompletableFuture.supplyAsync(() -> IrisStarSource.read(member, original)).join());
            }
            assertEquals(original, IrisStarSource.read(member, original));
        }
    }

    @Test void nestedRejectedLoadAndExceptionCannotLeakPermission() throws Exception {
        try (var fs = FileSystems.newFileSystem(pack())) {
            var root = fs.getPath("/shaders");
            var member = root.resolve(StarSource.MEMBER);
            var original = Files.readString(member);
            try (var outer = IrisStarSource.open(root)) {
                assertThrows(IllegalStateException.class, () -> {
                    try (var inner = IrisStarSource.open(Path.of("shaders"))) {
                        assertEquals(original, IrisStarSource.read(member, original));
                        throw new IllegalStateException("failed pack load");
                    }
                });
                assertOptimized(IrisStarSource.read(member, original));
            }
            assertEquals(original, IrisStarSource.read(member, original));
        }
    }

    @Test void disabledAdapterAndWrongRootRemainClockOnly() throws Exception {
        try (var fs = FileSystems.newFileSystem(pack())) {
            var root = fs.getPath("/shaders");
            var member = root.resolve(StarSource.MEMBER);
            var original = Files.readString(member);
            try (var scope = IrisStarSource.open(fs.getPath("/"))) {
                assertEquals(original, IrisStarSource.read(member, original));
            }
            String previous = System.getProperty("sleeptrails.adapter.disabled");
            try {
                System.setProperty("sleeptrails.adapter.disabled", "true");
                try (var scope = IrisStarSource.open(root)) {
                    assertEquals(original, IrisStarSource.read(member, original));
                }
            } finally {
                if (previous == null) System.clearProperty("sleeptrails.adapter.disabled");
                else System.setProperty("sleeptrails.adapter.disabled", previous);
            }
        }
    }

    @Test void irisRelativeZipRootUsesTheSameExactMember() throws Exception {
        try (var fs = FileSystems.newFileSystem(pack())) {
            var root = fs.getPath("shaders");
            var member = root.resolve(StarSource.MEMBER);
            var original = Files.readString(member);
            try (var scope = IrisStarSource.open(root)) {
                assertOptimized(IrisStarSource.read(member, original));
            }
        }
    }

    @Test void sameMemberInAnUnrecognizedArchiveCannotBorrowAdmission() throws Exception {
        var changed = Files.createTempFile(Path.of("."), "unsupported-star-pack-", ".zip");
        try {
            String original;
            try (var zip = new ZipFile(pack().toFile())) {
                original = new String(zip.getInputStream(zip.getEntry("shaders/" + StarSource.MEMBER)).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            }
            try (var zip = new ZipOutputStream(Files.newOutputStream(changed))) {
                zip.putNextEntry(new ZipEntry("shaders/" + StarSource.MEMBER));
                zip.write(original.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            try (var fs = FileSystems.newFileSystem(changed); var scope = IrisStarSource.open(fs.getPath("/shaders"))) {
                assertEquals(original, IrisStarSource.read(fs.getPath("/shaders/" + StarSource.MEMBER), original));
            }
        } finally { Files.deleteIfExists(changed); }
    }
}

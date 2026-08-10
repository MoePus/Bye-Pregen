package com.moepus.byepregen.linkage;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(SurfacePackagedLinkageTest.MOD_ID)
public final class SurfacePackagedLinkageTest {
    static final String MOD_ID = "byepregen_surface_linkage_test";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESULT_PROPERTY = "byepregen.surfaceLinkageResult";
    private static final int TEST_CHUNK_X = 128;
    private static final int TEST_CHUNK_Z = -128;

    public SurfacePackagedLinkageTest() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        server.execute(() -> run(server));
    }

    private static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            level.getChunkSource().getChunk(
                    TEST_CHUNK_X,
                    TEST_CHUNK_Z,
                    ChunkStatus.FULL,
                    true
            );
            Evidence evidence = collectEvidence();
            writeResult(evidence.format());
            LOGGER.info("BYEPREGEN_PACKAGED_SURFACE_LINKAGE_PASS {}", evidence);
            server.halt(false);
        } catch (Throwable throwable) {
            writeFailure(throwable);
            LOGGER.error("BYEPREGEN_PACKAGED_SURFACE_LINKAGE_FAIL", throwable);
            server.halt(false);
        }
    }

    private static Evidence collectEvidence() throws ReflectiveOperationException {
        ClassLoader loader = SurfacePackagedLinkageTest.class.getClassLoader();
        Class<?> modClass = Class.forName("com.moepus.byepregen.Byepregen", true, loader);
        URL location = modClass.getProtectionDomain().getCodeSource().getLocation();
        String codeSource = location == null ? "" : location.toExternalForm();
        String normalizedSource = codeSource.toLowerCase(Locale.ROOT);
        if (!normalizedSource.contains(".jar") || normalizedSource.contains("build/classes")) {
            throw new IllegalStateException("ByePregen was not loaded from a packaged jar: " + codeSource);
        }

        Class<?> metricsClass = Class.forName(
                "com.moepus.byepregen.worldgen.surface.SurfaceScalarMetrics",
                true,
                loader
        );
        Object snapshot = metricsClass.getMethod("snapshot").invoke(null);
        long compiled = longValue(snapshot, "compiled");
        long rejected = longValue(snapshot, "rejected");
        long buildBindings = longValue(snapshot, "buildBindings");
        long topBindings = longValue(snapshot, "topBindings");
        long bindFailures = longValue(snapshot, "bindFailures");
        long classBytes = longValue(snapshot, "latestClassBytes");
        if (compiled < 1 || buildBindings < 1 || classBytes < 1) {
            throw new IllegalStateException("Surface scalar did not compile and bind: " + snapshot);
        }
        if (topBindings != 0) {
            throw new IllegalStateException("Cold topMaterial path must remain vanilla: " + snapshot);
        }
        if (rejected != 0 || bindFailures != 0) {
            throw new IllegalStateException("Surface scalar rejected or failed to bind: " + snapshot);
        }
        return new Evidence(codeSource, compiled, buildBindings, topBindings, classBytes);
    }

    private static long longValue(Object target, String methodName)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return ((Number) method.invoke(target)).longValue();
    }

    private static void writeResult(String result) throws Exception {
        Path path = resultPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, result, StandardCharsets.UTF_8);
    }

    private static void writeFailure(Throwable throwable) {
        try {
            writeResult("FAIL\n" + throwable + "\n");
        } catch (Exception writeFailure) {
            throwable.addSuppressed(writeFailure);
        }
    }

    private static Path resultPath() {
        String value = System.getProperty(RESULT_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing -D" + RESULT_PROPERTY);
        }
        return Path.of(value);
    }

    private record Evidence(
            String codeSource,
            long compiled,
            long buildBindings,
            long topBindings,
            long classBytes
    ) {
        String format() {
            return "PASS\n"
                    + "codeSource=" + this.codeSource + "\n"
                    + "compiled=" + this.compiled + "\n"
                    + "buildBindings=" + this.buildBindings + "\n"
                    + "topBindings=" + this.topBindings + "\n"
                    + "latestClassBytes=" + this.classBytes + "\n";
        }
    }
}

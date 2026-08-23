package com.moepus.byepregen.linkage;

import com.moepus.byepregen.harness.HarnessResultFile;
import com.moepus.byepregen.harness.HarnessServerLifecycle;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.net.URL;
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
    private static final HarnessServerLifecycle.FailureOptions FAILURE =
            new HarnessServerLifecycle.FailureOptions(
                    RESULT_PROPERTY,
                    LOGGER,
                    "BYEPREGEN_PACKAGED_SURFACE_LINKAGE_FAIL"
            );
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
        HarnessServerLifecycle.execute(server, FAILURE, () -> {
            ServerLevel level = server.overworld();
            level.getChunkSource().getChunk(
                    TEST_CHUNK_X,
                    TEST_CHUNK_Z,
                    ChunkStatus.FULL,
                    true
            );
            Evidence evidence = collectEvidence();
            HarnessResultFile.write(RESULT_PROPERTY, evidence.format());
            LOGGER.info("BYEPREGEN_PACKAGED_SURFACE_LINKAGE_PASS {}", evidence);
        });
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
        long bindings = longValue(snapshot, "bindings");
        long bindFailures = longValue(snapshot, "bindFailures");
        long classBytes = longValue(snapshot, "latestClassBytes");
        if (compiled < 1 || bindings < 1 || classBytes < 1) {
            throw new IllegalStateException("Surface scalar did not compile and bind: " + snapshot);
        }
        if (rejected != 0 || bindFailures != 0) {
            throw new IllegalStateException("Surface scalar rejected or failed to bind: " + snapshot);
        }
        return new Evidence(codeSource, compiled, bindings, classBytes);
    }

    private static long longValue(Object target, String methodName)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return ((Number) method.invoke(target)).longValue();
    }

    private record Evidence(
            String codeSource,
            long compiled,
            long bindings,
            long classBytes
    ) {
        String format() {
            return "PASS\n"
                    + "codeSource=" + this.codeSource + "\n"
                    + "compiled=" + this.compiled + "\n"
                    + "bindings=" + this.bindings + "\n"
                    + "latestClassBytes=" + this.classBytes + "\n";
        }
    }
}

package com.moepus.byepregen.worldgentest;

import com.moepus.byepregen.worldgen.biome.ClimateRTreeSearchContext;
import com.moepus.byepregen.worldgen.surface.SurfaceScalarMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

public final class WorldgenRuntimeVerifier {
    private WorldgenRuntimeVerifier() { }

    public static void checkSurfacePaths() {
        if (System.getProperty("byepregen.worldgenHarness.enabled") == null) return;
        boolean enabled = Boolean.getBoolean("byepregen.worldgenHarness.enabled");
        if (enabled != (SurfaceRuntimeProbe.queries() > 0)
                || enabled != (SurfaceScalarMetrics.snapshot().compiled() > 0)) {
            throw new AssertionError("Surface compiler/cache runtime gate mismatch");
        }
    }

    public static void verify(MinecraftServer server) {
        Path result = Path.of(System.getProperty("byepregen.worldgenHarness.result"));
        try {
            int queries = ClimateSearchVerifier.verify();
            int materialSamples = MaterialRuleVerifier.verify(server);
            // Registered rule trees must reach both the compiler and its bounded column scan.
            if (SurfaceScalarMetrics.snapshot().compiled() == 0) throw new AssertionError("Surface compiler did not run");
            if (SurfaceScalarMetrics.snapshot().bounded() == 0) {
                throw new AssertionError("No material rule bounded its stone-depth column scan");
            }
            // The depth-column cache must actually answer searches, not just exist.
            if (ClimateRTreeSearchContext.columnSearches() == 0) {
                throw new AssertionError("Climate depth-column search never ran");
            }
            int postprocessCases = PostProcessVerifier.verify(server.overworld());
            int arenaCases = ArenaTerrainVerifier.verify(server.overworld());
            for (var level : server.getAllLevels()) level.getChunk(57, -61);
            if (SurfaceRuntimeProbe.queries() == 0) throw new AssertionError("Surface cache did not execute");
            Files.writeString(result, "PASS\nclimateQueries=" + queries
                    + "\nclimateColumnSearches=" + ClimateRTreeSearchContext.columnSearches()
                    + "\nmaterialSamples=" + materialSamples
                    + "\nsurfacePlans=" + SurfaceScalarMetrics.snapshot().compiled()
                    + "\nboundedSurfacePlans=" + SurfaceScalarMetrics.snapshot().bounded()
                    + "\nsurfaceBiomeQueries=" + SurfaceRuntimeProbe.queries()
                    + "\npostprocessCases=" + postprocessCases + "\narenaFillCases=" + arenaCases + '\n');
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(result, "FAIL\n" + failure); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        } finally {
            server.halt(false);
        }
    }
}


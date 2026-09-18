package com.moepus.byepregen.dfctest;

import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.*;

public final class DfcRuntimeVerifier {
    private static final LongAdder VOLUMES = new LongAdder();
    private static final LongAdder POINTS = new LongAdder();
    private static final LongAdder COLUMNS = new LongAdder();
    private static final long[] SEEDS = {1, 42, 8675309};
    private static final DensityVolume[] SAMPLES = {
            new DensityVolume(7, 19, 5, -17, -23, 31, 1, 1, 1),
            new DensityVolume(5, 9, 3, -16, -32, 16, 4, 8, 4),
            new DensityVolume(3, 7, 3, -15, -29, 17, 2, 3, 5),
            new DensityVolume(8, 32, 8, -16, -32, 16)
    };

    private DfcRuntimeVerifier() { }
    public static void recordVolume() { VOLUMES.increment(); }
    public static void recordPoint() { POINTS.increment(); }
    public static void recordColumn() { COLUMNS.increment(); }
    public static long volumes() { return VOLUMES.sum(); }
    public static long columns() { return COLUMNS.sum(); }

    public static void verify(MinecraftServer server) {
        Path result = Path.of(System.getProperty("byepregen.dfcHarness.result"));
        try {
            int functions = 0;
            var levels = new ArrayList<ServerLevel>();
            server.getAllLevels().forEach(levels::add);
            levels.sort(Comparator.comparing(level -> level.dimension().identifier().toString()));
            try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(result + ".samples"))))) {
                for (ServerLevel level : levels) functions += sampleLevel(level, output);
            }
            boolean enabled = ConfigManager.getConfig().worldgen().arena().densityColumnCompiler();
            if (enabled != (VOLUMES.sum() > 0) || enabled != (POINTS.sum() > 0)) {
                throw new AssertionError("DFC runtime gate/path mismatch: " + VOLUMES.sum() + '/' + POINTS.sum());
            }
            boolean expectColumns = enabled;
            if (expectColumns != (COLUMNS.sum() > 0)) throw new AssertionError("DFC column path/gate mismatch: " + COLUMNS.sum());
            Files.writeString(result, "PASS\nfunctions=" + functions + "\nkernels=" + ColumnClassBuilder.generatedCount()
                    + "\nvolumes=" + VOLUMES.sum() + "\npoints=" + POINTS.sum() + "\ncolumns=" + COLUMNS.sum() + '\n');
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(result, "FAIL\n" + failure); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        } finally {
            server.halt(false);
        }
    }

    private static int sampleLevel(ServerLevel level, DataOutputStream output) throws Exception {
        TreeMap<String, DensityFunction> functions = new TreeMap<>();
        var registry = level.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION);
        registry.listElements().forEach(holder -> functions.put(holder.key().identifier().toString(), holder.value()));
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var settings = generator.generatorSettings().value();
        var router = settings.noiseRouter();
        for (var component : router.getClass().getRecordComponents()) {
            functions.put("router/" + component.getName(), (DensityFunction) component.getAccessor().invoke(router));
        }
        DfcOperatorProbes.add(functions, level.registryAccess().lookupOrThrow(Registries.NOISE));
        for (long seed : SEEDS) {
            var randomState = RandomState.create(level.registryAccess().lookupOrThrow(Registries.NOISE), seed, settings);
            for (var entry : functions.entrySet()) {
                DensitySampler sampler = randomState.getSampler(entry.getValue());
                SamplerContext context = SamplerContext.builder().enableCaches().build();
                String name = level.dimension().identifier() + "/" + seed + "/" + entry.getKey();
                for (int index = 0; index < SAMPLES.length; ++index) sample(sampler, context, name, index, SAMPLES[index], output);
            }
            DensitySampler finalDensity = randomState.getSampler(router.finalDensity());
            var noise = settings.noiseSettings();
            for (int x : new int[]{-32, 0}) {
                var volume = new DensityVolume(16, noise.height(), 16, x, noise.minY(), 16);
                sample(finalDensity, SamplerContext.builder().enableCaches().build(),
                        level.dimension().identifier() + "/" + seed + "/full-final/" + x, 0, volume, output);
                DfcColumnVerifier.verify(finalDensity, volume);
            }
        }
        return functions.size() * SEEDS.length;
    }

    private static void sample(DensitySampler sampler, SamplerContext context, String name, int index,
                               DensityVolume volume, DataOutputStream output) throws Exception {
        samplePoints(sampler, SamplerContext.builder().enableCaches().build(),
                name + "/point-fresh-" + index, volume, output);
        try (ScopedDensityBuffer buffer = context.acquireBuffer(volume)) {
            sampler.sampleVolume(context, buffer, volume);
            output.writeUTF(name + "/volume-" + index);
            output.writeInt(buffer.size());
            for (int i = 0; i < buffer.size(); ++i) output.writeFloat(buffer.get(i));
        }
        samplePoints(sampler, context, name + "/point-cached-" + index, volume, output);
        var replacement = new DensityVolume(volume.sizeX(), volume.sizeY(), volume.sizeZ(),
                volume.maxBlockX() + 17, volume.minBlockY(), volume.maxBlockZ() + 17,
                volume.stepBlockX(), volume.stepBlockY(), volume.stepBlockZ());
        try (ScopedDensityBuffer buffer = context.acquireBuffer(replacement)) {
            sampler.sampleVolume(context, buffer, replacement);
        }
        samplePoints(sampler, context, name + "/point-replaced-" + index, volume, output);
    }

    private static void samplePoints(DensitySampler sampler, SamplerContext context, String name,
                                     DensityVolume volume, DataOutputStream output) throws Exception {
        output.writeUTF(name);
        output.writeInt(volume.sizeY());
        for (int y = 0; y < volume.sizeY(); ++y) {
            output.writeFloat(sampler.sampleValue(context, volume.minBlockX(), volume.blockY(y), volume.minBlockZ()));
        }
    }
}

package com.moepus.byepregen.worldgentest;

import com.moepus.byepregen.worldgen.surface.SurfaceScalarMetrics;
import com.moepus.byepregen.worldgen.surface.SurfaceTemplateCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

final class MaterialRuleVerifier {
    private static final Constructor<?> CONSTRUCTOR = MaterialRuleContext.class.getDeclaredConstructors()[0];
    private static final Method UPDATE_XZ = method("updateXZ");
    private static final Method UPDATE_Y = method("updateY");
    private static final int[] HEIGHTS = {-65, -64, -60, -59, -8, 0, 1, 31, 49, 50, 62, 63, 64, 65, 90, 127, 128, 255, 319};

    static { CONSTRUCTOR.setAccessible(true); }

    static int verify(MinecraftServer server) throws Exception {
        int samples = 0;
        for (ServerLevel level : server.getAllLevels()) samples += verifyLevel(level);
        return samples;
    }

    private static int verifyLevel(ServerLevel level) throws Exception {
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        List<MaterialRule> rules = new ArrayList<>();
        level.registryAccess().lookupOrThrow(Registries.MATERIAL_RULE).listElements().forEach(h -> rules.add(h.value()));
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList();
        int samples = 0;
        for (long seed : new long[]{1, 42, 8675309}) {
            var random = RandomState.create(level.registryAccess().lookupOrThrow(Registries.NOISE), seed,
                    generator.generatorSettings().value());
            for (int mode = 0; mode < 4; mode++) {
                Set<Holder<Biome>> possible = switch (mode) {
                    case 0 -> null;
                    case 1 -> Set.of();
                    case 2 -> Set.of(biomes.getFirst());
                    default -> new HashSet<>(biomes);
                };
                Function<BlockPos, Holder<Biome>> getter = mode == 2 ? pos -> biomes.getFirst()
                        : pos -> biomes.get(Math.floorMod(pos.getX() + pos.getY() + pos.getZ(), biomes.size()));
                for (MaterialRule rule : rules) {
                    var a = context(level, random, getter, possible);
                    var b = context(level, random, getter, possible);
                    samples += compare(rule, a, b);
                }
            }
        }
        return samples;
    }

    private static MaterialRuleContext context(ServerLevel level, RandomState random,
            Function<BlockPos, Holder<Biome>> getter, Set<Holder<Biome>> possible) throws Exception {
        return (MaterialRuleContext) CONSTRUCTOR.newInstance(random.surfaceSystem(), random,
                new DensityVolume(2, 32, 2, -17, -64, 31),
                random.samplersWithContext(SamplerContext.builder().enableCaches().build()), getter,
                new WorldGenerationContext(level.getChunkSource().getGenerator(), level), possible);
    }

    private static int compare(MaterialRule source, MaterialRuleContext a, MaterialRuleContext b) throws Exception {
        var reference = source.compile(a);
        long before = SurfaceScalarMetrics.snapshot().compiled();
        var compiled = (RuleEvaluator) new SurfaceTemplateCache().bind(source, b);
        // bind() falls back to the vanilla rule silently, so require that a template was compiled.
        if (SurfaceScalarMetrics.snapshot().compiled() == before) {
            throw new AssertionError("surface compiler fell back for " + source);
        }
        int count = 0;
        for (int column = 0; column < 6; column++) {
            int x = -17 + column, z = 31 - column;
            UPDATE_XZ.invoke(a, x, z, column - 4, column);
            UPDATE_XZ.invoke(b, x, z, column - 4, column);
            for (int y : HEIGHTS) {
                int water = column % 2 == 0 ? Integer.MIN_VALUE : 63;
                UPDATE_Y.invoke(a, column + 1, 8 - column, water, y);
                UPDATE_Y.invoke(b, column + 1, 8 - column, water, y);
                if (reference.tryApply(x, y, z) != compiled.tryApply(x, y, z)) {
                    throw new AssertionError("Material mismatch at " + x + '/' + y + '/' + z + ": " + source);
                }
                count++;
            }
        }
        return count;
    }

    private static Method method(String name) {
        try {
            Method method = MaterialRuleContext.class.getDeclaredMethod(name, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
}

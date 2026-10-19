package com.moepus.byepregen.harness.terrablender;

import com.moepus.byepregen.worldgen.surface.SurfaceTemplateCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

/** Compares real registered material rules, including TerraBlender's vanilla fallback tree. */
final class TerraBlenderLiveRules {
    private static final Constructor<?> CONSTRUCTOR = MaterialRuleContext.class.getDeclaredConstructors()[0];
    private static final Method UPDATE_XZ = method("updateXZ");
    private static final Method UPDATE_Y = method("updateY");
    private static final int[] HEIGHTS = {-64, -8, 0, 31, 63, 64, 90, 127, 255, 319};

    static { CONSTRUCTOR.setAccessible(true); }

    private TerraBlenderLiveRules() { }

    static int verify(MinecraftServer server) throws Exception {
        int samples = 0;
        for (ServerLevel level : server.getAllLevels()) samples += verifyLevel(level);
        return samples;
    }

    private static int verifyLevel(ServerLevel level) throws Exception {
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var settings = generator.generatorSettings().value();
        MaterialRule source = new MaterialRule.HolderHolder(settings.materialRule());
        var random = RandomState.create(level.registryAccess().lookupOrThrow(Registries.NOISE), 42, settings);
        List<Holder.Reference<Biome>> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList();
        Function<BlockPos, Holder<Biome>> getter = pos -> biomes.get(Math.floorMod(pos.getY(), biomes.size()));
        int samples = 0;
        SurfaceTemplateCache cache = new SurfaceTemplateCache();
        for (int mode = 0; mode < 4; mode++) {
            Set<Holder<Biome>> possible = switch (mode) {
                case 0 -> null;
                case 1 -> Set.of();
                case 2 -> Set.of(biomes.getFirst());
                default -> new HashSet<>(biomes);
            };
            var referenceContext = context(level, random, getter, possible);
            var compiledContext = context(level, random, getter, possible);
            RuleEvaluator reference = source.compile(referenceContext);
            RuleEvaluator compiled = (RuleEvaluator) cache.bind(source, compiledContext);
            samples += compare(reference, compiled, referenceContext, compiledContext);
        }
        return samples;
    }

    private static int compare(RuleEvaluator reference, RuleEvaluator compiled,
            MaterialRuleContext a, MaterialRuleContext b) throws Exception {
        int samples = 0;
        for (int column = 0; column < 3; column++) {
            int x = -17 + column, z = 31 - column;
            UPDATE_XZ.invoke(a, x, z, column - 4, column);
            UPDATE_XZ.invoke(b, x, z, column - 4, column);
            for (int y : HEIGHTS) {
                UPDATE_Y.invoke(a, column + 1, 8 - column, 63, y);
                UPDATE_Y.invoke(b, column + 1, 8 - column, 63, y);
                if (reference.tryApply(x, y, z) != compiled.tryApply(x, y, z)) {
                    throw new AssertionError("TerraBlender material mismatch at " + x + '/' + y + '/' + z);
                }
                samples++;
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

    private static Method method(String name) {
        try {
            Method method = MaterialRuleContext.class.getDeclaredMethod(name, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}

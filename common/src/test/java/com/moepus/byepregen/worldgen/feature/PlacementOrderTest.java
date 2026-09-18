package com.moepus.byepregen.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.MapCodec;
import com.moepus.byepregen.config.ConfigManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The fast placement path has to walk modifiers exactly like vanilla's FeaturePlacer: a stack of
 * (position, modifier index) whose outputs are pushed back in reverse, so the first output descends
 * first. Both the traversal and the random draws are compared against a reference implementation
 * transcribed from FeaturePlacer's bytecode - the layer order and the RNG interleaving are what
 * worldgen output depends on.
 */
final class PlacementOrderTest {
    @BeforeAll
    static void setUp() throws IOException {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path directory = Files.createTempDirectory("byepregen-placement-order");
        ConfigManager.initialize(directory.resolve("byepregen.toml"));
    }

    @Test
    void walksModifiersAndDrawsRandomsLikeVanilla() {
        List<int[][]> cases = List.of(
                new int[][]{{0, 2}, {1, 2}, {1, 1}},
                new int[][]{{0, 3}, {2, 1}, {1, 3}, {1, 1}},
                new int[][]{{1, 2}, {2, 1}, {0, 3}, {3, 2}, {1, 1}});
        for (int[][] modifiers : cases) {
            assertEquals(reference(modifiers, 0x5EEDL), production(modifiers, 0x5EEDL),
                    "modifier count " + modifiers.length);
        }
    }

    /** Transcribed from FeaturePlacer.place: pop, collect, push the outputs back in reverse. */
    private static List<String> reference(int[][] modifiers, long seed) {
        List<String> log = new ArrayList<>();
        RandomSource random = RandomSource.create(seed);
        Deque<int[]> pending = new ArrayDeque<>();
        pending.addLast(new int[]{0, 0, 0, 0});
        while (!pending.isEmpty()) {
            int[] entry = pending.removeLast();
            int index = entry[3];
            int[] modifier = modifiers[index];
            StringBuilder draws = new StringBuilder();
            for (int i = 0; i < modifier[0]; ++i) {
                draws.append(random.nextInt(1000)).append(',');
            }
            log.add("m" + index + "(" + entry[0] + "," + entry[1] + "," + entry[2] + ")"
                    + (draws.length() == 0 ? "" : "{" + draws + "}"));
            List<int[]> children = new ArrayList<>();
            for (int i = 0; i < modifier[1]; ++i) {
                children.add(new int[]{entry[0] + i, entry[1], entry[2]});
            }
            if (index + 1 == modifiers.length) {
                for (int[] child : children) {
                    log.add("place(" + child[0] + "," + child[1] + "," + child[2] + ")");
                }
            } else {
                for (int i = children.size() - 1; i >= 0; --i) {
                    int[] child = children.get(i);
                    pending.addLast(new int[]{child[0], child[1], child[2], index + 1});
                }
            }
        }
        return log;
    }

    private static List<String> production(int[][] modifiers, long seed) {
        List<String> log = new ArrayList<>();
        Feature feature = new RecordingFeature(log);
        List<PlacementModifier> placement = new ArrayList<>();
        for (int i = 0; i < modifiers.length; ++i) {
            placement.add(new TestModifier(i, modifiers[i][0], modifiers[i][1], log));
        }
        PlacementContext context = new PlacementContext(heightAccessor(), new TestGenerator(), java.util.Optional.empty());
        FastPlacementContext fast = FastPlacementContext.acquire(
                context, RandomSource.create(seed), feature, placement);
        try {
            assertTrue(fast.place(new BlockPos(0, 0, 0), FeaturePlan.create(feature, placement)));
        } finally {
            FastPlacementContext.release(fast);
        }
        return log;
    }

    /** PlacementContext only reads the height accessor and the generator's bounds while it is built. */
    private static WorldGenLevel heightAccessor() {
        return (WorldGenLevel) java.lang.reflect.Proxy.newProxyInstance(
                PlacementOrderTest.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class},
                (proxy, method, args) -> switch (method.getReturnType().getName()) {
                    case "int" -> 0;
                    case "long" -> 0L;
                    case "boolean" -> false;
                    case "double" -> 0.0D;
                    case "float" -> 0.0F;
                    default -> null;
                });
    }

    /** Nothing in this test reads the generator beyond its vertical bounds. */
    private static final class TestGenerator extends ChunkGenerator {
        private TestGenerator() {
            super(new TestBiomeSource());
        }

        @Override protected MapCodec<? extends ChunkGenerator> codec() { return MapCodec.unit(this); }
        @Override public void spawnOriginalMobs(WorldGenRegion region) { }
        @Override public int getGenDepth() { return 384; }
        @Override public int getSeaLevel() { return 63; }
        @Override public int getMinY() { return -64; }
        @Override public int getBaseHeight(int x, int z, Heightmap.Types type,
                                          LevelHeightAccessor level, RandomState randomState) { return 0; }
        @Override public NoiseColumn getBaseColumn(int x, int z,
                                                   LevelHeightAccessor level, RandomState randomState) { return null; }
        @Override public void addDebugScreenInfo(List<String> lines, RandomState randomState,
                                                 BlockPos pos, SamplerContext context) { }

        @Override
        public CompletableFuture<ChunkAccess> buildTerrain(
                ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structureManager,
                BiomeManager biomeManager, WorldGenRegion region, java.util.Set<Holder<Biome>> biomes) {
            return CompletableFuture.completedFuture(chunk);
        }
    }

    private static final class TestBiomeSource extends BiomeSource {
        @Override protected MapCodec<? extends BiomeSource> codec() { return MapCodec.unit(this); }
        @Override protected java.util.stream.Stream<Holder<Biome>> collectPossibleBiomes() {
            return java.util.stream.Stream.empty();
        }
        @Override public BiomeResolver createResolver(Climate.Sampler sampler) { return null; }
    }

    /** Draws {@code draws} randoms, then emits {@code children} positions like a placement modifier. */
    private record TestModifier(int index, int draws, int children, List<String> log)
            implements PlacementModifier, FastPlacementModifier {
        @Override
        public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z) {
            this.record(context.random(), x, y, z);
            for (int i = 0; i < this.children; ++i) {
                context.emit(x + i, y, z);
            }
        }

        @Override
        public void modify(PlacementContext context, RandomSource random, BlockPos pos,
                           Consumer<BlockPos> output) {
            this.record(random, pos.getX(), pos.getY(), pos.getZ());
            for (int i = 0; i < this.children; ++i) {
                output.accept(new BlockPos(pos.getX() + i, pos.getY(), pos.getZ()));
            }
        }

        private void record(RandomSource random, int x, int y, int z) {
            StringBuilder values = new StringBuilder();
            for (int i = 0; i < this.draws; ++i) {
                values.append(random.nextInt(1000)).append(',');
            }
            this.log.add("m" + this.index + "(" + x + "," + y + "," + z + ")"
                    + (values.length() == 0 ? "" : "{" + values + "}"));
        }

        @Override
        public MapCodec<? extends PlacementModifier> codec() {
            return MapCodec.unit(this);
        }
    }

    private record RecordingFeature(List<String> log) implements Feature {
        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) {
            this.log.add("place(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")");
            return true;
        }

        @Override
        public MapCodec<? extends Feature> codec() {
            return MapCodec.unit(this);
        }
    }
}

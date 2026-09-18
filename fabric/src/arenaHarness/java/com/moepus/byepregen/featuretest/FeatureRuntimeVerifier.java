package com.moepus.byepregen.featuretest;

import com.mojang.serialization.MapCodec;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.worldgen.feature.FastDiskFeature;
import com.moepus.byepregen.worldgen.feature.FastPlacedFeature;
import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.*;

public final class FeatureRuntimeVerifier {
    private static final long[] SEEDS = {1, 42, 8675309};
    private static final LongAdder MEMOIZED_DISKS = new LongAdder();

    private FeatureRuntimeVerifier() { }

    public static void verify(MinecraftServer server) {
        Path result = Path.of(System.getProperty("byepregen.featureHarness.result"));
        try {
            TreeMap<String, String> results = new TreeMap<>();
            TreeMap<String, String> exactTrees = new TreeMap<>();
            ServerLevel level = server.overworld();
            for (Scenario scenario : scenarios(level)) {
                for (long seed : SEEDS) results.put(scenario.name() + '/' + seed, run(level, scenario, seed, exactTrees));
            }
            results.put("failure-cleanup", failureCleanup(level));
            FeatureTestWorld frontier = TreeSetVerifier.leafFrontier(level);
            exactTrees.put("tree-leaf-frontier", frontier.signature());
            results.put("tree-leaf-frontier", frontier.treeSignature());
            if (ConfigManager.getConfig().worldgen().placedFeatures().enabled() && MEMOIZED_DISKS.sum() == 0) {
                throw new AssertionError("Memoized disk path did not execute");
            }
            Files.createDirectories(result.getParent());
            writeResults(Path.of(result + ".trees-exact"), exactTrees);
            writeResults(result, results);
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(result, "FAIL\n" + failure); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        } finally {
            server.halt(false);
        }
    }

    public static void recordMemoizedDisk() { MEMOIZED_DISKS.increment(); }

    private static void writeResults(Path path, Map<String, String> results) throws Exception {
        StringBuilder output = new StringBuilder("PASS\n");
        results.forEach((key, value) -> output.append(key).append('=').append(value).append('\n'));
        Files.writeString(path, output);
    }

    private static String failureCleanup(ServerLevel level) throws Exception {
        FeatureTestWorld world = new FeatureTestWorld(level, false);
        RandomSource random = RandomSource.create(9876);
        var generator = level.getChunkSource().getGenerator();
        var failing = new PlacedFeature(Holder.direct(new FailingFeature()), List.of(CountPlacement.of(3)));
        try {
            new FeaturePlacer(world.level(), generator).place(failing, random, new BlockPos(0, 63, 0));
            throw new AssertionError("Expected feature exception");
        } catch (IllegalStateException expected) {
            if (!"fixture failure".equals(expected.getMessage())) throw expected;
        }
        var next = new PlacedFeature(Holder.direct(new TraceFeature()), List.of(CountPlacement.of(2)));
        boolean success = new FeaturePlacer(world.level(), generator).place(next, random, new BlockPos(1, 63, 1));
        return success + ":" + random.nextLong() + ":" + world.signature();
    }

    private static String run(ServerLevel level, Scenario scenario, long seed, Map<String, String> exactTrees) throws Exception {
        FeatureTestWorld world = new FeatureTestWorld(level, scenario.layers());
        RandomSource random = RandomSource.create(seed);
        var config = ConfigManager.getConfig().worldgen().placedFeatures();
        PlacedFeature placed = new PlacedFeature(Holder.direct(scenario.feature()), scenario.modifiers());
        if (((Object) placed instanceof FastPlacedFeature) != config.enabled()) {
            throw new AssertionError("Placed feature mixin gate did not match config");
        }
        if (((Object) CountPlacement.of(2) instanceof FastPlacementModifier) != (config.enabled() || config.localOptimizations())) {
            throw new AssertionError("Repeating modifier fast path missing");
        }
        if (scenario.feature() instanceof DiskFeature disk
                && ((Object) disk instanceof FastDiskFeature) != (config.enabled() || config.localOptimizations())) {
            throw new AssertionError("Disk fast path missing");
        }
        boolean success = new FeaturePlacer(world.level(), level.getChunkSource().getGenerator())
                .placeWithBiomeCheck(placed, random, scenario.origin());
        if (scenario.name().equals("disk-null-chain") && world.writeCount() == 0) {
            throw new AssertionError("Nullable first rule suppressed the next rule");
        }
        String prefix = success + ":" + random.nextLong() + ":";
        String exact = prefix + world.signature();
        if (!scenario.name().startsWith("tree-")) return exact;
        exactTrees.put(scenario.name() + '/' + seed, exact);
        return prefix + world.treeSignature();
    }

    private static List<Scenario> scenarios(ServerLevel level) {
        List<Scenario> cases = new ArrayList<>();
        Feature trace = new TraceFeature();
        cases.add(new Scenario("branching", trace, List.of(new TopFeatureCheckPlacement(true), CountPlacement.of(3), new BurstPlacement(false),
                OffsetPlacement.of(UniformInt.of(-2, 2), ConstantInt.of(0)), RarityFilter.onAverageOnceEvery(2)), false));
        cases.add(new Scenario("mutable-fallback", trace, List.of(new BurstPlacement(true), CountPlacement.of(2)), false));
        cases.add(new Scenario("layers", trace, List.of(CountOnEveryLayerPlacement.of(UniformInt.of(1, 4)),
                OffsetPlacement.of(UniformInt.of(-1, 1), ConstantInt.of(0))), true));
        cases.add(new Scenario("fixed", trace, List.of(FixedPlacement.of(new BlockPos(1, 63, 1),
                new BlockPos(2, 63, 3), new BlockPos(16, 63, 0)), InSquarePlacement.spread()), false));
        cases.add(new Scenario("height-scan", trace, List.of(CountPlacement.of(5), InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG),
                HeightRangePlacement.uniform(VerticalAnchor.absolute(60), VerticalAnchor.absolute(67)),
                EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.matchesBlocks(Blocks.DIRT, Blocks.GRASS_BLOCK), 8),
                BlockPredicateFilter.forPredicate(BlockPredicate.hasSturdyFace(Direction.UP))), false));
        PlacedFeature nested = new PlacedFeature(Holder.direct(trace), List.of(new TopFeatureCheckPlacement(false), CountPlacement.of(3), new BurstPlacement(false)));
        cases.add(new Scenario("nested", new NestedFeature(nested), List.of(CountPlacement.of(2)), false));
        cases.add(new Scenario("empty-placement", trace, List.of(), false));
        addDisks(cases);
        addTrees(cases, level);
        return cases;
    }

    private static void addTrees(List<Scenario> cases, ServerLevel level) {
        for (String name : List.of("oak", "oak_bees_005", "oak_leaf_litter", "fancy_oak", "birch", "spruce",
                "acacia", "dark_oak", "cherry", "jungle_tree", "mega_jungle_tree", "mangrove", "tall_mangrove")) {
            Feature tree = level.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(Identifier.withDefaultNamespace(name));
            if (tree == null) throw new AssertionError("Missing feature " + name);
            cases.add(new Scenario("tree-" + name, tree, List.of(), false));
            cases.add(new Scenario("tree-neighbors-" + name, new NeighborTrees(tree), List.of(), false,
                    new BlockPos(-1, 63, -1)));
            if (name.equals("mangrove")) {
                TreeFeature original = (TreeFeature) tree;
                TreeFeature bare = new TreeFeature(original.trunkProvider(), original.trunkPlacer(),
                        original.foliageProvider(), original.foliagePlacer(), original.rootPlacer(),
                        original.minimumSize(), List.of(), original.ignoreVines(), original.belowTrunkProvider());
                cases.add(new Scenario("tree-mangrove-no-decorators", bare, List.of(), false));
            }
        }
    }

    private static void addDisks(List<Scenario> cases) {
        RuleBasedStateProvider empty = new RuleBasedStateProvider(null, List.of());
        RuleBasedStateProvider nullableChain = RuleBasedStateProvider.builder()
                .ifTrueThenProvide(BlockPredicate.alwaysTrue(), empty)
                .ifTrueThenProvide(BlockPredicate.alwaysTrue(), Blocks.CLAY).build();
        BlockPredicate target = BlockPredicate.allOf(BlockPredicate.matchesBlocks(Blocks.DIRT, Blocks.GRASS_BLOCK),
                BlockPredicate.not(BlockPredicate.matchesBlocks(new Vec3i(0, 1, 0), List.of(Blocks.CLAY))));
        List<PlacementModifier> repeated = List.of(CountPlacement.of(8), OffsetPlacement.of(UniformInt.of(-2, 2), ConstantInt.of(0)));
        cases.add(new Scenario("disk-null-chain", new DiskFeature(Holder.direct(nullableChain), target, UniformInt.of(2, 4), 3),
                repeated, false, new BlockPos(15, 62, 15)));
        cases.add(new Scenario("disk-negative", new DiskFeature(Holder.direct(nullableChain), target, UniformInt.of(2, 4), 3),
                repeated, false, new BlockPos(-1, 62, -1)));
        cases.add(new Scenario("disk-empty", new DiskFeature(Holder.direct(empty), target, ConstantInt.of(3), 3), repeated, false));
        var alternating = RuleBasedStateProvider.builder().ifTrueThenProvide(BlockPredicate.alwaysTrue(), new AlternatingProvider()).build();
        cases.add(new Scenario("disk-skipped-lane", new DiskFeature(Holder.direct(alternating), target, ConstantInt.of(3), 3),
                repeated, false, new BlockPos(15, 62, -1)));
        cases.add(new Scenario("disk-build-limit", new DiskFeature(Holder.direct(nullableChain), BlockPredicate.alwaysTrue(), ConstantInt.of(2), 4),
                repeated, false, new BlockPos(15, 94, -1)));
    }

    private record Scenario(String name, Feature feature, List<PlacementModifier> modifiers, boolean layers, BlockPos origin) {
        Scenario(String name, Feature feature, List<PlacementModifier> modifiers, boolean layers) {
            this(name, feature, modifiers, layers, new BlockPos(7, 63, 7));
        }
    }

    private record TraceFeature() implements Feature {
        @Override public MapCodec<? extends Feature> codec() { return MapCodec.unit(this); }
        @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            BlockState state = random.nextBoolean() ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
            level.setBlock(origin, state, 2);
            return random.nextBoolean();
        }
    }

    private record NeighborTrees(Feature tree) implements Feature {
        @Override public MapCodec<? extends Feature> codec() { return MapCodec.unit(this); }
        @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            boolean result = this.tree.place(level, generator, random, origin);
            return this.tree.place(level, generator, random, origin.offset(4, 0, 4)) | result;
        }
    }

    private record FailingFeature() implements Feature {
        @Override public MapCodec<? extends Feature> codec() { return MapCodec.unit(this); }
        @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            new TraceFeature().place(level, generator, random, origin);
            throw new IllegalStateException("fixture failure");
        }
    }

    private record TopFeatureCheckPlacement(boolean expected) implements PlacementModifier {
        @Override public MapCodec<? extends PlacementModifier> codec() { return MapCodec.unit(this); }
        @Override public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
            if (context.topFeature().isPresent() != this.expected) throw new AssertionError("Wrong topFeature scope");
            output.accept(origin);
        }
    }

    private record NestedFeature(PlacedFeature nested) implements Feature {
        @Override public MapCodec<? extends Feature> codec() { return MapCodec.unit(this); }
        @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            return this.nested.place(level, generator, random, origin);
        }
    }

    private record BurstPlacement(boolean reusePosition) implements PlacementModifier {
        @Override public MapCodec<? extends PlacementModifier> codec() { return MapCodec.unit(this); }
        @Override public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 3; ++i) {
                mutable.set(origin.getX() + random.nextInt(4), origin.getY(), origin.getZ() + random.nextInt(4));
                output.accept(this.reusePosition ? mutable : mutable.immutable());
            }
            random.nextLong();
        }
    }

    private record AlternatingProvider() implements BlockStateProvider {
        @Override public MapCodec<? extends BlockStateProvider> codec() { return MapCodec.unit(this); }
        @Override public BlockState getState(LevelAccessor level, RandomSource random, BlockPos pos) {
            throw new AssertionError("Disk must call getOptionalState");
        }
        @Override public BlockState getOptionalState(LevelAccessor level, RandomSource random, BlockPos pos) {
            return (pos.getY() & 1) == 0 ? Blocks.CLAY.defaultBlockState() : null;
        }
    }
}

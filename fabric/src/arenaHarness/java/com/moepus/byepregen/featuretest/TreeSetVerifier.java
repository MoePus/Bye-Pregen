package com.moepus.byepregen.featuretest;

import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.worldgen.feature.FastObjectHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Checks the live injected locals and exercises leaves queued at multiple distances. */
public final class TreeSetVerifier {
    private TreeSetVerifier() { }

    public static void checkSets(TreeFeature tree, Set<BlockPos> roots, Set<BlockPos> trunks,
                                 Set<BlockPos> foliage, Set<BlockPos> decorations) {
        var config = ConfigManager.getConfig().worldgen().placedFeatures();
        boolean optimized = (config.enabled() || config.localOptimizations()) && tree.decorators().isEmpty();
        checkSet(roots, optimized, "roots");
        checkSet(trunks, optimized, "trunks");
        checkSet(foliage, optimized, "foliage");
        checkSet(decorations, optimized, "decorations");
    }

    public static void checkBuckets(List<Set<BlockPos>> buckets) {
        var config = ConfigManager.getConfig().worldgen().placedFeatures();
        for (Set<BlockPos> bucket : buckets) {
            checkSet(bucket, config.enabled() || config.localOptimizations(), "leaf distance bucket");
        }
    }

    private static void checkSet(Set<BlockPos> set, boolean optimized, String role) {
        Class<?> expected = optimized ? FastObjectHashSet.class : HashSet.class;
        if (set.getClass() != expected) throw new AssertionError("Unexpected " + role + " set: " + set.getClass());
    }

    static FeatureTestWorld leafFrontier(ServerLevel level) throws Exception {
        FeatureTestWorld world = new FeatureTestWorld(level, false);
        Set<BlockPos> logs = new HashSet<>();
        for (int z = 0; z < 2; ++z) {
            BlockPos log = new BlockPos(1, 64, z);
            logs.add(log);
            world.level().setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 19);
            world.level().setBlock(log.east(), Blocks.OAK_LEAVES.defaultBlockState(), 19);
        }
        // An isolated leaf must stay at distance 7; the comparison may only fold distances 1..6.
        world.level().setBlock(new BlockPos(8, 64, 0), Blocks.OAK_LEAVES.defaultBlockState(), 19);
        var updateLeaves = TreeFeature.class.getDeclaredMethod("updateLeaves", LevelAccessor.class,
                BoundingBox.class, Set.class, Set.class, Set.class);
        updateLeaves.setAccessible(true);
        updateLeaves.invoke(null, world.level(), new BoundingBox(1, 64, 0, 8, 64, 1), logs, Set.of(), Set.of());
        return world;
    }
}

package com.moepus.byepregen.mixin.feature.disk;

import com.moepus.byepregen.worldgen.feature.DiskBlockPredicateEvaluator;
import com.moepus.byepregen.worldgen.feature.FastDiskPlacement;
import com.moepus.byepregen.worldgen.feature.FastDiskStateCursor;
import com.moepus.byepregen.worldgen.feature.FastRuleBasedBlockStateProvider;
import com.moepus.byepregen.worldgen.feature.WorldGenRegionSectionCache;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = DiskFeature.class, remap = false)
public abstract class DiskFeatureMixin extends Feature<DiskConfiguration> {
    protected DiskFeatureMixin(Codec<DiskConfiguration> codec) {
        super(codec);
    }

    @Shadow
    protected abstract boolean placeColumn(
        DiskConfiguration config,
        WorldGenLevel level,
        RandomSource random,
        int maxY,
        int minY,
        BlockPos.MutableBlockPos pos
    );

    /**
     * @author MoePus, Codex
     * @reason Reuse chunk sections and predicate states across each hot Disk column.
     */
    @Overwrite
    public boolean place(FeaturePlaceContext<DiskConfiguration> context) {
        DiskConfiguration config = context.config();
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int radius = config.radius().sample(random);
        if (!(level instanceof WorldGenRegionSectionCache chunkCache)) {
            return this.byepregen$placeVanilla(context, radius);
        }

        FastDiskStateCursor cursor = new FastDiskStateCursor(level, chunkCache);
        FastDiskPlacement placement = new FastDiskPlacement(context, cursor);
        boolean placed = false;
        int radiusSquared = radius * radius;
        for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
            int dz = z - origin.getZ();
            for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
                int dx = x - origin.getX();
                if (dx * dx + dz * dz <= radiusSquared) {
                    placed |= this.byepregen$placeColumn(placement, x, z);
                }
            }
        }
        return placed;
    }

    private boolean byepregen$placeColumn(FastDiskPlacement placement, int x, int z) {
        FastDiskStateCursor cursor = placement.cursor();
        if (!cursor.selectColumn(x, z)) {
            placement.pos().set(x, placement.maxY(), z);
            return this.placeColumn(
                placement.config(),
                placement.level(),
                placement.random(),
                placement.maxY(),
                placement.minY() - 1,
                placement.pos()
            );
        }

        boolean placed = false;
        boolean previousFirstMarked = false;
        for (int y = placement.maxY(); y >= placement.minY(); y--) {
            boolean currentFirstMarked = false;
            placement.pos().set(x, y, z);
            cursor.beginPosition(y);
            if (DiskBlockPredicateEvaluator.test(placement.config().target(), cursor, placement.pos())) {
                BlockState state = ((FastRuleBasedBlockStateProvider) (Object) placement.config().stateProvider())
                    .byepregen$getState(placement.random(), placement.pos(), cursor);
                placement.level().setBlock(placement.pos(), state, 2);
                currentFirstMarked = this.byepregen$markFirstAbove(placement.pos(), cursor, y);
                if (currentFirstMarked && !previousFirstMarked) {
                    this.byepregen$markSecondAbove(placement.pos(), cursor, y);
                }
                placed = true;
            }
            previousFirstMarked = currentFirstMarked;
        }
        return placed;
    }

    private boolean byepregen$markFirstAbove(BlockPos.MutableBlockPos pos, FastDiskStateCursor cursor, int baseY) {
        int firstY = baseY + 1;
        pos.setY(firstY);
        if (cursor.getState(pos.getX(), firstY, pos.getZ()).isAir()) {
            return false;
        }
        cursor.markForPostprocessing(pos);
        return true;
    }

    private void byepregen$markSecondAbove(BlockPos.MutableBlockPos pos, FastDiskStateCursor cursor, int baseY) {
        int secondY = baseY + 2;
        pos.setY(secondY);
        if (!cursor.getState(pos.getX(), secondY, pos.getZ()).isAir()) {
            cursor.markForPostprocessing(pos);
        }
    }

    private boolean byepregen$placeVanilla(FeaturePlaceContext<DiskConfiguration> context, int radius) {
        DiskConfiguration config = context.config();
        BlockPos origin = context.origin();
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (BlockPos column : BlockPos.betweenClosed(
            origin.offset(-radius, 0, -radius), origin.offset(radius, 0, radius)
        )) {
            int dx = column.getX() - origin.getX();
            int dz = column.getZ() - origin.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                pos.set(column);
                placed |= this.placeColumn(
                    config,
                    context.level(),
                    context.random(),
                    origin.getY() + config.halfHeight(),
                    origin.getY() - config.halfHeight() - 1,
                    pos
                );
            }
        }
        return placed;
    }

}

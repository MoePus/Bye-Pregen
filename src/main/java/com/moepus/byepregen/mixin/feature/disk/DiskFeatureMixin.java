package com.moepus.byepregen.mixin.feature.disk;

import com.moepus.byepregen.worldgen.feature.FastDiskPlacement;
import com.moepus.byepregen.worldgen.feature.FastDiskFeature;
import com.moepus.byepregen.worldgen.feature.FastDiskStateCursor;
import com.moepus.byepregen.worldgen.feature.FastRuleBasedBlockStateProvider;
import com.moepus.byepregen.worldgen.feature.WorldGenRegionSectionCache;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DiskFeature.class)
public abstract class DiskFeatureMixin extends Feature<DiskConfiguration> implements FastDiskFeature {
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

    @Override
    public boolean byepregen$placeColumn(FastDiskFeature.ColumnContext context) {
        return this.placeColumn(
                context.config(),
                context.level(),
                context.random(),
                context.maximumY(),
                context.minimumY() - 1,
                context.pos()
        );
    }

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
        if (!(level instanceof WorldGenRegionSectionCache chunkCache)
                || !((Object) config.stateProvider() instanceof FastRuleBasedBlockStateProvider)) {
            int radius = config.radius().sample(random);
            return this.byepregen$placeVanilla(context, radius);
        }

        FastDiskStateCursor cursor = new FastDiskStateCursor(level, chunkCache);
        FastDiskPlacement placement = new FastDiskPlacement(
                config, level, random, cursor, null, this::byepregen$placeColumn
        );
        return placement.placeOrigin(origin.getX(), origin.getY(), origin.getZ());
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

package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.DiskFeature;

public final class PredicateMemoizedDiskPlacement {
    private final FastDiskPlacement placement;
    private boolean placed;

    private PredicateMemoizedDiskPlacement(
            FastPlacementContext context,
            DiskFeature config,
            FastRuleBasedBlockStateProvider stateProvider,
            WorldGenRegionSectionCache sectionCache,
            KnownFalseDiskPredicateCache knownFalse
    ) {
        WorldGenLevel level = context.placementContext().getLevel();
        this.placement = new FastDiskPlacement(
                config,
                stateProvider,
                level,
                context.random(),
                new FastDiskStateCursor(level, sectionCache),
                knownFalse,
                column -> ((FastDiskFeature) (Object) config).byepregen$placeColumn(column)
        );
    }

    static PredicateMemoizedDiskPlacement open(
            FastPlacementContext context,
            DiskFeature config,
            FastRuleBasedBlockStateProvider stateProvider,
            Vec3i[] dependencies,
            boolean repeatingPlacement
    ) {
        WorldGenLevel level = context.placementContext().getLevel();
        if (!repeatingPlacement || !(level instanceof WorldGenRegionSectionCache sectionCache)) {
            return null;
        }
        return new PredicateMemoizedDiskPlacement(
                context,
                config,
                stateProvider,
                sectionCache,
                new KnownFalseDiskPredicateCache(dependencies, level.getMinY(), level.getMaxY() + 1)
        );
    }

    public void placeOrigin(int originX, int originY, int originZ) {
        this.placed |= this.placement.placeOrigin(originX, originY, originZ);
    }

    public boolean placed() {
        return this.placed;
    }

}

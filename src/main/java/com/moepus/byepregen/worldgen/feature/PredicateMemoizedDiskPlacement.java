package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

public final class PredicateMemoizedDiskPlacement {
    private final FastDiskPlacement placement;
    private boolean placed;

    private PredicateMemoizedDiskPlacement(
            FastPlacementContext context,
            DiskConfiguration config,
            KnownFalseDiskPredicateCache knownFalse
    ) {
        WorldGenRegionSectionCache cache = (WorldGenRegionSectionCache) context.placementContext().getLevel();
        this.placement = new FastDiskPlacement(
                config,
                context.placementContext().getLevel(),
                context.random(),
                new FastDiskStateCursor(context.placementContext().getLevel(), cache),
                knownFalse,
                PredicateMemoizedDiskPlacement::placeFallbackColumn
        );
    }

    static PredicateMemoizedDiskPlacement open(
            FastPlacementContext context,
            DiskConfiguration config,
            Vec3i[] dependencies,
            boolean repeatingPlacement
    ) {
        if (!(context.placementContext().getLevel() instanceof WorldGenRegionSectionCache)) {
            return null;
        }
        KnownFalseDiskPredicateCache cache = createCache(
                context,
                config,
                dependencies,
                repeatingPlacement
        );
        return cache == null ? null : new PredicateMemoizedDiskPlacement(context, config, cache);
    }

    private static KnownFalseDiskPredicateCache createCache(
            FastPlacementContext context,
            DiskConfiguration config,
            Vec3i[] dependencies,
            boolean repeatingPlacement
    ) {
        FastPlacementContext parent = context.parent();
        if (parent != null && parent.feature().feature() == Feature.RANDOM_PATCH) {
            return parent.nestedDiskCache(config, dependencies);
        }
        if (!repeatingPlacement) {
            return null;
        }
        WorldGenLevel level = context.placementContext().getLevel();
        return new KnownFalseDiskPredicateCache(
                dependencies,
                level.getMinBuildHeight(),
                level.getMaxBuildHeight()
        );
    }

    public void placeOrigin(int originX, int originY, int originZ) {
        this.placed |= this.placement.placeOrigin(originX, originY, originZ);
    }

    public boolean placed() {
        return this.placed;
    }

    private static boolean placeFallbackColumn(FastDiskFeature.ColumnContext context) {
        return ((FastDiskFeature) (Object) Feature.DISK).byepregen$placeColumn(context);
    }
}

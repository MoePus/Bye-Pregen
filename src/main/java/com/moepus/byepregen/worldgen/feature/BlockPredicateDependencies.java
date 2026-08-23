package com.moepus.byepregen.worldgen.feature;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;

final class BlockPredicateDependencies {
    private BlockPredicateDependencies() {
    }

    static Vec3i[] find(BlockPredicate predicate) {
        Set<Vec3i> offsets = new LinkedHashSet<>();
        return collect(predicate, offsets) ? offsets.toArray(Vec3i[]::new) : null;
    }

    private static boolean collect(BlockPredicate predicate, Set<Vec3i> offsets) {
        if (predicate instanceof FastStateTestingPredicate stateTesting) {
            offsets.add(stateTesting.byepregen$getOffset());
            return true;
        }

        BlockPredicateType<?> type = predicate.type();
        if (type == BlockPredicateType.ALL_OF || type == BlockPredicateType.ANY_OF) {
            return collectChildren(predicate, offsets);
        }
        if (type == BlockPredicateType.NOT && predicate instanceof FastNegatingPredicate negating) {
            return collect(negating.byepregen$getPredicate(), offsets);
        }
        return type == BlockPredicateType.TRUE || type == BlockPredicateType.INSIDE_WORLD_BOUNDS;
    }

    private static boolean collectChildren(BlockPredicate predicate, Set<Vec3i> offsets) {
        if (!(predicate instanceof FastCombiningPredicate combining)) {
            return false;
        }
        for (BlockPredicate child : combining.byepregen$getPredicates()) {
            if (!collect(child, offsets)) {
                return false;
            }
        }
        return true;
    }
}

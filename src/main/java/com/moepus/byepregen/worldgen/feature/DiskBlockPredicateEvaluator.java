package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;

public final class DiskBlockPredicateEvaluator {
    private DiskBlockPredicateEvaluator() {
    }

    public static boolean test(BlockPredicate predicate, FastDiskStateCursor cursor, BlockPos pos) {
        if (predicate instanceof FastDiskBlockPredicate fastPredicate) {
            return fastPredicate.byepregen$test(cursor, pos);
        }
        if (predicate instanceof FastCombiningPredicate combiningPredicate) {
            BlockPredicateType<?> type = predicate.type();
            if (type == BlockPredicateType.ALL_OF) {
                return testAll(combiningPredicate.byepregen$getPredicates(), cursor, pos);
            }
            if (type == BlockPredicateType.ANY_OF) {
                return testAny(combiningPredicate.byepregen$getPredicates(), cursor, pos);
            }
        }
        return predicate.test(cursor.level(), pos);
    }

    private static boolean testAll(BlockPredicate[] predicates, FastDiskStateCursor cursor, BlockPos pos) {
        for (BlockPredicate predicate : predicates) {
            if (!test(predicate, cursor, pos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean testAny(BlockPredicate[] predicates, FastDiskStateCursor cursor, BlockPos pos) {
        for (BlockPredicate predicate : predicates) {
            if (test(predicate, cursor, pos)) {
                return true;
            }
        }
        return false;
    }
}

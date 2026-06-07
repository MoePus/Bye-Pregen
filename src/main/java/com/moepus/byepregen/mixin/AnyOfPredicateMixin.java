package com.moepus.byepregen.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate;

@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.AnyOfPredicate")
public abstract class AnyOfPredicateMixin extends CombiningPredicate {
    protected AnyOfPredicateMixin(List<BlockPredicate> p_190455_) {
        super(p_190455_);
    }

    /**
     * @author MoePus, Codex
     * @reason Avoid allocating ListItr in hot worldgen predicate checks.
     */
    @Overwrite
    public boolean test(final WorldGenLevel level, final BlockPos pos) {
        final List<BlockPredicate> predicates = this.predicates;
        for (int i = 0, size = predicates.size(); i < size; i++) {
            if (predicates.get(i).test(level, pos)) {
                return true;
            }
        }

        return false;
    }
}

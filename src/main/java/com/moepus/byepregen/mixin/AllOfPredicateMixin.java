package com.moepus.byepregen.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.AllOfPredicate", remap = false)
public abstract class AllOfPredicateMixin extends CombiningPredicate {
    protected AllOfPredicateMixin(List<BlockPredicate> p_190455_) {
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
            if (!predicates.get(i).test(level, pos)) {
                return false;
            }
        }

        return true;
    }
}

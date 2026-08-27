package com.moepus.byepregen.mixin.feature.predicate;

import com.moepus.byepregen.worldgen.feature.FastCombiningPredicate;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate;

@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.AnyOfPredicate")
public abstract class AnyOfPredicateMixin extends CombiningPredicate implements FastCombiningPredicate {
    @Unique
    private BlockPredicate[] byepregen$predicates;

    protected AnyOfPredicateMixin(List<BlockPredicate> p_190455_) {
        super(p_190455_);
    }

    @InjectLite(method = "<init>", at = @At("TAIL"))
    private void byepregen$cachePredicates(List<BlockPredicate> predicates) {
        this.byepregen$predicates = predicates.toArray(new BlockPredicate[predicates.size()]);
    }

    @Override
    public final BlockPredicate[] byepregen$getPredicates() {
        return this.byepregen$predicates;
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

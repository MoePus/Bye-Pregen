package com.moepus.byepregen.mixin;

import com.moepus.byepregen.Feature.FastCombiningPredicate;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.AllOfPredicate", remap = false)
public abstract class AllOfPredicateMixin extends CombiningPredicate implements FastCombiningPredicate {
    @Unique
    private BlockPredicate[] bpg$predicates;

    protected AllOfPredicateMixin(List<BlockPredicate> p_190455_) {
        super(p_190455_);
    }

    @InjectLite(method = "<init>", at = @At("TAIL"))
    private void bpg$cachePredicates(List<BlockPredicate> predicates) {
        this.bpg$predicates = predicates.toArray(new BlockPredicate[predicates.size()]);
    }

    @Override
    public final BlockPredicate[] bpg$getPredicates() {
        return this.bpg$predicates;
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

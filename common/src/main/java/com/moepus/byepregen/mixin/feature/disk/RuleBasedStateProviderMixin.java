package com.moepus.byepregen.mixin.feature.disk;

import com.moepus.byepregen.worldgen.feature.DiskBlockPredicateEvaluator;
import com.moepus.byepregen.worldgen.feature.FastDiskStateCursor;
import com.moepus.byepregen.worldgen.feature.FastRuleBasedBlockStateProvider;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RuleBasedStateProvider.class, remap = false)
public abstract class RuleBasedStateProviderMixin implements FastRuleBasedBlockStateProvider {
    @Unique
    private RuleBasedStateProvider.Rule[] byepregen$rules;

    @Shadow @Final @Nullable private BlockStateProvider fallback;

    @InjectLite(
            method = "<init>(Lnet/minecraft/world/level/levelgen/feature/stateproviders/BlockStateProvider;Ljava/util/List;)V",
            at = @At("TAIL"))
    private void byepregen$cacheRules(
        BlockStateProvider fallback,
        List<RuleBasedStateProvider.Rule> rules
    ) {
        this.byepregen$rules = rules.toArray(new RuleBasedStateProvider.Rule[rules.size()]);
    }

    @Override
    @Nullable
    public BlockState byepregen$getState(RandomSource random, BlockPos pos, FastDiskStateCursor cursor) {
        for (RuleBasedStateProvider.Rule rule : this.byepregen$rules) {
            if (DiskBlockPredicateEvaluator.test(rule.ifTrue(), cursor, pos)) {
                return rule.then().getState(cursor.level(), random, pos);
            }
        }
        return this.fallback == null ? null : this.fallback.getState(cursor.level(), random, pos);
    }
}

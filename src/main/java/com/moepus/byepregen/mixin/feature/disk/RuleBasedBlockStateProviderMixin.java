package com.moepus.byepregen.mixin.feature.disk;

import com.moepus.byepregen.Feature.DiskBlockPredicateEvaluator;
import com.moepus.byepregen.Feature.FastDiskStateCursor;
import com.moepus.byepregen.Feature.FastRuleBasedBlockStateProvider;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedBlockStateProvider;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RuleBasedBlockStateProvider.class, remap = false)
public abstract class RuleBasedBlockStateProviderMixin implements FastRuleBasedBlockStateProvider {
    @Unique
    private RuleBasedBlockStateProvider.Rule[] bpg$rules;

    @Shadow
    public abstract BlockStateProvider fallback();

    @InjectLite(method = "<init>", at = @At("TAIL"))
    private void bpg$cacheRules(
        BlockStateProvider fallback,
        List<RuleBasedBlockStateProvider.Rule> rules
    ) {
        this.bpg$rules = rules.toArray(new RuleBasedBlockStateProvider.Rule[rules.size()]);
    }

    @Override
    public BlockState bpg$getState(RandomSource random, BlockPos pos, FastDiskStateCursor cursor) {
        for (RuleBasedBlockStateProvider.Rule rule : this.bpg$rules) {
            if (DiskBlockPredicateEvaluator.test(rule.ifTrue(), cursor, pos)) {
                return rule.then().getState(random, pos);
            }
        }
        return this.fallback().getState(random, pos);
    }
}

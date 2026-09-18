package com.moepus.byepregen.mixin.feature.disk;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.DiskBlockPredicateEvaluator;
import com.moepus.byepregen.worldgen.feature.FastDiskStateCursor;
import com.moepus.byepregen.worldgen.feature.FastRuleBasedBlockStateProvider;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.jspecify.annotations.Nullable;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(RuleBasedStateProvider.class)
public abstract class RuleBasedStateProviderMixin implements FastRuleBasedBlockStateProvider {
    @Shadow @Final @Nullable private Holder<BlockStateProvider> fallback;
    @Shadow @Final private List<RuleBasedStateProvider.Rule> rules;

    @Override
    @Nullable
    public BlockState byepregen$getState(RandomSource random, BlockPos pos, FastDiskStateCursor cursor) {
        for (int i = 0; i < this.rules.size(); ++i) {
            RuleBasedStateProvider.Rule rule = this.rules.get(i);
            if (!DiskBlockPredicateEvaluator.test(rule.ifTrue(), cursor, pos)) continue;
            BlockState state = rule.then().value().getOptionalState(cursor.level(), random, pos);
            if (state != null) return state;
        }
        return this.fallback == null ? null : this.fallback.value().getOptionalState(cursor.level(), random, pos);
    }
}

package com.moepus.byepregen.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;

import java.util.List;

final class ArenaMaterialEvaluator {
    private final NoiseChunk.BlockStateFiller rootRule;
    private final NoiseChunk.BlockStateFiller[] rules;
    private final NoiseChunk.BlockStateFiller firstRule;
    private final NoiseChunk.BlockStateFiller secondRule;

    private ArenaMaterialEvaluator(
            NoiseChunk.BlockStateFiller rootRule,
            NoiseChunk.BlockStateFiller[] rules
    ) {
        this.rootRule = rootRule;
        this.rules = rules;
        this.firstRule = rules != null && rules.length > 0 ? rules[0] : null;
        this.secondRule = rules != null && rules.length > 1 ? rules[1] : null;
    }

    static ArenaMaterialEvaluator create(NoiseChunk.BlockStateFiller rootRule) {
        if (rootRule instanceof MaterialRuleList(List<NoiseChunk.BlockStateFiller> materialRuleList)) {
            NoiseChunk.BlockStateFiller[] snapshot = materialRuleList
                    .toArray(NoiseChunk.BlockStateFiller[]::new);
            return new ArenaMaterialEvaluator(null, snapshot);
        }
        return new ArenaMaterialEvaluator(rootRule, null);
    }

    BlockState calculate(NoiseChunk context) {
        if (this.rules == null) {
            return this.rootRule.calculate(context);
        }
        if (this.rules.length == 2) {
            BlockState state = this.firstRule.calculate(context);
            return state != null ? state : this.secondRule.calculate(context);
        }
        if (this.rules.length == 1) {
            return this.firstRule.calculate(context);
        }
        for (NoiseChunk.BlockStateFiller rule : this.rules) {
            BlockState state = rule.calculate(context);
            if (state != null) {
                return state;
            }
        }
        return null;
    }
}

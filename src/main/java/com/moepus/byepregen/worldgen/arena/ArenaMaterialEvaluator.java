package com.moepus.byepregen.worldgen.arena;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;

final class ArenaMaterialEvaluator {
    private final NoiseChunk.BlockStateFiller rootRule;
    private final MaterialRules materialRules;
    private final Aquifer aquifer;

    private ArenaMaterialEvaluator(
            NoiseChunk.BlockStateFiller rootRule,
            MaterialRules materialRules,
            Aquifer aquifer
    ) {
        this.rootRule = rootRule;
        this.materialRules = materialRules;
        this.aquifer = aquifer;
    }

    static ArenaMaterialEvaluator create(
            NoiseChunk.BlockStateFiller rootRule,
            Aquifer aquifer,
            NoiseChunk.BlockStateFiller aquiferRule
    ) {
        if (rootRule instanceof MaterialRuleList(List<NoiseChunk.BlockStateFiller> materialRuleList)) {
            NoiseChunk.BlockStateFiller[] snapshot = materialRuleList
                    .toArray(NoiseChunk.BlockStateFiller[]::new);
            boolean supportsColumnDensity = snapshot.length > 0 && snapshot[0] == aquiferRule;
            return new ArenaMaterialEvaluator(
                    null,
                    new MaterialRules(snapshot, supportsColumnDensity),
                    aquifer
            );
        }
        return new ArenaMaterialEvaluator(rootRule, null, aquifer);
    }

    boolean supportsColumnDensity() {
        return this.materialRules != null && this.materialRules.supportsColumnDensity();
    }

    BlockState calculateColumn(NoiseChunk context, double density) {
        if (!this.supportsColumnDensity()) {
            throw new IllegalStateException("Material rules do not expose the vanilla aquifer rule");
        }
        BlockState state = this.aquifer.computeSubstance(context, density);
        if (state != null) {
            return state;
        }
        NoiseChunk.BlockStateFiller[] rules = this.materialRules.values();
        for (int i = 1; i < rules.length; ++i) {
            state = rules[i].calculate(context);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    BlockState calculate(NoiseChunk context) {
        if (this.materialRules == null) {
            return this.rootRule.calculate(context);
        }
        for (NoiseChunk.BlockStateFiller rule : this.materialRules.values()) {
            BlockState state = rule.calculate(context);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    private record MaterialRules(
            NoiseChunk.BlockStateFiller[] values,
            boolean supportsColumnDensity
    ) {
    }
}

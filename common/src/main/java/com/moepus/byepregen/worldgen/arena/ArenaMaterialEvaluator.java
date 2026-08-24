package com.moepus.byepregen.worldgen.arena;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;

final class ArenaMaterialEvaluator {
    private final NoiseChunk.BlockStateFiller rootRule;
    private final MaterialRules materialRules;
    private final Aquifer aquifer;
    private final DensityFunction beardifier;

    private ArenaMaterialEvaluator(
            NoiseChunk.BlockStateFiller rootRule,
            MaterialRules materialRules,
            Aquifer aquifer,
            DensityFunction beardifier
    ) {
        this.rootRule = rootRule;
        this.materialRules = materialRules;
        this.aquifer = aquifer;
        this.beardifier = beardifier;
    }

    static ArenaMaterialEvaluator create(
            NoiseChunk.BlockStateFiller rootRule,
            Aquifer aquifer,
            NoiseChunk.BlockStateFiller aquiferRule,
            DensityFunction beardifier
    ) {
        if (rootRule instanceof MaterialRuleList(NoiseChunk.BlockStateFiller[] materialRuleList)) {
            NoiseChunk.BlockStateFiller[] snapshot = materialRuleList.clone();
            boolean supportsColumnDensity = snapshot.length > 0 && snapshot[0] == aquiferRule;
            return new ArenaMaterialEvaluator(
                    null,
                    new MaterialRules(snapshot, supportsColumnDensity),
                    aquifer,
                    beardifier
            );
        }
        return new ArenaMaterialEvaluator(rootRule, null, aquifer, beardifier);
    }

    boolean supportsColumnDensity() {
        return this.materialRules != null && this.materialRules.supportsColumnDensity();
    }

    BlockState calculateWithColumnDensity(DensityFunction.FunctionContext context, double density) {
        if (!this.supportsColumnDensity()) {
            throw new IllegalStateException("Material rules do not expose the vanilla aquifer rule");
        }
        double finalDensity = density + this.beardifier.compute(context);
        BlockState state = this.aquifer.computeSubstance(context, finalDensity);
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

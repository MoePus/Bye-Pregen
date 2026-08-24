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
    private ColumnContextProvider beardifierContexts;
    private double[] beardifierColumn;
    private int beardifierMinY;

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

    void configureColumnRange(int minY, int height) {
        this.beardifierMinY = minY;
        this.beardifierColumn = new double[height];
        this.beardifierContexts = new ColumnContextProvider();
    }

    void prepareColumn(int blockX, int blockZ) {
        this.beardifierContexts.prepare(blockX, blockZ, this.beardifierMinY);
        this.beardifier.fillArray(this.beardifierColumn, this.beardifierContexts);
    }

    BlockState calculateWithColumnDensity(DensityFunction.FunctionContext context, double density) {
        int beardifierIndex = context.blockY() - this.beardifierMinY;
        double finalDensity = density + this.beardifierColumn[beardifierIndex];
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

    private static final class ColumnContextProvider implements
            DensityFunction.ContextProvider, DensityFunction.FunctionContext {
        private int blockX;
        private int blockY;
        private int blockZ;
        private int minY;

        private void prepare(int blockX, int blockZ, int minY) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.minY = minY;
        }

        @Override
        public DensityFunction.FunctionContext forIndex(int index) {
            this.blockY = this.minY + index;
            return this;
        }

        @Override
        public void fillAllDirectly(double[] values, DensityFunction function) {
            for (int i = 0; i < values.length; ++i) {
                values[i] = function.compute(this.forIndex(i));
            }
        }

        @Override public int blockX() { return this.blockX; }
        @Override public int blockY() { return this.blockY; }
        @Override public int blockZ() { return this.blockZ; }
    }
}

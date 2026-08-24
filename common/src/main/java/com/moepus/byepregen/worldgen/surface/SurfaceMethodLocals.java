package com.moepus.byepregen.worldgen.surface;

import java.util.EnumMap;
import java.util.EnumSet;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class SurfaceMethodLocals {
    private static final int FIRST_LOCAL = 4;
    private static final int MIN_REUSE_COUNT = 2;

    private final EnumMap<Input, Integer> locals;
    private final int scratchLocal;

    private SurfaceMethodLocals(EnumMap<Input, Integer> locals) {
        this.locals = locals;
        this.scratchLocal = FIRST_LOCAL + locals.size();
    }

    static SurfaceMethodLocals create(
            SurfaceEmissionContext context,
            SurfaceRegionPlan.Body body
    ) {
        return create(context.layout(), context.regions(), body);
    }

    static SurfaceMethodLocals create(
            SurfaceScalarLayout layout,
            SurfaceRegionPlan regions,
            SurfaceRegionPlan.Body body
    ) {
        Usage usage = new Usage(layout, regions);
        usage.count(body);
        EnumMap<Input, Integer> locals = new EnumMap<>(Input.class);
        int next = FIRST_LOCAL;
        for (Input input : Input.values()) {
            if (usage.canCache(input)) {
                locals.put(input, next++);
            }
        }
        return new SurfaceMethodLocals(locals);
    }

    boolean caches(String accessor) {
        Input input = Input.fromAccessor(accessor);
        return input != null && this.locals.containsKey(input);
    }

    void emitPrelude(MethodVisitor method, SurfaceEmissionContext context) {
        for (Input input : Input.values()) {
            Integer local = this.locals.get(input);
            if (local == null) {
                continue;
            }
            context.loadContext(method);
            context.invokeContext(method, input.accessor(), "()I");
            method.visitVarInsn(Opcodes.ISTORE, local);
        }
    }

    void loadInt(
            MethodVisitor method,
            SurfaceEmissionContext context,
            String accessor
    ) {
        Input input = Input.fromAccessor(accessor);
        Integer local = input == null ? null : this.locals.get(input);
        if (local == null) {
            context.loadContextInt(method, accessor);
        } else {
            method.visitVarInsn(Opcodes.ILOAD, local);
        }
    }

    int scratchLocal() {
        return this.scratchLocal;
    }

    private enum Input {
        X(SurfaceRuntimeAbi.BLOCK_X),
        Y(SurfaceRuntimeAbi.BLOCK_Y),
        Z(SurfaceRuntimeAbi.BLOCK_Z),
        SURFACE_DEPTH(SurfaceRuntimeAbi.SURFACE_DEPTH),
        WATER(SurfaceRuntimeAbi.WATER_HEIGHT),
        STONE_ABOVE(SurfaceRuntimeAbi.STONE_ABOVE),
        STONE_BELOW(SurfaceRuntimeAbi.STONE_BELOW);

        private final String accessor;

        Input(String accessor) {
            this.accessor = accessor;
        }

        String accessor() {
            return this.accessor;
        }

        static Input fromAccessor(String accessor) {
            for (Input input : values()) {
                if (input.accessor.equals(accessor)) {
                    return input;
                }
            }
            return null;
        }
    }

    private static final class Usage {
        private final SurfaceScalarLayout layout;
        private final SurfaceRegionPlan regions;
        private final EnumMap<Input, Integer> counts = new EnumMap<>(Input.class);
        private final EnumSet<Input> readsAfterBarrier = EnumSet.noneOf(Input.class);
        private boolean barrierSeen;

        private Usage(SurfaceScalarLayout layout, SurfaceRegionPlan regions) {
            this.layout = layout;
            this.regions = regions;
        }

        private int count(Input input) {
            return this.counts.getOrDefault(input, 0);
        }

        private boolean canCache(Input input) {
            return this.count(input) >= MIN_REUSE_COUNT
                    && !this.readsAfterBarrier.contains(input);
        }

        private void count(SurfaceRegionPlan.Body body) {
            switch (body) {
                case SurfaceRegionPlan.RuleBody rule -> this.countRule(
                        rule.rule(), rule.rule(), null
                );
                case SurfaceRegionPlan.SequenceRange range -> this.countSequence(
                        range.sequence(),
                        null,
                        range.sequence(),
                        range.fromIndex(),
                        range.toIndex()
                );
            }
        }

        private void countRule(
                SurfaceRulePlan.Rule rule,
                SurfaceRulePlan.Rule regionRoot,
                SurfaceRulePlan.Sequence activeRange
        ) {
            if (rule != regionRoot && this.regions.outlinedMethod(rule) != null) {
                this.crossBarrierIfNeeded(rule);
                return;
            }
            if (rule instanceof SurfaceRulePlan.OpaqueRule) {
                this.barrierSeen = true;
            } else if (rule instanceof SurfaceRulePlan.Test test) {
                this.countCondition(test.condition());
                this.countRule(test.followup(), regionRoot, activeRange);
            } else if (rule instanceof SurfaceRulePlan.Sequence sequence) {
                this.countSequence(
                        sequence, regionRoot, activeRange, 0, sequence.rules().size()
                );
            }
        }

        private void countSequence(
                SurfaceRulePlan.Sequence sequence,
                SurfaceRulePlan.Rule regionRoot,
                SurfaceRulePlan.Sequence activeRange,
                int fromIndex,
                int toIndex
        ) {
            int index = fromIndex;
            while (index < toIndex) {
                SurfaceRegionPlan.SequenceRange range = activeRange == sequence
                        ? null
                        : this.regions.rangeStartingAt(sequence, index);
                if (range == null) {
                    this.countRule(sequence.rules().get(index), regionRoot, activeRange);
                    index++;
                } else {
                    this.crossBarrierIfNeeded(range);
                    index = range.toIndex();
                }
            }
        }

        private void countCondition(SurfaceRulePlan.Condition condition) {
            if (condition instanceof SurfaceRulePlan.NotCondition not) {
                this.countCondition(not.target());
                return;
            }
            SurfaceConditionSpec spec = SurfaceRulePlan.conditionValue(condition).spec();
            switch (spec) {
                case SurfaceConditionSpec.Noise ignored -> this.add(Input.X, Input.Z);
                case SurfaceConditionSpec.StoneDepth stone -> this.countStone(stone);
                case SurfaceConditionSpec.VerticalGradient ignored ->
                        this.add(Input.X, Input.Y, Input.Y, Input.Y, Input.Z);
                case SurfaceConditionSpec.Water water -> this.countWater(water);
                case SurfaceConditionSpec.YAbove yAbove ->
                        this.countYAbove(condition, yAbove);
                case SurfaceConditionSpec.Singleton singleton -> this.countSingleton(singleton);
                default -> {
                }
            }
            if (!(spec instanceof SurfaceConditionSpec.YAbove)
                    && this.containsBarrier(condition)) {
                this.barrierSeen = true;
            }
        }

        private void countStone(SurfaceConditionSpec.StoneDepth stone) {
            if (stone.hasFixedLimit() && stone.baseLimit() <= 0) {
                return;
            }
            if (stone.surfaceType() == CaveSurface.CEILING
                    && this.layout.plan().boundedStoneDepthBelow()) {
                return;
            }
            this.add(stone.surfaceType() == CaveSurface.CEILING
                    ? Input.STONE_BELOW
                    : Input.STONE_ABOVE);
            if (stone.addSurfaceDepth()) {
                this.add(Input.SURFACE_DEPTH);
            }
        }

        private void countWater(SurfaceConditionSpec.Water water) {
            this.add(Input.WATER, Input.WATER, Input.Y);
            if (water.addStoneDepth()) {
                this.add(Input.STONE_ABOVE);
            }
            if (water.surfaceDepthMultiplier() != 0) {
                this.add(Input.SURFACE_DEPTH);
            }
        }

        private void countYAbove(
                SurfaceRulePlan.Condition condition,
                SurfaceConditionSpec.YAbove yAbove
        ) {
            this.add(Input.Y);
            if (yAbove.addStoneDepth()) {
                this.add(Input.STONE_ABOVE);
            }
            SurfaceScalarLayout.ConditionLayout lowered = this.layout.condition(condition);
            if (!(lowered instanceof SurfaceScalarLayout.AbsoluteY)) {
                this.barrierSeen = true;
            }
            if (yAbove.surfaceDepthMultiplier() != 0) {
                this.add(Input.SURFACE_DEPTH);
            }
        }

        private void countSingleton(SurfaceConditionSpec.Singleton singleton) {
            switch (singleton) {
                case ABOVE_PRELIMINARY_SURFACE -> this.add(Input.Y);
                case HOLE -> this.add(Input.SURFACE_DEPTH);
                case STEEP, TEMPERATURE -> {
                }
            }
        }

        private void add(Input... inputs) {
            for (Input input : inputs) {
                this.counts.merge(input, 1, Integer::sum);
                if (this.barrierSeen) {
                    this.readsAfterBarrier.add(input);
                }
            }
        }

        private void crossBarrierIfNeeded(SurfaceRegionPlan.SequenceRange range) {
            if (range.sequence().rules()
                    .subList(range.fromIndex(), range.toIndex())
                    .stream()
                    .anyMatch(this::containsBarrier)) {
                this.barrierSeen = true;
            }
        }

        private void crossBarrierIfNeeded(SurfaceRulePlan.Rule rule) {
            if (this.containsBarrier(rule)) {
                this.barrierSeen = true;
            }
        }

        private boolean containsBarrier(SurfaceRulePlan.Rule rule) {
            if (rule instanceof SurfaceRulePlan.OpaqueRule) {
                return true;
            }
            if (rule instanceof SurfaceRulePlan.Sequence sequence) {
                return sequence.rules().stream().anyMatch(this::containsBarrier);
            }
            if (rule instanceof SurfaceRulePlan.Test test) {
                return this.containsBarrier(test.condition())
                        || this.containsBarrier(test.followup());
            }
            return false;
        }

        private boolean containsBarrier(SurfaceRulePlan.Condition condition) {
            if (condition instanceof SurfaceRulePlan.NotCondition not) {
                return this.containsBarrier(not.target());
            }
            SurfaceScalarLayout.ConditionLayout lowered = this.layout.condition(condition);
            return lowered instanceof SurfaceScalarLayout.Delegate
                    || lowered instanceof SurfaceScalarLayout.BoundY;
        }
    }
}

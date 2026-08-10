package com.moepus.byepregen.worldgen.surface;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

public final class SurfaceRulePlan {
    private final Rule root;
    private final List<ConditionValue> conditionValues;
    private final List<SurfaceConditionPlan> conditionPlans;
    private final SurfaceControlFlowPlan controlFlow;
    private final int entryCount;
    private final int occurrenceCount;
    private final int cacheGroupCount;

    SurfaceRulePlan(
            Rule root,
            List<ConditionValue> conditionValues,
            int entryCount,
            int occurrenceCount,
            int cacheGroupCount
    ) {
        this.root = Objects.requireNonNull(root, "root");
        this.conditionValues = List.copyOf(conditionValues);
        this.conditionPlans = this.conditionValues.stream()
                .map(SurfaceConditionPlan::create)
                .toList();
        int ruleEntryCount = requireNonNegative(entryCount, "entryCount");
        this.controlFlow = SurfaceControlFlowPlan.build(this.root, ruleEntryCount);
        this.entryCount = this.controlFlow.entryCount();
        this.occurrenceCount = requireNonNegative(occurrenceCount, "occurrenceCount");
        this.cacheGroupCount = requireNonNegative(cacheGroupCount, "cacheGroupCount");
        this.validateValues();
    }

    public Rule root() {
        return this.root;
    }

    public List<ConditionValue> conditionValues() {
        return this.conditionValues;
    }

    public SurfaceConditionPlan conditionPlan(ValueId valueId) {
        Objects.requireNonNull(valueId, "valueId");
        if (valueId.value() >= this.conditionPlans.size()) {
            throw new IllegalArgumentException("Unknown condition value " + valueId.value());
        }
        return this.conditionPlans.get(valueId.value());
    }

    public List<SurfaceConditionPlan> conditionPlans() {
        return this.conditionPlans;
    }

    public int entryCount() {
        return this.entryCount;
    }

    public SurfaceControlFlowPlan controlFlow() {
        return this.controlFlow;
    }

    public int occurrenceCount() {
        return this.occurrenceCount;
    }

    public int cacheGroupCount() {
        return this.cacheGroupCount;
    }

    private void validateValues() {
        for (int index = 0; index < this.conditionValues.size(); index++) {
            ConditionValue value = this.conditionValues.get(index);
            if (value.id().value() != index) {
                throw new IllegalArgumentException("Condition values are not densely ordered");
            }
        }
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " is negative: " + value);
        }
        return value;
    }

    public record EntryId(int value) {
        public EntryId {
            requireNonNegative(value, "entry id");
        }
    }

    public record OccurrenceId(int value) {
        public OccurrenceId {
            requireNonNegative(value, "occurrence id");
        }
    }

    public record ValueId(int value) {
        public ValueId {
            requireNonNegative(value, "value id");
        }
    }

    public record CacheGroupId(int value) {
        public CacheGroupId {
            requireNonNegative(value, "cache group id");
        }
    }

    public record BindingSlotId(int value) {
        public BindingSlotId {
            requireNonNegative(value, "binding slot id");
        }
    }

    public record RuleMetadata(
            EntryId entryId,
            OccurrenceId occurrenceId,
            Object source,
            SurfaceRuleSemantics.Semantics semantics,
            SurfaceRuleSemantics.BindingEffect bindingEffect
    ) {
        public RuleMetadata {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(occurrenceId, "occurrenceId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(semantics, "semantics");
            Objects.requireNonNull(bindingEffect, "bindingEffect");
        }
    }

    public record ConditionMetadata(
            OccurrenceId occurrenceId,
            Object source,
            SurfaceRuleSemantics.BindingEffect bindingEffect,
            CacheGroupId cacheGroupId
    ) {
        public ConditionMetadata {
            Objects.requireNonNull(occurrenceId, "occurrenceId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(bindingEffect, "bindingEffect");
            Objects.requireNonNull(cacheGroupId, "cacheGroupId");
        }
    }

    public record ConditionValue(
            ValueId id,
            SurfaceConditionSpec spec,
            SurfaceRuleSemantics.Semantics semantics
    ) {
        public ConditionValue {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(semantics, "semantics");
        }
    }

    public sealed interface Rule permits State, Sequence, Test, Bandlands, OpaqueRule {
        RuleMetadata metadata();
    }

    public record State(RuleMetadata metadata, BlockState state) implements Rule {
        public State {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(state, "state");
        }
    }

    public record Sequence(RuleMetadata metadata, List<Rule> rules) implements Rule {
        public Sequence {
            Objects.requireNonNull(metadata, "metadata");
            rules = List.copyOf(rules);
        }
    }

    public record Test(
            RuleMetadata metadata,
            Condition condition,
            Rule followup
    ) implements Rule {
        public Test {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(followup, "followup");
        }
    }

    public record Bandlands(RuleMetadata metadata) implements Rule {
        public Bandlands {
            Objects.requireNonNull(metadata, "metadata");
        }
    }

    public record OpaqueRule(RuleMetadata metadata) implements Rule {
        public OpaqueRule {
            Objects.requireNonNull(metadata, "metadata");
        }
    }

    public sealed interface Condition permits KnownCondition, NotCondition, OpaqueCondition {
        ConditionMetadata metadata();

        ConditionValue value();
    }

    public record KnownCondition(
            ConditionMetadata metadata,
            ConditionValue value
    ) implements Condition {
        public KnownCondition {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(value, "value");
        }
    }

    public record NotCondition(
            ConditionMetadata metadata,
            ConditionValue value,
            Condition target
    ) implements Condition {
        public NotCondition {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(target, "target");
        }
    }

    public record OpaqueCondition(
            ConditionMetadata metadata,
            ConditionValue value
    ) implements Condition {
        public OpaqueCondition {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(value, "value");
        }
    }
}

package com.moepus.byepregen.worldgen.surface;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

public final class SurfaceRulePlan {
    private final Rule root;

    SurfaceRulePlan(Rule root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public Rule root() {
        return this.root;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " is negative: " + value);
        }
        return value;
    }

    public record ValueId(int value) {
        public ValueId {
            requireNonNegative(value, "value id");
        }
    }

    public record BindingSlotId(int value) {
        public BindingSlotId {
            requireNonNegative(value, "binding slot id");
        }
    }

    public record ConditionValue(ValueId id, SurfaceConditionSpec spec) {
        public ConditionValue {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(spec, "spec");
        }
    }

    static ConditionValue conditionValue(Condition condition) {
        return switch (condition) {
            case KnownCondition known -> known.value();
            case OpaqueCondition opaque -> opaque.value();
            case NotCondition ignored -> throw new IllegalArgumentException(
                    "Negated condition has no independent value"
            );
        };
    }

    public sealed interface Rule permits State, Sequence, Test, Bandlands, OpaqueRule {
    }

    public record State(BlockState state) implements Rule {
        public State {
            Objects.requireNonNull(state, "state");
        }
    }

    public record Sequence(List<Rule> rules) implements Rule {
        public Sequence {
            rules = List.copyOf(rules);
        }
    }

    public record Test(Condition condition, Rule followup) implements Rule {
        public Test {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(followup, "followup");
        }
    }

    public record Bandlands() implements Rule {
    }

    public record OpaqueRule(Object source) implements Rule {
        public OpaqueRule {
            Objects.requireNonNull(source, "source");
        }
    }

    public sealed interface Condition permits KnownCondition, NotCondition, OpaqueCondition {
    }

    public record KnownCondition(Object source, ConditionValue value) implements Condition {
        public KnownCondition {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(value, "value");
        }
    }

    public record NotCondition(Condition target) implements Condition {
        public NotCondition {
            Objects.requireNonNull(target, "target");
        }
    }

    public record OpaqueCondition(Object source, ConditionValue value) implements Condition {
        public OpaqueCondition {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(value, "value");
        }
    }
}

package com.moepus.byepregen.worldgen.surface;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

public final class SurfaceRulePlan {
    // Deeper checks can cost more reads per Y than vanilla's amortized run scan.
    private static final int MAX_LAZY_STONE_DEPTH = 1;

    private final Rule root;
    private final boolean boundedStoneDepthBelow;

    SurfaceRulePlan(Rule root) {
        this.root = Objects.requireNonNull(root, "root");
        this.boundedStoneDepthBelow = canBoundStoneDepthBelow(root);
    }

    public Rule root() {
        return this.root;
    }

    public boolean boundedStoneDepthBelow() {
        return this.boundedStoneDepthBelow;
    }

    private static boolean canBoundStoneDepthBelow(Rule rule) {
        return switch (rule) {
            case State ignored -> true;
            case Bandlands ignored -> true;
            case OpaqueRule ignored -> false;
            case Sequence sequence -> sequence.rules().stream()
                    .allMatch(SurfaceRulePlan::canBoundStoneDepthBelow);
            case Test test -> canBoundStoneDepthBelow(test.condition())
                    && canBoundStoneDepthBelow(test.followup());
        };
    }

    private static boolean canBoundStoneDepthBelow(Condition condition) {
        return switch (condition) {
            case NotCondition not -> canBoundStoneDepthBelow(not.target());
            case OpaqueCondition opaque ->
                    opaque.source() instanceof SurfaceRuleSourceAccess.BiomeCondition;
            case KnownCondition known -> canBoundStoneDepthBelow(known.value().spec());
        };
    }

    private static boolean canBoundStoneDepthBelow(SurfaceConditionSpec spec) {
        if (!(spec instanceof SurfaceConditionSpec.StoneDepth stone)
                || stone.surfaceType() != CaveSurface.CEILING) {
            return true;
        }
        if (!stone.hasFixedLimit()) {
            return false;
        }
        return stone.baseLimit() <= MAX_LAZY_STONE_DEPTH;
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

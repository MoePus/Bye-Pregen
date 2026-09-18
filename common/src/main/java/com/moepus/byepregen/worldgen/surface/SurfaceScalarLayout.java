package com.moepus.byepregen.worldgen.surface;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.VerticalAnchor;

final class SurfaceScalarLayout {
    private final SurfaceRulePlan plan;
    private final SurfaceBindingLayout bindings;
    private final IdentityHashMap<SurfaceRulePlan.Rule, SurfaceRulePlan.BindingSlotId> ruleSlots;
    private final IdentityHashMap<SurfaceRulePlan.Condition, ConditionLayout> conditions;
    private final int noiseConditions;
    private final Map<SurfaceConditionSpec.Noise, SurfaceRulePlan.BindingSlotId> noiseSlots;

    private SurfaceScalarLayout(SurfaceRulePlan plan, Builder builder) {
        this.plan = plan;
        this.bindings = builder.bindings.build();
        this.ruleSlots = builder.ruleSlots;
        this.conditions = builder.conditions;
        this.noiseConditions = builder.noiseConditions;
        this.noiseSlots = builder.noiseSlots;
    }

    static SurfaceScalarLayout lower(SurfaceRulePlan plan) throws SurfaceCompileException {
        Builder builder = new Builder();
        builder.lowerRule(plan.root());
        return new SurfaceScalarLayout(plan, builder);
    }

    SurfaceRulePlan plan() {
        return this.plan;
    }

    SurfaceBindingLayout bindings() {
        return this.bindings;
    }

    SurfaceRulePlan.BindingSlotId ruleSlot(SurfaceRulePlan.Rule rule) {
        return this.ruleSlots.get(rule);
    }

    ConditionLayout condition(SurfaceRulePlan.Condition condition) {
        ConditionLayout layout = this.conditions.get(condition);
        if (layout == null) {
            throw new IllegalArgumentException("Condition was not lowered: " + condition);
        }
        return layout;
    }

    int noiseOccurrences() {
        return this.noiseConditions;
    }

    int noiseSamples() {
        return this.noiseSlots.size();
    }

    sealed interface ConditionLayout
            permits Inline, Delegate, Noise, Gradient, AbsoluteY, BoundY {
    }

    enum Inline implements ConditionLayout {
        INSTANCE
    }

    record Delegate(SurfaceRulePlan.BindingSlotId slot) implements ConditionLayout {
    }

    /**
     * 26.3: the noise condition reads its already-cached {@code DoubleSupplier} through a bound
     * field, so there is no sample bank, no range mask and no column epoch to keep.
     */
    record Noise(SurfaceRulePlan.BindingSlotId supplier) implements ConditionLayout {
    }

    record Gradient(
            SurfaceRulePlan.BindingSlotId lower,
            SurfaceRulePlan.BindingSlotId upper,
            SurfaceRulePlan.BindingSlotId random
    ) implements ConditionLayout {
    }

    record AbsoluteY(int y) implements ConditionLayout {
    }

    record BoundY(SurfaceRulePlan.BindingSlotId anchor) implements ConditionLayout {
    }

    private static final class Builder {
        private final SurfaceBindingLayout.Builder bindings = new SurfaceBindingLayout.Builder();
        private final IdentityHashMap<SurfaceRulePlan.Rule, SurfaceRulePlan.BindingSlotId> ruleSlots =
                new IdentityHashMap<>();
        private final IdentityHashMap<Object, SurfaceRulePlan.BindingSlotId> stateSlots =
                new IdentityHashMap<>();
        private final IdentityHashMap<SurfaceRulePlan.Condition, ConditionLayout> conditions =
                new IdentityHashMap<>();
        private final Map<SurfaceConditionSpec.Noise, SurfaceRulePlan.BindingSlotId> noiseSlots =
                new LinkedHashMap<>();
        private int noiseConditions;

        private void lowerRule(SurfaceRulePlan.Rule rule) throws SurfaceCompileException {
            switch (rule) {
                case SurfaceRulePlan.State state -> this.lowerState(state);
                case SurfaceRulePlan.Sequence sequence -> {
                    for (SurfaceRulePlan.Rule child : sequence.rules()) {
                        this.lowerRule(child);
                    }
                }
                case SurfaceRulePlan.Test test -> {
                    this.lowerCondition(test.condition());
                    this.lowerRule(test.followup());
                }
                case SurfaceRulePlan.OpaqueRule opaque -> this.ruleSlots.put(
                        opaque,
                        this.bindings.add(SurfaceBindingLayout.Kind.RULE, opaque.source())
                );
                case SurfaceRulePlan.Bandlands ignored -> {
                }
            }
        }

        private void lowerState(SurfaceRulePlan.State state) {
            SurfaceRulePlan.BindingSlotId slot = this.stateSlots.computeIfAbsent(
                    state.state(),
                    ignored -> this.bindings.add(SurfaceBindingLayout.Kind.STATE, state.state())
            );
            this.ruleSlots.put(state, slot);
        }

        private void lowerCondition(SurfaceRulePlan.Condition condition)
                throws SurfaceCompileException {
            if (condition instanceof SurfaceRulePlan.NotCondition not) {
                this.lowerCondition(not.target());
                return;
            }
            if (condition instanceof SurfaceRulePlan.OpaqueCondition opaque) {
                this.conditions.put(opaque, this.lowerDelegate(opaque.source()));
                return;
            }
            this.lowerKnown((SurfaceRulePlan.KnownCondition) condition);
        }

        private void lowerKnown(SurfaceRulePlan.KnownCondition condition)
                throws SurfaceCompileException {
            SurfaceConditionSpec spec = condition.value().spec();
            ConditionLayout layout = switch (spec) {
                case SurfaceConditionSpec.Noise noise -> this.lowerNoise(noise);
                case SurfaceConditionSpec.StoneDepth ignored -> Inline.INSTANCE;
                case SurfaceConditionSpec.VerticalGradient gradient -> this.lowerGradient(gradient);
                case SurfaceConditionSpec.Water ignored -> Inline.INSTANCE;
                case SurfaceConditionSpec.YAbove yAbove -> this.lowerY(yAbove);
                case SurfaceConditionSpec.Singleton singleton -> switch (singleton) {
                    case STEEP, TEMPERATURE -> this.lowerDelegate(condition.source());
                    case ABOVE_PRELIMINARY_SURFACE, HOLE -> Inline.INSTANCE;
                };
                case SurfaceConditionSpec.Opaque ignored -> throw unexpectedSpec(spec);
            };
            this.conditions.put(condition, layout);
        }

        private Noise lowerNoise(SurfaceConditionSpec.Noise noise) {
            this.noiseConditions++;
            SurfaceRulePlan.BindingSlotId slot = this.noiseSlots.get(noise);
            if (slot == null) {
                slot = this.bindings.add(SurfaceBindingLayout.Kind.NOISE, noise);
                this.noiseSlots.put(noise, slot);
            }
            return new Noise(slot);
        }

        private Gradient lowerGradient(SurfaceConditionSpec.VerticalGradient gradient) {
            SurfaceRulePlan.BindingSlotId lower = this.bindings.add(
                    SurfaceBindingLayout.Kind.RESOLVED_ANCHOR, gradient.trueAtAndBelow()
            );
            SurfaceRulePlan.BindingSlotId upper = this.bindings.add(
                    SurfaceBindingLayout.Kind.RESOLVED_ANCHOR, gradient.falseAtAndAbove()
            );
            SurfaceRulePlan.BindingSlotId random = this.bindings.add(
                    SurfaceBindingLayout.Kind.RANDOM_FACTORY, gradient.randomName()
            );
            return new Gradient(lower, upper, random);
        }

        private ConditionLayout lowerY(SurfaceConditionSpec.YAbove yAbove) {
            if (yAbove.anchor() instanceof VerticalAnchor.Absolute absolute) {
                return new AbsoluteY(absolute.y());
            }
            // 26.3: the anchor is resolved while binding (the context resolves it for us), so the
            // generated rule only loads the resulting int.
            return new BoundY(this.bindings.add(
                    SurfaceBindingLayout.Kind.RESOLVED_ANCHOR, yAbove.anchor()
            ));
        }

        private Delegate lowerDelegate(Object source) {
            return new Delegate(this.bindings.add(SurfaceBindingLayout.Kind.CONDITION, source));
        }

        private static SurfaceCompileException unexpectedSpec(SurfaceConditionSpec spec) {
            return new SurfaceCompileException("Unexpected known condition: " + spec);
        }
    }
}

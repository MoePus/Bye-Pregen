package com.moepus.byepregen.worldgen.surface;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

final class SurfaceScalarLayout {
    private final SurfaceRulePlan plan;
    private final SurfaceBindingLayout bindings;
    private final IdentityHashMap<SurfaceRulePlan.Rule, SurfaceRulePlan.BindingSlotId> ruleSlots;
    private final IdentityHashMap<SurfaceRulePlan.Condition, ConditionLayout> conditions;
    private final List<NoiseSample> noiseSamples;
    private final int noisePredicates;

    private SurfaceScalarLayout(SurfaceRulePlan plan, Builder builder) {
        this.plan = plan;
        this.bindings = builder.bindings.build();
        this.ruleSlots = builder.ruleSlots;
        this.conditions = builder.conditions;
        this.noiseSamples = builder.noiseSamples.stream()
                .map(MutableNoiseSample::freeze)
                .toList();
        this.noisePredicates = builder.nextNoisePredicate;
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

    List<NoiseSample> noiseSamples() {
        return this.noiseSamples;
    }

    int noiseOccurrences() {
        return this.noisePredicates;
    }

    int noiseValueBanks() {
        return bankCount(this.noisePredicates);
    }

    int noiseSampleBanks() {
        return bankCount(this.noiseSamples.size());
    }

    sealed interface ConditionLayout
            permits Inline, Delegate, NoiseCondition, Gradient, AbsoluteY, BoundY {
    }

    enum Inline implements ConditionLayout {
        INSTANCE
    }

    record Delegate(SurfaceRulePlan.BindingSlotId slot) implements ConditionLayout {
    }

    record NoiseCondition(
            int sampleIndex,
            int predicateIndex
    ) implements ConditionLayout {
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

    record NoiseSample(
            int index,
            SurfaceRulePlan.BindingSlotId noiseSlot,
            List<NoiseRange> ranges
    ) {
        NoiseSample {
            ranges = List.copyOf(ranges);
        }

        long mask() {
            return 1L << (this.index & (Long.SIZE - 1));
        }
    }

    record NoiseRange(int predicateIndex, double minimum, double maximum) {
        long mask() {
            return 1L << (this.predicateIndex & (Long.SIZE - 1));
        }
    }

    private static int bankCount(int values) {
        return (values + Long.SIZE - 1) / Long.SIZE;
    }

    private static final class Builder {
        private final SurfaceBindingLayout.Builder bindings = new SurfaceBindingLayout.Builder();
        private final IdentityHashMap<SurfaceRulePlan.Rule, SurfaceRulePlan.BindingSlotId> ruleSlots =
                new IdentityHashMap<>();
        private final IdentityHashMap<Object, SurfaceRulePlan.BindingSlotId> stateSlots =
                new IdentityHashMap<>();
        private final IdentityHashMap<SurfaceRulePlan.Condition, ConditionLayout> conditions =
                new IdentityHashMap<>();
        private final Map<ResourceKey<NormalNoise.NoiseParameters>, MutableNoiseSample> samples =
                new LinkedHashMap<>();
        private final List<MutableNoiseSample> noiseSamples = new ArrayList<>();
        private final Map<SurfaceRulePlan.ValueId, Integer> noisePredicates = new LinkedHashMap<>();
        private int nextNoisePredicate;

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
                case SurfaceConditionSpec.Noise noise -> this.lowerNoise(condition, noise);
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

        private NoiseCondition lowerNoise(
                SurfaceRulePlan.KnownCondition condition,
                SurfaceConditionSpec.Noise noise
        ) {
            MutableNoiseSample sample = this.samples.get(noise.noise());
            if (sample == null) {
                SurfaceRulePlan.BindingSlotId slot = this.bindings.add(
                        SurfaceBindingLayout.Kind.NOISE, noise.noise()
                );
                sample = new MutableNoiseSample(this.noiseSamples.size(), slot);
                this.samples.put(noise.noise(), sample);
                this.noiseSamples.add(sample);
            } else {
                this.bindings.addDiscarded(SurfaceBindingLayout.Kind.NOISE, noise.noise());
            }
            Integer existing = this.noisePredicates.get(condition.value().id());
            int predicate = existing == null
                    ? this.addNoisePredicate(noise, sample)
                    : existing;
            if (existing == null) {
                this.noisePredicates.put(condition.value().id(), predicate);
            }
            return new NoiseCondition(sample.index, predicate);
        }

        private int addNoisePredicate(
                SurfaceConditionSpec.Noise noise,
                MutableNoiseSample sample
        ) {
            int predicate = this.nextNoisePredicate++;
            sample.ranges.add(new NoiseRange(predicate, noise.minimum(), noise.maximum()));
            return predicate;
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
            return new BoundY(this.bindings.add(
                    SurfaceBindingLayout.Kind.Y_ANCHOR, yAbove.anchor()
            ));
        }

        private Delegate lowerDelegate(Object source) {
            return new Delegate(this.bindings.add(SurfaceBindingLayout.Kind.CONDITION, source));
        }

        private static SurfaceCompileException unexpectedSpec(SurfaceConditionSpec spec) {
            return new SurfaceCompileException("Unexpected known condition: " + spec);
        }
    }

    private static final class MutableNoiseSample {
        private final int index;
        private final SurfaceRulePlan.BindingSlotId noiseSlot;
        private final List<NoiseRange> ranges = new ArrayList<>();

        private MutableNoiseSample(int index, SurfaceRulePlan.BindingSlotId noiseSlot) {
            this.index = index;
            this.noiseSlot = noiseSlot;
        }

        private NoiseSample freeze() {
            return new NoiseSample(this.index, this.noiseSlot, this.ranges);
        }
    }
}

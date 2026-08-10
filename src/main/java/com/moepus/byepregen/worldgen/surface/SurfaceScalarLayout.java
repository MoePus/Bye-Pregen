package com.moepus.byepregen.worldgen.surface;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

final class SurfaceScalarLayout {
    static final int MAX_BIOME_VALUES = Long.SIZE - 1;

    private final SurfaceRulePlan plan;
    private final SurfaceBindingLayout bindings;
    private final IdentityHashMap<SurfaceRulePlan.Rule, SurfaceRulePlan.BindingSlotId> ruleSlots;
    private final IdentityHashMap<SurfaceRulePlan.Condition, ConditionLayout> conditions;
    private final List<BiomeValue> biomeValues;
    private final int biomeFallbacks;
    private final SurfaceRulePlan.BindingSlotId biomeTableSlot;
    private final List<NoiseSample> noiseSamples;
    private final int noisePredicates;

    private SurfaceScalarLayout(Builder builder) {
        this.plan = builder.plan;
        this.bindings = builder.bindings.build();
        this.ruleSlots = builder.ruleSlots;
        this.conditions = builder.conditions;
        this.biomeValues = List.copyOf(builder.biomeValues.values());
        this.biomeFallbacks = builder.biomeFallbackSources.size();
        this.biomeTableSlot = builder.biomeTableSlot;
        this.noiseSamples = builder.noiseSamples.stream()
                .map(MutableNoiseSample::freeze)
                .toList();
        this.noisePredicates = builder.nextNoisePredicate;
    }

    static SurfaceScalarLayout lower(SurfaceRulePlan plan) throws SurfaceCompileException {
        Builder builder = new Builder(plan);
        builder.lowerRule(plan.root());
        if (builder.biomeValues.size() > MAX_BIOME_VALUES) {
            throw new SurfaceCompileException(
                    "Too many canonical biome predicates: " + builder.biomeValues.size()
            );
        }
        builder.finishBiomeTable();
        return new SurfaceScalarLayout(builder);
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
        return this.conditions.get(condition);
    }

    List<BiomeValue> biomeValues() {
        return this.biomeValues;
    }

    SurfaceRulePlan.BindingSlotId biomeTableSlot() {
        return this.biomeTableSlot;
    }

    int biomeFallbacks() {
        return this.biomeFallbacks;
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

    record ConditionLayout(
            SurfaceRulePlan.BindingSlotId primaryBinding,
            SurfaceRulePlan.BindingSlotId secondaryBinding,
            SurfaceRulePlan.BindingSlotId tertiaryBinding,
            int sampleIndex,
            int cacheIndex,
            int behaviorBit,
            boolean resolvedAnchor
    ) {
        static ConditionLayout empty() {
            return new ConditionLayout(null, null, null, -1, -1, -1, false);
        }
    }

    record BiomeValue(int bitIndex, java.util.Set<ResourceKey<Biome>> biomes) {
        long mask() {
            return 1L << this.bitIndex;
        }
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
        private final SurfaceRulePlan plan;
        private final SurfaceBindingLayout.Builder bindings = new SurfaceBindingLayout.Builder();
        private final IdentityHashMap<SurfaceRulePlan.Rule, SurfaceRulePlan.BindingSlotId> ruleSlots =
                new IdentityHashMap<>();
        private final IdentityHashMap<Object, SurfaceRulePlan.BindingSlotId> stateSlots =
                new IdentityHashMap<>();
        private final IdentityHashMap<SurfaceRulePlan.Condition, ConditionLayout> conditions =
                new IdentityHashMap<>();
        private final Map<SurfaceRulePlan.ValueId, BiomeValue> biomeValues = new LinkedHashMap<>();
        private final List<Object> biomeFallbackSources = new ArrayList<>();
        private final Map<ResourceKey<NormalNoise.NoiseParameters>, MutableNoiseSample> samples =
                new LinkedHashMap<>();
        private final List<MutableNoiseSample> noiseSamples = new ArrayList<>();
        private final Map<SurfaceRulePlan.ValueId, Integer> noisePredicates = new LinkedHashMap<>();
        private SurfaceRulePlan.BindingSlotId biomeTableSlot;
        private int nextNoisePredicate;

        private Builder(SurfaceRulePlan plan) {
            this.plan = plan;
        }

        private void lowerRule(SurfaceRulePlan.Rule rule) throws SurfaceCompileException {
            if (rule instanceof SurfaceRulePlan.State state) {
                SurfaceRulePlan.BindingSlotId slot = this.stateSlots.computeIfAbsent(
                        state.state(),
                        ignored -> this.bindings.add(
                                SurfaceBindingLayout.Kind.STATE, state.state()
                        )
                );
                this.ruleSlots.put(rule, slot);
                return;
            }
            if (rule instanceof SurfaceRulePlan.Sequence sequence) {
                for (SurfaceRulePlan.Rule child : sequence.rules()) {
                    this.lowerRule(child);
                }
                return;
            }
            if (rule instanceof SurfaceRulePlan.Test test) {
                this.lowerCondition(test.condition());
                this.lowerRule(test.followup());
                return;
            }
            if (rule instanceof SurfaceRulePlan.OpaqueRule opaque) {
                this.ruleSlots.put(rule, this.bindings.add(
                        SurfaceBindingLayout.Kind.RULE, opaque.metadata().source()
                ));
            }
        }

        private void lowerCondition(SurfaceRulePlan.Condition condition)
                throws SurfaceCompileException {
            if (condition instanceof SurfaceRulePlan.NotCondition not) {
                this.lowerCondition(not.target());
                this.conditions.put(condition, ConditionLayout.empty());
                return;
            }
            if (condition instanceof SurfaceRulePlan.OpaqueCondition opaque) {
                this.conditions.put(condition, new ConditionLayout(
                        this.bindings.add(
                                SurfaceBindingLayout.Kind.CONDITION,
                                opaque.metadata().source()
                        ), null, null, -1, -1, -1, false
                ));
                return;
            }
            this.lowerKnown((SurfaceRulePlan.KnownCondition) condition);
        }

        private void lowerKnown(SurfaceRulePlan.KnownCondition condition)
                throws SurfaceCompileException {
            SurfaceConditionSpec spec = condition.value().spec();
            SurfaceConditionPlan plan = this.plan.conditionPlan(condition.value().id());
            ConditionLayout layout = switch (plan.bindingRecipe()) {
                case BIOME_BEHAVIOR -> this.lowerBiome(condition);
                case NOISE -> this.lowerNoise(
                        condition, (SurfaceConditionSpec.Noise) spec
                );
                case GRADIENT -> this.lowerGradient(
                        (SurfaceConditionSpec.VerticalGradient) spec
                );
                case Y_ANCHOR -> this.lowerY((SurfaceConditionSpec.YAbove) spec);
                case CONDITION_DELEGATE -> this.lowerDelegate(condition);
                case NONE -> ConditionLayout.empty();
                case OPAQUE_CONDITION -> throw new SurfaceCompileException(
                        "Unexpected known condition: " + spec
                );
            };
            this.conditions.put(condition, layout);
        }

        private ConditionLayout lowerBiome(SurfaceRulePlan.KnownCondition condition)
                throws SurfaceCompileException {
            int fallbackIndex = this.biomeFallbackSources.size();
            this.biomeFallbackSources.add(condition.metadata().source());
            SurfaceRulePlan.ValueId valueId = condition.value().id();
            BiomeValue value = this.biomeValues.get(valueId);
            if (value == null) {
                SurfaceConditionSpec.Biome biome = (SurfaceConditionSpec.Biome) condition.value().spec();
                value = new BiomeValue(this.biomeValues.size(), biome.biomes());
                this.biomeValues.put(valueId, value);
            }
            return new ConditionLayout(
                    null, null, null, -1, fallbackIndex, value.bitIndex(), false
            );
        }

        private ConditionLayout lowerNoise(
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
            Integer existingPredicate = this.noisePredicates.get(condition.value().id());
            int predicate = existingPredicate == null
                    ? this.addNoisePredicate(condition, noise, sample)
                    : existingPredicate;
            return new ConditionLayout(
                    sample.noiseSlot, null, null, sample.index, predicate, -1, false
            );
        }

        private int addNoisePredicate(
                SurfaceRulePlan.KnownCondition condition,
                SurfaceConditionSpec.Noise noise,
                MutableNoiseSample sample
        ) {
            int predicate = this.nextNoisePredicate++;
            this.noisePredicates.put(condition.value().id(), predicate);
            sample.ranges.add(new NoiseRange(predicate, noise.minimum(), noise.maximum()));
            return predicate;
        }

        private ConditionLayout lowerGradient(SurfaceConditionSpec.VerticalGradient gradient) {
            SurfaceRulePlan.BindingSlotId lower = this.bindings.add(
                    SurfaceBindingLayout.Kind.RESOLVED_ANCHOR, gradient.trueAtAndBelow()
            );
            SurfaceRulePlan.BindingSlotId upper = this.bindings.add(
                    SurfaceBindingLayout.Kind.RESOLVED_ANCHOR, gradient.falseAtAndAbove()
            );
            SurfaceRulePlan.BindingSlotId random = this.bindings.add(
                    SurfaceBindingLayout.Kind.RANDOM_FACTORY, gradient.randomName()
            );
            return new ConditionLayout(lower, upper, random, -1, -1, -1, true);
        }

        private ConditionLayout lowerY(SurfaceConditionSpec.YAbove yAbove) {
            if (yAbove.anchor() instanceof VerticalAnchor.Absolute absolute) {
                return new ConditionLayout(
                        null, null, null, -1, absolute.y(), -1, true
                );
            }
            return new ConditionLayout(
                    this.bindings.add(
                            SurfaceBindingLayout.Kind.Y_ANCHOR, yAbove.anchor()
                    ),
                    null, null, -1, -1, -1, false
            );
        }

        private ConditionLayout lowerDelegate(SurfaceRulePlan.KnownCondition condition) {
            SurfaceRulePlan.BindingSlotId slot = this.bindings.add(
                    SurfaceBindingLayout.Kind.CONDITION,
                    condition.metadata().source()
            );
            return new ConditionLayout(slot, null, null, -1, -1, -1, false);
        }

        private void finishBiomeTable() {
            if (this.biomeValues.isEmpty()) {
                return;
            }
            this.biomeTableSlot = this.bindings.add(
                    SurfaceBindingLayout.Kind.BIOME_TABLE,
                    new SurfaceBiomeBehaviorTable(
                            List.copyOf(this.biomeValues.values()),
                            this.biomeFallbackSources
                    )
            );
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

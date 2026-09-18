package com.moepus.byepregen.worldgen.surface;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.condition.AbovePreliminarySurfaceCondition;
import net.minecraft.world.level.levelgen.material.condition.HoleCondition;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.NoiseThresholdCondition;
import net.minecraft.world.level.levelgen.material.condition.NotCondition;
import net.minecraft.world.level.levelgen.material.condition.SteepCondition;
import net.minecraft.world.level.levelgen.material.condition.StoneDepthCondition;
import net.minecraft.world.level.levelgen.material.condition.TemperatureCondition;
import net.minecraft.world.level.levelgen.material.condition.VerticalGradientCondition;
import net.minecraft.world.level.levelgen.material.condition.WaterCondition;
import net.minecraft.world.level.levelgen.material.condition.YCondition;
import net.minecraft.world.level.levelgen.material.rule.BandlandsRule;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;

/**
 * 26.3: the surface tree is walked through the public condition/rule records instead of the 26.2
 * {@code SurfaceRules$*Source} accessor mixins.
 */
public final class SurfaceRuleAnalyzer {
    public static final Limits DEFAULT_LIMITS = new Limits(4096, 256);

    private static final String RULE_PACKAGE = "net.minecraft.world.level.levelgen.material.rule";
    private static final String CONDITION_PACKAGE =
            "net.minecraft.world.level.levelgen.material.condition";

    private SurfaceRuleAnalyzer() {
    }

    public static SurfaceRulePlan analyze(MaterialRule root) {
        return analyze(root, DEFAULT_LIMITS);
    }

    public static SurfaceRulePlan analyze(MaterialRule root, Limits limits) {
        return analyze(root, limits, SurfaceRuleAnalyzer::isVanillaSource);
    }

    static SurfaceRulePlan analyze(
            MaterialRule root,
            Limits limits,
            Predicate<Object> trustedSources
    ) {
        Objects.requireNonNull(root, "root");
        SurfaceRuleAnalysisBuilder state = new SurfaceRuleAnalysisBuilder(
                Objects.requireNonNull(limits, "limits"),
                Objects.requireNonNull(trustedSources, "trustedSources")
        );
        SurfaceRulePlan.Rule analyzed = analyzeRule(root, state, 1);
        return state.finish(analyzed);
    }

    private static SurfaceRulePlan.Rule analyzeRule(
            MaterialRule source,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        Object identity = source == null ? MissingSource.RULE : source;
        if (!state.enter(identity, depth)) {
            return state.opaqueRule(identity);
        }
        try {
            if (source == BandlandsRule.INSTANCE) {
                return new SurfaceRulePlan.Bandlands();
            }
            if (!state.isTrusted(source)) {
                return state.opaqueRule(source);
            }
            if (source instanceof MaterialRule.HolderHolder holder) {
                MaterialRule value = holder.holder().value();
                return value == null
                        ? state.opaqueRule(source)
                        : analyzeRule(value, state, depth + 1);
            }
            if (source instanceof BlockRule block) {
                return analyzeState(source, block, state);
            }
            if (source instanceof SequenceRule sequence) {
                return analyzeSequence(source, sequence, state, depth);
            }
            if (source instanceof ConditionRule condition) {
                return analyzeTest(source, condition, state, depth);
            }
            return state.opaqueRule(source);
        } finally {
            state.exit(identity);
        }
    }

    private static SurfaceRulePlan.Rule analyzeState(
            MaterialRule source,
            BlockRule access,
            SurfaceRuleAnalysisBuilder state
    ) {
        BlockState result = access.resultState();
        if (result == null) {
            return state.opaqueRule(source);
        }
        return new SurfaceRulePlan.State(result);
    }

    private static SurfaceRulePlan.Rule analyzeSequence(
            MaterialRule source,
            SequenceRule access,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        List<MaterialRule> sources = copyRules(access.sequence());
        if (sources == null || !state.canExpand(sources.size())) {
            return state.opaqueRule(source);
        }
        List<SurfaceRulePlan.Rule> rules = new java.util.ArrayList<>(sources.size());
        for (MaterialRule child : sources) {
            rules.add(analyzeRule(child, state, depth + 1));
        }
        return new SurfaceRulePlan.Sequence(rules);
    }

    private static SurfaceRulePlan.Rule analyzeTest(
            MaterialRule source,
            ConditionRule access,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        MaterialCondition conditionSource = access.ifTrue();
        MaterialRule followupSource = access.thenRun();
        if (conditionSource == null || followupSource == null || !state.canExpand(2)) {
            return state.opaqueRule(source);
        }
        SurfaceRulePlan.Condition condition = analyzeCondition(conditionSource, state, depth + 1);
        SurfaceRulePlan.Rule followup = analyzeRule(followupSource, state, depth + 1);
        return new SurfaceRulePlan.Test(condition, followup);
    }

    private static SurfaceRulePlan.Condition analyzeCondition(
            MaterialCondition source,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        Object identity = source == null ? MissingSource.CONDITION : source;
        if (!state.enter(identity, depth)) {
            return state.opaqueCondition(identity);
        }
        try {
            SurfaceConditionSpec.Singleton singleton = singleton(source);
            if (singleton != null) {
                return state.knownCondition(source, singleton);
            }
            if (!state.isTrusted(source)) {
                return state.opaqueCondition(source);
            }
            if (source instanceof MaterialCondition.HolderHolder holder) {
                MaterialCondition value = holder.holder().value();
                return value == null
                        ? state.opaqueCondition(source)
                        : analyzeCondition(value, state, depth + 1);
            }
            if (source instanceof NotCondition not) {
                return analyzeNot(source, not, state, depth);
            }
            return analyzeKnownCondition(source, state);
        } finally {
            state.exit(identity);
        }
    }

    private static SurfaceRulePlan.Condition analyzeNot(
            MaterialCondition source,
            NotCondition access,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        MaterialCondition targetSource = access.target();
        if (targetSource == null || !state.canExpand(1)) {
            return state.opaqueCondition(source);
        }
        SurfaceRulePlan.Condition target = analyzeCondition(targetSource, state, depth + 1);
        return new SurfaceRulePlan.NotCondition(target);
    }

    private static SurfaceRulePlan.Condition analyzeKnownCondition(
            MaterialCondition source,
            SurfaceRuleAnalysisBuilder state
    ) {
        if (source instanceof NoiseThresholdCondition access) {
            return state.knownCondition(source, noiseSpec(access));
        }
        if (source instanceof StoneDepthCondition access) {
            return state.knownCondition(source, stoneSpec(access));
        }
        if (source instanceof VerticalGradientCondition access) {
            return state.knownCondition(source, gradientSpec(access));
        }
        if (source instanceof WaterCondition access) {
            return state.knownCondition(source, waterSpec(access));
        }
        if (source instanceof YCondition access) {
            return state.knownCondition(source, ySpec(access));
        }
        return state.opaqueCondition(source);
    }

    private static SurfaceConditionSpec noiseSpec(NoiseThresholdCondition access) {
        return new SurfaceConditionSpec.Noise(
                access.noise(), access.minThreshold(), access.maxThreshold(), access.is3d()
        );
    }

    private static SurfaceConditionSpec.StoneDepth stoneSpec(StoneDepthCondition access) {
        return new SurfaceConditionSpec.StoneDepth(
                access.offset(), access.addSurfaceDepth(),
                access.secondaryDepthRange(), access.surfaceType()
        );
    }

    private static SurfaceConditionSpec gradientSpec(VerticalGradientCondition access) {
        return new SurfaceConditionSpec.VerticalGradient(
                access.randomName(), access.trueAtAndBelow(), access.falseAtAndAbove()
        );
    }

    private static SurfaceConditionSpec waterSpec(WaterCondition access) {
        return new SurfaceConditionSpec.Water(
                access.offset(), access.surfaceDepthMultiplier(), access.addStoneDepth()
        );
    }

    private static SurfaceConditionSpec ySpec(YCondition access) {
        return new SurfaceConditionSpec.YAbove(
                access.anchor(), access.surfaceDepthMultiplier(), access.addStoneDepth()
        );
    }

    private static SurfaceConditionSpec.Singleton singleton(MaterialCondition source) {
        if (source instanceof AbovePreliminarySurfaceCondition) {
            return SurfaceConditionSpec.Singleton.ABOVE_PRELIMINARY_SURFACE;
        }
        if (source instanceof HoleCondition) {
            return SurfaceConditionSpec.Singleton.HOLE;
        }
        if (source instanceof SteepCondition) {
            return SurfaceConditionSpec.Singleton.STEEP;
        }
        return source instanceof TemperatureCondition
                ? SurfaceConditionSpec.Singleton.TEMPERATURE
                : null;
    }

    /**
     * 26.3: vanilla rules and conditions are plain records in two dedicated packages, so the nest
     * host test of 26.2 becomes a package test plus the two holder wrappers.
     */
    private static boolean isVanillaSource(Object source) {
        if (source == null) {
            return false;
        }
        Class<?> type = source.getClass();
        if (type == MaterialRule.HolderHolder.class
                || type == MaterialCondition.HolderHolder.class) {
            return true;
        }
        String name = type.getPackageName();
        return RULE_PACKAGE.equals(name) || CONDITION_PACKAGE.equals(name);
    }

    private static List<MaterialRule> copyRules(List<MaterialRule> rules) {
        if (rules == null) {
            return null;
        }
        try {
            return List.copyOf(rules);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public record Limits(int maxSourceNodes, int maxDepth) {
        public Limits {
            if (maxSourceNodes < 1 || maxDepth < 1) {
                throw new IllegalArgumentException("Surface analyzer limits must be positive");
            }
        }
    }

    private enum MissingSource {
        RULE,
        CONDITION
    }

}

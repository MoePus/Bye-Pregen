package com.moepus.byepregen.worldgen.surface;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

public final class SurfaceRuleAnalyzer {
    public static final Limits DEFAULT_LIMITS = new Limits(4096, 256);

    private static final SurfaceRules.RuleSource BANDLANDS = SurfaceRules.bandlands();
    private static final SurfaceRules.ConditionSource ABOVE_PRELIMINARY =
            SurfaceRules.abovePreliminarySurface();
    private static final SurfaceRules.ConditionSource HOLE = SurfaceRules.hole();
    private static final SurfaceRules.ConditionSource STEEP = SurfaceRules.steep();
    private static final SurfaceRules.ConditionSource TEMPERATURE = SurfaceRules.temperature();

    private SurfaceRuleAnalyzer() {
    }

    public static SurfaceRulePlan analyze(SurfaceRules.RuleSource root) {
        return analyze(root, DEFAULT_LIMITS);
    }

    public static SurfaceRulePlan analyze(SurfaceRules.RuleSource root, Limits limits) {
        return analyze(root, limits, SurfaceRuleAnalyzer::isVanillaSource);
    }

    static SurfaceRulePlan analyze(
            SurfaceRules.RuleSource root,
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
            SurfaceRules.RuleSource source,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        Object identity = source == null ? MissingSource.RULE : source;
        if (!state.enter(identity, depth)) {
            return state.opaqueRule(identity);
        }
        try {
            if (source == BANDLANDS) {
                return new SurfaceRulePlan.Bandlands();
            }
            if (!state.isTrusted(source)) {
                return state.opaqueRule(source);
            }
            if (source instanceof SurfaceRuleSourceAccess.Block access) {
                return analyzeState(source, access, state);
            }
            if (source instanceof SurfaceRuleSourceAccess.Sequence access) {
                return analyzeSequence(source, access, state, depth);
            }
            if (source instanceof SurfaceRuleSourceAccess.Test access) {
                return analyzeTest(source, access, state, depth);
            }
            return state.opaqueRule(source);
        } finally {
            state.exit(identity);
        }
    }

    private static SurfaceRulePlan.Rule analyzeState(
            SurfaceRules.RuleSource source,
            SurfaceRuleSourceAccess.Block access,
            SurfaceRuleAnalysisBuilder state
    ) {
        BlockState result = access.byepregen$resultState();
        if (result == null) {
            return state.opaqueRule(source);
        }
        return new SurfaceRulePlan.State(result);
    }

    private static SurfaceRulePlan.Rule analyzeSequence(
            SurfaceRules.RuleSource source,
            SurfaceRuleSourceAccess.Sequence access,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        List<SurfaceRules.RuleSource> sources = copyRules(access.byepregen$sequence());
        if (sources == null || !state.canExpand(sources.size())) {
            return state.opaqueRule(source);
        }
        List<SurfaceRulePlan.Rule> rules = new java.util.ArrayList<>(sources.size());
        for (SurfaceRules.RuleSource child : sources) {
            rules.add(analyzeRule(child, state, depth + 1));
        }
        return new SurfaceRulePlan.Sequence(rules);
    }

    private static SurfaceRulePlan.Rule analyzeTest(
            SurfaceRules.RuleSource source,
            SurfaceRuleSourceAccess.Test access,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        SurfaceRules.ConditionSource conditionSource = access.byepregen$condition();
        SurfaceRules.RuleSource followupSource = access.byepregen$followup();
        if (conditionSource == null || followupSource == null || !state.canExpand(2)) {
            return state.opaqueRule(source);
        }
        SurfaceRulePlan.Condition condition = analyzeCondition(conditionSource, state, depth + 1);
        SurfaceRulePlan.Rule followup = analyzeRule(followupSource, state, depth + 1);
        return new SurfaceRulePlan.Test(condition, followup);
    }

    private static SurfaceRulePlan.Condition analyzeCondition(
            SurfaceRules.ConditionSource source,
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
            if (source instanceof SurfaceRuleSourceAccess.NotCondition access) {
                return analyzeNot(source, access, state, depth);
            }
            return analyzeKnownCondition(source, state);
        } finally {
            state.exit(identity);
        }
    }

    private static SurfaceRulePlan.Condition analyzeNot(
            SurfaceRules.ConditionSource source,
            SurfaceRuleSourceAccess.NotCondition access,
            SurfaceRuleAnalysisBuilder state,
            int depth
    ) {
        SurfaceRules.ConditionSource targetSource = access.byepregen$target();
        if (targetSource == null || !state.canExpand(1)) {
            return state.opaqueCondition(source);
        }
        SurfaceRulePlan.Condition target = analyzeCondition(targetSource, state, depth + 1);
        return new SurfaceRulePlan.NotCondition(target);
    }

    private static SurfaceRulePlan.Condition analyzeKnownCondition(
            SurfaceRules.ConditionSource source,
            SurfaceRuleAnalysisBuilder state
    ) {
        if (source instanceof SurfaceRuleSourceAccess.NoiseCondition access) {
            return state.knownCondition(source, noiseSpec(access));
        }
        if (source instanceof SurfaceRuleSourceAccess.StoneDepthCondition access) {
            return state.knownCondition(source, stoneSpec(access));
        }
        if (source instanceof SurfaceRuleSourceAccess.VerticalGradientCondition access) {
            return state.knownCondition(source, gradientSpec(access));
        }
        if (source instanceof SurfaceRuleSourceAccess.WaterCondition access) {
            return state.knownCondition(source, waterSpec(access));
        }
        if (source instanceof SurfaceRuleSourceAccess.YCondition access) {
            return state.knownCondition(source, ySpec(access));
        }
        return state.opaqueCondition(source);
    }

    private static SurfaceConditionSpec noiseSpec(SurfaceRuleSourceAccess.NoiseCondition access) {
        return new SurfaceConditionSpec.Noise(
                access.byepregen$noise(), access.byepregen$minimum(), access.byepregen$maximum()
        );
    }

    private static SurfaceConditionSpec.StoneDepth stoneSpec(
            SurfaceRuleSourceAccess.StoneDepthCondition access
    ) {
        return new SurfaceConditionSpec.StoneDepth(
                access.byepregen$offset(), access.byepregen$addSurfaceDepth(),
                access.byepregen$secondaryDepthRange(), access.byepregen$surfaceType()
        );
    }

    private static SurfaceConditionSpec gradientSpec(
            SurfaceRuleSourceAccess.VerticalGradientCondition access
    ) {
        return new SurfaceConditionSpec.VerticalGradient(
                access.byepregen$randomName(), access.byepregen$trueAtAndBelow(),
                access.byepregen$falseAtAndAbove()
        );
    }

    private static SurfaceConditionSpec waterSpec(SurfaceRuleSourceAccess.WaterCondition access) {
        return new SurfaceConditionSpec.Water(
                access.byepregen$offset(), access.byepregen$surfaceDepthMultiplier(),
                access.byepregen$addStoneDepth()
        );
    }

    private static SurfaceConditionSpec ySpec(SurfaceRuleSourceAccess.YCondition access) {
        return new SurfaceConditionSpec.YAbove(
                access.byepregen$anchor(), access.byepregen$surfaceDepthMultiplier(),
                access.byepregen$addStoneDepth()
        );
    }

    private static SurfaceConditionSpec.Singleton singleton(SurfaceRules.ConditionSource source) {
        if (source == ABOVE_PRELIMINARY) return SurfaceConditionSpec.Singleton.ABOVE_PRELIMINARY_SURFACE;
        if (source == HOLE) return SurfaceConditionSpec.Singleton.HOLE;
        if (source == STEEP) return SurfaceConditionSpec.Singleton.STEEP;
        return source == TEMPERATURE ? SurfaceConditionSpec.Singleton.TEMPERATURE : null;
    }

    private static boolean isVanillaSource(Object source) {
        return source != null && source.getClass().getNestHost() == SurfaceRules.class;
    }

    private static List<SurfaceRules.RuleSource> copyRules(List<SurfaceRules.RuleSource> rules) {
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

package com.moepus.byepregen.worldgen.surface;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

import static com.moepus.byepregen.worldgen.surface.SurfaceRuleSemantics.BindingEffect;
import static com.moepus.byepregen.worldgen.surface.SurfaceRuleSemantics.Dependency;
import static com.moepus.byepregen.worldgen.surface.SurfaceRuleSemantics.EvaluationEffect;
import static com.moepus.byepregen.worldgen.surface.SurfaceRuleSemantics.ProofKind;
import static com.moepus.byepregen.worldgen.surface.SurfaceRuleSemantics.Scope;
import static com.moepus.byepregen.worldgen.surface.SurfaceRuleSemantics.Semantics;
import static com.moepus.byepregen.worldgen.surface.SurfaceRuleSemantics.ValueReuse;

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
                return new SurfaceRulePlan.Bandlands(state.ruleMetadata(
                        source, SurfaceRuleSemantics.POINT_OBSERVATION, BindingEffect.NONE
                ));
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
        return new SurfaceRulePlan.State(state.ruleMetadata(
                source, SurfaceRuleSemantics.TEMPLATE_PURE, BindingEffect.NONE
        ), result);
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
        SurfaceRulePlan.RuleMetadata metadata = state.ruleMetadata(
                source, SurfaceRuleSemantics.POINT_OBSERVATION, BindingEffect.NONE
        );
        List<SurfaceRulePlan.Rule> rules = new java.util.ArrayList<>(sources.size());
        for (SurfaceRules.RuleSource child : sources) {
            rules.add(analyzeRule(child, state, depth + 1));
        }
        return new SurfaceRulePlan.Sequence(metadata, rules);
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
        SurfaceRulePlan.RuleMetadata metadata = state.ruleMetadata(
                source, SurfaceRuleSemantics.POINT_OBSERVATION, BindingEffect.NONE
        );
        SurfaceRulePlan.Condition condition = analyzeCondition(conditionSource, state, depth + 1);
        SurfaceRulePlan.Rule followup = analyzeRule(followupSource, state, depth + 1);
        return new SurfaceRulePlan.Test(metadata, condition, followup);
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
                return state.knownCondition(source, singleton, singletonSemantics(singleton), BindingEffect.NONE);
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
        SurfaceRulePlan.OccurrenceId occurrenceId = state.nextOccurrence();
        SurfaceRulePlan.Condition target = analyzeCondition(targetSource, state, depth + 1);
        SurfaceRulePlan.ConditionMetadata metadata = state.conditionMetadata(
                occurrenceId,
                source,
                BindingEffect.NONE,
                target.metadata().cacheGroupId()
        );
        SurfaceRulePlan.ConditionValue value = state.value(
                new SurfaceConditionSpec.Negated(target.value().id()),
                target.value().semantics()
        );
        return new SurfaceRulePlan.NotCondition(metadata, value, target);
    }

    private static SurfaceRulePlan.Condition analyzeKnownCondition(
            SurfaceRules.ConditionSource source,
            SurfaceRuleAnalysisBuilder state
    ) {
        if (source instanceof SurfaceRuleSourceAccess.BiomeCondition access) {
            return state.knownCondition(source, biomeSpec(access), biomeSemantics(), BindingEffect.NONE);
        }
        if (source instanceof SurfaceRuleSourceAccess.NoiseCondition access) {
            return state.knownCondition(source, noiseSpec(access), noiseSemantics(), BindingEffect.MAY_THROW);
        }
        if (source instanceof SurfaceRuleSourceAccess.StoneDepthCondition access) {
            SurfaceConditionSpec.StoneDepth spec = stoneSpec(access);
            return state.knownCondition(source, spec, stoneSemantics(spec), BindingEffect.NONE);
        }
        if (source instanceof SurfaceRuleSourceAccess.VerticalGradientCondition access) {
            return state.knownCondition(
                    source, gradientSpec(access), gradientSemantics(), BindingEffect.MAY_THROW
            );
        }
        if (source instanceof SurfaceRuleSourceAccess.WaterCondition access) {
            return state.knownCondition(source, waterSpec(access), waterSemantics(), BindingEffect.NONE);
        }
        if (source instanceof SurfaceRuleSourceAccess.YCondition access) {
            return state.knownCondition(source, ySpec(access), ySemantics(), BindingEffect.NONE);
        }
        return state.opaqueCondition(source);
    }

    private static SurfaceConditionSpec biomeSpec(SurfaceRuleSourceAccess.BiomeCondition access) {
        return new SurfaceConditionSpec.Biome(copySet(access.byepregen$biomes()));
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

    private static Semantics singletonSemantics(SurfaceConditionSpec.Singleton singleton) {
        return switch (singleton) {
            case ABOVE_PRELIMINARY_SURFACE -> semantics(
                    Scope.UPDATE_Y,
                    EvaluationEffect.MAY_THROW,
                    ProofKind.AFFINE_TRAJECTORY,
                    ValueReuse.NONE,
                    Dependency.XZ,
                    Dependency.Y,
                    Dependency.SURFACE_DEPTH,
                    Dependency.MIN_SURFACE,
                    Dependency.HEIGHTMAP
            );
            case HOLE -> semantics(
                    Scope.COLUMN,
                    EvaluationEffect.PURE_TOTAL,
                    ProofKind.COLUMN_FACT,
                    ValueReuse.CANONICAL_WITHIN_SCOPE,
                    Dependency.XZ,
                    Dependency.SURFACE_DEPTH
            );
            case STEEP -> semantics(
                    Scope.COLUMN,
                    EvaluationEffect.MUTABLE_OBSERVATION,
                    ProofKind.BARRIER,
                    ValueReuse.NONE,
                    Dependency.XZ,
                    Dependency.HEIGHTMAP,
                    Dependency.MUTABLE_CONTEXT
            );
            case TEMPERATURE -> semantics(
                    Scope.UPDATE_Y,
                    EvaluationEffect.MUTABLE_OBSERVATION,
                    ProofKind.BARRIER,
                    ValueReuse.NONE,
                    Dependency.XZ,
                    Dependency.Y,
                    Dependency.BIOME,
                    Dependency.MUTABLE_CONTEXT
            );
        };
    }

    private static Semantics biomeSemantics() {
        return semantics(
                Scope.UPDATE_Y,
                EvaluationEffect.MUTABLE_OBSERVATION,
                ProofKind.BARRIER,
                ValueReuse.HOLDER_BEHAVIOR,
                Dependency.XZ,
                Dependency.Y,
                Dependency.BIOME,
                Dependency.MUTABLE_CONTEXT
        );
    }

    private static Semantics noiseSemantics() {
        return semantics(
                Scope.COLUMN,
                EvaluationEffect.PURE_TOTAL,
                ProofKind.COLUMN_FACT,
                ValueReuse.CANONICAL_WITHIN_SCOPE,
                Dependency.XZ,
                Dependency.NOISE
        );
    }

    private static Semantics stoneSemantics(SurfaceConditionSpec.StoneDepth stone) {
        EvaluationEffect effect = stone.secondaryDepthRange() == 0
                ? EvaluationEffect.PURE_TOTAL
                : EvaluationEffect.MAY_THROW;
        return semantics(
                Scope.UPDATE_Y,
                effect,
                ProofKind.AFFINE_TRAJECTORY,
                ValueReuse.NONE,
                Dependency.XZ,
                Dependency.STONE_DEPTH,
                Dependency.SURFACE_DEPTH,
                Dependency.SURFACE_SECONDARY
        );
    }

    private static Semantics gradientSemantics() {
        return semantics(
                Scope.UPDATE_Y,
                EvaluationEffect.MAY_THROW,
                ProofKind.VERTICAL_RANDOM,
                ValueReuse.NONE,
                Dependency.XZ,
                Dependency.Y,
                Dependency.RANDOM
        );
    }

    private static Semantics waterSemantics() {
        return semantics(
                Scope.UPDATE_Y,
                EvaluationEffect.PURE_TOTAL,
                ProofKind.AFFINE_TRAJECTORY,
                ValueReuse.NONE,
                Dependency.XZ,
                Dependency.Y,
                Dependency.WATER,
                Dependency.STONE_DEPTH,
                Dependency.SURFACE_DEPTH
        );
    }

    private static Semantics ySemantics() {
        return semantics(
                Scope.UPDATE_Y,
                EvaluationEffect.MAY_THROW,
                ProofKind.AFFINE_TRAJECTORY,
                ValueReuse.NONE,
                Dependency.XZ,
                Dependency.Y,
                Dependency.STONE_DEPTH,
                Dependency.SURFACE_DEPTH,
                Dependency.MUTABLE_CONTEXT
        );
    }

    private static Semantics semantics(
            Scope scope,
            EvaluationEffect effect,
            ProofKind proofKind,
            ValueReuse valueReuse,
            Dependency... dependencies
    ) {
        return SurfaceRuleSemantics.semantics(
                scope, effect, proofKind, valueReuse, dependencies
        );
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

    private static <T> java.util.Set<T> copySet(List<T> values) {
        return values == null ? java.util.Set.of() : java.util.Set.copyOf(values);
    }
}

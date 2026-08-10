package com.moepus.byepregen.worldgen.surface;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.SurfaceRules;

public final class SurfaceRuleAnalyzerTest {
    private SurfaceRuleAnalyzerTest() {
    }

    public static void main(String[] args) {
        preservesSourceOrderAndStableIds();
        canonicalizesValuesWithoutMergingOccurrences();
        keepsUnknownSourcesAsOpaqueBarriers();
        recognizesSingletonIdentityWithoutMixins();
        rejectsUnregisteredShapeImplementations();
        sharesOnlyVanillaSingletonCacheGroups();
        fallsBackAtAnalysisLimits();
    }

    private static void preservesSourceOrderAndStableIds() {
        SurfaceRules.ConditionSource biome = fakeBiome(List.of(Biomes.PLAINS));
        SurfaceRules.RuleSource source = fakeSequence(List.of(
                fakeTest(fakeNot(biome), SurfaceRules.bandlands()),
                unknownRule()
        ));

        SurfaceRulePlan plan = analyze(source);
        SurfaceRulePlan.Sequence root = requireType(plan.root(), SurfaceRulePlan.Sequence.class);
        SurfaceRulePlan.Test test = requireType(root.rules().get(0), SurfaceRulePlan.Test.class);
        SurfaceRulePlan.NotCondition not = requireType(
                test.condition(), SurfaceRulePlan.NotCondition.class
        );
        SurfaceRulePlan.KnownCondition target = requireType(
                not.target(), SurfaceRulePlan.KnownCondition.class
        );
        SurfaceRulePlan.Bandlands bandlands = requireType(
                test.followup(), SurfaceRulePlan.Bandlands.class
        );
        SurfaceRulePlan.OpaqueRule opaque = requireType(
                root.rules().get(1), SurfaceRulePlan.OpaqueRule.class
        );

        assertId(0, root.metadata().entryId().value(), "root entry");
        assertId(0, root.metadata().occurrenceId().value(), "root occurrence");
        assertId(1, test.metadata().entryId().value(), "test entry");
        assertId(1, test.metadata().occurrenceId().value(), "test occurrence");
        assertId(2, not.metadata().occurrenceId().value(), "not occurrence");
        assertId(3, target.metadata().occurrenceId().value(), "biome occurrence");
        assertId(2, bandlands.metadata().entryId().value(), "bandlands entry");
        assertId(4, bandlands.metadata().occurrenceId().value(), "bandlands occurrence");
        assertId(3, opaque.metadata().entryId().value(), "opaque entry");
        assertId(5, opaque.metadata().occurrenceId().value(), "opaque occurrence");
        assertId(5, plan.entryCount(), "entry count");
        assertId(6, plan.occurrenceCount(), "occurrence count");
        assertSame(
                not.metadata().cacheGroupId(),
                target.metadata().cacheGroupId(),
                "not must reuse the target cache group"
        );
        assertControlFlow(plan, test, bandlands, opaque);
    }

    private static void canonicalizesValuesWithoutMergingOccurrences() {
        SurfaceRules.ConditionSource firstBiome = fakeBiome(List.of(Biomes.PLAINS));
        SurfaceRules.ConditionSource secondBiome = fakeBiome(List.of(Biomes.PLAINS));
        SurfaceRules.RuleSource source = fakeSequence(List.of(
                fakeTest(firstBiome, SurfaceRules.bandlands()),
                fakeTest(secondBiome, SurfaceRules.bandlands())
        ));

        SurfaceRulePlan.Sequence root = requireType(
                analyze(source).root(), SurfaceRulePlan.Sequence.class
        );
        SurfaceRulePlan.KnownCondition first = condition(root.rules().get(0));
        SurfaceRulePlan.KnownCondition second = condition(root.rules().get(1));
        assertSame(first.value(), second.value(), "equal biome values must canonicalize");
        assertNotEquals(
                first.metadata().occurrenceId(),
                second.metadata().occurrenceId(),
                "condition occurrences must remain distinct"
        );
        assertNotEquals(
                first.metadata().cacheGroupId(),
                second.metadata().cacheGroupId(),
                "non-singleton vanilla caches remain occurrence-local"
        );
    }

    private static void keepsUnknownSourcesAsOpaqueBarriers() {
        SurfaceRules.ConditionSource condition = unknownCondition();
        SurfaceRules.RuleSource source = fakeSequence(List.of(
                fakeTest(condition, SurfaceRules.bandlands()),
                fakeTest(condition, SurfaceRules.bandlands())
        ));

        SurfaceRulePlan.Sequence root = requireType(
                analyze(source).root(), SurfaceRulePlan.Sequence.class
        );
        SurfaceRulePlan.OpaqueCondition first = opaqueCondition(root.rules().get(0));
        SurfaceRulePlan.OpaqueCondition second = opaqueCondition(root.rules().get(1));
        if (!first.value().semantics().effect().barrier()) {
            throw new AssertionError("opaque condition is not an effect barrier");
        }
        assertNotEquals(first.value().id(), second.value().id(), "opaque values must be unique");
        if (analyze(unknownRule()).root()
                instanceof SurfaceRulePlan.OpaqueRule) {
            return;
        }
        throw new AssertionError("unknown root rule was not preserved as opaque");
    }

    private static void recognizesSingletonIdentityWithoutMixins() {
        SurfaceRules.RuleSource source = fakeTest(
                SurfaceRules.hole(), SurfaceRules.bandlands()
        );
        SurfaceRulePlan.Test test = requireType(
                analyze(source).root(), SurfaceRulePlan.Test.class
        );
        SurfaceRulePlan.KnownCondition condition = requireType(
                test.condition(), SurfaceRulePlan.KnownCondition.class
        );
        assertEquals(
                SurfaceConditionSpec.Singleton.HOLE,
                condition.value().spec(),
                "hole singleton"
        );
        assertEquals(
                SurfaceRuleSemantics.Scope.COLUMN,
                condition.value().semantics().scope(),
                "hole scope"
        );
        assertEquals(
                SurfaceRuleSemantics.EvaluationEffect.PURE_TOTAL,
                condition.value().semantics().effect(),
                "hole effect"
        );
    }

    private static void rejectsUnregisteredShapeImplementations() {
        SurfaceRules.RuleSource source = fakeSequence(List.of(SurfaceRules.bandlands()));
        requireType(SurfaceRuleAnalyzer.analyze(source).root(), SurfaceRulePlan.OpaqueRule.class);
    }

    private static void sharesOnlyVanillaSingletonCacheGroups() {
        SurfaceRulePlan.Sequence root = requireType(analyze(fakeSequence(List.of(
                fakeTest(SurfaceRules.hole(), SurfaceRules.bandlands()),
                fakeTest(SurfaceRules.hole(), SurfaceRules.bandlands())
        ))).root(), SurfaceRulePlan.Sequence.class);
        SurfaceRulePlan.KnownCondition first = condition(root.rules().get(0));
        SurfaceRulePlan.KnownCondition second = condition(root.rules().get(1));
        assertEquals(
                first.metadata().cacheGroupId(),
                second.metadata().cacheGroupId(),
                "Context singleton occurrences must share their vanilla cache group"
        );
    }

    private static void fallsBackAtAnalysisLimits() {
        SurfaceRules.RuleSource source = fakeSequence(List.of(unknownRule(), unknownRule()));
        SurfaceRulePlan plan = SurfaceRuleAnalyzer.analyze(
                source,
                new SurfaceRuleAnalyzer.Limits(1, 1),
                ignored -> true
        );
        requireType(plan.root(), SurfaceRulePlan.OpaqueRule.class);
        assertId(2, plan.entryCount(), "limited entry count");
        assertId(1, plan.occurrenceCount(), "limited occurrence count");
    }

    private static void assertControlFlow(
            SurfaceRulePlan plan,
            SurfaceRulePlan.Test test,
            SurfaceRulePlan.Bandlands bandlands,
            SurfaceRulePlan.OpaqueRule opaque
    ) {
        SurfaceControlFlowPlan flow = plan.controlFlow();
        SurfaceControlFlowPlan.Branch branch = requireType(
                flow.entry(test.metadata().entryId()), SurfaceControlFlowPlan.Branch.class
        );
        assertEquals(bandlands.metadata().entryId(), branch.onTrue(), "true successor");
        assertEquals(opaque.metadata().entryId(), branch.onFalse(), "false continuation");
        SurfaceControlFlowPlan.Delegate delegate = requireType(
                flow.entry(opaque.metadata().entryId()), SurfaceControlFlowPlan.Delegate.class
        );
        assertEquals(flow.failEntry(), delegate.onNull(), "opaque null continuation");
        requireType(flow.entry(flow.failEntry()), SurfaceControlFlowPlan.Fail.class);
    }

    private static SurfaceRulePlan analyze(SurfaceRules.RuleSource source) {
        return SurfaceRuleAnalyzer.analyze(
                source,
                SurfaceRuleAnalyzer.DEFAULT_LIMITS,
                ignored -> true
        );
    }

    private static SurfaceRulePlan.KnownCondition condition(SurfaceRulePlan.Rule rule) {
        SurfaceRulePlan.Test test = requireType(rule, SurfaceRulePlan.Test.class);
        return requireType(test.condition(), SurfaceRulePlan.KnownCondition.class);
    }

    private static SurfaceRulePlan.OpaqueCondition opaqueCondition(SurfaceRulePlan.Rule rule) {
        SurfaceRulePlan.Test test = requireType(rule, SurfaceRulePlan.Test.class);
        return requireType(test.condition(), SurfaceRulePlan.OpaqueCondition.class);
    }

    private static <T> T requireType(Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new AssertionError("Expected " + type.getSimpleName() + ", got " + value);
        }
        return type.cast(value);
    }

    private static void assertId(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!ObjectsSupport.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertNotEquals(Object first, Object second, String message) {
        if (ObjectsSupport.equals(first, second)) {
            throw new AssertionError(message + ": both were " + first);
        }
    }

    private static SurfaceRules.RuleSource fakeSequence(List<SurfaceRules.RuleSource> rules) {
        return ruleProxy(
                SurfaceRuleSourceAccess.Sequence.class,
                (method, arguments) -> method.equals("byepregen$sequence") ? rules : null
        );
    }

    private static SurfaceRules.RuleSource fakeTest(
            SurfaceRules.ConditionSource condition,
            SurfaceRules.RuleSource followup
    ) {
        return ruleProxy(SurfaceRuleSourceAccess.Test.class, (method, arguments) -> switch (method) {
            case "byepregen$condition" -> condition;
            case "byepregen$followup" -> followup;
            default -> null;
        });
    }

    private static SurfaceRules.ConditionSource fakeNot(SurfaceRules.ConditionSource target) {
        return conditionProxy(
                SurfaceRuleSourceAccess.NotCondition.class,
                (method, arguments) -> method.equals("byepregen$target") ? target : null
        );
    }

    private static SurfaceRules.ConditionSource fakeBiome(List<ResourceKey<Biome>> biomes) {
        Predicate<ResourceKey<Biome>> predicate = biomes::contains;
        return conditionProxy(
                SurfaceRuleSourceAccess.BiomeCondition.class,
                (method, arguments) -> switch (method) {
                    case "byepregen$biomes" -> biomes;
                    case "byepregen$biomeNameTest" -> predicate;
                    default -> null;
                }
        );
    }

    private static SurfaceRules.RuleSource unknownRule() {
        return ruleProxy(null, (method, arguments) -> null);
    }

    private static SurfaceRules.ConditionSource unknownCondition() {
        return conditionProxy(null, (method, arguments) -> null);
    }

    private static SurfaceRules.RuleSource ruleProxy(
            Class<?> access,
            ProxyCall call
    ) {
        return (SurfaceRules.RuleSource) proxy(SurfaceRules.RuleSource.class, access, call);
    }

    private static SurfaceRules.ConditionSource conditionProxy(
            Class<?> access,
            ProxyCall call
    ) {
        return (SurfaceRules.ConditionSource) proxy(
                SurfaceRules.ConditionSource.class,
                access,
                call
        );
    }

    private static Object proxy(Class<?> sourceType, Class<?> access, ProxyCall call) {
        Class<?>[] interfaces = access == null
                ? new Class<?>[]{sourceType}
                : new Class<?>[]{sourceType, access};
        return Proxy.newProxyInstance(
                SurfaceRuleAnalyzerTest.class.getClassLoader(),
                interfaces,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> sourceType.getSimpleName() + "TestProxy";
                    default -> call.invoke(method.getName(), arguments);
                }
        );
    }

    @FunctionalInterface
    private interface ProxyCall {
        Object invoke(String method, Object[] arguments);
    }

    private static final class ObjectsSupport {
        private ObjectsSupport() {
        }

        private static boolean equals(Object first, Object second) {
            return java.util.Objects.equals(first, second);
        }
    }
}

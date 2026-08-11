package com.moepus.byepregen.worldgen.surface;

import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.SurfaceRules;

public final class SurfaceRuleAnalyzerTest {
    private SurfaceRuleAnalyzerTest() {
    }

    public static void main(String[] args) {
        preservesSourceOrderAndOpaqueSources();
        keepsUnknownSourcesAsOpaqueBarriers();
        recognizesSingletonIdentityWithoutMixins();
        rejectsUnregisteredShapeImplementations();
        canonicalizesSingletonValues();
        fallsBackAtAnalysisLimits();
    }

    private static void preservesSourceOrderAndOpaqueSources() {
        SurfaceRules.ConditionSource biome = fakeBiome();
        SurfaceRules.RuleSource unknown = unknownRule();
        SurfaceRules.RuleSource source = fakeSequence(List.of(
                fakeTest(fakeNot(biome), SurfaceRules.bandlands()),
                unknown
        ));

        SurfaceRulePlan plan = analyze(source);
        SurfaceRulePlan.Sequence root = requireType(plan.root(), SurfaceRulePlan.Sequence.class);
        SurfaceRulePlan.Test test = requireType(root.rules().get(0), SurfaceRulePlan.Test.class);
        SurfaceRulePlan.NotCondition not = requireType(
                test.condition(), SurfaceRulePlan.NotCondition.class
        );
        SurfaceRulePlan.OpaqueCondition target = requireType(
                not.target(), SurfaceRulePlan.OpaqueCondition.class
        );
        requireType(
                test.followup(), SurfaceRulePlan.Bandlands.class
        );
        SurfaceRulePlan.OpaqueRule opaque = requireType(
                root.rules().get(1), SurfaceRulePlan.OpaqueRule.class
        );
        assertSame(biome, target.source(), "delegated condition source");
        assertSame(unknown, opaque.source(), "opaque rule source");
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
    }

    private static void rejectsUnregisteredShapeImplementations() {
        SurfaceRules.RuleSource source = fakeSequence(List.of(SurfaceRules.bandlands()));
        requireType(SurfaceRuleAnalyzer.analyze(source).root(), SurfaceRulePlan.OpaqueRule.class);
    }

    private static void canonicalizesSingletonValues() {
        SurfaceRulePlan.Sequence root = requireType(analyze(fakeSequence(List.of(
                fakeTest(SurfaceRules.hole(), SurfaceRules.bandlands()),
                fakeTest(SurfaceRules.hole(), SurfaceRules.bandlands())
        ))).root(), SurfaceRulePlan.Sequence.class);
        SurfaceRulePlan.KnownCondition first = condition(root.rules().get(0));
        SurfaceRulePlan.KnownCondition second = condition(root.rules().get(1));
        assertSame(first.value(), second.value(), "singleton values must canonicalize");
    }

    private static void fallsBackAtAnalysisLimits() {
        SurfaceRules.RuleSource source = fakeSequence(List.of(unknownRule(), unknownRule()));
        SurfaceRulePlan plan = SurfaceRuleAnalyzer.analyze(
                source,
                new SurfaceRuleAnalyzer.Limits(1, 1),
                ignored -> true
        );
        SurfaceRulePlan.OpaqueRule opaque = requireType(
                plan.root(), SurfaceRulePlan.OpaqueRule.class
        );
        assertSame(source, opaque.source(), "limited source must remain delegated");
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

    private static SurfaceRules.ConditionSource fakeBiome() {
        return SurfaceRules.isBiome(Biomes.PLAINS);
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

package com.moepus.byepregen.worldgen.surface;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

public final class SurfaceBindingLayoutTest {
    private SurfaceBindingLayoutTest() {
    }

    @Test
    void eventsFollowSourceOrderAndStopOnFailure() {
        SurfaceRules.RuleSource source = sequence(List.of(
                test(unknownCondition(), unknownRule()),
                unknownRule(),
                test(unknownCondition(), unknownRule())
        ));
        SurfaceBindingLayout bindings = lower(source).bindings();
        List<SurfaceBindingLayout.Kind> expected = List.of(
                SurfaceBindingLayout.Kind.CONDITION,
                SurfaceBindingLayout.Kind.RULE,
                SurfaceBindingLayout.Kind.RULE,
                SurfaceBindingLayout.Kind.CONDITION,
                SurfaceBindingLayout.Kind.RULE
        );
        assertEquals(
                expected,
                bindings.events().stream().map(SurfaceBindingLayout.BindEvent::kind).toList(),
                "binding event source order"
        );
        assertStopsAtFirstFailure(bindings, expected);
    }

    private static void assertStopsAtFirstFailure(
            SurfaceBindingLayout bindings,
            List<SurfaceBindingLayout.Kind> expected
    ) {
        List<SurfaceBindingLayout.Kind> trace = new ArrayList<>();
        try {
            bindings.bindForTest(fakeContext(), (event, raw, context) -> {
                trace.add(event.kind());
                if (trace.size() == 3) {
                    throw new TestException();
                }
                return event.source();
            });
            throw new AssertionError("Expected binding failure");
        } catch (TestException expectedFailure) {
            assertEquals(expected.subList(0, 3), trace, "binding must stop at first failure");
        }
    }

    @Test
    void preservesGradientAndYAnchorRecipes() {
        VerticalAnchor lower = new TestAnchor(-8);
        VerticalAnchor upper = new TestAnchor(32);
        VerticalAnchor lazy = new TestAnchor(80);
        SurfaceRules.RuleSource source = sequence(List.of(
                test(gradient(lower, upper), SurfaceRules.bandlands()),
                test(yAbove(lazy), SurfaceRules.bandlands())
        ));
        List<SurfaceBindingLayout.BindEvent> events = lower(source).bindings().events();
        assertEquals(
                List.of(
                        SurfaceBindingLayout.Kind.RESOLVED_ANCHOR,
                        SurfaceBindingLayout.Kind.RESOLVED_ANCHOR,
                        SurfaceBindingLayout.Kind.RANDOM_FACTORY,
                        SurfaceBindingLayout.Kind.Y_ANCHOR
                ),
                events.stream().map(SurfaceBindingLayout.BindEvent::kind).toList(),
                "gradient eager and Y lazy binding order"
        );
        assertSame(lazy, events.get(3).source(), "Y anchor must stay unresolved in the layout");
    }

    @Test
    void canonicalizesNoiseStorageWithoutSkippingBindingEvents() {
        SurfaceScalarLayout layout = lower(sequence(List.of(
                test(noise(Noises.SURFACE, -1.0D, 0.0D), SurfaceRules.bandlands()),
                test(noise(Noises.SURFACE, 0.0D, 1.0D), SurfaceRules.bandlands()),
                test(noise(Noises.SURFACE, -1.0D, 0.0D), SurfaceRules.bandlands()),
                unknownRule()
        )));
        SurfaceBindingLayout bindings = layout.bindings();
        assertEquals(
                List.of(
                        SurfaceBindingLayout.Kind.NOISE,
                        SurfaceBindingLayout.Kind.NOISE,
                        SurfaceBindingLayout.Kind.NOISE,
                        SurfaceBindingLayout.Kind.RULE
                ),
                bindings.events().stream().map(SurfaceBindingLayout.BindEvent::kind).toList(),
                "noise binding events"
        );
        assertEquals(
                List.of(SurfaceBindingLayout.Kind.NOISE, SurfaceBindingLayout.Kind.RULE),
                bindings.storedSlots().stream().map(SurfaceBindingLayout.Slot::kind).toList(),
                "canonical generated storage"
        );
        if (!(bindings.events().get(1) instanceof SurfaceBindingLayout.Discarded)
                || !(bindings.events().get(2) instanceof SurfaceBindingLayout.Discarded)) {
            throw new AssertionError("duplicate noise binds must be explicit discarded events");
        }
        SurfaceBindingLayout.Slot trailing = (SurfaceBindingLayout.Slot)
                bindings.events().get(3);
        assertEquals(1, trailing.id().value(), "stored IDs must stay dense across discarded events");
        assertEquals(1, layout.noiseSamples().size(), "canonical noise samples");
        assertEquals(2, layout.noiseOccurrences(), "canonical noise predicates");
        assertEquals(
                2,
                layout.noiseSamples().getFirst().ranges().size(),
                "ranges sharing one noise sample"
        );
        List<SurfaceBindingLayout.Kind> trace = new ArrayList<>();
        Object[] values = bindings.bindForTest(fakeContext(), (event, raw, context) -> {
            trace.add(event.kind());
            return "event-" + trace.size();
        });
        assertEquals(4, trace.size(), "all binding events must execute");
        assertEquals(2, values.length, "only stored bindings enter the constructor array");
        assertEquals("event-1", values[0], "first noise result must back canonical storage");
        assertEquals("event-4", values[1], "later stored slot index");
    }

    @Test
    void delegatesBiomePredicates() {
        SurfaceScalarLayout layout = lower(test(
                biome(),
                SurfaceRules.bandlands()
        ));
        assertEquals(
                List.of(SurfaceBindingLayout.Kind.CONDITION),
                layout.bindings().storedSlots().stream()
                        .map(SurfaceBindingLayout.Slot::kind)
                        .toList(),
                "biome condition binding"
        );
    }

    @Test
    void doesNotSnapshotContextAcrossOpaqueBarrier() {
        SurfaceScalarLayout safe = lower(sequence(List.of(
                test(water(), SurfaceRules.bandlands()),
                test(water(), SurfaceRules.bandlands())
        )));
        SurfaceScalarLayout blocked = lower(sequence(List.of(
                test(water(), unknownRule()),
                test(water(), SurfaceRules.bandlands())
        )));
        SurfaceScalarLayout trailingBarrier = lower(sequence(List.of(
                test(water(), SurfaceRules.bandlands()),
                test(water(), SurfaceRules.bandlands()),
                unknownRule()
        )));
        if (!rootLocals(safe).caches(SurfaceRuntimeAbi.BLOCK_Y)) {
            throw new AssertionError("pure repeated Y reads should be cached");
        }
        if (rootLocals(blocked).caches(SurfaceRuntimeAbi.BLOCK_Y)) {
            throw new AssertionError("opaque rule must block Context snapshots");
        }
        if (!rootLocals(trailingBarrier).caches(SurfaceRuntimeAbi.BLOCK_Y)) {
            throw new AssertionError("a trailing barrier must not discard earlier snapshots");
        }
    }

    @Test
    void doesNotSnapshotLazyStoneDepthBelow() {
        SurfaceRules.ConditionSource ceiling = stoneDepth(CaveSurface.CEILING, 0);
        SurfaceScalarLayout layout = lower(sequence(List.of(
                test(ceiling, SurfaceRules.bandlands()),
                test(ceiling, SurfaceRules.bandlands())
        )));
        if (rootLocals(layout).caches(SurfaceRuntimeAbi.STONE_BELOW)) {
            throw new AssertionError("adjacent ceiling must not snapshot exact depth below");
        }
    }

    private static SurfaceMethodLocals rootLocals(SurfaceScalarLayout layout) {
        SurfaceRegionPlan regions = SurfaceRegionPlan.create(layout.plan().root());
        return SurfaceMethodLocals.create(
                layout,
                regions,
                new SurfaceRegionPlan.RuleBody(layout.plan().root())
        );
    }

    private static SurfaceScalarLayout lower(SurfaceRules.RuleSource source) {
        SurfaceRulePlan plan = SurfaceRuleAnalyzer.analyze(
                source,
                SurfaceRuleAnalyzer.DEFAULT_LIMITS,
                ignored -> true
        );
        try {
            return SurfaceScalarLayout.lower(plan);
        } catch (SurfaceCompileException exception) {
            throw new AssertionError("Cannot lower synthetic surface rule", exception);
        }
    }

    private static SurfaceContextAccess fakeContext() {
        return (SurfaceContextAccess) Proxy.newProxyInstance(
                SurfaceBindingLayoutTest.class.getClassLoader(),
                new Class<?>[]{SurfaceContextAccess.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType())
        );
    }

    private static SurfaceRules.RuleSource sequence(List<SurfaceRules.RuleSource> rules) {
        return ruleProxy(
                SurfaceRuleSourceAccess.Sequence.class,
                (method, arguments) -> method.equals("byepregen$sequence") ? rules : null
        );
    }

    private static SurfaceRules.RuleSource test(
            SurfaceRules.ConditionSource condition,
            SurfaceRules.RuleSource followup
    ) {
        return ruleProxy(SurfaceRuleSourceAccess.Test.class, (method, arguments) -> switch (method) {
            case "byepregen$condition" -> condition;
            case "byepregen$followup" -> followup;
            default -> null;
        });
    }

    private static SurfaceRules.ConditionSource gradient(
            VerticalAnchor lower,
            VerticalAnchor upper
    ) {
        return conditionProxy(
                SurfaceRuleSourceAccess.VerticalGradientCondition.class,
                (method, arguments) -> switch (method) {
                    case "byepregen$randomName" -> Identifier.parse("byepregen:test");
                    case "byepregen$trueAtAndBelow" -> lower;
                    case "byepregen$falseAtAndAbove" -> upper;
                    default -> null;
                }
        );
    }

    private static SurfaceRules.ConditionSource yAbove(VerticalAnchor anchor) {
        return conditionProxy(
                SurfaceRuleSourceAccess.YCondition.class,
                (method, arguments) -> switch (method) {
                    case "byepregen$anchor" -> anchor;
                    case "byepregen$surfaceDepthMultiplier" -> 0;
                    case "byepregen$addStoneDepth" -> false;
                    default -> null;
                }
        );
    }

    private static SurfaceRules.ConditionSource noise(
            ResourceKey<NormalNoise.NoiseParameters> key,
            double minimum,
            double maximum
    ) {
        return conditionProxy(
                SurfaceRuleSourceAccess.NoiseCondition.class,
                (method, arguments) -> switch (method) {
                    case "byepregen$noise" -> key;
                    case "byepregen$minimum" -> minimum;
                    case "byepregen$maximum" -> maximum;
                    default -> null;
                }
        );
    }

    private static SurfaceRules.ConditionSource biome() {
        return SurfaceRules.isBiome(Biomes.PLAINS);
    }

    private static SurfaceRules.ConditionSource water() {
        return conditionProxy(
                SurfaceRuleSourceAccess.WaterCondition.class,
                (method, arguments) -> switch (method) {
                    case "byepregen$offset", "byepregen$surfaceDepthMultiplier" -> 0;
                    case "byepregen$addStoneDepth" -> false;
                    default -> null;
                }
        );
    }

    private static SurfaceRules.ConditionSource stoneDepth(CaveSurface surface, int offset) {
        return conditionProxy(
                SurfaceRuleSourceAccess.StoneDepthCondition.class,
                (method, arguments) -> switch (method) {
                    case "byepregen$offset" -> offset;
                    case "byepregen$addSurfaceDepth" -> false;
                    case "byepregen$secondaryDepthRange" -> 0;
                    case "byepregen$surfaceType" -> surface;
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

    private static SurfaceRules.RuleSource ruleProxy(Class<?> access, ProxyCall call) {
        return (SurfaceRules.RuleSource) proxy(SurfaceRules.RuleSource.class, access, call);
    }

    private static SurfaceRules.ConditionSource conditionProxy(Class<?> access, ProxyCall call) {
        return (SurfaceRules.ConditionSource) proxy(
                SurfaceRules.ConditionSource.class, access, call
        );
    }

    private static Object proxy(Class<?> sourceType, Class<?> access, ProxyCall call) {
        Class<?>[] interfaces = access == null
                ? new Class<?>[]{sourceType}
                : new Class<?>[]{sourceType, access};
        return Proxy.newProxyInstance(
                SurfaceBindingLayoutTest.class.getClassLoader(),
                interfaces,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> sourceType.getSimpleName() + "BindingTestProxy";
                    default -> call.invoke(method.getName(), arguments);
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0D;
        return 0;
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    @FunctionalInterface
    private interface ProxyCall {
        Object invoke(String method, Object[] arguments);
    }

    private record TestAnchor(int y) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return this.y;
        }
    }

    private static final class TestException extends RuntimeException {
    }
}

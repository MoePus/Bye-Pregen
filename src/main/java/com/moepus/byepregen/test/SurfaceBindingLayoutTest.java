package com.moepus.byepregen.worldgen.surface;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class SurfaceBindingLayoutTest {
    private SurfaceBindingLayoutTest() {
    }

    public static void main(String[] args) {
        slotsFollowSourceOrderAndStopOnFailure();
        preservesGradientAndYAnchorRecipes();
        canonicalizesNoiseStorageWithoutSkippingBindingEvents();
        delegatesBiomePredicatesUntilSpecializationIsProven();
        classifiesReferenceAndDirectBiomeHolders();
        doesNotSnapshotContextAcrossOpaqueBarrier();
    }

    private static void slotsFollowSourceOrderAndStopOnFailure() {
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
                bindings.slots().stream().map(SurfaceBindingLayout.Slot::kind).toList(),
                "binding slot source order"
        );
        assertStopsAtFirstFailure(bindings, expected);
    }

    private static void assertStopsAtFirstFailure(
            SurfaceBindingLayout bindings,
            List<SurfaceBindingLayout.Kind> expected
    ) {
        List<SurfaceBindingLayout.Kind> trace = new ArrayList<>();
        try {
            bindings.bindForTest(fakeContext(), (slot, raw, context) -> {
                trace.add(slot.kind());
                if (trace.size() == 3) {
                    throw new TestException();
                }
                return slot.source();
            });
            throw new AssertionError("Expected binding failure");
        } catch (TestException expectedFailure) {
            assertEquals(expected.subList(0, 3), trace, "binding must stop at first failure");
        }
    }

    private static void preservesGradientAndYAnchorRecipes() {
        VerticalAnchor lower = new TestAnchor(-8);
        VerticalAnchor upper = new TestAnchor(32);
        VerticalAnchor lazy = new TestAnchor(80);
        SurfaceRules.RuleSource source = sequence(List.of(
                test(gradient(lower, upper), SurfaceRules.bandlands()),
                test(yAbove(lazy), SurfaceRules.bandlands())
        ));
        List<SurfaceBindingLayout.Slot> slots = lower(source).bindings().slots();
        assertEquals(
                List.of(
                        SurfaceBindingLayout.Kind.RESOLVED_ANCHOR,
                        SurfaceBindingLayout.Kind.RESOLVED_ANCHOR,
                        SurfaceBindingLayout.Kind.RANDOM_FACTORY,
                        SurfaceBindingLayout.Kind.Y_ANCHOR
                ),
                slots.stream().map(SurfaceBindingLayout.Slot::kind).toList(),
                "gradient eager and Y lazy binding order"
        );
        assertSame(lazy, slots.get(3).source(), "Y anchor must stay unresolved in the layout");
    }

    public static void verifyBuiltInYAnchorRecipe() {
        VerticalAnchor builtIn = VerticalAnchor.absolute(96);
        SurfaceScalarLayout layout = lower(
                test(yAbove(builtIn), SurfaceRules.bandlands())
        );
        assertEquals(
                List.of(),
                layout.bindings().slots(),
                "absolute Y anchor must not need a binding field"
        );
        SurfaceRulePlan.Test root = (SurfaceRulePlan.Test) layout.plan().root();
        SurfaceScalarLayout.ConditionLayout condition = layout.condition(root.condition());
        assertEquals(96, condition.cacheIndex(), "absolute Y anchor constant");
        if (!condition.resolvedAnchor()) {
            throw new AssertionError("absolute Y anchor was not constant-lowered");
        }
    }

    private static void canonicalizesNoiseStorageWithoutSkippingBindingEvents() {
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
                bindings.slots().stream().map(SurfaceBindingLayout.Slot::kind).toList(),
                "noise binding events"
        );
        assertEquals(
                List.of(SurfaceBindingLayout.Kind.NOISE, SurfaceBindingLayout.Kind.RULE),
                bindings.storedSlots().stream().map(SurfaceBindingLayout.Slot::kind).toList(),
                "canonical generated storage"
        );
        assertEquals(1, layout.noiseSamples().size(), "canonical noise samples");
        assertEquals(2, layout.noiseOccurrences(), "canonical noise predicates");
        assertEquals(
                2,
                layout.noiseSamples().getFirst().ranges().size(),
                "ranges sharing one noise sample"
        );
        List<SurfaceBindingLayout.Kind> trace = new ArrayList<>();
        Object[] values = bindings.bindForTest(fakeContext(), (slot, raw, context) -> {
            trace.add(slot.kind());
            return "event-" + trace.size();
        });
        assertEquals(4, trace.size(), "all binding events must execute");
        assertEquals(2, values.length, "only stored bindings enter the constructor array");
        assertEquals("event-1", values[0], "first noise result must back canonical storage");
        assertEquals("event-4", values[1], "later stored slot index");
    }

    private static void classifiesReferenceAndDirectBiomeHolders() {
        SurfaceBiomeBehaviorTable table = new SurfaceBiomeBehaviorTable(
                List.of(
                        new SurfaceScalarLayout.BiomeValue(0, Set.of(Biomes.PLAINS)),
                        new SurfaceScalarLayout.BiomeValue(
                                1, Set.of(Biomes.PLAINS, Biomes.DESERT)
                        )
                ),
                List.of()
        );
        HolderOwner<Biome> owner = new HolderOwner<>() {
        };
        assertBehavior(3L, table.behavior(reference(owner, Biomes.PLAINS)), "plains mask");
        assertBehavior(2L, table.behavior(reference(owner, Biomes.DESERT)), "desert mask");
        assertBehavior(0L, table.behavior(reference(owner, Biomes.FOREST)), "unlisted mask");
        assertBehavior(0L, table.behavior(Holder.direct(null)), "direct holder mask");
        assertEquals(0L, table.behavior(customHolder()), "custom holder fallback marker");
    }

    private static void delegatesBiomePredicatesUntilSpecializationIsProven() {
        SurfaceScalarLayout layout = lower(test(
                biome(List.of(Biomes.PLAINS)),
                SurfaceRules.bandlands()
        ));
        assertEquals(
                List.of(SurfaceBindingLayout.Kind.CONDITION),
                layout.bindings().storedSlots().stream()
                        .map(SurfaceBindingLayout.Slot::kind)
                        .toList(),
                "biome condition binding"
        );
        assertEquals(List.of(), layout.biomeValues(), "biome behavior specialization");
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome> customHolder() {
        return (Holder<Biome>) Proxy.newProxyInstance(
                SurfaceBindingLayoutTest.class.getClassLoader(),
                new Class<?>[]{Holder.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType())
        );
    }

    private static void assertBehavior(long expected, long actual, String message) {
        if (actual >= 0L) {
            throw new AssertionError(message + ": missing supported marker");
        }
        assertEquals(expected, actual & Long.MAX_VALUE, message);
    }

    private static Holder.Reference<Biome> reference(
            HolderOwner<Biome> owner,
            ResourceKey<Biome> key
    ) {
        return Holder.Reference.createStandAlone(owner, key);
    }

    private static void doesNotSnapshotContextAcrossOpaqueBarrier() {
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

    private static SurfaceMethodLocals rootLocals(SurfaceScalarLayout layout) {
        SurfaceRegionPlan regions = SurfaceRegionPlan.create(
                layout.plan().root(), SurfaceScalarTarget.BUILD_POINT
        );
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
                    case "byepregen$randomName" -> ResourceLocation.parse("byepregen:test");
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

    private static SurfaceRules.ConditionSource biome(List<ResourceKey<Biome>> biomes) {
        return conditionProxy(
                SurfaceRuleSourceAccess.BiomeCondition.class,
                (method, arguments) -> switch (method) {
                    case "byepregen$biomes" -> biomes;
                    case "byepregen$biomeNameTest" ->
                            (java.util.function.Predicate<ResourceKey<Biome>>) biomes::contains;
                    default -> null;
                }
        );
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

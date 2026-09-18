package com.moepus.byepregen.worldgen.surface;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.BiomeCondition;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.NoiseThresholdCondition;
import net.minecraft.world.level.levelgen.material.condition.StoneDepthCondition;
import net.minecraft.world.level.levelgen.material.condition.VerticalGradientCondition;
import net.minecraft.world.level.levelgen.material.condition.WaterCondition;
import net.minecraft.world.level.levelgen.material.condition.YCondition;
import net.minecraft.world.level.levelgen.material.rule.BandlandsRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

public final class SurfaceBindingLayoutTest {
    private static final HolderOwner<Biome> BIOME_OWNER = new HolderOwner<>() {};

    private SurfaceBindingLayoutTest() {
    }

    @Test
    void eventsFollowSourceOrderAndStopOnFailure() {
        MaterialRule source = sequence(List.of(
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
            bindings.bindForTest(null, (event, context) -> {
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
        MaterialRule source = sequence(List.of(
                test(gradient(lower, upper), BandlandsRule.INSTANCE),
                test(yAbove(lazy), BandlandsRule.INSTANCE)
        ));
        List<SurfaceBindingLayout.BindEvent> events = lower(source).bindings().events();
        // 26.3: the context resolves every anchor while binding, so the lazy Y anchor is another
        // resolved-anchor slot instead of a stored VerticalAnchor.
        assertEquals(
                List.of(
                        SurfaceBindingLayout.Kind.RESOLVED_ANCHOR,
                        SurfaceBindingLayout.Kind.RESOLVED_ANCHOR,
                        SurfaceBindingLayout.Kind.RANDOM_FACTORY,
                        SurfaceBindingLayout.Kind.RESOLVED_ANCHOR
                ),
                events.stream().map(SurfaceBindingLayout.BindEvent::kind).toList(),
                "gradient and Y anchor binding order"
        );
        assertSame(lazy, events.get(3).source(), "Y anchor must stay unresolved in the layout");
    }

    @Test
    void canonicalizesNoiseStorageWithoutSkippingBindingEvents() {
        SurfaceScalarLayout layout = lower(sequence(List.of(
                test(noise(Noises.SURFACE, -1.0D, 0.0D, false), BandlandsRule.INSTANCE),
                test(noise(Noises.SURFACE, 0.0D, 1.0D, false), BandlandsRule.INSTANCE),
                test(noise(Noises.SURFACE, -1.0D, 0.0D, false), BandlandsRule.INSTANCE),
                test(noise(Noises.SURFACE, -1.0D, 0.0D, true), BandlandsRule.INSTANCE),
                unknownRule()
        )));
        SurfaceBindingLayout bindings = layout.bindings();
        // 26.3: one bound DoubleSupplier per distinct (noise, thresholds, is3d); the repeated spec
        // reuses its slot instead of publishing a discarded event.
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
                List.of(
                        SurfaceBindingLayout.Kind.NOISE,
                        SurfaceBindingLayout.Kind.NOISE,
                        SurfaceBindingLayout.Kind.NOISE,
                        SurfaceBindingLayout.Kind.RULE
                ),
                bindings.storedSlots().stream().map(SurfaceBindingLayout.Slot::kind).toList(),
                "generated storage"
        );
        SurfaceBindingLayout.Slot trailing = (SurfaceBindingLayout.Slot)
                bindings.events().get(3);
        assertEquals(3, trailing.id().value(), "stored IDs must stay dense");
        assertEquals(4, layout.noiseOccurrences(), "noise conditions lowered");
        assertEquals(3, layout.noiseSamples(), "distinct bound noise samplers");
        List<SurfaceBindingLayout.Kind> trace = new ArrayList<>();
        Object[] values = bindings.bindForTest(null, (event, context) -> {
            trace.add(event.kind());
            return "event-" + trace.size();
        });
        assertEquals(4, trace.size(), "all binding events must execute");
        assertEquals(4, values.length, "only stored bindings enter the constructor array");
        assertEquals("event-1", values[0], "first noise result must back canonical storage");
        assertEquals("event-4", values[3], "later stored slot index");
    }

    @Test
    void delegatesBiomePredicates() {
        SurfaceScalarLayout layout = lower(test(
                biome(),
                BandlandsRule.INSTANCE
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
                test(water(), BandlandsRule.INSTANCE),
                test(water(), BandlandsRule.INSTANCE)
        )));
        SurfaceScalarLayout blocked = lower(sequence(List.of(
                test(water(), unknownRule()),
                test(water(), BandlandsRule.INSTANCE)
        )));
        SurfaceScalarLayout trailingBarrier = lower(sequence(List.of(
                test(water(), BandlandsRule.INSTANCE),
                test(water(), BandlandsRule.INSTANCE),
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
    void snapshotsExactStoneDepthBelow() {
        // 26.3: every CEILING check reads the exact stone depth, so repeating it is cacheable; 26.2
        // re-answered the adjacent form from the column instead.
        SurfaceConditionSpec.StoneDepth ceiling = new SurfaceConditionSpec.StoneDepth(
                0, false, 0, CaveSurface.CEILING
        );
        MaterialCondition condition = new StoneDepthCondition(
                ceiling.offset(), ceiling.addSurfaceDepth(),
                ceiling.secondaryDepthRange(), ceiling.surfaceType()
        );
        SurfaceScalarLayout layout = lower(sequence(List.of(
                test(condition, BandlandsRule.INSTANCE),
                test(condition, BandlandsRule.INSTANCE)
        )));
        if (!rootLocals(layout).caches(SurfaceRuntimeAbi.STONE_BELOW)) {
            throw new AssertionError("repeated ceiling checks should snapshot the exact depth below");
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

    private static SurfaceScalarLayout lower(MaterialRule source) {
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

    private static MaterialRule sequence(List<MaterialRule> rules) {
        return new SequenceRule(rules);
    }

    private static MaterialRule test(MaterialCondition condition, MaterialRule followup) {
        return new ConditionRule(condition, followup);
    }

    private static MaterialCondition gradient(VerticalAnchor lower, VerticalAnchor upper) {
        return new VerticalGradientCondition(
                Identifier.parse("byepregen:test"), lower, upper
        );
    }

    private static MaterialCondition yAbove(VerticalAnchor anchor) {
        return new YCondition(anchor, 0, false);
    }

    private static MaterialCondition noise(
            ResourceKey<NormalNoise> key,
            double minimum,
            double maximum,
            boolean is3d
    ) {
        return new NoiseThresholdCondition(key, minimum, maximum, is3d);
    }

    private static MaterialCondition biome() {
        return new BiomeCondition(HolderSet.direct(
                Holder.Reference.createStandAlone(BIOME_OWNER, Biomes.PLAINS)
        ));
    }

    private static MaterialCondition water() {
        return new WaterCondition(0, 0, false);
    }

    private static MaterialRule unknownRule() {
        return new MaterialRule() {
            @Override public RuleEvaluator compile(MaterialRuleContext context) {
                throw new UnsupportedOperationException();
            }

            @Override public MapCodec<? extends MaterialRule> codec() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static MaterialCondition unknownCondition() {
        return new MaterialCondition() {
            @Override public ConditionEvaluator compile(MaterialRuleContext context) {
                throw new UnsupportedOperationException();
            }

            @Override public MapCodec<? extends MaterialCondition> codec() {
                throw new UnsupportedOperationException();
            }
        };
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

    private record TestAnchor(int y) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return this.y;
        }
    }

    private static final class TestException extends RuntimeException {
    }
}

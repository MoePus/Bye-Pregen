package com.moepus.byepregen.worldgen.surface;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.BiomeCondition;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.HoleCondition;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.NotCondition;
import net.minecraft.world.level.levelgen.material.rule.BandlandsRule;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 26.3: the 26.2 test drove the analyzer through dynamic proxies implementing the accessor mixin
 * interfaces. The transplanted analyzer reads the vanilla records directly, so the fixtures are the
 * records themselves and only the "unknown shape" cases need local implementations.
 */
public final class SurfaceRuleAnalyzerTest {
    private static final HolderOwner<Biome> BIOME_OWNER = new HolderOwner<>() {};

    private SurfaceRuleAnalyzerTest() {
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preservesSourceOrderAndOpaqueSources() {
        MaterialCondition biome = blankCondition();
        MaterialRule unknown = unknownRule();
        MaterialRule source = new SequenceRule(List.of(
                new ConditionRule(new NotCondition(biome), BandlandsRule.INSTANCE),
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
        requireType(test.followup(), SurfaceRulePlan.Bandlands.class);
        SurfaceRulePlan.OpaqueRule opaque = requireType(
                root.rules().get(1), SurfaceRulePlan.OpaqueRule.class
        );
        assertSame(biome, target.source(), "delegated condition source");
        assertSame(unknown, opaque.source(), "opaque rule source");
    }

    @Test
    void keepsUnknownSourcesAsOpaqueBarriers() {
        MaterialCondition condition = blankCondition();
        MaterialRule source = new SequenceRule(List.of(
                new ConditionRule(condition, BandlandsRule.INSTANCE),
                new ConditionRule(condition, BandlandsRule.INSTANCE)
        ));

        SurfaceRulePlan.Sequence root = requireType(
                analyze(source).root(), SurfaceRulePlan.Sequence.class
        );
        SurfaceRulePlan.OpaqueCondition first = opaqueCondition(root.rules().get(0));
        SurfaceRulePlan.OpaqueCondition second = opaqueCondition(root.rules().get(1));
        assertNotEquals(first.value().id(), second.value().id(), "opaque values must be unique");
        if (analyze(unknownRule()).root() instanceof SurfaceRulePlan.OpaqueRule) {
            return;
        }
        throw new AssertionError("unknown root rule was not preserved as opaque");
    }

    @Test
    void recognizesSingletonIdentityWithoutMixins() {
        MaterialRule source = new ConditionRule(HoleCondition.INSTANCE, BandlandsRule.INSTANCE);
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

    @Test
    void rejectsUnregisteredShapeImplementations() {
        MaterialRule impostor = new MaterialRule() {
            @Override public RuleEvaluator compile(MaterialRuleContext context) {
                throw new UnsupportedOperationException();
            }

            @Override public MapCodec<? extends MaterialRule> codec() {
                throw new UnsupportedOperationException();
            }
        };
        requireType(SurfaceRuleAnalyzer.analyze(impostor).root(), SurfaceRulePlan.OpaqueRule.class);
        requireType(
                SurfaceRuleAnalyzer.analyze(
                        new SequenceRule(List.of(BandlandsRule.INSTANCE))
                ).root(),
                SurfaceRulePlan.Sequence.class
        );
    }

    @Test
    void unwrapsHolderWrappedSources() {
        MaterialRule wrapped = new MaterialRule.HolderHolder(Holder.Reference.createIntrusive(
                new HolderOwner<MaterialRule>() { },
                new BlockRule(Blocks.STONE.defaultBlockState())
        ));
        requireType(analyze(wrapped).root(), SurfaceRulePlan.State.class);

        MaterialCondition condition = new MaterialCondition.HolderHolder(
                Holder.Reference.createIntrusive(
                        new HolderOwner<MaterialCondition>() { },
                        new NotCondition(HoleCondition.INSTANCE)
                )
        );
        SurfaceRulePlan.Test test = requireType(
                analyze(new ConditionRule(condition, BandlandsRule.INSTANCE)).root(),
                SurfaceRulePlan.Test.class
        );
        requireType(test.condition(), SurfaceRulePlan.NotCondition.class);
    }

    @Test
    void canonicalizesSingletonValues() {
        SurfaceRulePlan.Sequence root = requireType(analyze(new SequenceRule(List.of(
                new ConditionRule(HoleCondition.INSTANCE, BandlandsRule.INSTANCE),
                new ConditionRule(HoleCondition.INSTANCE, BandlandsRule.INSTANCE)
        ))).root(), SurfaceRulePlan.Sequence.class);
        SurfaceRulePlan.KnownCondition first = condition(root.rules().get(0));
        SurfaceRulePlan.KnownCondition second = condition(root.rules().get(1));
        assertSame(first.value(), second.value(), "singleton values must canonicalize");
    }

    @Test
    void fallsBackAtAnalysisLimits() {
        MaterialRule source = new SequenceRule(List.of(unknownRule(), unknownRule()));
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

    @Test
    void provesBoundedStoneDepthConservatively() {
        // 26.3: no CEILING check may appear in a plan whose stone-depth column scan is shortened,
        // because MaterialRuleContext cannot re-answer those checks from the column.
        assertEquals(
                false,
                planFor(stoneDepth(CaveSurface.CEILING, 0, false, 0)).boundedStoneDepthBelow(),
                "ceiling check keeps the scan"
        );
        assertEquals(
                false,
                planFor(stoneDepth(CaveSurface.CEILING, 0, true, 0)).boundedStoneDepthBelow(),
                "dynamic ceiling keeps the scan"
        );
        assertEquals(
                false,
                planFor(stoneDepth(CaveSurface.CEILING, 1, false, 0)).boundedStoneDepthBelow(),
                "second block below keeps the scan"
        );
        assertEquals(
                false,
                planFor(stoneDepth(CaveSurface.CEILING, -1, false, 0)).boundedStoneDepthBelow(),
                "fixed false ceiling keeps the scan"
        );
        assertEquals(
                true,
                planFor(stoneDepth(CaveSurface.FLOOR, 0, false, 0)).boundedStoneDepthBelow(),
                "floor checks read the depth above"
        );
        assertEquals(
                true,
                planFor(stoneDepth(CaveSurface.FLOOR, 0, true, 1)).boundedStoneDepthBelow(),
                "dynamic floor checks read the depth above"
        );

        SurfaceRulePlan.OpaqueCondition biome = new SurfaceRulePlan.OpaqueCondition(
                biomeCondition(),
                new SurfaceRulePlan.ConditionValue(
                        new SurfaceRulePlan.ValueId(0), new SurfaceConditionSpec.Opaque("biome")
                )
        );
        assertEquals(true, planFor(biome).boundedStoneDepthBelow(), "biome delegate");
        SurfaceRulePlan.OpaqueCondition unknown = new SurfaceRulePlan.OpaqueCondition(
                new Object(),
                new SurfaceRulePlan.ConditionValue(
                        new SurfaceRulePlan.ValueId(0), new SurfaceConditionSpec.Opaque("unknown")
                )
        );
        assertEquals(false, planFor(unknown).boundedStoneDepthBelow(), "opaque condition");
        assertEquals(
                false,
                planFor(new SurfaceRulePlan.OpaqueRule(new Object())).boundedStoneDepthBelow(),
                "opaque rule"
        );
    }

    private static SurfaceConditionSpec.StoneDepth stoneDepth(
            CaveSurface surface,
            int offset,
            boolean addSurfaceDepth,
            int secondaryDepthRange
    ) {
        return new SurfaceConditionSpec.StoneDepth(
                offset, addSurfaceDepth, secondaryDepthRange, surface
        );
    }

    private static SurfaceRulePlan planFor(SurfaceConditionSpec spec) {
        SurfaceRulePlan.KnownCondition condition = new SurfaceRulePlan.KnownCondition(
                new Object(),
                new SurfaceRulePlan.ConditionValue(new SurfaceRulePlan.ValueId(0), spec)
        );
        return planFor(condition);
    }

    private static SurfaceRulePlan planFor(SurfaceRulePlan.Condition condition) {
        return planFor(new SurfaceRulePlan.Test(condition, new SurfaceRulePlan.Bandlands()));
    }

    private static SurfaceRulePlan planFor(SurfaceRulePlan.Rule rule) {
        return new SurfaceRulePlan(rule);
    }

    private static SurfaceRulePlan analyze(MaterialRule source) {
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
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertNotEquals(Object first, Object second, String message) {
        if (java.util.Objects.equals(first, second)) {
            throw new AssertionError(message + ": both were " + first);
        }
    }

    private static MaterialCondition blankCondition() {
        return new MaterialCondition() {
            @Override public ConditionEvaluator compile(MaterialRuleContext context) {
                throw new UnsupportedOperationException();
            }

            @Override public MapCodec<? extends MaterialCondition> codec() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static MaterialCondition biomeCondition() {
        return new BiomeCondition(HolderSet.direct(
                Holder.Reference.createStandAlone(BIOME_OWNER, Biomes.PLAINS)
        ));
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
}

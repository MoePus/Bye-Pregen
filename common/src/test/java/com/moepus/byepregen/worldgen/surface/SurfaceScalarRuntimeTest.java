package com.moepus.byepregen.worldgen.surface;

import com.mojang.serialization.MapCodec;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.AbovePreliminarySurfaceCondition;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.HoleCondition;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.NotCondition;
import net.minecraft.world.level.levelgen.material.condition.SteepCondition;
import net.minecraft.world.level.levelgen.material.condition.StoneDepthCondition;
import net.minecraft.world.level.levelgen.material.condition.WaterCondition;
import net.minecraft.world.level.levelgen.material.condition.YCondition;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

/**
 * Runs the generated rule against vanilla's own bound rule on a fabricated
 * {@link MaterialRuleContext}, so the emitted bytecode is differentially checked without a world.
 *
 * <p>The context is allocated without its constructor and then filled in by reflection, because
 * every condition the generator inlines reads nothing but these fields. Epoch counters are bumped
 * per round so vanilla's lazy conditions recompute exactly like the generated rule does.</p>
 */
public final class SurfaceScalarRuntimeTest {
    private static final Unsafe UNSAFE = unsafe();

    private SurfaceScalarRuntimeTest() {
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void matchesVanillaForInlinedConditions() throws Exception {
        MaterialRuleContext context = newContext();
        for (MaterialRule rule : rules()) {
            RuleEvaluator reference = rule.compile(context);
            SurfaceTemplateCache cache = new SurfaceTemplateCache();
            Object bound = cache.bind(rule, context);
            if (!bound.getClass().isHidden()) {
                throw new AssertionError("Rule was not compiled: " + rule);
            }
            RuleEvaluator compiled = (RuleEvaluator) bound;
            for (Round round : rounds()) {
                round.apply(context);
                BlockState expected = reference.tryApply(round.x(), round.y(), round.z());
                BlockState actual = compiled.tryApply(round.x(), round.y(), round.z());
                if (expected != actual) {
                    throw new AssertionError(
                            "Material mismatch for " + rule + " at " + round
                                    + ": vanilla=" + expected + " generated=" + actual
                    );
                }
            }
        }
    }

    @Test
    void matchesVanillaAcrossOutlinedRegions() throws Exception {
        MaterialRuleContext context = newContext();
        MaterialRule rule = partitionedTree();
        RuleEvaluator reference = rule.compile(context);
        RuleEvaluator compiled = (RuleEvaluator) new SurfaceTemplateCache().bind(rule, context);
        if (SurfaceScalarMetrics.snapshot().latestRegions() == 0) {
            throw new AssertionError("tree did not produce any outlined region");
        }
        for (Round round : rounds()) {
            round.apply(context);
            BlockState expected = reference.tryApply(round.x(), round.y(), round.z());
            BlockState actual = compiled.tryApply(round.x(), round.y(), round.z());
            if (expected != actual) {
                throw new AssertionError("Region mismatch at " + round + ": "
                        + expected + " != " + actual);
            }
        }
    }

    @Test
    void marksOnlyScanBoundedPlans() {
        SurfaceRulePlan bounded = SurfaceRuleAnalyzer.analyze(new ConditionRule(
                new WaterCondition(0, 0, false), new BlockRule(dirt())
        ));
        if (!bounded.boundedStoneDepthBelow()) {
            throw new AssertionError("water-only plan must bound the stone depth scan");
        }
        SurfaceRulePlan ceiling = SurfaceRuleAnalyzer.analyze(new ConditionRule(
                new StoneDepthCondition(0, false, 0, CaveSurface.CEILING), new BlockRule(dirt())
        ));
        if (ceiling.boundedStoneDepthBelow()) {
            throw new AssertionError("ceiling check must keep the exact stone depth scan");
        }
    }

    private static BlockState dirt() {
        return Blocks.DIRT.defaultBlockState();
    }

    private static BlockState stone() {
        return Blocks.STONE.defaultBlockState();
    }

    private static BlockState gravel() {
        return Blocks.GRAVEL.defaultBlockState();
    }

    private static List<MaterialRule> rules() {
        List<MaterialRule> rules = new ArrayList<>();
        rules.add(new BlockRule(stone()));
        rules.add(new SequenceRule(List.of(
                new ConditionRule(HoleCondition.INSTANCE, new BlockRule(dirt())),
                new BlockRule(gravel())
        )));
        rules.add(new ConditionRule(HoleCondition.INSTANCE, new BlockRule(dirt())));
        rules.add(new ConditionRule(
                new NotCondition(HoleCondition.INSTANCE), new BlockRule(dirt())
        ));
        rules.add(new ConditionRule(
                new NotCondition(new NotCondition(HoleCondition.INSTANCE)), new BlockRule(gravel())
        ));
        rules.add(new ConditionRule(
                AbovePreliminarySurfaceCondition.INSTANCE, new BlockRule(dirt())
        ));
        rules.add(new ConditionRule(SteepCondition.INSTANCE, new BlockRule(gravel())));
        rules.add(new ConditionRule(delegateCondition(), new BlockRule(dirt())));
        rules.add(new ConditionRule(HoleCondition.INSTANCE, delegateRule()));
        rules.add(new SequenceRule(List.of(delegateRule(), new BlockRule(stone()))));
        for (CaveSurface surface : CaveSurface.values()) {
            rules.add(new ConditionRule(
                    new StoneDepthCondition(-1, false, 0, surface), new BlockRule(dirt())
            ));
            rules.add(new ConditionRule(
                    new StoneDepthCondition(0, true, 0, surface), new BlockRule(gravel())
            ));
            rules.add(new ConditionRule(
                    new StoneDepthCondition(2, false, 6, surface), new BlockRule(stone())
            ));
        }
        rules.add(new ConditionRule(new WaterCondition(-1, 0, false), new BlockRule(dirt())));
        rules.add(new ConditionRule(new WaterCondition(0, 1, true), new BlockRule(gravel())));
        rules.add(new ConditionRule(new WaterCondition(2, -1, true), new BlockRule(stone())));
        rules.add(new ConditionRule(new YCondition(VerticalAnchor.absolute(64), 0, false),
                new BlockRule(dirt())));
        rules.add(new ConditionRule(new YCondition(VerticalAnchor.absolute(0), -1, true),
                new BlockRule(gravel())));
        rules.add(new ConditionRule(new YCondition(VerticalAnchor.absolute(70), 3, true),
                new BlockRule(stone())));
        rules.add(new ConditionRule(
                new NotCondition(new WaterCondition(1, 2, false)), new BlockRule(dirt())
        ));
        rules.add(partitionedTree());
        return rules;
    }

    /** A root sequence whose largest child is big enough to be outlined and partitioned. */
    private static MaterialRule partitionedTree() {
        List<MaterialRule> children = new ArrayList<>();
        for (int index = 0; index < 48; index++) {
            MaterialCondition condition = switch (index % 4) {
                case 0 -> new WaterCondition(index % 3 - 1, index % 3, index % 2 == 0);
                case 1 -> new YCondition(
                        VerticalAnchor.absolute(index * 4), index % 2, index % 3 == 0
                );
                case 2 -> new StoneDepthCondition(
                        index % 2, index % 2 == 0, 0, CaveSurface.FLOOR
                );
                default -> AbovePreliminarySurfaceCondition.INSTANCE;
            };
            children.add(new ConditionRule(
                    condition, new BlockRule(index % 5 == 0 ? stone() : dirt())
            ));
        }
        return new SequenceRule(List.of(
                new SequenceRule(children),
                new BlockRule(gravel())
        ));
    }

    private static MaterialCondition delegateCondition() {
        return new MaterialCondition() {
            @Override public ConditionEvaluator compile(MaterialRuleContext context) {
                return () -> context.surfaceDepth() > 2;
            }

            @Override public MapCodec<? extends MaterialCondition> codec() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static MaterialRule delegateRule() {
        return new MaterialRule() {
            @Override public RuleEvaluator compile(MaterialRuleContext context) {
                return (x, y, z) -> y <= 60 ? gravel() : null;
            }

            @Override public MapCodec<? extends MaterialRule> codec() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * The live pipeline only ever reports a stone depth of at least one, which is what makes the
     * generator's "fixed limit at or below zero is never true" fold exact.
     */
    private static List<Round> rounds() {
        List<Round> rounds = new ArrayList<>();
        for (int blockY : new int[]{-64, -8, 4, 63, 64, 100, 320}) {
            for (int surfaceDepth : new int[]{-2, 0, 1, 5}) {
                rounds.add(new Round(3, blockY, -5, surfaceDepth, 1, 1,
                        62, 64, 0.5D, 0, 0));
                rounds.add(new Round(3, blockY, -5, surfaceDepth, 9, 4,
                        63, 0, -1.0D, -8, 8));
                rounds.add(new Round(3, blockY, -5, surfaceDepth, 2, 2,
                        Integer.MIN_VALUE, 12, 1.0D, 4, -4));
            }
        }
        return rounds;
    }

    private record Round(
            int x,
            int y,
            int z,
            int surfaceDepth,
            int stoneDepthAbove,
            int stoneDepthBelow,
            int waterHeight,
            int minSurfaceLevel,
            double surfaceSecondary,
            int surfaceGradientX,
            int surfaceGradientZ
    ) {
        private void apply(MaterialRuleContext context) throws Exception {
            long epoch = field("lastUpdateXZ").getLong(context) + 1;
            setLong(context, "lastUpdateXZ", epoch);
            setLong(context, "lastUpdateY", epoch);
            // Keep the derived-value caches aligned with the epoch so no null system is consulted.
            setLong(context, "lastSurfaceDepth2Update", epoch);
            setLong(context, "lastMinSurfaceLevelUpdate", epoch);
            setInt(context, "blockX", this.x);
            setInt(context, "blockY", this.y);
            setInt(context, "blockZ", this.z);
            setInt(context, "surfaceDepth", this.surfaceDepth);
            setInt(context, "stoneDepthAbove", this.stoneDepthAbove);
            setInt(context, "stoneDepthBelow", this.stoneDepthBelow);
            setInt(context, "waterHeight", this.waterHeight);
            setInt(context, "minSurfaceLevel", this.minSurfaceLevel);
            setInt(context, "surfaceGradientX", this.surfaceGradientX);
            setInt(context, "surfaceGradientZ", this.surfaceGradientZ);
            setDouble(context, "surfaceSecondary", this.surfaceSecondary);
        }
    }

    private static MaterialRuleContext newContext() throws InstantiationException {
        return (MaterialRuleContext) UNSAFE.allocateInstance(MaterialRuleContext.class);
    }

    private static void setInt(MaterialRuleContext context, String name, int value)
            throws Exception {
        field(name).setInt(context, value);
    }

    private static void setLong(MaterialRuleContext context, String name, long value)
            throws Exception {
        field(name).setLong(context, value);
    }

    private static void setDouble(MaterialRuleContext context, String name, double value)
            throws Exception {
        field(name).setDouble(context, value);
    }

    private static Field field(String name) throws Exception {
        Field field = MaterialRuleContext.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}

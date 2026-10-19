package com.moepus.byepregen.harness.terrablender;

import com.moepus.byepregen.worldgen.surface.TerraBlenderCompat;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

/** Checks the exact boundaries of TerraBlender's native per-context rule identity memoization. */
final class TerraBlenderSharedRules {
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    private TerraBlenderSharedRules() { }

    static int verify(MaterialRuleContext referenceContext, MaterialRuleContext optimizedContext,
            MaterialRuleContext reboundContext) {
        for (Shape shape : Shape.values()) {
            AtomicInteger referenceBinds = new AtomicInteger(), optimizedBinds = new AtomicInteger();
            MaterialRule reference = graph(shape, referenceBinds);
            MaterialRule optimized = TerraBlenderCompat.wrap(graph(shape, optimizedBinds));
            checkResult(shape, reference.compile(referenceContext));
            checkResult(shape, optimized.compile(optimizedContext));
            require(referenceBinds.get() == shape.expectedBinds, "Incorrect native fixture: " + shape);
            require(optimizedBinds.get() == referenceBinds.get(), "Shared rule binding changed: " + shape);
            checkResult(shape, optimized.compile(reboundContext));
            require(optimizedBinds.get() == shape.expectedBinds * 2, "Evaluator leaked across contexts: " + shape);
        }
        return Shape.values().length;
    }

    private static MaterialRule graph(Shape shape, AtomicInteger binds) {
        MaterialRule shared = new StatefulRule(binds);
        MaterialRule base = new BlockRule(STONE);
        MaterialRule conditional = new ConditionRule(new AlwaysTrue(), shared);
        MaterialRule branch = switch (shape) {
            case CROSS_BRANCH -> new SequenceRule(List.of(shared));
            case REPEATED_SEQUENCE -> new SequenceRule(List.of(shared, shared));
            case HOLDER_ALIASES -> new SequenceRule(List.of(reference(shared), reference(shared)));
            case SHARED_CONDITIONAL -> new SequenceRule(List.of(conditional, conditional));
            case SEPARATE_CONDITIONALS -> new SequenceRule(List.of(conditional,
                    new ConditionRule(new AlwaysTrue(), shared)));
        };
        if (shape == Shape.CROSS_BRANCH) base = new SequenceRule(List.of(shared, base));
        return new NamespacedSurfaceRuleSource(base, Map.of("example", branch));
    }

    private static MaterialRule reference(MaterialRule source) {
        return new MaterialRule.HolderHolder(Holder.direct(source));
    }

    private static void checkResult(Shape shape, RuleEvaluator evaluator) {
        BlockState expected = shape == Shape.SEPARATE_CONDITIONALS ? STONE : DIRT;
        require(evaluator.tryApply(0, 64, 0) == expected, "Shared evaluator state changed: " + shape);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private enum Shape {
        CROSS_BRANCH(1), REPEATED_SEQUENCE(1), HOLDER_ALIASES(1), SHARED_CONDITIONAL(1), SEPARATE_CONDITIONALS(2);

        private final int expectedBinds;

        Shape(int expectedBinds) {
            this.expectedBinds = expectedBinds;
        }
    }

    private record StatefulRule(AtomicInteger binds) implements MaterialRule {
        @Override public RuleEvaluator compile(MaterialRuleContext context) {
            this.binds.incrementAndGet();
            AtomicInteger calls = new AtomicInteger();
            return (x, y, z) -> calls.incrementAndGet() == 1 ? null : DIRT;
        }

        @Override public MapCodec<? extends MaterialRule> codec() {
            throw new UnsupportedOperationException("test-only rule");
        }
    }

    private record AlwaysTrue() implements MaterialCondition {
        @Override public ConditionEvaluator compile(MaterialRuleContext context) {
            return () -> true;
        }

        @Override public MapCodec<? extends MaterialCondition> codec() {
            throw new UnsupportedOperationException("test-only condition");
        }
    }
}

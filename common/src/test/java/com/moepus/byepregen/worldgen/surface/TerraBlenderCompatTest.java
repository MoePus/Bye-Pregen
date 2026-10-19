package com.moepus.byepregen.worldgen.surface;

import com.mojang.serialization.MapCodec;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

final class TerraBlenderCompatTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reusesTemplatesButRebindsEvaluatorsForEachContext() throws Exception {
        MaterialRuleContext a = context(), b = context();
        AtomicInteger binds = new AtomicInteger();
        MaterialRule leaf = rule(context -> {
            binds.incrementAndGet();
            return (x, y, z) -> (context == a ? Blocks.DIRT : Blocks.STONE).defaultBlockState();
        });
        MaterialRule compiled = new TerraBlenderCompat.CompiledSource(rule(context ->
                TerraBlenderCompat.compileRule(leaf, context)));
        long before = SurfaceScalarMetrics.snapshot().compiled();
        RuleEvaluator first = compiled.compile(a), second = compiled.compile(b);
        assertTrue(first.getClass().isHidden());
        assertSame(Blocks.DIRT.defaultBlockState(), first.tryApply(0, 64, 0));
        assertSame(Blocks.STONE.defaultBlockState(), second.tryApply(0, 64, 0));
        assertEquals(2, binds.get());
        assertEquals(before + 1, SurfaceScalarMetrics.snapshot().compiled());
    }

    @Test
    void keysTemplatesByIdentityInsteadOfRecordEquality() throws Exception {
        MaterialRule left = new BlockRule(Blocks.DIRT.defaultBlockState());
        MaterialRule right = new BlockRule(Blocks.DIRT.defaultBlockState());
        assertEquals(left, right);
        MaterialRule compiled = new TerraBlenderCompat.CompiledSource(rule(context -> {
            TerraBlenderCompat.compileRule(left, context);
            return TerraBlenderCompat.compileRule(right, context);
        }));
        long before = SurfaceScalarMetrics.snapshot().compiled();
        compiled.compile(context());
        assertEquals(before + 2, SurfaceScalarMetrics.snapshot().compiled());
    }

    @Test
    void doesNotCachePerContextRulesCreatedByOpaqueDelegates() throws Exception {
        MaterialRule opaque = rule(context -> TerraBlenderCompat.compileRule(
                new BlockRule(Blocks.STONE.defaultBlockState()), context));
        MaterialRule compiled = new TerraBlenderCompat.CompiledSource(rule(context ->
                TerraBlenderCompat.compileRule(opaque, context)));
        long before = SurfaceScalarMetrics.snapshot().compiled();
        assertSame(Blocks.STONE.defaultBlockState(), compiled.compile(context()).tryApply(0, 64, 0));
        assertSame(Blocks.STONE.defaultBlockState(), compiled.compile(context()).tryApply(0, 64, 0));
        assertEquals(before + 1, SurfaceScalarMetrics.snapshot().compiled());
    }

    @Test
    void restoresParentScopeAndClearsItAfterFailure() throws Exception {
        MaterialRule leaf = new BlockRule(Blocks.DIRT.defaultBlockState());
        MaterialRule failing = new TerraBlenderCompat.CompiledSource(rule(context -> {
            TerraBlenderCompat.compileRule(leaf, context);
            throw new IllegalArgumentException("fixture failure");
        }));
        MaterialRule parent = new TerraBlenderCompat.CompiledSource(rule(context -> {
            TerraBlenderCompat.compileRule(leaf, context);
            assertThrows(IllegalArgumentException.class, () -> failing.compile(context));
            return TerraBlenderCompat.compileRule(leaf, context);
        }));
        MaterialRuleContext context = context();
        long before = SurfaceScalarMetrics.snapshot().compiled();
        parent.compile(context);
        assertEquals(before + 2, SurfaceScalarMetrics.snapshot().compiled());
        assertThrows(IllegalArgumentException.class, () -> failing.compile(context));
        long bindings = SurfaceScalarMetrics.snapshot().bindings();
        assertSame(Blocks.DIRT.defaultBlockState(), TerraBlenderCompat.compileRule(leaf, context).tryApply(0, 64, 0));
        assertEquals(bindings, SurfaceScalarMetrics.snapshot().bindings());
    }

    private static MaterialRule rule(Function<MaterialRuleContext, RuleEvaluator> compiler) {
        return new MaterialRule() {
            @Override public RuleEvaluator compile(MaterialRuleContext context) {
                return compiler.apply(context);
            }

            @Override public MapCodec<? extends MaterialRule> codec() {
                throw new UnsupportedOperationException("test-only rule");
            }
        };
    }

    private static MaterialRuleContext context() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (MaterialRuleContext) ((Unsafe) field.get(null)).allocateInstance(MaterialRuleContext.class);
    }
}

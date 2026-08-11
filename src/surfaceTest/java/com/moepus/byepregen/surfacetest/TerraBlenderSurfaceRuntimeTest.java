package com.moepus.byepregen.surfacetest;

import com.moepus.byepregen.worldgen.surface.SurfaceTemplateCache;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

final class TerraBlenderSurfaceRuntimeTest {
    private static final String NAMESPACED_RULE_CLASS =
            "terrablender.worldgen.surface.NamespacedSurfaceRuleSource$NamespacedRule";

    private TerraBlenderSurfaceRuntimeTest() {
    }

    static void run() throws Exception {
        Object context = SurfaceOpaqueRuntimeTest.createContext();
        AtomicReference<Holder<Biome>> biome = new AtomicReference<>();
        SurfaceOpaqueRuntimeTest.setField(context, "biome", (Supplier<Holder<Biome>>) biome::get);

        NullProbe nullProbe = new NullProbe();
        Map<String, SurfaceRules.RuleSource> sources = new LinkedHashMap<>();
        sources.put("minecraft", SurfaceRules.state(Blocks.GOLD_BLOCK.defaultBlockState()));
        sources.put("fallback", nullProbe.source());
        NamespacedSurfaceRuleSource source = new NamespacedSurfaceRuleSource(
                SurfaceRules.state(Blocks.DIAMOND_BLOCK.defaultBlockState()),
                sources
        );

        Object bound = new SurfaceTemplateCache().bind(source, context);
        assertDispatcherAndBranches(bound);

        biome.set(holder("minecraft"));
        assertSame(Blocks.GOLD_BLOCK.defaultBlockState(), apply(bound), "namespace hit");
        biome.set(holder("fallback"));
        assertSame(Blocks.DIAMOND_BLOCK.defaultBlockState(), apply(bound), "base fallback");
        assertEquals(1, nullProbe.calls, "selected null rule calls");
    }

    private static void assertDispatcherAndBranches(Object bound) throws ReflectiveOperationException {
        if (!bound.getClass().getName().equals(NAMESPACED_RULE_CLASS)) {
            throw new AssertionError("root rule is not TerraBlender's namespace dispatcher: "
                    + bound.getClass().getName());
        }
        Object base = field(bound, "baseRule");
        Map<?, ?> rules = (Map<?, ?>) field(bound, "rules");
        Object selected = rules.get("minecraft");
        Object fallback = rules.get("fallback");
        assertHidden(base, "base");
        assertHidden(selected, "minecraft namespace");
        assertHidden(fallback, "fallback namespace");
        if (base.getClass() == selected.getClass() || selected.getClass() == fallback.getClass()) {
            throw new AssertionError("TerraBlender branches did not receive independent compiled classes");
        }
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome> holder(String namespace) {
        ResourceKey<Biome> key = ResourceKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(namespace, "surface_test")
        );
        return (Holder<Biome>) Proxy.newProxyInstance(
                TerraBlenderSurfaceRuntimeTest.class.getClassLoader(),
                new Class<?>[]{Holder.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("is")
                            && arguments[0] instanceof Predicate<?> predicate) {
                        return ((Predicate<ResourceKey<Biome>>) predicate).test(key);
                    }
                    return switch (method.getName()) {
                        case "unwrapKey" -> Optional.of(key);
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "TestBiomeHolder[" + key.location() + "]";
                        default -> throw new UnsupportedOperationException(method.toString());
                    };
                }
        );
    }

    private static BlockState apply(Object rule) throws ReflectiveOperationException {
        Method method = rule.getClass().getDeclaredMethod("tryApply", int.class, int.class, int.class);
        method.setAccessible(true);
        return (BlockState) method.invoke(rule, 0, 64, 0);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void assertHidden(Object rule, String branch) {
        if (rule == null || !rule.getClass().isHidden()) {
            throw new AssertionError(branch + " rule is not a generated hidden class");
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class NullProbe {
        private int calls;

        private SurfaceRules.RuleSource source() {
            return (SurfaceRules.RuleSource) Proxy.newProxyInstance(
                    TerraBlenderSurfaceRuntimeTest.class.getClassLoader(),
                    new Class<?>[]{SurfaceRules.RuleSource.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "apply" -> boundRule();
                        case "codec" -> throw new UnsupportedOperationException("Test source has no codec");
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "TerraBlenderNullRuleSource";
                        default -> throw new UnsupportedOperationException(method.toString());
                    }
            );
        }

        private Object boundRule() throws ClassNotFoundException {
            Class<?> ruleType = Class.forName(SurfaceRules.class.getName() + "$SurfaceRule");
            return Proxy.newProxyInstance(
                    TerraBlenderSurfaceRuntimeTest.class.getClassLoader(),
                    new Class<?>[]{ruleType},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("tryApply")) {
                            this.calls++;
                            return null;
                        }
                        throw new UnsupportedOperationException(method.toString());
                    }
            );
        }
    }
}

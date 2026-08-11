package com.moepus.byepregen.surfacetest;

import com.moepus.byepregen.worldgen.surface.SurfaceBoundAccess;
import com.moepus.byepregen.worldgen.surface.SurfaceTemplateCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;

public final class SurfaceOpaqueRuntimeTest {
    public static final String ENABLED_PROPERTY = "byepregen.surfaceOpaqueRuntimeTest";
    private static final int OUTLINED_DELEGATES = 72;

    private SurfaceOpaqueRuntimeTest() {
    }

    public static void main(String[] args) throws Exception {
        Object context = createContext();
        foldsAbsoluteYAnchor(context);
        continuesAfterNullDelegate(context);
        stopsAfterNonNullDelegate(context);
        continuesAfterOutlinedRegion(context);
        customBiomeHolderFallsBackLazily(context);
        System.out.println("Surface opaque hidden-class runtime test passed");
    }

    private static void foldsAbsoluteYAnchor(Object context) throws ReflectiveOperationException {
        BlockState above = Blocks.DIAMOND_BLOCK.defaultBlockState();
        BlockState below = Blocks.GOLD_BLOCK.defaultBlockState();
        SurfaceRules.RuleSource source = SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.yBlockCheck(VerticalAnchor.absolute(96), 0),
                        SurfaceRules.state(above)
                ),
                SurfaceRules.state(below)
        );
        SurfaceBoundAccess.Rule generated = bind(source, context, false);
        setField(context, "blockY", 96);
        assertSame(above, generated.tryApply(0, 96, 0), "absolute Y anchor above");
        setField(context, "blockY", 95);
        assertSame(below, generated.tryApply(0, 95, 0), "absolute Y anchor below");
    }

    public static void runAndExit() {
        try {
            main(new String[0]);
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void continuesAfterNullDelegate(Object context) {
        Probe probe = new Probe(null);
        BlockState fallback = Blocks.DIAMOND_BLOCK.defaultBlockState();
        SurfaceRules.RuleSource source = SurfaceRules.sequence(
                opaqueRuleSource(probe),
                SurfaceRules.state(fallback)
        );

        SurfaceBoundAccess.Rule generated = bind(source, context, false);
        BlockState actual = probe.apply(generated, 17, -23, 91);
        assertSame(fallback, actual, "null delegate continuation");
        assertEquals(1, probe.calls, "null delegate calls");
    }

    private static void stopsAfterNonNullDelegate(Object context) {
        BlockState selected = Blocks.GOLD_BLOCK.defaultBlockState();
        Probe selectedProbe = new Probe(selected);
        Probe unreachableProbe = new Probe(null);
        SurfaceRules.RuleSource source = SurfaceRules.sequence(
                opaqueRuleSource(selectedProbe),
                opaqueRuleSource(unreachableProbe),
                SurfaceRules.state(Blocks.DIAMOND_BLOCK.defaultBlockState())
        );

        SurfaceBoundAccess.Rule generated = bind(source, context, false);
        BlockState actual = selectedProbe.apply(generated, -5, 64, 12);
        assertSame(selected, actual, "non-null delegate result");
        assertEquals(1, selectedProbe.calls, "non-null delegate calls");
        assertEquals(
                0,
                unreachableProbe.calls,
                "delegate after non-null result"
        );
    }

    private static void continuesAfterOutlinedRegion(Object context) {
        Probe probe = new Probe(null);
        SurfaceRules.RuleSource[] delegates = new SurfaceRules.RuleSource[OUTLINED_DELEGATES];
        Arrays.setAll(delegates, ignored -> opaqueRuleSource(probe));
        BlockState fallback = Blocks.EMERALD_BLOCK.defaultBlockState();
        SurfaceRules.RuleSource source = SurfaceRules.sequence(
                SurfaceRules.sequence(delegates),
                SurfaceRules.state(fallback)
        );

        SurfaceBoundAccess.Rule generated = bind(source, context, true);
        BlockState actual = probe.apply(generated, -101, 7, 303);
        assertSame(fallback, actual, "outlined null continuation");
        assertEquals(
                OUTLINED_DELEGATES,
                probe.calls,
                "outlined delegate calls"
        );
    }

    private static void customBiomeHolderFallsBackLazily(Object context)
            throws ReflectiveOperationException {
        BiomeProbe probe = new BiomeProbe();
        Holder<Biome> holder = probe.holder();
        setBiome(context, 101L, () -> holder);
        SurfaceRules.RuleSource source = SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(Biomes.PLAINS),
                        SurfaceRules.state(Blocks.DIAMOND_BLOCK.defaultBlockState())
                ),
                SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(Biomes.DESERT),
                        SurfaceRules.state(Blocks.GOLD_BLOCK.defaultBlockState())
                ),
                SurfaceRules.state(Blocks.EMERALD_BLOCK.defaultBlockState())
        );
        SurfaceBoundAccess.Rule generated = bind(source, context, false);

        probe.answer(true);
        assertSame(
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                generated.tryApply(0, 64, 0),
                "custom biome first occurrence"
        );
        assertEquals(1, probe.calls, "custom biome short circuit");

        setBiome(context, 102L, () -> holder);
        probe.answer(false, true);
        assertSame(
                Blocks.GOLD_BLOCK.defaultBlockState(),
                generated.tryApply(0, 63, 0),
                "custom biome next Y"
        );
        assertEquals(2, probe.calls, "custom biome occurrence order");
        generated.tryApply(0, 63, 0);
        assertEquals(2, probe.calls, "custom biome same-Y cache");
    }

    private static void setBiome(
            Object context,
            long lastUpdateY,
            Supplier<Holder<Biome>> supplier
    ) throws ReflectiveOperationException {
        setField(context, "lastUpdateY", lastUpdateY);
        setField(context, "biome", supplier);
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static SurfaceBoundAccess.Rule bind(
            SurfaceRules.RuleSource source,
            Object context,
            boolean requireRegion
    ) {
        Object bound = new SurfaceTemplateCache().bind(source, context);
        Class<?> generated = bound.getClass();
        if (!generated.isHidden() || generated.getNestHost() != SurfaceRules.class) {
            throw new AssertionError("rule is not a hidden SurfaceRules nestmate");
        }
        if (!(bound instanceof SurfaceBoundAccess.Rule rule)) {
            throw new AssertionError("rule does not implement runtime ABI");
        }
        if (requireRegion && Arrays.stream(generated.getDeclaredMethods())
                .noneMatch(method -> method.getName().startsWith("region$"))) {
            throw new AssertionError("planner did not outline a region");
        }
        return rule;
    }

    private static Object createContext() throws Exception {
        Class<?> type = Class.forName(SurfaceRules.class.getName() + "$Context");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(new Object[constructor.getParameterCount()]);
    }

    private static SurfaceRules.RuleSource opaqueRuleSource(Probe probe) {
        return (SurfaceRules.RuleSource) Proxy.newProxyInstance(
                SurfaceOpaqueRuntimeTest.class.getClassLoader(),
                new Class<?>[]{SurfaceRules.RuleSource.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "apply" -> boundRule(probe);
                    case "codec" -> throw new UnsupportedOperationException(
                            "Synthetic opaque rule has no codec"
                    );
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "OpaqueRuleSourceRuntimeProxy";
                    default -> throw new UnsupportedOperationException(method.toString());
                }
        );
    }

    private static Object boundRule(Probe probe) throws ClassNotFoundException {
        Class<?> ruleType = Class.forName(SurfaceRules.class.getName() + "$SurfaceRule");
        return Proxy.newProxyInstance(
                SurfaceOpaqueRuntimeTest.class.getClassLoader(),
                new Class<?>[]{ruleType},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "tryApply" -> probe.tryApply(
                            (int) arguments[0],
                            (int) arguments[1],
                            (int) arguments[2]
                    );
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "OpaqueBoundRuleRuntimeProxy";
                    default -> throw new UnsupportedOperationException(method.toString());
                }
        );
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

    private static final class Probe {
        private final BlockState result;
        private int expectedX;
        private int expectedY;
        private int expectedZ;
        private int calls;

        private Probe(BlockState result) {
            this.result = result;
        }

        private BlockState apply(SurfaceBoundAccess.Rule rule, int x, int y, int z) {
            this.expectedX = x;
            this.expectedY = y;
            this.expectedZ = z;
            return rule.tryApply(x, y, z);
        }

        private BlockState tryApply(int x, int y, int z) {
            this.calls++;
            if (x != this.expectedX || y != this.expectedY || z != this.expectedZ) {
                throw new AssertionError("Opaque delegate received wrong coordinates: "
                        + x + "," + y + "," + z);
            }
            return this.result;
        }
    }

    private static final class BiomeProbe {
        private boolean[] answers = new boolean[0];
        private int answerIndex;
        private int calls;

        @SuppressWarnings("unchecked")
        private Holder<Biome> holder() {
            return (Holder<Biome>) Proxy.newProxyInstance(
                    SurfaceOpaqueRuntimeTest.class.getClassLoader(),
                    new Class<?>[]{Holder.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("is")
                                && arguments != null
                                && arguments.length == 1
                                && arguments[0] instanceof Predicate<?>) {
                            return this.test();
                        }
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            case "toString" -> "CustomBiomeHolder";
                            default -> defaultValue(method.getReturnType());
                        };
                    }
            );
        }

        private void answer(boolean... values) {
            this.answers = values;
            this.answerIndex = 0;
            this.calls = 0;
        }

        private boolean test() {
            this.calls++;
            if (this.answerIndex >= this.answers.length) {
                throw new AssertionError("Unexpected custom Holder predicate evaluation");
            }
            return this.answers[this.answerIndex++];
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0D;
        if (type == float.class) return 0.0F;
        return 0;
    }
}

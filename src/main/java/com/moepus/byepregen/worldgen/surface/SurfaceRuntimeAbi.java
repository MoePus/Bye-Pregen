package com.moepus.byepregen.worldgen.surface;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.objectweb.asm.Type;

final class SurfaceRuntimeAbi {
    static final String BLOCK_X = "byepregen$blockX";
    static final String BLOCK_Y = "byepregen$blockY";
    static final String BLOCK_Z = "byepregen$blockZ";
    static final String LAST_UPDATE_XZ = "byepregen$lastUpdateXZ";
    static final String MIN_SURFACE_LEVEL = "byepregen$getMinSurfaceLevel";
    static final String STONE_ABOVE = "byepregen$stoneDepthAbove";
    static final String STONE_BELOW = "byepregen$stoneDepthBelow";
    static final String SURFACE_DEPTH = "byepregen$surfaceDepth";
    static final String SURFACE_SECONDARY = "byepregen$getSurfaceSecondary";
    static final String SURFACE_SYSTEM = "byepregen$surfaceSystem";
    static final String WATER_HEIGHT = "byepregen$waterHeight";
    static final String WORLD_CONTEXT = "byepregen$worldGenerationContext";
    static final String BAND = "byepregen$getBand";

    private final MethodHandles.Lookup lookup;
    private final Class<?> contextClass;
    private final Class<?> conditionClass;
    private final Class<?> ruleClass;
    private final String contextOwner;
    private final String conditionOwner;
    private final String ruleOwner;
    private final String conditionTest;
    private final String ruleTryApply;
    private final String noiseGetValue;
    private final String randomAt;
    private final String randomNextFloat;
    private final String anchorResolveY;
    private final String mathMap;

    private SurfaceRuntimeAbi(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
        this.lookup = lookup;
        ClassLoader loader = SurfaceRules.class.getClassLoader();
        this.contextClass = Class.forName(SurfaceRules.class.getName() + "$Context", false, loader);
        this.conditionClass = Class.forName(SurfaceRules.class.getName() + "$Condition", false, loader);
        this.ruleClass = Class.forName(SurfaceRules.class.getName() + "$SurfaceRule", false, loader);
        this.contextOwner = Type.getInternalName(this.contextClass);
        this.conditionOwner = Type.getInternalName(this.conditionClass);
        this.ruleOwner = Type.getInternalName(this.ruleClass);
        this.conditionTest = methodName(this.conditionClass, "test", boolean.class);
        this.ruleTryApply = methodName(
                this.ruleClass, "tryApply", BlockState.class, int.class, int.class, int.class
        );
        this.noiseGetValue = methodName(
                NormalNoise.class, "getValue", double.class,
                double.class, double.class, double.class
        );
        this.randomAt = methodName(
                PositionalRandomFactory.class,
                "at",
                RandomSource.class,
                int.class,
                int.class,
                int.class
        );
        this.randomNextFloat = methodName(RandomSource.class, "nextFloat", float.class);
        this.anchorResolveY = methodName(
                VerticalAnchor.class, "resolveY", int.class, WorldGenerationContext.class
        );
        this.mathMap = methodName(
                Mth.class,
                "map",
                double.class,
                double.class,
                double.class,
                double.class,
                double.class,
                double.class
        );
        this.preflightInjectedMethods();
    }

    static SurfaceRuntimeAbi resolve() throws SurfaceCompileException {
        try {
            Method method = Arrays.stream(SurfaceRules.class.getDeclaredMethods())
                    .filter(candidate -> Modifier.isStatic(candidate.getModifiers()))
                    .filter(candidate -> candidate.getParameterCount() == 0)
                    .filter(candidate -> candidate.getReturnType() == MethodHandles.Lookup.class)
                    .filter(candidate -> candidate.getName().startsWith("byepregen$lookup"))
                    .findFirst()
                    .orElseThrow();
            method.setAccessible(true);
            return new SurfaceRuntimeAbi((MethodHandles.Lookup) method.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new SurfaceCompileException("Cannot resolve SurfaceRules runtime ABI", exception);
        }
    }

    MethodHandles.Lookup lookup() {
        return this.lookup;
    }

    Class<?> contextClass() {
        return this.contextClass;
    }

    Class<?> ruleClass() {
        return this.ruleClass;
    }

    String contextOwner() {
        return this.contextOwner;
    }

    String conditionOwner() {
        return this.conditionOwner;
    }

    String ruleOwner() {
        return this.ruleOwner;
    }

    String conditionTest() {
        return this.conditionTest;
    }

    String ruleTryApply() {
        return this.ruleTryApply;
    }

    String noiseGetValue() {
        return this.noiseGetValue;
    }

    String randomAt() {
        return this.randomAt;
    }

    String randomNextFloat() {
        return this.randomNextFloat;
    }

    String anchorResolveY() {
        return this.anchorResolveY;
    }

    String mathMap() {
        return this.mathMap;
    }

    String bindingDescriptor(SurfaceBindingLayout.Kind kind) {
        if (kind == SurfaceBindingLayout.Kind.CONDITION) {
            return Type.getDescriptor(this.conditionClass);
        }
        if (kind == SurfaceBindingLayout.Kind.RULE) {
            return Type.getDescriptor(this.ruleClass);
        }
        return Type.getDescriptor(kind.fieldType());
    }

    private void preflightInjectedMethods() throws ReflectiveOperationException {
        requireMethod(this.contextClass, BLOCK_X, int.class);
        requireMethod(this.contextClass, BLOCK_Y, int.class);
        requireMethod(this.contextClass, BLOCK_Z, int.class);
        requireMethod(this.contextClass, SURFACE_DEPTH, int.class);
        requireMethod(this.contextClass, WATER_HEIGHT, int.class);
        requireMethod(this.contextClass, STONE_ABOVE, int.class);
        requireMethod(this.contextClass, STONE_BELOW, int.class);
        requireMethod(this.contextClass, LAST_UPDATE_XZ, long.class);
        requireMethod(this.contextClass, SURFACE_SYSTEM, SurfaceSystem.class);
        requireMethod(this.contextClass, WORLD_CONTEXT, WorldGenerationContext.class);
        requireMethod(this.contextClass, SURFACE_SECONDARY, double.class);
        requireMethod(this.contextClass, MIN_SURFACE_LEVEL, int.class);
        requireMethod(SurfaceSystem.class, BAND, BlockState.class, int.class, int.class, int.class);
    }

    private static void requireMethod(
            Class<?> owner,
            String name,
            Class<?> returnType,
            Class<?>... parameters
    ) throws NoSuchMethodException {
        Method method = owner.getMethod(name, parameters);
        if (method.getReturnType() != returnType) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " return type");
        }
    }

    private static String methodName(
            Class<?> owner,
            String preferredName,
            Class<?> returnType,
            Class<?>... parameters
    ) throws NoSuchMethodException {
        try {
            Method preferred = owner.getMethod(preferredName, parameters);
            if (preferred.getReturnType() == returnType) {
                return preferred.getName();
            }
        } catch (NoSuchMethodException ignored) {
            // Production namespaces may not retain Mojmap names.
        }
        Method match = null;
        for (Method method : owner.getMethods()) {
            if (method.getReturnType() != returnType
                    || !Arrays.equals(method.getParameterTypes(), parameters)) {
                continue;
            }
            if (match != null && !match.getName().equals(method.getName())) {
                throw new NoSuchMethodException("Ambiguous method on " + owner.getName());
            }
            match = method;
        }
        if (match == null) {
            throw new NoSuchMethodException("Missing method on " + owner.getName());
        }
        return match.getName();
    }
}

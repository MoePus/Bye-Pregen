package com.moepus.byepregen.worldgen.surface;

import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Names the 26.3 runtime members the generated rules call.
 *
 * <p>The context getters cannot be called by their production names: Minecraft's remapped
 * namespaces rename them, and they cannot be found by shape either because several share {@code ()I}.
 * The compiler therefore reads them through the accessors {@code MaterialRuleContextAccessMixin}
 * injects, whose names live in this mod's own namespace and survive remapping - exactly how 26.2 read
 * its context. The public getter names stay as a fallback for plain-JVM tests, where no mixin runs;
 * in that case the mixin is also what gates the compiler, so a production namespace cannot end up
 * here.</p>
 */
final class SurfaceRuntimeAbi {
    static volatile String BLOCK_X = "blockX";
    static volatile String BLOCK_Y = "blockY";
    static volatile String BLOCK_Z = "blockZ";
    static volatile String SURFACE_DEPTH = "surfaceDepth";
    static volatile String WATER_HEIGHT = "waterHeight";
    static volatile String STONE_ABOVE = "stoneDepthAbove";
    static volatile String STONE_BELOW = "stoneDepthBelow";
    static volatile String SURFACE_SECONDARY = "getSurfaceSecondary";
    static volatile String MIN_SURFACE_LEVEL = "getMinSurfaceLevel";
    static final String BAND = "getBand";
    static final String NOISE_VALUE = "getAsDouble";

    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen Surface Scalar");
    private static volatile boolean contextNamesResolved;

    private final Class<?> contextClass;
    private final Class<?> conditionClass;
    private final Class<?> ruleClass;
    private final String contextOwner;
    private final String conditionOwner;
    private final String ruleOwner;
    private final String conditionTest;
    private final String ruleTryApply;
    private final String randomAt;
    private final String randomNextFloat;
    private final String mathMap;

    private SurfaceRuntimeAbi() throws SurfaceCompileException {
        this.contextClass = MaterialRuleContext.class;
        this.conditionClass = ConditionEvaluator.class;
        this.ruleClass = RuleEvaluator.class;
        this.contextOwner = Type.getInternalName(this.contextClass);
        this.conditionOwner = Type.getInternalName(this.conditionClass);
        this.ruleOwner = Type.getInternalName(this.ruleClass);
        try {
            this.conditionTest = methodName(this.conditionClass, "test", boolean.class);
            this.ruleTryApply = methodName(
                    this.ruleClass, "tryApply", BlockState.class, int.class, int.class, int.class
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
        } catch (NoSuchMethodException exception) {
            throw new SurfaceCompileException("Cannot resolve the SurfaceRule runtime ABI", exception);
        }
        this.resolveContextNames();
        this.preflightContextGetters();
    }

    static SurfaceRuntimeAbi resolve() throws SurfaceCompileException {
        return new SurfaceRuntimeAbi();
    }

    /**
     * Prefers this mod's injected accessors, whose names survive remapping, and only falls back to
     * the public getter names when no mixin is applied (plain-JVM tests). Resolution happens once per
     * process because the names are shared by every emitter.
     */
    private void resolveContextNames() {
        if (contextNamesResolved) return;
        boolean[] injected = {true};
        BLOCK_X = pick("byepregen$blockX", BLOCK_X, int.class, injected);
        BLOCK_Y = pick("byepregen$blockY", BLOCK_Y, int.class, injected);
        BLOCK_Z = pick("byepregen$blockZ", BLOCK_Z, int.class, injected);
        SURFACE_DEPTH = pick("byepregen$surfaceDepth", SURFACE_DEPTH, int.class, injected);
        WATER_HEIGHT = pick("byepregen$waterHeight", WATER_HEIGHT, int.class, injected);
        STONE_ABOVE = pick("byepregen$stoneDepthAbove", STONE_ABOVE, int.class, injected);
        STONE_BELOW = pick("byepregen$stoneDepthBelow", STONE_BELOW, int.class, injected);
        SURFACE_SECONDARY = pick("byepregen$getSurfaceSecondary", SURFACE_SECONDARY, double.class, injected);
        MIN_SURFACE_LEVEL = pick("byepregen$getMinSurfaceLevel", MIN_SURFACE_LEVEL, int.class, injected);
        contextNamesResolved = true;
        if (injected[0]) {
            LOGGER.info("Surface ABI reads the context through the injected accessors");
        } else {
            LOGGER.warn("Surface ABI fell back to the public context getters: the accessor mixin is not applied");
        }
    }

    private String pick(String accessor, String fallback, Class<?> returnType, boolean[] injected) {
        try {
            requireMethod(this.contextClass, accessor, returnType);
            return accessor;
        } catch (NoSuchMethodException missing) {
            injected[0] = false;
            return fallback;
        }
    }

    Class<?> contextClass() {
        return this.contextClass;
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

    String randomAt() {
        return this.randomAt;
    }

    String randomNextFloat() {
        return this.randomNextFloat;
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

    /**
     * The 26.3 context getters all share a few signatures, so they cannot be discovered by shape;
     * their mapped names have to be present verbatim or the compiler declines to generate code.
     */
    private void preflightContextGetters() throws SurfaceCompileException {
        try {
            requireMethod(this.contextClass, BLOCK_X, int.class);
            requireMethod(this.contextClass, BLOCK_Y, int.class);
            requireMethod(this.contextClass, BLOCK_Z, int.class);
            requireMethod(this.contextClass, SURFACE_DEPTH, int.class);
            requireMethod(this.contextClass, WATER_HEIGHT, int.class);
            requireMethod(this.contextClass, STONE_ABOVE, int.class);
            requireMethod(this.contextClass, STONE_BELOW, int.class);
            requireMethod(this.contextClass, SURFACE_SECONDARY, double.class);
            requireMethod(this.contextClass, MIN_SURFACE_LEVEL, int.class);
            requireMethod(this.contextClass, BAND, BlockState.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException exception) {
            throw new SurfaceCompileException(
                    "Cannot resolve the MaterialRuleContext ABI", exception
            );
        }
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

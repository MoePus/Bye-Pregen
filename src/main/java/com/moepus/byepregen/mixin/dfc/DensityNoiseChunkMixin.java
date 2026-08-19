package com.moepus.byepregen.mixin.dfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import com.moepus.byepregen.dfc.runtime.FinalDensityColumnProvider;
import com.moepus.byepregen.dfc.runtime.RandomStateColumnProvider;
import com.moepus.byepregen.dfc.runtime.DensityColumnMetrics;
import com.moepus.byepregen.worldgen.arena.InterpolatedMarkerAccess;
import com.mojang.logging.LogUtils;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;

@MixinGate(feature = MixinFeature.DFC)
@Mixin(NoiseChunk.class)
public abstract class DensityNoiseChunkMixin implements FinalDensityColumnProvider {
    @Unique private static final boolean byepregen$VERIFY_COLUMN =
            Boolean.getBoolean("byepregen.verifyDfcColumn");
    @Unique private static final double byepregen$VERIFY_TOLERANCE = 1.0E-6D;
    @Unique private static final Logger byepregen$LOGGER = LogUtils.getLogger();
    @Shadow @Final private Map<DensityFunction, DensityFunction> wrapped;
    @Shadow @Final private List<NoiseChunk.NoiseInterpolator> interpolators;
    @Shadow private int cellStartBlockY;
    @Shadow private int inCellY;
    @Shadow private long interpolationCounter;
    @Shadow private long arrayInterpolationCounter;
    @Shadow private int arrayIndex;
    @Shadow protected abstract DensityFunction wrap(DensityFunction function);

    @Unique private CompiledColumnEvaluator byepregen$finalDensityColumnEvaluator;
    @Unique private ColumnEvaluationContext byepregen$finalDensityColumnContext;
    @Unique private DensityFunction byepregen$verificationRoot;
    @Unique private long byepregen$verificationCounter = Long.MIN_VALUE;
    @Unique private final Map<Object, NoiseChunk.NoiseInterpolator> byepregen$interpolatedTokens
            = new IdentityHashMap<>();

    @Inject(method = "wrap", at = @At("RETURN"))
    private void byepregen$captureInterpolatedToken(
            DensityFunction source,
            CallbackInfoReturnable<DensityFunction> callback
    ) {
        if (!(source instanceof DensityFunctions.Marker marker)
                || marker.type() != DensityFunctions.Marker.Type.Interpolated
                || !(source instanceof InterpolatedMarkerAccess access)) {
            return;
        }
        Object token = access.byepregen$getInterpolationToken();
        DensityFunction wrapped = callback.getReturnValue();
        if (token != null && wrapped instanceof NoiseChunk.NoiseInterpolator interpolator) {
            this.byepregen$interpolatedTokens.put(token, interpolator);
        }
    }

    @ModifyExpressionValue(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/DensityFunction;mapAll("
                            + "Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)"
                            + "Lnet/minecraft/world/level/levelgen/DensityFunction;"
            )
    )
    private DensityFunction byepregen$captureWrappedFinalDensity(DensityFunction wrapped) {
        if (!byepregen$VERIFY_COLUMN) return wrapped;
        if (wrapped instanceof DensityFunctions.MarkerOrMarked marker
                && marker.type() == DensityFunctions.Marker.Type.CacheAllInCell) {
            this.byepregen$verificationRoot = marker.wrapped();
        } else {
            // C2ME DFC replaces the mapped CacheAllInCell return with its compiled scalar root.
            this.byepregen$verificationRoot = wrapped;
        }
        return wrapped;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void byepregen$bindFinalDensityColumn(
            int cellCountXZ,
            RandomState randomState,
            int firstNoiseX,
            int firstNoiseZ,
            NoiseSettings noiseSettings,
            DensityFunctions.BeardifierOrMarker beardifier,
            NoiseGeneratorSettings generatorSettings,
            Aquifer.FluidPicker fluidPicker,
            Blender blender,
            CallbackInfo callback
    ) {
        if (blender != Blender.empty()
                || !((Object) randomState instanceof RandomStateColumnProvider provider)) return;
        ColumnTemplate template = provider.byepregen$finalDensityColumnTemplate();
        if (template == null || !template.available()) return;
        try {
            this.byepregen$finalDensityColumnEvaluator = template.bindResolved(new ColumnTemplate.BindingResolver() {
                @Override
                public DensityFunction resolveDensity(DensityFunction source) {
                    return byepregen$resolveBinding(source);
                }

                @Override
                public DensityFunction resolveInterpolated(DensityFunction source, int ordinal) {
                    return byepregen$resolveInterpolated(source, ordinal);
                }
            });
            this.byepregen$finalDensityColumnContext = new ColumnEvaluationContext();
            if (byepregen$VERIFY_COLUMN && this.byepregen$verificationRoot == null) {
                throw new IllegalStateException("Missing wrapped final-density scalar root");
            }
            DensityColumnMetrics.recordBound();
        } catch (RuntimeException | LinkageError throwable) {
            this.byepregen$finalDensityColumnEvaluator = null;
            this.byepregen$finalDensityColumnContext = null;
            DensityColumnMetrics.recordBindFailure();
            byepregen$LOGGER.warn("Disabling final-density column evaluator for one NoiseChunk", throwable);
        }
    }

    @Unique
    private DensityFunction byepregen$resolveBinding(DensityFunction source) {
        DensityFunction existing = this.byepregen$findWrappedIdentity(source);
        if (existing != null) return existing;
        if (byepregen$containsInterpolatedSource(source)) {
            throw new IllegalStateException("Cannot bind an unwrapped density subtree containing Interpolated: "
                    + source.getClass().getName());
        }
        DensityColumnMetrics.recordBindingFallback();
        int interpolatorCount = this.interpolators.size();
        DensityFunction mapped = source.mapAll(this::wrap);
        int added = this.interpolators.size() - interpolatorCount;
        if (added > 0) {
            DensityColumnMetrics.recordAddedInterpolators(added);
            throw new IllegalStateException("Column binding created " + added
                    + " duplicate NoiseInterpolator(s) for " + source.getClass().getName());
        }
        return mapped;
    }

    @Unique
    private DensityFunction byepregen$findWrappedIdentity(DensityFunction source) {
        for (Map.Entry<DensityFunction, DensityFunction> entry : this.wrapped.entrySet()) {
            if (entry.getKey() == source) return entry.getValue();
        }
        return null;
    }

    @Unique
    private static boolean byepregen$containsInterpolatedSource(DensityFunction source) {
        boolean[] found = new boolean[1];
        source.mapAll(function -> {
            if (function instanceof DensityFunctions.Marker marker
                    && marker.type() == DensityFunctions.Marker.Type.Interpolated) {
                found[0] = true;
            }
            return function;
        });
        return found[0];
    }

    @Unique
    private DensityFunction byepregen$resolveInterpolated(DensityFunction source, int slot) {
        if (source instanceof InterpolatedMarkerAccess access) {
            Object token = access.byepregen$getInterpolationToken();
            DensityFunction mapped = token == null ? null : this.byepregen$interpolatedTokens.get(token);
            if (mapped instanceof NoiseChunk.NoiseInterpolator) return mapped;
        }
        DensityFunction existing = this.byepregen$findWrappedIdentity(source);
        if (existing instanceof NoiseChunk.NoiseInterpolator) return existing;
        throw new IllegalStateException("Missing runtime NoiseInterpolator for interpolated slot " + slot
                + " (tokens=" + this.byepregen$interpolatedTokens.size()
                + ", interpolators=" + this.interpolators.size() + ')');
    }

    @Override
    public boolean byepregen$hasFinalDensityColumn() {
        return this.byepregen$finalDensityColumnEvaluator != null;
    }

    @Override
    public void byepregen$evalFinalDensityColumn(
            double[] output,
            int blockX,
            int blockZ,
            int minY,
            int cellHeight,
            ColumnEvaluationContext.InterpolationProvider interpolationProvider
    ) {
        CompiledColumnEvaluator evaluator = this.byepregen$finalDensityColumnEvaluator;
        ColumnEvaluationContext context = this.byepregen$finalDensityColumnContext;
        if (evaluator == null || context == null) {
            throw new IllegalStateException("Final density has no compiled column evaluator");
        }
        context.prepare(output, blockX, blockZ, minY, cellHeight, interpolationProvider);
        try {
            DensityColumnMetrics.recordEvaluatedColumn();
            evaluator.evalColumn(context);
            this.byepregen$verifyColumn(
                    output, blockX, blockZ, minY, cellHeight, interpolationProvider);
        } finally {
            context.clear();
        }
    }

    @Unique
    private void byepregen$verifyColumn(
            double[] output,
            int blockX,
            int blockZ,
            int minY,
            int cellHeight,
            ColumnEvaluationContext.InterpolationProvider interpolationProvider
    ) {
        DensityFunction root = this.byepregen$verificationRoot;
        if (root == null) return;
        double[] savedValues = new double[this.interpolators.size()];
        double[][] columns = new double[this.interpolators.size()][];
        for (int index = 0; index < this.interpolators.size(); ++index) {
            NoiseChunk.NoiseInterpolator interpolator = this.interpolators.get(index);
            savedValues[index] = interpolator.value;
            columns[index] = interpolationProvider.byepregen$getColumn(interpolator);
        }
        int savedCellStartY = this.cellStartBlockY;
        int savedInCellY = this.inCellY;
        int savedArrayIndex = this.arrayIndex;
        long savedCounter = this.interpolationCounter;
        long savedArrayCounter = this.arrayInterpolationCounter;
        long baseCounter = this.byepregen$verificationCounter;
        this.byepregen$verificationCounter += output.length + 1L;
        try {
            this.arrayInterpolationCounter = baseCounter - 1L;
            this.arrayIndex = 0;
            for (int lane = 0; lane < output.length; ++lane) {
                int blockY = minY + lane * cellHeight;
                this.cellStartBlockY = blockY + 1;
                this.inCellY = -1;
                this.interpolationCounter = baseCounter + lane;
                for (int index = 0; index < this.interpolators.size(); ++index) {
                    this.interpolators.get(index).value = columns[index][lane];
                }
                double expected = root.compute((NoiseChunk) (Object) this);
                if (!byepregen$equivalent(expected, output[lane])) {
                    throw new IllegalStateException("Final-density column mismatch at "
                            + blockX + ',' + blockY + ',' + blockZ + ": expected="
                            + expected + ", actual=" + output[lane]);
                }
            }
        } finally {
            this.cellStartBlockY = savedCellStartY;
            this.inCellY = savedInCellY;
            this.arrayIndex = savedArrayIndex;
            this.interpolationCounter = savedCounter;
            this.arrayInterpolationCounter = savedArrayCounter;
            for (int index = 0; index < this.interpolators.size(); ++index) {
                this.interpolators.get(index).value = savedValues[index];
            }
        }
        DensityColumnMetrics.recordVerifiedBoundaries(output.length);
    }

    @Unique
    private static boolean byepregen$equivalent(double expected, double actual) {
        if (Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual)) return true;
        if (Double.isNaN(expected) && Double.isNaN(actual)) return true;
        if (!Double.isFinite(expected) || !Double.isFinite(actual)) return false;
        double tolerance = byepregen$VERIFY_TOLERANCE * (1.0D + Math.abs(expected));
        return Math.abs(expected - actual) <= tolerance;
    }
}

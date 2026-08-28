package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import com.moepus.byepregen.dfc.runtime.DensityColumnMetrics;
import com.moepus.byepregen.dfc.runtime.NoiseChunkColumnBinder;
import com.moepus.byepregen.worldgen.biome.BiomeColumnEvaluator;
import com.moepus.byepregen.worldgen.biome.BiomeColumnTemplates;
import com.moepus.byepregen.worldgen.biome.RandomStateBiomeColumnProvider;
import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

@MixinGate(
        feature = MixinFeature.DFC,
        conflictingMods = {"reterraforged", "terrablender", "blueprint"}
)
@Mixin(NoiseChunk.class)
public abstract class BiomeDensityNoiseChunkMixin implements BiomeColumnEvaluator {
    @Unique private static final Logger byepregen$LOGGER = LogUtils.getLogger();
    @Shadow protected abstract Climate.Sampler cachedClimateSampler(
            NoiseRouter router,
            List<Climate.ParameterPoint> spawnTarget
    );

    @Unique private BiomeColumnTemplates byepregen$biomeColumnTemplates;
    @Unique private Map<Root, CompiledColumnEvaluator> byepregen$biomeColumns = Map.of();
    @Unique private final ColumnEvaluationContext byepregen$biomeColumnContext =
            new ColumnEvaluationContext();
    @Unique private boolean byepregen$biomeBindingAttempted;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void byepregen$captureBiomeTemplates(
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
        if (blender == Blender.empty()
                && (Object) randomState instanceof RandomStateBiomeColumnProvider provider) {
            this.byepregen$biomeColumnTemplates = provider.byepregen$biomeColumnTemplates();
        }
    }

    @Override
    public Climate.Sampler byepregen$climateSampler(
            NoiseRouter router,
            List<Climate.ParameterPoint> spawnTarget
    ) {
        return this.cachedClimateSampler(router, spawnTarget);
    }

    @Override
    public boolean byepregen$prepareBiomeColumns() {
        if (this.byepregen$biomeBindingAttempted) return !this.byepregen$biomeColumns.isEmpty();
        this.byepregen$biomeBindingAttempted = true;
        BiomeColumnTemplates templates = this.byepregen$biomeColumnTemplates;
        if (templates == null || !templates.available()) return false;
        try {
            NoiseChunkColumnBinder binder = (NoiseChunkColumnBinder) this;
            EnumMap<Root, CompiledColumnEvaluator> bound = templates.bind(binder::byepregen$bindColumn);
            this.byepregen$biomeColumns = bound;
            DensityColumnMetrics.recordBiomeBound(bound.size());
            return true;
        } catch (RuntimeException | LinkageError throwable) {
            DensityColumnMetrics.recordBindFailure();
            byepregen$LOGGER.warn("Disabling biome density column evaluators for one NoiseChunk", throwable);
            return false;
        }
    }

    @Override
    public boolean byepregen$hasDepthOnlyClimate() {
        BiomeColumnTemplates templates = this.byepregen$biomeColumnTemplates;
        return templates != null && templates.depthOnlyClimate();
    }

    @Override
    public boolean byepregen$evalBiomeColumn(Root root, Request request) {
        CompiledColumnEvaluator evaluator = this.byepregen$biomeColumns.get(root);
        if (evaluator == null) return false;
        ColumnEvaluationContext context = this.byepregen$biomeColumnContext;
        try {
            context.prepare(request.output(), request.blockX(), request.blockZ(),
                    request.minBlockY(), request.blockStep(), ignored -> {
                        throw new IllegalStateException("Biome climate root uses interpolation");
                    });
            evaluator.evalColumn(context);
            return true;
        } catch (RuntimeException | LinkageError throwable) {
            this.byepregen$biomeColumns = Map.of();
            byepregen$LOGGER.warn("Disabling biome density columns after {} failed", root, throwable);
            return false;
        } finally {
            context.clear();
        }
    }
}

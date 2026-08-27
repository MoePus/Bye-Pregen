package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.dfc.compile.DensityColumnCompiler;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.RandomStateColumnProvider;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@MixinGate(feature = MixinFeature.DFC)
@Mixin(RandomState.class)
public abstract class DensityRandomStateMixin implements RandomStateColumnProvider {
    @Unique private ColumnTemplate byepregen$finalDensityColumnTemplate;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void byepregen$compileFinalDensityColumn(
            NoiseGeneratorSettings settings,
            HolderGetter<NormalNoise.NoiseParameters> noiseParameters,
            long seed,
            CallbackInfo callback
    ) {
        RandomState self = (RandomState) (Object) this;
        this.byepregen$finalDensityColumnTemplate = DensityColumnCompiler.compile(self.router());
    }

    @Override
    public ColumnTemplate byepregen$finalDensityColumnTemplate() {
        return this.byepregen$finalDensityColumnTemplate;
    }

}

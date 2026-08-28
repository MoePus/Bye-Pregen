package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.biome.BiomeColumnTemplates;
import com.moepus.byepregen.worldgen.biome.RandomStateBiomeColumnProvider;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@MixinGate(
        feature = MixinFeature.DFC,
        conflictingMods = {"reterraforged", "terrablender", "blueprint"}
)
@Mixin(RandomState.class)
public abstract class BiomeDensityRandomStateMixin implements RandomStateBiomeColumnProvider {
    @Unique private BiomeColumnTemplates byepregen$biomeColumnTemplates;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;router:"
                            + "Lnet/minecraft/world/level/levelgen/NoiseRouter;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void byepregen$compileBiomeColumns(
            NoiseGeneratorSettings settings,
            HolderGetter<NormalNoise.NoiseParameters> noiseParameters,
            long seed,
            CallbackInfo callback
    ) {
        RandomState self = (RandomState) (Object) this;
        this.byepregen$biomeColumnTemplates = BiomeColumnTemplates.compile(self.router());
    }

    @Override
    public BiomeColumnTemplates byepregen$biomeColumnTemplates() {
        return this.byepregen$biomeColumnTemplates;
    }
}

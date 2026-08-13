package com.moepus.byepregen.mixin.arena;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.ArenaNoiseInterpolatorAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(feature = MixinFeature.ARENA)
@Mixin(NoiseChunk.NoiseInterpolator.class)
public abstract class NoiseInterpolatorArenaMixin implements ArenaNoiseInterpolatorAccess {
    @Shadow
    double[][] slice0;
    @Shadow
    double[][] slice1;
    @Shadow public double valueXZ11;

    @Unique
    @Override
    public void byepregen$prepareArenaXZ(int cellZ, double deltaX, double[] zBaseSteps) {
        // Preserve vanilla's X-then-Z lerp order. Each Y boundary is stored as a Z
        // base and step so all block columns can reuse the two current X slices.
        double[] slice00 = this.slice0[cellZ];
        double[] slice01 = this.slice0[cellZ + 1];
        double[] slice10 = this.slice1[cellZ];
        double[] slice11 = this.slice1[cellZ + 1];
        double inverseCellWidth = this.valueXZ11;
        for (int pointY = 0, edgeIndex = 0; pointY < slice00.length; ++pointY, edgeIndex += 2) {
            double zBase = Mth.lerp(deltaX, slice00[pointY], slice10[pointY]);
            double zEnd = Mth.lerp(deltaX, slice01[pointY], slice11[pointY]);
            double zStep = (zEnd - zBase) * inverseCellWidth;
            zBaseSteps[edgeIndex] = zBase;
            zBaseSteps[edgeIndex + 1] = zStep;
        }
    }
}

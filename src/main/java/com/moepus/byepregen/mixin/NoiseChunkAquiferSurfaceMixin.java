package com.moepus.byepregen.mixin;

import com.moepus.byepregen.worldgen.AquiferSurfaceShortcutAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkAquiferSurfaceMixin implements AquiferSurfaceShortcutAccess {
    @Unique private static final int byepregen$AQUIFER_HIGH_AIR_MARGIN = 5;

    @Unique private boolean byepregen$aquiferSurfaceColumnActive;
    @Unique private int byepregen$aquiferFluidUpperBound;

    @Unique
    @Override
    public void byepregen$beginAquiferSurfaceColumn(int fluidUpperBound) {
        if (this.byepregen$aquiferSurfaceColumnActive) {
            throw new IllegalStateException("Aquifer surface column is already active");
        }
        this.byepregen$aquiferFluidUpperBound = fluidUpperBound;
        this.byepregen$aquiferSurfaceColumnActive = true;
    }

    @Unique
    @Override
    public void byepregen$endAquiferSurfaceColumn() {
        this.byepregen$aquiferSurfaceColumnActive = false;
        this.byepregen$aquiferFluidUpperBound = 0;
    }

    @Unique
    @Override
    public boolean byepregen$canSkipAquifer(int blockY) {
        return this.byepregen$aquiferSurfaceColumnActive
                && blockY - byepregen$AQUIFER_HIGH_AIR_MARGIN >= this.byepregen$aquiferFluidUpperBound;
    }
}

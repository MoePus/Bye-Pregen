package com.moepus.byepregen.mixin.palette;

import com.moepus.byepregen.palette.access.BlockStateRawIdAccess;
import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PalettedContainer.class, remap = false)
public abstract class PalettedContainerRawIdMixin<T> implements BlockStateRawIdAccess {
    @Shadow
    public volatile PalettedContainer.Data<T> data;

    @Shadow
    @Final
    private Strategy<T> strategy;

    @Override
    public int getRawId(int x, int y, int z) {
        PalettedContainer.Data<T> data = this.data;
        int localId = data.storage().get(this.strategy.getIndex(x, y, z));
        Palette<T> palette = data.palette();
        if (palette instanceof PaletteRawIdAccess rawIdAccess) {
            return rawIdAccess.byepregen$rawIdForLocalId(localId, this.strategy.globalMap());
        }
        throw new UnsupportedOperationException("Missing raw-id access for palette " + palette.getClass().getName());
    }
}

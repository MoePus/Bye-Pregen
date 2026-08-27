package com.moepus.byepregen.mixin.palette;

import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import net.minecraft.world.level.chunk.GlobalPalette;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GlobalPalette.class)
public abstract class GlobalPaletteMixin implements PaletteRawIdAccess {
    @Override
    public int byepregen$rawIdForLocalId(int localId) {
        return localId;
    }
}

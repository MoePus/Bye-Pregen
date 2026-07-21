package com.moepus.byepregen.PaletteContainer.FastPalette;

import net.minecraft.world.level.chunk.PalettedContainer;

public interface FastPalettedContainerAccess<T> {
    void byepregen$updateFastData(PalettedContainer.Data<T> data);
}

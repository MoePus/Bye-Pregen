package com.moepus.byepregen.mixin.palette.compat.lithium;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import com.moepus.byepregen.palette.access.PaletteRawIds;
import net.minecraft.core.IdMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(requiredMods = "lithium")
@Mixin(targets = "net.caffeinemc.mods.lithium.common.world.chunk.LithiumHashPalette", remap = false)
public abstract class LithiumHashPaletteMixin<T> implements PaletteRawIdAccess {
    @Shadow
    private T[] entries;

    @Shadow
    private int size;

    @Override
    public int byepregen$rawIdForLocalId(int localId, IdMap<?> globalMap) {
        if (localId < 0 || localId >= this.size) {
            return -1;
        }
        T value = this.entries[localId];
        return value == null ? -1 : PaletteRawIds.rawId(globalMap, value);
    }
}

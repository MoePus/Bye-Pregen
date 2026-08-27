package com.moepus.byepregen.mixin.palette.compat.lithium;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import net.minecraft.core.IdMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(requiredMods = "harium")
@Pseudo
@Mixin(targets = "me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette", remap = false)
public abstract class HariumHashPaletteMixin<T> implements PaletteRawIdAccess {
    @Shadow @Final private IdMap<T> idList;
    @Shadow private T[] entries;
    @Shadow private int size;

    @Override
    public int byepregen$rawIdForLocalId(int localId) {
        if (localId < 0 || localId >= this.size) {
            return -1;
        }
        T value = this.entries[localId];
        return value == null ? -1 : this.idList.getId(value);
    }
}

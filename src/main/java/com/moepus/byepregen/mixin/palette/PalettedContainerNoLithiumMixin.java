package com.moepus.byepregen.mixin.palette;

import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@MixinGate(conflictingMods = "lithium")
@Mixin(value = PalettedContainer.class, remap = false)
public class PalettedContainerNoLithiumMixin {
    /**
     * @author ishland
     * @reason removes locking
     */
    @Overwrite
    public void acquire() {
        // no-op
    }

    /**
     * @author ishland
     * @reason removes locking
     */
    @Overwrite
    public void release() {
        // no-op
    }
}

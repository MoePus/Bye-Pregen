package com.moepus.byepregen.mixin;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

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

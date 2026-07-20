package com.moepus.byepregen.mixin;

import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@MixinGate(conflictingMods = "lithium")
@Mixin(PalettedContainer.class)
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

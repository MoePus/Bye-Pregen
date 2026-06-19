package com.moepus.byepregen.mixin.compat;

import com.moepus.byepregen.gcfree.ChunkSaveHookGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "com.ishland.c2me.base.common.util.HookCompatibility", remap = false)
public abstract class C2MEHookCompatibilityMixin {
    /**
     * @author ByePregen
     * @reason C2ME treats the Architectury NeoForge bridge as a real ChunkDataEvent.Save listener.
     */
    @Overwrite
    public static boolean isChunkSaveEventFree() {
        return ChunkSaveHookGate.CAN_USE_RAW_SAVE;
    }
}

package com.moepus.byepregen.mixin.chunksave.compat.c2me;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.chunksave.compat.ChunkSaveHookGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE, requiredMods = "c2me_base")
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

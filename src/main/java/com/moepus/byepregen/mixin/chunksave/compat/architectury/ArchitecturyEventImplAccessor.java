package com.moepus.byepregen.mixin.chunksave.compat.architectury;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.MixinFeature;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE, requiredMods = "architectury")
@Mixin(targets = "dev.architectury.event.EventFactory$EventImpl", remap = false)
public interface ArchitecturyEventImplAccessor {
    @Accessor("listeners")
    ArrayList<?> byepregen$listeners();
}

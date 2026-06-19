package com.moepus.byepregen.mixin.accessor;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.architectury.event.EventFactory$EventImpl", remap = false)
public interface ArchitecturyEventImplAccessor {
    @Accessor("listeners")
    ArrayList<?> byepregen$listeners();
}

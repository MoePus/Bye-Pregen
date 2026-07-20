package com.moepus.byepregen.mixin;

import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PalettedContainer.class)
public abstract class PalettedContainerMixin {
    @Unique
    private static final ThreadingDetector c6c$dummy = new ThreadingDetector("c6c$PalettedContainer");

    @Redirect(method = "<init>*",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/util/ThreadingDetector;"))
    ThreadingDetector onInit(String p_199415_) {
        return c6c$dummy;
    }
}

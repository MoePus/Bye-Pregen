package com.moepus.byepregen.mixin.palette;

import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PalettedContainer.class, remap = false)
public abstract class PalettedContainerThreadingDetectorMixin {
    @Unique
    private static final ThreadingDetector byepregen$dummy =
            new ThreadingDetector("byepregen$PalettedContainer");

    @Redirect(
            method = "<init>*",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/util/ThreadingDetector;")
    )
    private ThreadingDetector byepregen$replaceThreadingDetector(String name) {
        return byepregen$dummy;
    }
}

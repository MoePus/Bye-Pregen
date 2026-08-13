package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.YABlockLightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(value = BlockLightEngine.class, remap = false)
public abstract class BlockLightEngineYASuperMixin {
    @Unique
    private static final BlockPos.MutableBlockPos byepregen$dummyMutablePos = new BlockPos.MutableBlockPos();

    @Redirect(
            method = "<init>(Lnet/minecraft/world/level/chunk/LightChunkGetter;Lnet/minecraft/world/level/lighting/BlockLightSectionStorage;)V",
            at = @At(value = "NEW", target = "()Lnet/minecraft/core/BlockPos$MutableBlockPos;")
    )
    private BlockPos.MutableBlockPos byepregen$skipMutablePos() {
        return (Object)this instanceof YABlockLightEngine ? byepregen$dummyMutablePos : new BlockPos.MutableBlockPos();
    }
}

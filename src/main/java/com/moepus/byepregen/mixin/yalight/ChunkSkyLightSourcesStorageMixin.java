package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.YAChunkSkyLightSources;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(value = ChunkSkyLightSources.class, remap = false)
public abstract class ChunkSkyLightSourcesStorageMixin {
    @Unique
    private static final SimpleBitStorage byepregen$dummyHeightmap = new SimpleBitStorage(1, 1);

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "(II)Lnet/minecraft/util/SimpleBitStorage;"
            )
    )
    private SimpleBitStorage byepregen$skipYASuperHeightmap(int bits, int size) {
        if ((Object)this instanceof YAChunkSkyLightSources) {
            return byepregen$dummyHeightmap;
        }
        return new SimpleBitStorage(bits, size);
    }
}

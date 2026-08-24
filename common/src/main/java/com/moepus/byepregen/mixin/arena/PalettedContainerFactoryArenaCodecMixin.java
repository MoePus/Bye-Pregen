package com.moepus.byepregen.mixin.arena;

import com.mojang.serialization.Codec;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.palette.arena.codec.StateCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@MixinGate(feature = MixinFeature.ARENA)
@Mixin(value = PalettedContainerFactory.class, remap = false)
public abstract class PalettedContainerFactoryArenaCodecMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Codec<PalettedContainer<BlockState>> byepregen$wrapBlockStateCodec(
            Codec<PalettedContainer<BlockState>> fallback) {
        return fallback instanceof StateCodec ? fallback : new StateCodec(fallback);
    }
}

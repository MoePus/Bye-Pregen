package com.moepus.byepregen.mixin.compat;

import com.moepus.byepregen.FastPalettedContainerAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.codeberg.zenxarch.fastnoise.noise.FastChunkSection", remap = false)
public abstract class FastNoiseFastChunkSectionMixin {
    @Shadow
    @Final
    private LevelChunkSection section;

    @Inject(
            method = "init(Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/chunk/PalettedContainer;data:Lnet/minecraft/world/level/chunk/PalettedContainer$Data;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void byepregen$syncFastPalettedContainer(BlockState state, CallbackInfo ci) {
        PalettedContainer<BlockState> container = this.section.getStates();
        if (container instanceof FastPalettedContainerAccess<?> access) {
            @SuppressWarnings("unchecked")
            FastPalettedContainerAccess<BlockState> typedAccess = (FastPalettedContainerAccess<BlockState>) access;
            typedAccess.byepregen$updateFastData(container.data);
        }
    }
}

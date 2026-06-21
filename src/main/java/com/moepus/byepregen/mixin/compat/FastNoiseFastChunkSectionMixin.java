package com.moepus.byepregen.mixin.compat;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
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

    @Shadow
    private long[] storage;

    @Shadow
    private BlockState[] states;

    @Inject(method = "recalculateCounts", at = @At("HEAD"), remap = false)
    private void byepregen$importArenaContainer(CallbackInfo ci) {
        PalettedContainer<BlockState> container = this.section.getStates();
        if (container instanceof ArenaBlockStatePalettedContainer arenaContainer) {
            arenaContainer.importPackedPalette(this.states, this.storage);
        }
    }
}

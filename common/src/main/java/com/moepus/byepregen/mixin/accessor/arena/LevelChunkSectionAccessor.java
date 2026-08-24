package com.moepus.byepregen.mixin.accessor.arena;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.ARENA, config = ConfigFlag.MATERIALIZE_ARENA_LEVEL_CHUNK)
@Mixin(value = LevelChunkSection.class, remap = false)
public interface LevelChunkSectionAccessor {
    @Mutable
    @Accessor("states")
    void byepregen$setStates(PalettedContainer<BlockState> states);
}

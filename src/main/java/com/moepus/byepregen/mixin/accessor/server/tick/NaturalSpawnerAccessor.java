package com.moepus.byepregen.mixin.accessor.server.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = NaturalSpawner.class, remap = false)
public interface NaturalSpawnerAccessor {
    @Invoker("getRoughBiome")
    static Biome byepregen$getRoughBiome(BlockPos pos, ChunkAccess chunk) {
        throw new AssertionError();
    }
}

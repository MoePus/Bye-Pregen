package com.moepus.byepregen.mixin.gcfree;

import com.moepus.byepregen.gcfree.WorldgenChunkState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerWorldgenStateMixin {
    @InjectLite(method = "write", at = @At("RETURN"))
    private static void byepregen$markVanillaSerializedWorldgenChunk(
            ServerLevel level, ChunkAccess chunk) {
        if (chunk instanceof WorldgenChunkState state) {
            state.byepregen$setFreshWorldgenChunk(false);
        }
    }
}

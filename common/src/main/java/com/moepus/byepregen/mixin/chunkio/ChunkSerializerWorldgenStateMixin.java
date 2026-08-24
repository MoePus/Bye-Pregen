package com.moepus.byepregen.mixin.chunkio;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.chunksave.serialize.WorldgenChunkState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE)
@Mixin(SerializableChunkData.class)
public abstract class ChunkSerializerWorldgenStateMixin {
    @InjectLite(method = "copyOf", at = @At("RETURN"))
    private static void byepregen$markVanillaSerializedWorldgenChunk(
            ServerLevel level, ChunkAccess chunk) {
        if (chunk instanceof WorldgenChunkState state) {
            state.byepregen$setFreshWorldgenChunk(false);
        }
    }
}

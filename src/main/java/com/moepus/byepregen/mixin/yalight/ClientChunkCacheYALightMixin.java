package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.YAImmediateChunkAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheYALightMixin implements YAImmediateChunkAccess {
    @Unique
    private long byepregen$lightChunkKey0 = ChunkPos.INVALID_CHUNK_POS;
    @Unique
    private long byepregen$lightChunkKey1 = ChunkPos.INVALID_CHUNK_POS;
    @Unique
    private LevelChunk byepregen$lightChunk0;
    @Unique
    private LevelChunk byepregen$lightChunk1;

    @Shadow
    @Nullable
    public abstract LevelChunk getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk);

    @Override
    @Nullable
    public LevelChunk byepregen$getAnyChunkNow(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (key == this.byepregen$lightChunkKey0) {
            return this.byepregen$lightChunk0;
        }
        if (key == this.byepregen$lightChunkKey1) {
            return this.byepregen$lightChunk1;
        }

        LevelChunk chunk = this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        this.byepregen$lightChunkKey1 = this.byepregen$lightChunkKey0;
        this.byepregen$lightChunk1 = this.byepregen$lightChunk0;
        this.byepregen$lightChunkKey0 = key;
        this.byepregen$lightChunk0 = chunk;
        return chunk;
    }

    @InjectLite(method = {"drop", "updateViewCenter", "updateViewRadius"}, at = @At("RETURN"))
    private void byepregen$clearLightChunkCacheAfterMutation() {
        this.byepregen$clearLightChunkCache();
    }

    @InjectLite(method = "replaceWithPacketData", at = @At("RETURN"))
    private void byepregen$clearLightChunkCacheOnLoad() {
        this.byepregen$clearLightChunkCache();
    }

    @Unique
    private void byepregen$clearLightChunkCache() {
        this.byepregen$lightChunkKey0 = ChunkPos.INVALID_CHUNK_POS;
        this.byepregen$lightChunkKey1 = ChunkPos.INVALID_CHUNK_POS;
        this.byepregen$lightChunk0 = null;
        this.byepregen$lightChunk1 = null;
    }
}

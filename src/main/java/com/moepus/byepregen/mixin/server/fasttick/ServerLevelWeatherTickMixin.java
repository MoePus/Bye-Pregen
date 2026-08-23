package com.moepus.byepregen.mixin.server.fasttick;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.mixin.accessor.server.tick.ServerChunkCacheTickAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.mixinlite.injector.MethodScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(config = ConfigFlag.FAST_CHUNK_TICKING)
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelWeatherTickMixin {
    @Unique
    private LevelChunk byepregen$currentTickChunk;

    @MethodScope(method = "tickChunk", exit = "byepregen$exitTickChunk")
    private LevelChunk byepregen$enterTickChunk(LevelChunk chunk) {
        ServerLevel level = (ServerLevel) (Object) this;
        ServerChunkCacheTickAccessor cache = (ServerChunkCacheTickAccessor) level.getChunkSource();
        cache.byepregen$storeInCache(chunk.getPos().toLong(), chunk, ChunkStatus.FULL);

        LevelChunk previous = this.byepregen$currentTickChunk;
        this.byepregen$currentTickChunk = chunk;
        return previous;
    }

    @Unique
    private void byepregen$exitTickChunk(LevelChunk previous) {
        this.byepregen$currentTickChunk = previous;
    }

    @Redirect(
            method = "tickPrecipitation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"
            ),
            require = 1,
            allow = 1
    )
    private BlockPos byepregen$getCurrentChunkHeightmapPos(
            ServerLevel level,
            Heightmap.Types type,
            BlockPos pos) {
        LevelChunk chunk = this.byepregen$currentChunkAt(pos);
        if (chunk == null) {
            return level.getHeightmapPos(type, pos);
        }
        int height = chunk.getHeight(type, pos.getX() & 15, pos.getZ() & 15) + 1;
        return new BlockPos(pos.getX(), height, pos.getZ());
    }

    @Redirect(
            method = "tickPrecipitation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"
            ),
            require = 1,
            allow = 1
    )
    private Holder<Biome> byepregen$getCurrentChunkBiome(
            ServerLevel level,
            BlockPos pos) {
        LevelChunk chunk = this.byepregen$currentChunkAt(pos);
        if (chunk == null) {
            return level.getBiome(pos);
        }
        return chunk.getNoiseBiome(
                QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ()));
    }

    @Unique
    private LevelChunk byepregen$currentChunkAt(BlockPos pos) {
        LevelChunk chunk = this.byepregen$currentTickChunk;
        if (chunk == null) {
            return null;
        }
        return chunk.getPos().x == (pos.getX() >> 4) && chunk.getPos().z == (pos.getZ() >> 4)
                ? chunk
                : null;
    }
}

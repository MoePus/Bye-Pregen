package com.moepus.byepregen.compat;

import com.moepus.byepregen.MixinPlugin;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.CompletableFuture;

public final class C2MECompat {
    private static final String C2ME_MOD_ID = "c2me";
    private static final boolean C2ME_INSTALLED = MixinPlugin.isModExist(C2ME_MOD_ID);

    private C2MECompat() {
    }

    public static boolean isC2MEInstalled() {
        return C2ME_INSTALLED;
    }

    public static void managedBlockWithSyncLoad(
            ServerChunkCache cache,
            ChunkMap chunkMap,
            CompletableFuture<?> future,
            int chunkX,
            int chunkZ) {
        C2MECompatImpl.managedBlockWithSyncLoad(cache, chunkMap, future, chunkX, chunkZ);
    }

    public static Long2ByteMap tickingChunksForNaturalSpawning(ChunkMap chunkMap) {
        return C2MECompatImpl.tickingChunksForNaturalSpawning(chunkMap);
    }

    public static LevelChunk chunkForBroadcast(ChunkHolder holder) {
        return C2MECompatImpl.chunkForBroadcast(holder);
    }

    public static void executeTasksMidTick(ServerLevel level) {
        C2MECompatImpl.executeTasksMidTick(level);
    }
}

package com.moepus.byepregen.compat;

import com.ishland.c2me.base.common.scheduler.IVanillaChunkManager;
import com.ishland.c2me.base.common.scheduler.SchedulingManager;
import com.ishland.c2me.base.common.theinterface.IFastChunkHolder;
import com.ishland.c2me.base.mixin.access.IChunkLevelManager;
import com.ishland.c2me.base.mixin.access.ISimulationDistanceLevelPropagator;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

final class C2MECompatImpl {
    private static final Method EXECUTE_TASKS_MID_TICK = getExecuteTasksMidTick();

    private C2MECompatImpl() {
    }

    static void managedBlockWithSyncLoad(
            ServerChunkCache cache,
            ChunkMap chunkMap,
            CompletableFuture<?> future,
            int chunkX,
            int chunkZ) {
        SchedulingManager schedulingManager = ((IVanillaChunkManager) chunkMap).c2me$getSchedulingManager();
        schedulingManager.setCurrentSyncLoad(new ChunkPos(chunkX, chunkZ));
        try {
            ServerChunkCacheAccess.mainThreadProcessor(cache).managedBlock(future::isDone);
        } finally {
            schedulingManager.setCurrentSyncLoad(null);
        }
    }

    static Long2ByteMap tickingChunksForNaturalSpawning(ChunkMap chunkMap) {
        return ((ISimulationDistanceLevelPropagator)
                ((IChunkLevelManager) chunkMap.getDistanceManager()).getSimulationDistanceLevelPropagator())
                .getLevels();
    }

    static LevelChunk chunkForBroadcast(ChunkHolder holder) {
        if (holder instanceof IFastChunkHolder fastChunkHolder) {
            return fastChunkHolder.c2me$immediateWorldChunk();
        }
        ChunkResult<LevelChunk> result = holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK);
        return result.orElse(null);
    }

    static void executeTasksMidTick(ServerLevel level) {
        if (EXECUTE_TASKS_MID_TICK == null) {
            return;
        }
        try {
            EXECUTE_TASKS_MID_TICK.invoke(level.getServer(), level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to execute C2ME mid-tick tasks", e);
        }
    }

    private static Method getExecuteTasksMidTick() {
        try {
            Class<?> type = Class.forName("com.ishland.c2me.opts.scheduling.common.ServerMidTickTask");
            return type.getMethod("executeTasksMidTick", ServerLevel.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }
}

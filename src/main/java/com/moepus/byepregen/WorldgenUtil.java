package com.moepus.byepregen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class WorldgenUtil {
    public static boolean isCrossChunk(final BlockPos origin, final BlockPos target) {
        return SectionPos.blockToSectionCoord(origin.getX()) != SectionPos.blockToSectionCoord(target.getX())
                || SectionPos.blockToSectionCoord(origin.getZ()) != SectionPos.blockToSectionCoord(target.getZ());
    }

    public static boolean isFullChunk(ServerLevelAccessor level, final BlockPos pos) {
        final ChunkAccess chunk = level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL);
    }

    public static boolean isChunkEdge(BlockPos pos, ChunkPos chunkPos) {
        int localX = pos.getX() - chunkPos.getMinBlockX();
        int localZ = pos.getZ() - chunkPos.getMinBlockZ();
        return localX == 0 || localX == 15 || localZ == 0 || localZ == 15;
    }

    public static boolean areEdgeNeighborChunksFull(Level level, BlockPos origin) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        ChunkPos chunkPos = new ChunkPos(origin);
        int localX = origin.getX() - chunkPos.getMinBlockX();
        int localZ = origin.getZ() - chunkPos.getMinBlockZ();

        if (localX > 0 && localX < 15 && localZ > 0 && localZ < 15) {
            return true;
        }

        if (localX == 0 && !isLoadedFullChunk(serverLevel, chunkPos.x - 1, chunkPos.z)) {
            return false;
        }
        if (localX == 15 && !isLoadedFullChunk(serverLevel, chunkPos.x + 1, chunkPos.z)) {
            return false;
        }
        if (localZ == 0 && !isLoadedFullChunk(serverLevel, chunkPos.x, chunkPos.z - 1)) {
            return false;
        }
        if (localZ == 15 && !isLoadedFullChunk(serverLevel, chunkPos.x, chunkPos.z + 1)) {
            return false;
        }

        return true;
    }

    private static boolean isLoadedFullChunk(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        return chunk != null && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL);
    }
}

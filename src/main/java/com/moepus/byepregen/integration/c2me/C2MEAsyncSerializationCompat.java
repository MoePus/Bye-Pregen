package com.moepus.byepregen.integration.c2me;

import com.ishland.c2me.rewrites.chunksystem.common.async_chunkio.AsyncSerializationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class C2MEAsyncSerializationCompat {
    private C2MEAsyncSerializationCompat() {
    }

    public static boolean hasBlockEntities(ChunkAccess chunk) {
        AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(chunk.getPos());
        if (scope != null) {
            return !scope.blockEntities.isEmpty();
        }
        return !chunk.getBlockEntitiesPos().isEmpty();
    }

    public static Iterable<BlockPos> blockEntityPositions(ChunkAccess chunk) {
        AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(chunk.getPos());
        if (scope != null) {
            return scope.blockEntities.keySet();
        }
        return chunk.getBlockEntitiesPos();
    }

    public static Tag blockEntityNbtForSaving(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
        AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(chunk.getPos());
        if (scope != null) {
            return scope.blockEntities.get(pos);
        }
        return chunk.getBlockEntityNbtForSaving(pos, level.registryAccess());
    }
}

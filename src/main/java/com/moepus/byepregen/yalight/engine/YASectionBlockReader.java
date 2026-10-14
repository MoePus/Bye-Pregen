package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.palette.access.BlockStateRawIdAccess;
import com.moepus.byepregen.yalight.access.YAImmediateChunkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.util.function.Supplier;

/** Writer-local readers, selected once per engine rather than per propagation edge. */
abstract class YASectionBlockReader {
    static Supplier<YASectionBlockReader> factoryFor(LightChunkGetter source) {
        // Our immediate-access mixins target the real client/server chunk caches only.
        // Other sources may expose virtual blocks which are absent from their palettes.
        return source instanceof YAImmediateChunkAccess ? Palette::new : BlockStates::new;
    }

    abstract BlockStateRawIdAccess bind(ChunkAccess chunk, int sectionY);

    private static final class Palette extends YASectionBlockReader {
        @Override
        BlockStateRawIdAccess bind(ChunkAccess chunk, int sectionY) {
            return YAChunkRunCache.blockAccessAt(chunk, sectionY);
        }
    }

    private static final class BlockStates extends YASectionBlockReader implements BlockStateRawIdAccess {
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private ChunkAccess chunk;
        private int originX;
        private int originY;
        private int originZ;

        @Override
        BlockStateRawIdAccess bind(ChunkAccess chunk, int sectionY) {
            this.chunk = null;
            if (chunk == null || sectionY < chunk.getMinSection() || sectionY >= chunk.getMaxSection()) {
                return null;
            }
            this.chunk = chunk;
            this.originX = chunk.getPos().getMinBlockX();
            this.originY = sectionY << 4;
            this.originZ = chunk.getPos().getMinBlockZ();
            return this;
        }

        @Override
        public int getRawId(int x, int y, int z) {
            return Block.getId(this.chunk.getBlockState(this.pos.set(
                    this.originX + x, this.originY + y, this.originZ + z)));
        }
    }
}

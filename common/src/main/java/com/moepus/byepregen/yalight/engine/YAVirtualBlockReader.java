package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.palette.access.BlockStateRawIdAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Writer-local adapter for worlds whose block states are not stored in section palettes. */
final class YAVirtualBlockReader implements BlockStateRawIdAccess {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private ChunkAccess chunk;
    private int originX;
    private int originY;
    private int originZ;

    BlockStateRawIdAccess bind(ChunkAccess chunk, int sectionY) {
        this.clear();
        if (chunk == null || sectionY < chunk.getMinSectionY() || sectionY > chunk.getMaxSectionY()) {
            return null;
        }
        this.chunk = chunk;
        this.originX = chunk.getPos().getMinBlockX();
        this.originY = sectionY << 4;
        this.originZ = chunk.getPos().getMinBlockZ();
        return this;
    }

    void clear() {
        this.chunk = null;
    }

    @Override
    public int getRawId(int x, int y, int z) {
        return Block.getId(this.chunk.getBlockState(this.pos.set(
                this.originX + x, this.originY + y, this.originZ + z)));
    }
}

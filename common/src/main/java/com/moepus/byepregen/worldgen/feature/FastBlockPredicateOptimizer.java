package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class FastBlockPredicateOptimizer {
    private FastBlockPredicateOptimizer() {
    }

    public static BlockState getState(WorldGenLevel level, BlockPos pos, Vec3i offset) {
        return getState(level, pos, offset.getX(), offset.getY(), offset.getZ());
    }

    public static BlockState getState(WorldGenLevel level, BlockPos pos, int offsetX, int offsetY, int offsetZ) {
        int x = pos.getX() + offsetX;
        int y = pos.getY() + offsetY;
        int z = pos.getZ() + offsetZ;
        if (level.isOutsideBuildHeight(y)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        if (!(level instanceof WorldGenRegionSectionCache sectionCache)) {
            return level.getBlockState(new BlockPos(x, y, z));
        }

        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        int sectionIndex = level.getSectionIndex(y);
        LevelChunkSection section = sectionCache.byepregen$getCachedSection(sectionX, sectionIndex, sectionZ);
        if (section == null) {
            return Blocks.AIR.defaultBlockState();
        }
        if (section.hasOnlyAir()) {
            return Blocks.AIR.defaultBlockState();
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }
}

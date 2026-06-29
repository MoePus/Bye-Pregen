package com.moepus.byepregen.yalight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class YASkyLightEngine extends YALightLayerEngine {
    // 16 chunk columns plus a one-column border on each side for edge propagation decisions.
    private static final int LOWEST_SOURCE_STRIDE = 18;

    private final YASkySourceCache sourceCache = new YASkySourceCache();
    private final YALightBlockAccess blocks;
    private final short[] lowestByColumn = new short[LOWEST_SOURCE_STRIDE * LOWEST_SOURCE_STRIDE];
    private final int sourceMinY;
    private int innerMinSourceCode;
    private int innerMaxSourceCode;

    YASkyLightEngine(LightChunkGetter chunkGetter, LevelHeightAccessor level) {
        super(chunkGetter, level, LightLayer.SKY);
        this.blocks = new YALightBlockAccess(this.sourceCache, this.chunkGetter, this.levelReader, this.mutablePos);
        this.sourceMinY = level.getMinBuildHeight() - 1;
    }

    @Override
    protected void clearRunCache() {
        this.sourceCache.clear();
    }

    @Override
    public int getLightValue(BlockPos pos) {
        int sectionY = pos.getY() >> 4;
        int sectionIndex = sectionY - this.minLightSection;
        if (sectionY > this.maxLightSection) {
            return 15;
        }
        if (sectionIndex < 0) {
            return 0;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        YANibbleArray nibble = this.storage.getVisibleSectionByIndex(this.storage.chunkAccess(chunkX, chunkZ), sectionIndex);
        if (nibble == null) {
            return this.storage.lightEnabled(chunkX, chunkZ) ? 0 : 15;
        }
        return nibble.getVisible(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected void checkBlockInternal(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        LightChunk chunk = this.sourceCache.chunk(this.chunkGetter, x >> 4, z >> 4);
        if (chunk != null) {
            YAChunkSkyLightSources sources = (YAChunkSkyLightSources) chunk.getSkyLightSources();
            if (sources != null) {
                sources.update(this.blocks, x, y, z);
                this.updateColumn(x, z);
            }
        }

        int source = this.getSourceLight(pos, null);
        int current = this.getCachedUpdatingLight(x, y, z);
        if (source == 15 && current < 15) {
            this.setCachedUpdatingLight(x, y, z, 15);
            this.enqueueSkySourceIncrease(x, y, z, this.skySourceDirections(x, y, z), false);
        } else if (current > source) {
            this.setCachedUpdatingLight(x, y, z, source);
            this.enqueueDecrease(pos, current, YALightMath.ALL_DIRECTIONS);
        } else if (current > 0) {
            this.enqueueDecrease(pos, current, YALightMath.ALL_DIRECTIONS);
            this.enqueueIncrease(pos, current, YALightMath.ALL_DIRECTIONS, YALightMath.FLAG_RECHECK);
        } else {
            this.enqueueDecrease(pos, 1, YALightMath.ALL_DIRECTIONS);
        }
    }

    @Override
    protected void propagateLightSourcesInternal(int chunkX, int chunkZ) {
        this.propagateLightSourcesFromSection(chunkX, this.maxLightSection, chunkZ);
    }

    private void propagateLightSourcesFromSection(int chunkX, int maxSection, int chunkZ) {
        this.sourceCache.centerChunks(chunkX, chunkZ);
        YAChunkSkyLightSources sources = this.sourceCache.sources(this.chunkGetter, chunkX, chunkZ);
        if (sources == null) {
            return;
        }
        // Cache the chunk plus border columns once; section classification and boundary enqueue share it.
        short[] lowestByColumn = this.lowestByColumn;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        this.fillLowestSourceCache(lowestByColumn, minX, minZ);
        int innerMinSourceCode = this.innerMinSourceCode;
        int innerMaxSourceCode = this.innerMaxSourceCode;

        int minBuildY = this.minLightSection << 4;
        int maxBuildY = (this.maxLightSection << 4) | 15;
        int minSection = this.minLightSection;
        for (int sectionY = maxSection; sectionY >= minSection; --sectionY) {
            int bottom = Math.max(minBuildY, sectionY << 4);
            int top = Math.min(maxBuildY, (sectionY << 4) | 15);
            SectionClassification classification = this.classifySkySection(innerMinSourceCode, innerMaxSourceCode, bottom, top);
            if (classification == SectionClassification.FULL) {
                this.storage.setFullSection(chunkX, sectionY, chunkZ);
                this.enqueueFullSectionSourceBoundaries(chunkX, sectionY, chunkZ, lowestByColumn, bottom, top);
                continue;
            }
            if (classification == SectionClassification.EMPTY) {
                continue;
            }
            this.updatePartialSection(chunkX, sectionY, chunkZ, lowestByColumn, bottom, top);
        }
    }

    private void fillLowestSourceCache(short[] lowestByColumn, int minX, int minZ) {
        int innerMinSourceCode = YAChunkSkyLightSources.NO_SOURCE_CODE;
        int innerMaxSourceCode = YAChunkSkyLightSources.NEGATIVE_INFINITY_CODE;
        for (int dz = -1; dz <= 16; ++dz) {
            for (int dx = -1; dx <= 16; ++dx) {
                int sourceCode = this.getCachedSourceCode(minX + dx, minZ + dz);
                lowestByColumn[this.lowestIndex(dx, dz)] = (short) sourceCode;
                if (dx >= 0 && dx < 16 && dz >= 0 && dz < 16) {
                    innerMinSourceCode = Math.min(innerMinSourceCode, sourceCode);
                    innerMaxSourceCode = Math.max(innerMaxSourceCode, sourceCode);
                }
            }
        }
        this.innerMinSourceCode = innerMinSourceCode;
        this.innerMaxSourceCode = innerMaxSourceCode;
    }

    @Override
    protected void propagateIncrease(long pos, long meta) {
        int level = YALightMath.level(meta);
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        int stored = this.getCachedUpdatingLight(x, y, z);
        if ((meta & YALightMath.FLAG_RECHECK) != 0L && stored != level) {
            return;
        }
        if ((meta & YALightMath.FLAG_WRITE_LEVEL) != 0L && stored < level) {
            this.setCachedUpdatingLight(x, y, z, level);
            stored = level;
        }
        if (stored != level) {
            return;
        }

        int fromBlock = Integer.MIN_VALUE;
        int directions = YALightMath.directions(meta);
        while (directions != 0) {
            int directionIndex = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            int toX = x + YALightMath.stepX(directionIndex);
            int toY = y + YALightMath.stepY(directionIndex);
            int toZ = z + YALightMath.stepZ(directionIndex);
            if (!this.canUseSection(toX >> 4, toY >> 4, toZ >> 4)) {
                continue;
            }
            int current = this.getCachedUpdatingLight(toX, toY, toZ);
            if (current >= level - 1) {
                continue;
            }
            int toBlock = this.blocks.blockAt(toX, toY, toZ);
            if (this.blocks.isFull(toBlock)) {
                continue;
            }
            int target = level - this.blocks.opacityAt(toX, toY, toZ, toBlock);
            if (target <= current) {
                continue;
            }
            if (fromBlock == Integer.MIN_VALUE) {
                fromBlock = this.blocks.blockAt(x, y, z);
            }
            if ((fromBlock | toBlock) != 0
                    && this.blocks.shapeOccludes(x, y, z, fromBlock, toX, toY, toZ, toBlock, YALightMath.direction(directionIndex))) {
                continue;
            }
            this.setCachedUpdatingLight(toX, toY, toZ, target);
            if (target > 1) {
                this.enqueueIncrease(BlockPos.asLong(toX, toY, toZ), target, YALightMath.withoutOpposite(directionIndex), 0L);
            }
        }
    }

    @Override
    protected void propagateDecrease(long pos, long meta) {
        int level = YALightMath.level(meta);
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        int directions = YALightMath.directions(meta);
        while (directions != 0) {
            int directionIndex = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            int toX = x + YALightMath.stepX(directionIndex);
            int toY = y + YALightMath.stepY(directionIndex);
            int toZ = z + YALightMath.stepZ(directionIndex);
            if (!this.canUseSection(toX >> 4, toY >> 4, toZ >> 4)) {
                continue;
            }
            int current = this.getCachedUpdatingLight(toX, toY, toZ);
            if (current == 0) {
                continue;
            }
            long toPos = BlockPos.asLong(toX, toY, toZ);
            int toBlock = this.blocks.blockAt(toX, toY, toZ);
            if (this.blocks.isFull(toBlock)) {
                this.setCachedUpdatingLight(toX, toY, toZ, 0);
                continue;
            }
            int target = Math.max(0, level - this.blocks.opacityAt(toX, toY, toZ, toBlock));
            if (current > target) {
                // The neighbor is still supported by some other sky path; verify it in the increase phase.
                this.enqueueIncrease(toPos, current, YALightMath.oppositeMask(directionIndex), YALightMath.FLAG_RECHECK);
                continue;
            }
            this.setCachedUpdatingLight(toX, toY, toZ, 0);
            int source = this.getSourceLight(toPos, null);
            if (source > 0) {
                this.enqueueIncrease(toPos, source, this.skySourceDirections(toX, toY, toZ), YALightMath.FLAG_WRITE_LEVEL);
            }
            this.enqueueDecrease(toPos, target, YALightMath.withoutOpposite(directionIndex));
        }
    }

    private SectionClassification classifySkySection(int innerMinSourceCode, int innerMaxSourceCode, int bottom, int top) {
        int bottomCode = this.sourceYCode(bottom);
        int topCode = this.sourceYCode(top);
        if (innerMaxSourceCode <= bottomCode) {
            return SectionClassification.FULL;
        }
        if (innerMinSourceCode > topCode) {
            return SectionClassification.EMPTY;
        }
        return SectionClassification.MIXED;
    }

    private void updatePartialSection(int chunkX, int sectionY, int chunkZ, short[] lowestByColumn, int bottom, int top) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int dz = 0; dz < 16; ++dz) {
            for (int dx = 0; dx < 16; ++dx) {
                int lowest = this.sourceCode(lowestByColumn, dx, dz);
                int north = this.sourceCode(lowestByColumn, dx, dz - 1);
                int south = this.sourceCode(lowestByColumn, dx, dz + 1);
                int west = this.sourceCode(lowestByColumn, dx - 1, dz);
                int east = this.sourceCode(lowestByColumn, dx + 1, dz);
                int x = minX + dx;
                int z = minZ + dz;
                for (int y = top; y >= bottom; --y) {
                    long pos = BlockPos.asLong(x, y, z);
                    int current = this.getCachedUpdatingLight(x, y, z);
                    int yCode = this.sourceYCode(y);
                    if (yCode >= lowest) {
                        int directions = this.skySourceDirectionsFromVisible(yCode, lowest, north, south, west, east);
                        if (directions != 0) {
                            if (current != 15) {
                                this.setCachedUpdatingLight(x, y, z, 15);
                                this.enqueueSkySourceIncrease(x, y, z, directions, false);
                            } else {
                                this.enqueueSkySourceIncrease(x, y, z, directions, true);
                            }
                        } else if (current != 15) {
                            this.setCachedUpdatingLight(x, y, z, 15);
                        }
                    } else if (current > 0) {
                        this.setCachedUpdatingLight(x, y, z, 0);
                        this.enqueueDecrease(pos, current, YALightMath.ALL_DIRECTIONS);
                    }
                }
            }
        }
    }

    private void enqueueFullSectionSourceBoundaries(int chunkX, int sectionY, int chunkZ, short[] lowestByColumn, int bottom, int top) {
        // A full section is internally all 15, so only its horizontal edges and bottom edge need outward work.
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX | 15;
        int maxZ = minZ | 15;
        for (int y = bottom; y <= top; ++y) {
            for (int dz = 0; dz < 16; ++dz) {
                int westDirections = this.skySourceDirections(lowestByColumn, 0, y, dz) & YALightMath.only(Direction.WEST);
                this.enqueueSkySourceIncrease(minX, y, minZ + dz, westDirections, true);
                int eastDirections = this.skySourceDirections(lowestByColumn, 15, y, dz) & YALightMath.only(Direction.EAST);
                this.enqueueSkySourceIncrease(maxX, y, minZ + dz, eastDirections, true);
            }
            for (int dx = 0; dx < 16; ++dx) {
                int northDirections = this.skySourceDirections(lowestByColumn, dx, y, 0) & YALightMath.only(Direction.NORTH);
                this.enqueueSkySourceIncrease(minX + dx, y, minZ, northDirections, true);
                int southDirections = this.skySourceDirections(lowestByColumn, dx, y, 15) & YALightMath.only(Direction.SOUTH);
                this.enqueueSkySourceIncrease(minX + dx, y, maxZ, southDirections, true);
            }
        }
        for (int dz = 0; dz < 16; ++dz) {
            for (int dx = 0; dx < 16; ++dx) {
                int downDirections = this.skySourceDirections(lowestByColumn, dx, bottom, dz) & YALightMath.only(Direction.DOWN);
                this.enqueueSkySourceIncrease(minX + dx, bottom, minZ + dz, downDirections, true);
            }
        }
    }

    @Override
    protected int getSourceLight(long pos, BlockState state) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        if (!this.sourceCache.enabled(this.storage, x >> 4, z >> 4)) {
            return 0;
        }
        return this.sourceYCode(y) >= this.getCachedSourceCode(x, z) ? 15 : 0;
    }

    private void updateColumn(int x, int z) {
        if (!this.sourceCache.enabled(this.storage, x >> 4, z >> 4)) {
            return;
        }
        int lowest = this.getCachedSourceCode(x, z);
        int north = this.getCachedSourceCode(x, z - 1);
        int south = this.getCachedSourceCode(x, z + 1);
        int west = this.getCachedSourceCode(x - 1, z);
        int east = this.getCachedSourceCode(x + 1, z);
        int minY = this.minLightSection << 4;
        int maxY = (this.maxLightSection << 4) | 15;

        for (int y = maxY; y >= minY; --y) {
            long pos = BlockPos.asLong(x, y, z);
            int current = this.getCachedUpdatingLight(x, y, z);
            int yCode = this.sourceYCode(y);
            if (yCode >= lowest) {
                int directions = this.skySourceDirectionsFromVisible(yCode, lowest, north, south, west, east);
                if (directions != 0) {
                    if (current != 15) {
                        this.setCachedUpdatingLight(x, y, z, 15);
                        this.enqueueSkySourceIncrease(x, y, z, directions, false);
                    } else {
                        this.enqueueSkySourceIncrease(x, y, z, directions, true);
                    }
                } else if (current != 15) {
                    this.setCachedUpdatingLight(x, y, z, 15);
                }
            } else if (current > 0) {
                this.setCachedUpdatingLight(x, y, z, 0);
                this.enqueueDecrease(pos, current, YALightMath.ALL_DIRECTIONS);
            }
        }
    }

    private int getCachedUpdatingLight(int x, int y, int z) {
        return this.sourceCache.getUpdatingLight(this.storage, x, y, z);
    }

    private void setCachedUpdatingLight(int x, int y, int z, int value) {
        this.sourceCache.setUpdatingLight(this.storage, x, y, z, value);
    }

    private void enqueueSkySourceIncrease(int x, int y, int z, int directions, boolean recheck) {
        if (directions == 0) {
            return;
        }
        this.enqueueIncrease(BlockPos.asLong(x, y, z), 15, directions, recheck ? YALightMath.FLAG_RECHECK : 0L);
    }

    private int skySourceDirections(int x, int y, int z) {
        int yCode = this.sourceYCode(y);
        int lowest = this.getCachedSourceCode(x, z);
        if (yCode < lowest) {
            return 0;
        }
        return this.skySourceDirectionsFromVisible(
                yCode,
                lowest,
                this.getCachedSourceCode(x, z - 1),
                this.getCachedSourceCode(x, z + 1),
                this.getCachedSourceCode(x - 1, z),
                this.getCachedSourceCode(x + 1, z)
        );
    }

    private int skySourceDirections(short[] lowestByColumn, int localX, int y, int localZ) {
        int yCode = this.sourceYCode(y);
        int lowest = this.sourceCode(lowestByColumn, localX, localZ);
        if (yCode < lowest) {
            return 0;
        }
        return this.skySourceDirectionsFromVisible(
                yCode,
                lowest,
                this.sourceCode(lowestByColumn, localX, localZ - 1),
                this.sourceCode(lowestByColumn, localX, localZ + 1),
                this.sourceCode(lowestByColumn, localX - 1, localZ),
                this.sourceCode(lowestByColumn, localX + 1, localZ)
        );
    }

    private int skySourceDirectionsFromVisible(int yCode, int lowest, int north, int south, int west, int east) {
        int directions = 0;
        if (yCode == lowest) {
            directions |= YALightMath.only(Direction.DOWN);
        }
        if (yCode < north) {
            directions |= YALightMath.only(Direction.NORTH);
        }
        if (yCode < south) {
            directions |= YALightMath.only(Direction.SOUTH);
        }
        if (yCode < west) {
            directions |= YALightMath.only(Direction.WEST);
        }
        if (yCode < east) {
            directions |= YALightMath.only(Direction.EAST);
        }
        return directions;
    }

    private int getCachedSourceCode(int x, int z) {
        return this.sourceCache.sourceCode(this.chunkGetter, x, z);
    }

    @Override
    protected boolean canUseSection(int chunkX, int sectionY, int chunkZ) {
        return sectionY >= this.minLightSection
                && sectionY <= this.maxLightSection
                && this.sourceCache.lightEnabled(this.storage, chunkX, chunkZ);
    }

    private int sourceYCode(int y) {
        // Vanilla exposes a sky source at negative infinity when a column is open
        // through the bottom of the world. The packed YA source code represents
        // that as 0, so every block in the bottom light sentinel must compare as
        // at-or-above that source instead of becoming a negative code.
        return Math.max(0, y - this.sourceMinY);
    }

    private int sourceCode(short[] lowestByColumn, int localX, int localZ) {
        return lowestByColumn[this.lowestIndex(localX, localZ)] & 0xFFFF;
    }

    private int lowestIndex(int localX, int localZ) {
        return (localZ + 1) * LOWEST_SOURCE_STRIDE + localX + 1;
    }

    private enum SectionClassification {
        FULL,
        EMPTY,
        MIXED
    }
}

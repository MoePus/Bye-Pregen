package com.moepus.byepregen.yalight.sky;

import com.moepus.byepregen.yalight.engine.YALightMath;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YANibbleArray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;

final class YASkySourcePropagator {
    private static final int LOWEST_SOURCE_STRIDE = 18;
    private static final int NIBBLE_Y_STRIDE = 1 << 8;

    private final YASkyLightEngine engine;
    private final YASkySourceCache sourceCache;
    private final int sourceMinY;
    private final short[] lowestByColumn = new short[LOWEST_SOURCE_STRIDE * LOWEST_SOURCE_STRIDE];
    private final YASkyFreshSourceInitializer freshSourceInitializer;
    private int firstFullSourceSection;
    private int lastEmptySourceSection;

    YASkySourcePropagator(YASkyLightEngine engine, YASkySourceCache sourceCache, int sourceMinY) {
        this.engine = engine;
        this.sourceCache = sourceCache;
        this.sourceMinY = sourceMinY;
        this.freshSourceInitializer = new YASkyFreshSourceInitializer(engine, this, sourceMinY);
    }

    void repairDirtyColumn(int x, int z) {
        YAChunkSkyLightSources sources = this.sourceCache.enabledSources(
                this.engine.storage, x >> 4, z >> 4);
        if (sources == null) {
            return;
        }
        if (!sources.consumeSourceDirty(x, z)) {
            return;
        }
        // Chunk writers maintain the source height. Re-reading blocks on the light thread can
        // publish a stale height that update() will never revisit.
        int lowestY = sources.getLowestSourceY(x, z);
        this.removeSourcesBelow(x, z, lowestY);
        this.addSourcesAbove(x, z, lowestY);
    }

    boolean propagateLightSources(ChunkAccess lightChunk, boolean fresh) {
        int chunkX = lightChunk.getPos().x();
        int chunkZ = lightChunk.getPos().z();
        this.sourceCache.centerChunks(chunkX, chunkZ);
        long chunkKey = ChunkPos.pack(chunkX, chunkZ);
        YAChunkLightData data = this.engine.storage.data(lightChunk);
        this.propagateCachedLightSources(chunkKey, data, fresh);
        return true;
    }

    private void propagateCachedLightSources(long chunkKey, YAChunkLightData data, boolean fresh) {
        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        this.fillLowestSourceCache(chunkX, chunkZ);
        int minBuildY = this.engine.minLightSection << 4;
        int maxBuildY = (this.engine.maxLightSection << 4) | 15;
        int fullBottomSection = Math.max(this.engine.minLightSection, this.firstFullSourceSection);
        this.fillFullSections(data, fullBottomSection);
        if (!fresh && fullBottomSection <= this.engine.maxLightSection) {
            this.enqueueFullSectionSourceBoundaries(chunkX, chunkZ, Math.max(minBuildY, fullBottomSection << 4));
        }
        this.fillMixedSections(chunkKey, data, fresh);
        if (fresh) {
            this.freshSourceInitializer.emitFrontier(
                    chunkX, chunkZ, minBuildY, maxBuildY, YALightMath.FLAG_FRESH_OWNER_TRANSFER);
        }
    }

    private void fillFullSections(YAChunkLightData data, int bottomSection) {
        for (int sectionY = this.engine.maxLightSection; sectionY >= bottomSection; --sectionY) {
            this.engine.storage.setFullSection(data, sectionY);
        }
    }

    private void fillMixedSections(long chunkKey, YAChunkLightData data, boolean fresh) {
        int top = Math.min(this.engine.maxLightSection, this.firstFullSourceSection - 1);
        int bottom = Math.max(this.engine.minLightSection, this.lastEmptySourceSection + 1);
        for (int sectionY = top; sectionY >= bottom; --sectionY) {
            if (fresh) {
                this.freshSourceInitializer.initializePartialSection(data, sectionY);
            } else {
                this.updatePartialSection(chunkKey, data, sectionY);
            }
        }
    }

    private void fillLowestSourceCache(int chunkX, int chunkZ) {
        YAChunkSkyLightSources north = this.sourceCache.sources(this.engine.chunkGetter, chunkX, chunkZ - 1);
        YAChunkSkyLightSources west = this.sourceCache.sources(this.engine.chunkGetter, chunkX - 1, chunkZ);
        YAChunkSkyLightSources center = this.sourceCache.sources(this.engine.chunkGetter, chunkX, chunkZ);
        YAChunkSkyLightSources east = this.sourceCache.sources(this.engine.chunkGetter, chunkX + 1, chunkZ);
        YAChunkSkyLightSources south = this.sourceCache.sources(this.engine.chunkGetter, chunkX, chunkZ + 1);
        int innerMinSourceCode = YAChunkSkyLightSources.NO_SOURCE_CODE;
        int innerMaxSourceCode = YAChunkSkyLightSources.NEGATIVE_INFINITY_CODE;
        for (int dz = 0; dz < 16; ++dz) {
            for (int dx = 0; dx < 16; ++dx) {
                int code = sourceCode(center, dx, dz);
                this.lowestByColumn[this.lowestIndex(dx, dz)] = (short)code;
                innerMinSourceCode = Math.min(innerMinSourceCode, code);
                innerMaxSourceCode = Math.max(innerMaxSourceCode, code);
            }
        }
        this.fillSourceBorder(north, -1);
        this.fillSourceSides(west, east);
        this.fillSourceBorder(south, 16);
        this.firstFullSourceSection = innerMaxSourceCode == YAChunkSkyLightSources.NEGATIVE_INFINITY_CODE
                ? this.engine.minLightSection
                : Math.floorDiv(this.sourceMinY + innerMaxSourceCode + 15, 16);
        this.lastEmptySourceSection = Math.floorDiv(this.sourceMinY + innerMinSourceCode - 16, 16);
    }

    private void fillSourceBorder(YAChunkSkyLightSources center, int localZ) {
        int sourceZ = localZ < 0 ? 15 : 0;
        for (int dx = 0; dx < 16; ++dx) {
            this.lowestByColumn[this.lowestIndex(dx, localZ)] = (short)sourceCode(center, dx, sourceZ);
        }
    }

    private void fillSourceSides(YAChunkSkyLightSources west, YAChunkSkyLightSources east) {
        for (int dz = 0; dz < 16; ++dz) {
            this.lowestByColumn[this.lowestIndex(-1, dz)] = (short)sourceCode(west, 15, dz);
            this.lowestByColumn[this.lowestIndex(16, dz)] = (short)sourceCode(east, 0, dz);
        }
    }

    private static int sourceCode(YAChunkSkyLightSources sources, int x, int z) {
        return sources == null ? YAChunkSkyLightSources.NO_SOURCE_CODE : sources.sourceCode(x, z);
    }

    private void updatePartialSection(long chunkKey, YAChunkLightData data, int sectionY) {
        if (data == null) {
            return;
        }
        int minX = ChunkPos.getX(chunkKey) << 4;
        int minZ = ChunkPos.getZ(chunkKey) << 4;
        int sectionIndex = this.engine.storage.sectionIndex(sectionY);
        // A classified mixed section always contains a direct source, so creation is inevitable.
        YANibbleArray nibble = data.getOrCreateUpdatingSectionByIndex(sectionIndex);
        boolean changed = false;
        for (int dz = 0; dz < 16; ++dz) {
            for (int dx = 0; dx < 16; ++dx) {
                changed |= this.updatePartialColumn(nibble, sectionIndex, minX, minZ, dx, dz);
            }
        }
        if (changed) {
            this.engine.storage.markDirty(data, sectionIndex);
        }
    }

    private boolean updatePartialColumn(
            YANibbleArray nibble,
            int sectionIndex,
            int minX,
            int minZ,
            int dx,
            int dz
    ) {
        int centerIndex = this.lowestIndex(dx, dz);
        int lowest = this.sourceCodeAt(centerIndex);
        int north = this.sourceCodeAt(centerIndex - LOWEST_SOURCE_STRIDE);
        int south = this.sourceCodeAt(centerIndex + LOWEST_SOURCE_STRIDE);
        int west = this.sourceCodeAt(centerIndex - 1);
        int east = this.sourceCodeAt(centerIndex + 1);
        int x = minX + dx;
        int z = minZ + dz;
        int y = ((sectionIndex + this.engine.minLightSection) << 4) | 15;
        int localIndex = YANibbleArray.index(dx, 15, dz);
        boolean changed = false;
        for (int localY = 15; localY >= 0; --localY) {
            int current = nibble.getUpdating(localIndex);
            int yCode = this.sourceYCode(y);
            if (yCode >= lowest) {
                int directions = this.skySourceDirectionsFromVisible(
                        yCode, lowest, north, south, west, east);
                changed |= this.setSourceCell(nibble, x, y, z, localIndex, current, directions);
            } else if (current > 0) {
                nibble.setUpdating(localIndex, 0);
                this.engine.enqueueDecrease(BlockPos.asLong(x, y, z), current, YALightMath.ALL_DIRECTIONS);
                changed = true;
            }
            --y;
            localIndex -= NIBBLE_Y_STRIDE;
        }
        return changed;
    }

    private boolean setSourceCell(
            YANibbleArray nibble,
            int x,
            int y,
            int z,
            int localIndex,
            int current,
            int directions
    ) {
        if (current == 15) {
            if (directions != 0) {
                this.enqueueSkySourceIncrease(x, y, z, directions, true);
            }
            return false;
        }
        nibble.setUpdating(localIndex, 15);
        if (directions != 0) {
            this.enqueueSkySourceIncrease(x, y, z, directions, false);
        }
        return true;
    }

    private void enqueueFullSectionSourceBoundaries(int chunkX, int chunkZ, int bottom) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX | 15;
        int maxZ = minZ | 15;
        int top = (this.engine.maxLightSection << 4) | 15;
        for (int dz = 0; dz < 16; ++dz) {
            this.enqueueSideRange(minX, minZ + dz, bottom, this.fullSourceRangeTop(this.sourceCode(-1, dz), top), Direction.WEST);
            this.enqueueSideRange(maxX, minZ + dz, bottom, this.fullSourceRangeTop(this.sourceCode(16, dz), top), Direction.EAST);
        }
        for (int dx = 0; dx < 16; ++dx) {
            this.enqueueSideRange(minX + dx, minZ, bottom, this.fullSourceRangeTop(this.sourceCode(dx, -1), top), Direction.NORTH);
            this.enqueueSideRange(minX + dx, maxZ, bottom, this.fullSourceRangeTop(this.sourceCode(dx, 16), top), Direction.SOUTH);
        }
        this.enqueueFullSectionBottomSources(minX, minZ, bottom);
    }

    private void enqueueSideRange(int x, int z, int bottom, int top, Direction direction) {
        for (int y = bottom; y <= top; ++y) {
            this.enqueueSkySourceIncrease(x, y, z, YALightMath.only(direction), true);
        }
    }

    private int fullSourceRangeTop(int sourceCode, int top) {
        return sourceCode == YAChunkSkyLightSources.NEGATIVE_INFINITY_CODE
                ? Integer.MIN_VALUE
                : Math.min(top, this.sourceMinY + sourceCode - 1);
    }

    private void enqueueFullSectionBottomSources(int minX, int minZ, int bottom) {
        int bottomCode = this.sourceYCode(bottom);
        for (int dz = 0; dz < 16; ++dz) {
            for (int dx = 0; dx < 16; ++dx) {
                if (bottomCode == this.sourceCode(dx, dz)) {
                    this.enqueueSkySourceIncrease(
                            minX + dx, bottom, minZ + dz, YALightMath.only(Direction.DOWN), true);
                }
            }
        }
    }

    private void removeSourcesBelow(int x, int z, int lowestY) {
        int minY = this.engine.minLightSection << 4;
        if (lowestY <= minY) {
            return;
        }
        for (int y = lowestY - 1; y >= minY; --y) {
            if (this.engine.getCachedUpdatingLight(x, y, z) == 15) {
                this.engine.setCachedUpdatingLight(x, y, z, 0);
                this.engine.enqueueDecrease(BlockPos.asLong(x, y, z), 15, YALightMath.ALL_DIRECTIONS);
            }
        }
    }

    private void addSourcesAbove(int x, int z, int lowestY) {
        int minY = this.engine.minLightSection << 4;
        int maxY = (this.engine.maxLightSection << 4) | 15;
        for (int y = Math.max(lowestY, minY); y <= maxY; ++y) {
            if (this.engine.getCachedUpdatingLight(x, y, z) != 15) {
                this.engine.setCachedUpdatingLight(x, y, z, 15);
                this.enqueueSkySourceIncrease(x, y, z, this.skySourceDirections(x, y, z), false);
            }
        }
    }

    void restoreSavedSkyLight(ChunkAccess chunk) {
        if (chunk == null) {
            return;
        }
        YAChunkSkyLightSources sources = (YAChunkSkyLightSources)chunk.getSkyLightSources();
        YAChunkLightData data = this.engine.storage.data(chunk);
        if (data == null) {
            return;
        }
        int highestLowestSourceY = sources.getHighestLowestSourceY();
        for (int sectionY = this.engine.maxLightSection; sectionY >= this.engine.minLightSection; --sectionY) {
            if ((sectionY << 4) < highestLowestSourceY) {
                return;
            }
            if (data.getUpdatingSectionByIndex(sectionY - this.engine.minLightSection) == null) {
                this.engine.storage.setFullSection(data, sectionY);
            }
        }
    }

    void enqueueSkySourceIncrease(int x, int y, int z, int directions, boolean recheck) {
        long flags = recheck ? YALightMath.FLAG_RECHECK : 0L;
        this.engine.enqueueIncrease(BlockPos.asLong(x, y, z), 15, directions, flags);
    }

    int skySourceDirections(int x, int y, int z) {
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
                this.getCachedSourceCode(x + 1, z));
    }

    private int skySourceDirectionsFromVisible(int yCode, int lowest, int north, int south, int west, int east) {
        int directions = yCode == lowest ? YALightMath.only(Direction.DOWN) : 0;
        directions |= yCode < north ? YALightMath.only(Direction.NORTH) : 0;
        directions |= yCode < south ? YALightMath.only(Direction.SOUTH) : 0;
        directions |= yCode < west ? YALightMath.only(Direction.WEST) : 0;
        directions |= yCode < east ? YALightMath.only(Direction.EAST) : 0;
        return directions;
    }

    int getCachedSourceCode(int x, int z) {
        return this.sourceCache.sourceCode(this.engine.chunkGetter, x, z);
    }

    int sourceYCode(int y) {
        return Math.max(0, y - this.sourceMinY);
    }

    int sourceCode(int localX, int localZ) {
        return this.sourceCodeAt(this.lowestIndex(localX, localZ));
    }

    private int sourceCodeAt(int index) {
        return this.lowestByColumn[index] & 0xFFFF;
    }

    private int lowestIndex(int localX, int localZ) {
        return (localZ + 1) * LOWEST_SOURCE_STRIDE + localX + 1;
    }
}

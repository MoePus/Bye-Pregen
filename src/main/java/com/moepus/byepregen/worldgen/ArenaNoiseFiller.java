package com.moepus.byepregen.worldgen;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Layout;
import com.moepus.byepregen.mixin.NoiseChunkAccessor;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;

public final class ArenaNoiseFiller {
    private static final int CHUNK_WIDTH = 16;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Set<Heightmap.Types> WORLDGEN_HEIGHTMAPS = Set.of(
            Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.WORLD_SURFACE_WG
    );

    private ArenaNoiseFiller() {
    }

    public static ChunkAccess fill(NoiseChunk noiseChunk, Request request) {
        return new FillState(noiseChunk, request).fill();
    }

    public record Request(
            ChunkAccess chunk,
            BlockState defaultBlock,
            int minCellY,
            int cellCountY
    ) {
    }

    private static final class FillState {
        private final NoiseChunk noiseChunk;
        private final NoiseChunkAccessor noiseChunkAccess;
        private final Request request;
        private final ChunkAccess chunk;
        private final LevelChunkSection[] sections;
        private final ArenaBlockStatePalettedContainer[] containers;
        private final boolean[] touchedSections;
        private final Aquifer aquifer;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private final int chunkStartX;
        private final int chunkStartZ;
        private final int cellWidth;
        private final int cellHeight;
        private final int horizontalCellCount;
        private ArenaBlockStatePalettedContainer currentContainer;
        private int currentSectionIndex;
        private int currentBlockY;
        private int currentLocalY;
        private int currentCellZ;

        private FillState(NoiseChunk noiseChunk, Request request) {
            this.noiseChunk = noiseChunk;
            this.noiseChunkAccess = (NoiseChunkAccessor) noiseChunk;
            this.request = request;
            this.chunk = request.chunk();
            this.sections = this.chunk.getSections();
            this.containers = arenaContainers(this.sections);
            this.touchedSections = new boolean[this.sections.length];
            this.aquifer = noiseChunk.aquifer();
            ChunkPos chunkPos = this.chunk.getPos();
            this.chunkStartX = chunkPos.getMinBlockX();
            this.chunkStartZ = chunkPos.getMinBlockZ();
            this.cellWidth = this.noiseChunkAccess.byepregen$cellWidth();
            this.cellHeight = this.noiseChunkAccess.byepregen$cellHeight();
            this.horizontalCellCount = CHUNK_WIDTH / this.cellWidth;
        }

        private ChunkAccess fill() {
            this.noiseChunk.initializeForFirstCellX();
            for (int cellX = 0; cellX < this.horizontalCellCount; ++cellX) {
                this.fillCellX(cellX);
                this.noiseChunk.swapSlices();
            }
            this.noiseChunk.stopInterpolation();
            this.finishSections();
            Heightmap.primeHeightmaps(this.chunk, WORLDGEN_HEIGHTMAPS);
            return this.chunk;
        }

        private void fillCellX(int cellX) {
            this.noiseChunk.advanceCellX(cellX);
            for (int cellY = this.request.cellCountY() - 1; cellY >= 0; --cellY) {
                for (int cellZ = 0; cellZ < this.horizontalCellCount; ++cellZ) {
                    this.noiseChunk.selectCellYZ(cellY, cellZ);
                    this.fillCellY(cellX, cellY, cellZ);
                }
            }
        }

        private void fillCellY(int cellX, int cellY, int cellZ) {
            for (int blockYInCell = this.cellHeight - 1; blockYInCell >= 0; --blockYInCell) {
                int blockY = (this.request.minCellY() + cellY) * this.cellHeight + blockYInCell;
                this.noiseChunk.updateForY(blockY, (double) blockYInCell / this.cellHeight);
                this.fillBlockLayer(cellX, cellZ, blockY);
            }
        }

        private void fillBlockLayer(int cellX, int cellZ, int blockY) {
            this.currentSectionIndex = this.chunk.getSectionIndex(blockY);
            this.currentContainer = this.containers[this.currentSectionIndex];
            this.currentBlockY = blockY;
            this.currentLocalY = blockY & 15;
            this.currentCellZ = cellZ;
            for (int blockXInCell = 0; blockXInCell < this.cellWidth; ++blockXInCell) {
                int blockX = this.chunkStartX + cellX * this.cellWidth + blockXInCell;
                this.noiseChunk.updateForX(blockX, (double) blockXInCell / this.cellWidth);
                this.fillBlockLine(blockX);
            }
        }

        private void fillBlockLine(int blockX) {
            for (int blockZInCell = 0; blockZInCell < this.cellWidth; ++blockZInCell) {
                int blockZ = this.chunkStartZ + this.currentCellZ * this.cellWidth + blockZInCell;
                this.noiseChunk.updateForZ(blockZ, (double) blockZInCell / this.cellWidth);
                this.writeBlock(blockX, blockZ);
            }
        }

        private void writeBlock(int blockX, int blockZ) {
            BlockState state = this.noiseChunkAccess.byepregen$getInterpolatedState();
            if (state == null) {
                state = this.request.defaultBlock();
            }
            if (state == AIR) {
                return;
            }

            int localIndex = Layout.localIndex(blockX & 15, this.currentLocalY, blockZ & 15);
            this.currentContainer.setRawId(localIndex, ArenaBlockStatePalettedContainer.rawId(state));
            this.touchedSections[this.currentSectionIndex] = true;
            if (this.aquifer.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
                this.mutablePos.set(blockX, this.currentBlockY, blockZ);
                this.chunk.markPosForPostprocessing(this.mutablePos);
            }
        }

        private void finishSections() {
            for (int i = 0; i < this.touchedSections.length; ++i) {
                if (this.touchedSections[i]) {
                    this.sections[i].recalcBlockCounts();
                }
            }
        }

        private static ArenaBlockStatePalettedContainer[] arenaContainers(LevelChunkSection[] sections) {
            ArenaBlockStatePalettedContainer[] containers = new ArenaBlockStatePalettedContainer[sections.length];
            for (int i = 0; i < sections.length; ++i) {
                containers[i] = (ArenaBlockStatePalettedContainer) sections[i].getStates();
            }
            return containers;
        }
    }
}

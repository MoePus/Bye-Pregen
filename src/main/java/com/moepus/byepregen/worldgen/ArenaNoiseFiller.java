package com.moepus.byepregen.worldgen;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaPageBuildBuffer;
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
    private static final int AIR_RAW_ID = ArenaBlockStatePalettedContainer.rawId(AIR);
    private static final double[] DELTAS_4 = exactDeltas(4);
    private static final double[] DELTAS_8 = exactDeltas(8);
    private static final double[] DELTAS_12 = exactDeltas(12);
    private static final double[] DELTAS_16 = exactDeltas(16);
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
        private final ArenaNoiseChunkAccess arenaNoiseChunk;
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
        private final double[] horizontalDeltas;
        private final double[] verticalDeltas;
        private final ArenaPageBuildBuffer[] pageBuffers;
        private int currentCellX;
        private int currentCellY;
        private int currentCellZ;
        private int slabMinBlockY;

        private FillState(NoiseChunk noiseChunk, Request request) {
            this.noiseChunk = noiseChunk;
            this.noiseChunkAccess = (NoiseChunkAccessor) noiseChunk;
            this.arenaNoiseChunk = (ArenaNoiseChunkAccess) noiseChunk;
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
            this.horizontalDeltas = deltasFor(this.cellWidth);
            this.verticalDeltas = deltasFor(this.cellHeight);
            this.pageBuffers = createPageBuffers(this.cellHeight / Layout.PAGE_HEIGHT);
        }

        private ChunkAccess fill() {
            this.arenaNoiseChunk.byepregen$initializeArenaInterpolation();
            try {
                this.fillCells();
            } finally {
                try {
                    this.arenaNoiseChunk.byepregen$releaseArenaInterpolation();
                } finally {
                    this.noiseChunk.stopInterpolation();
                }
            }
            this.finishSections();
            Heightmap.primeHeightmaps(this.chunk, WORLDGEN_HEIGHTMAPS);
            return this.chunk;
        }

        private void fillCells() {
            for (int cellY = this.request.cellCountY() - 1; cellY >= 0; --cellY) {
                this.currentCellY = cellY;
                this.slabMinBlockY = (this.request.minCellY() + cellY) * this.cellHeight;
                for (ArenaPageBuildBuffer pageBuffer : this.pageBuffers) {
                    pageBuffer.reset(AIR_RAW_ID);
                }
                for (int cellZ = 0; cellZ < this.horizontalCellCount; ++cellZ) {
                    this.currentCellZ = cellZ;
                    for (int cellX = 0; cellX < this.horizontalCellCount; ++cellX) {
                        this.currentCellX = cellX;
                        this.fillCell();
                    }
                }
                this.commitPages();
            }
        }

        private void fillCell() {
            this.arenaNoiseChunk.byepregen$selectArenaCell(
                    this.currentCellX,
                    this.currentCellY,
                    this.currentCellZ
            );
            int cellStartX = this.chunkStartX + this.currentCellX * this.cellWidth;
            for (int blockXInCell = 0; blockXInCell < this.cellWidth; ++blockXInCell) {
                int blockX = cellStartX + blockXInCell;
                this.arenaNoiseChunk.byepregen$updateArenaForX(
                        blockX,
                        this.horizontalDeltas[blockXInCell]
                );
                this.fillBlockColumnsForX(blockX);
            }
        }

        private void fillBlockColumnsForX(int blockX) {
            int cellStartZ = this.chunkStartZ + this.currentCellZ * this.cellWidth;
            for (int blockZInCell = 0; blockZInCell < this.cellWidth; ++blockZInCell) {
                int blockZ = cellStartZ + blockZInCell;
                this.arenaNoiseChunk.byepregen$updateArenaForZ(
                        blockZ,
                        this.horizontalDeltas[blockZInCell]
                );
                for (int blockYInCell = this.cellHeight - 1; blockYInCell >= 0; --blockYInCell) {
                    this.sampleBlock(blockX, blockYInCell, blockZ);
                }
            }
        }

        private void sampleBlock(int blockX, int blockYInSlab, int blockZ) {
            int blockY = this.slabMinBlockY + blockYInSlab;
            this.arenaNoiseChunk.byepregen$updateArenaForY(
                    blockY,
                    this.verticalDeltas[blockYInSlab]
            );
            BlockState state = this.noiseChunkAccess.byepregen$getInterpolatedState();
            if (state == null) {
                state = this.request.defaultBlock();
            }
            if (this.aquifer.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
                this.mutablePos.set(blockX, blockY, blockZ);
                this.chunk.markPosForPostprocessing(this.mutablePos);
            }
            if (state != AIR) {
                int pageInSlab = blockYInSlab / Layout.PAGE_HEIGHT;
                int pageLocalY = blockYInSlab & (Layout.PAGE_HEIGHT - 1);
                int pageLocalIndex = Layout.localIndex(blockX & 15, pageLocalY, blockZ & 15);
                this.pageBuffers[pageInSlab].setRawId(
                        pageLocalIndex,
                        ArenaBlockStatePalettedContainer.rawId(state)
                );
            }
        }

        private void commitPages() {
            for (int pageInSlab = 0; pageInSlab < this.pageBuffers.length; ++pageInSlab) {
                int blockY = this.slabMinBlockY + pageInSlab * Layout.PAGE_HEIGHT;
                int sectionIndex = this.chunk.getSectionIndex(blockY);
                ArenaBlockStatePalettedContainer container = this.containers[sectionIndex];
                ArenaPageBuildBuffer pageBuffer = this.pageBuffers[pageInSlab];
                container.writePage((blockY & 15) / Layout.PAGE_HEIGHT, pageBuffer);
                if (!pageBuffer.isUniformRawId(AIR_RAW_ID)) {
                    this.touchedSections[sectionIndex] = true;
                }
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

        private static ArenaPageBuildBuffer[] createPageBuffers(int count) {
            ArenaPageBuildBuffer[] buffers = new ArenaPageBuildBuffer[count];
            for (int i = 0; i < count; ++i) {
                buffers[i] = new ArenaPageBuildBuffer();
            }
            return buffers;
        }

        private static double[] deltasFor(int size) {
            return switch (size) {
                case 4 -> DELTAS_4;
                case 8 -> DELTAS_8;
                case 12 -> DELTAS_12;
                case 16 -> DELTAS_16;
                default -> exactDeltas(size);
            };
        }
    }

    private static double[] exactDeltas(int size) {
        double[] deltas = new double[size];
        for (int i = 0; i < size; ++i) {
            deltas[i] = (double) i / size;
        }
        return deltas;
    }
}

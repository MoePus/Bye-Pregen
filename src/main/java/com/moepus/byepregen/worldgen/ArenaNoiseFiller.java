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

    public static ChunkAccess fill(NoiseChunk noiseChunk, Request request, TargetSections targets) {
        return new FillState(noiseChunk, request, targets).fill();
    }

    public static TargetSections targetSections(Request request, int cellHeight) {
        int minBlockY = request.minCellY() * cellHeight;
        int maxBlockY = minBlockY + request.cellCountY() * cellHeight - 1;
        return new TargetSections(
                request.chunk().getSectionIndex(minBlockY),
                request.chunk().getSectionIndex(maxBlockY)
        );
    }

    public static boolean hasFreshAirTargets(Request request, TargetSections targets) {
        LevelChunkSection[] sections = request.chunk().getSections();
        for (int sectionIndex = targets.first(); sectionIndex <= targets.last(); ++sectionIndex) {
            if (!(sections[sectionIndex].getStates() instanceof ArenaBlockStatePalettedContainer container)
                    || !container.isFreshAirForWorldgen()) {
                return false;
            }
        }
        return true;
    }

    public record Request(
            ChunkAccess chunk,
            BlockState defaultBlock,
            int minCellY,
            int cellCountY
    ) {
    }

    public record TargetSections(int first, int last) {
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
        private final ArenaMaterialEvaluator materialEvaluator;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private final int chunkStartX;
        private final int chunkStartZ;
        private final int cellWidth;
        private final int cellHeight;
        private final double inverseCellWidth;
        private final int horizontalCellCount;
        private final int minBlockY;
        private ArenaBlockStatePalettedContainer currentContainer;
        private int currentSectionIndex;
        private int currentPage;

        private FillState(NoiseChunk noiseChunk, Request request, TargetSections targets) {
            this.noiseChunk = noiseChunk;
            this.noiseChunkAccess = (NoiseChunkAccessor) noiseChunk;
            this.arenaNoiseChunk = (ArenaNoiseChunkAccess) noiseChunk;
            this.request = request;
            this.chunk = request.chunk();
            this.sections = this.chunk.getSections();
            this.containers = arenaContainers(this.sections, targets);
            this.touchedSections = new boolean[this.sections.length];
            this.aquifer = noiseChunk.aquifer();
            this.materialEvaluator = ArenaMaterialEvaluator.create(this.noiseChunkAccess.byepregen$blockStateRule());
            ChunkPos chunkPos = this.chunk.getPos();
            this.chunkStartX = chunkPos.getMinBlockX();
            this.chunkStartZ = chunkPos.getMinBlockZ();
            this.cellWidth = this.noiseChunkAccess.byepregen$cellWidth();
            this.cellHeight = this.noiseChunkAccess.byepregen$cellHeight();
            this.horizontalCellCount = CHUNK_WIDTH / this.cellWidth;
            this.minBlockY = request.minCellY() * this.cellHeight;
            int blockHeight = request.cellCountY() * this.cellHeight;
            validateLayout(this.cellWidth, this.cellHeight, this.minBlockY, blockHeight);
            this.inverseCellWidth = 1.0D / this.cellWidth;
        }

        private ChunkAccess fill() {
            try {
                this.arenaNoiseChunk.byepregen$initializeArenaInterpolation(this.inverseCellWidth);
                try {
                    this.fillColumns();
                } finally {
                    try {
                        this.arenaNoiseChunk.byepregen$releaseArenaInterpolation();
                    } finally {
                        this.noiseChunk.stopInterpolation();
                    }
                }
            } catch (Throwable throwable) {
                this.rollbackSections();
                throw throwable;
            }
            this.finishSections();
            Heightmap.primeHeightmaps(this.chunk, WORLDGEN_HEIGHTMAPS);
            return this.chunk;
        }

        private void fillColumns() {
            for (int cellX = 0; cellX < this.horizontalCellCount; ++cellX) {
                this.arenaNoiseChunk.byepregen$advanceArenaCellX(cellX);
                this.fillCellXColumns(cellX);
                this.arenaNoiseChunk.byepregen$finishArenaCellX();
            }
        }

        private void fillCellXColumns(int cellX) {
            int cellStartX = this.chunkStartX + cellX * this.cellWidth;
            for (int cellZ = 0; cellZ < this.horizontalCellCount; ++cellZ) {
                this.fillCellColumns(cellStartX, cellZ);
            }
        }

        private void fillCellColumns(int cellStartX, int cellZ) {
            int cellStartZ = this.chunkStartZ + cellZ * this.cellWidth;
            for (int inCellX = 0; inCellX < this.cellWidth; ++inCellX) {
                int blockX = cellStartX + inCellX;
                double deltaX = inCellX * this.inverseCellWidth;
                this.arenaNoiseChunk.byepregen$prepareArenaCellXZ(cellZ, blockX, deltaX);
                for (int inCellZ = 0; inCellZ < this.cellWidth; ++inCellZ) {
                    this.fillColumn(blockX, cellStartZ + inCellZ);
                }
            }
        }

        private void fillColumn(int blockX, int blockZ) {
            this.arenaNoiseChunk.byepregen$beginArenaColumn(blockZ);
            for (int cellY = this.request.cellCountY() - 1; cellY >= 0; --cellY) {
                this.fillColumnCell(blockX, blockZ, cellY);
            }
        }

        private void fillColumnCell(int blockX, int blockZ, int cellY) {
            this.arenaNoiseChunk.byepregen$selectArenaColumnCellY(cellY);
            int cellStartY = this.minBlockY + cellY * this.cellHeight;
            for (int pageEnd = this.cellHeight; pageEnd > 0; pageEnd -= Layout.PAGE_HEIGHT) {
                int pageStart = pageEnd - Layout.PAGE_HEIGHT;
                this.selectPage(cellStartY + pageStart);
                int blockY = cellStartY + pageEnd;
                this.arenaNoiseChunk.byepregen$startArenaPage();
                this.sampleBlock(blockX, --blockY, blockZ);
                this.arenaNoiseChunk.byepregen$advanceArenaPageY();
                this.sampleBlock(blockX, --blockY, blockZ);
                this.arenaNoiseChunk.byepregen$setArenaPageLowerStepY();
                this.sampleBlock(blockX, --blockY, blockZ);
                this.arenaNoiseChunk.byepregen$setArenaPageLowerY();
                this.sampleBlock(blockX, --blockY, blockZ);
            }
        }

        private void selectPage(int blockY) {
            this.currentSectionIndex = this.chunk.getSectionIndex(blockY);
            this.currentContainer = this.containers[this.currentSectionIndex];
            this.currentPage = (blockY & 15) / Layout.PAGE_HEIGHT;
        }

        private void sampleBlock(int blockX, int blockY, int blockZ) {
            BlockState state = this.materialEvaluator.calculate(this.noiseChunk);
            if (state == null) {
                state = this.request.defaultBlock();
            }
            if (state == AIR) {
                return;
            }
            if (this.aquifer.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
                this.mutablePos.set(blockX, blockY, blockZ);
                this.chunk.markPosForPostprocessing(this.mutablePos);
            }
            int pageLocalY = blockY & (Layout.PAGE_HEIGHT - 1);
            int pageLocalIndex = Layout.localIndex(blockX & 15, pageLocalY, blockZ & 15);
            this.touchedSections[this.currentSectionIndex] = true;
            this.currentContainer.batchWriteRawId(
                    this.currentPage,
                    pageLocalIndex,
                    ArenaBlockStatePalettedContainer.rawId(state)
            );
        }

        private void rollbackSections() {
            for (int i = 0; i < this.touchedSections.length; ++i) {
                if (this.touchedSections[i]) {
                    this.containers[i].releaseRawIds();
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

        private static ArenaBlockStatePalettedContainer[] arenaContainers(
                LevelChunkSection[] sections, TargetSections targets) {
            ArenaBlockStatePalettedContainer[] containers = new ArenaBlockStatePalettedContainer[sections.length];
            for (int i = targets.first(); i <= targets.last(); ++i) {
                containers[i] = (ArenaBlockStatePalettedContainer) sections[i].getStates();
            }
            return containers;
        }

        private static void validateLayout(int cellWidth, int cellHeight, int minBlockY, int blockHeight) {
            if (CHUNK_WIDTH % cellWidth != 0) {
                throw new IllegalArgumentException("Cell width must divide the chunk width: " + cellWidth);
            }
            if (cellHeight % Layout.PAGE_HEIGHT != 0) {
                throw new IllegalArgumentException("Cell height must align to Arena pages: " + cellHeight);
            }
            if (Math.floorMod(minBlockY, Layout.PAGE_HEIGHT) != 0) {
                throw new IllegalArgumentException("Minimum block Y must align to Arena pages: " + minBlockY);
            }
            if (blockHeight % Layout.PAGE_HEIGHT != 0) {
                throw new IllegalArgumentException("Block height must align to Arena pages: " + blockHeight);
            }
        }

    }
}

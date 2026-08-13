package com.moepus.byepregen.worldgen.arena;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.palette.arena.Layout;
import com.moepus.byepregen.mixin.accessor.arena.NoiseChunkAccessor;

import java.util.Arrays;
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
    private static final int AQUIFER_XZ_OFFSET = 5;
    private static final int AQUIFER_XZ_SPACING = 16;
    private static final int AQUIFER_HORIZONTAL_GRID_COUNT = 2;
    private static final int QUART_SPACING = 4;
    private static final int SURFACE_SAMPLES_PER_GRID_AXIS = 3;
    private static final int SURFACE_SAMPLES_PER_AXIS = 6;
    private static final int UNCOMPUTED_ENVELOPE = Integer.MIN_VALUE;
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
            int cellCountY,
            int globalFluidUpperBound
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
        private final AquiferSurfaceShortcutAccess aquiferSurfaceShortcut;
        private final boolean hasNoiseBasedAquifer;
        private final ArenaMaterialEvaluator materialEvaluator;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private final int chunkStartX;
        private final int chunkStartZ;
        private final int cellWidth;
        private final int cellHeight;
        private final double inverseCellWidth;
        private final int horizontalCellCount;
        private final int minBlockY;
        private final int minAquiferGridX;
        private final int minAquiferGridZ;
        private final int[] aquiferSurfaceEnvelopes = new int[
                AQUIFER_HORIZONTAL_GRID_COUNT * AQUIFER_HORIZONTAL_GRID_COUNT
        ];
        private ArenaBlockStatePalettedContainer currentContainer;
        private int currentSectionIndex;
        private int currentPage;
        private boolean useDensityColumn;

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
            this.aquiferSurfaceShortcut = (AquiferSurfaceShortcutAccess) noiseChunk;
            this.hasNoiseBasedAquifer = this.aquifer instanceof Aquifer.NoiseBasedAquifer;
            this.materialEvaluator = ArenaMaterialEvaluator.create(
                    this.noiseChunkAccess.byepregen$blockStateRule(),
                    this.aquifer,
                    this.arenaNoiseChunk.byepregen$getAquiferMaterialRule()
            );
            ChunkPos chunkPos = this.chunk.getPos();
            this.chunkStartX = chunkPos.getMinBlockX();
            this.chunkStartZ = chunkPos.getMinBlockZ();
            this.minAquiferGridX = aquiferGrid(this.chunkStartX);
            this.minAquiferGridZ = aquiferGrid(this.chunkStartZ);
            Arrays.fill(this.aquiferSurfaceEnvelopes, UNCOMPUTED_ENVELOPE);
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
            this.useDensityColumn = this.materialEvaluator.supportsColumnDensity()
                    && this.arenaNoiseChunk.byepregen$prepareArenaDensityColumn(blockX, blockZ);
            if (!this.hasNoiseBasedAquifer) {
                this.fillColumnCells(blockX, blockZ);
                return;
            }
            int fluidUpperBound = this.aquiferFluidUpperBound(blockX, blockZ);
            this.aquiferSurfaceShortcut.byepregen$beginAquiferSurfaceColumn(fluidUpperBound);
            try {
                this.fillColumnCells(blockX, blockZ);
            } finally {
                this.aquiferSurfaceShortcut.byepregen$endAquiferSurfaceColumn();
            }
        }

        private void fillColumnCells(int blockX, int blockZ) {
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
            BlockState state = this.useDensityColumn
                    ? this.materialEvaluator.calculateColumn(
                            this.noiseChunk,
                            this.arenaNoiseChunk.byepregen$getArenaDensity(blockY)
                    )
                    : this.materialEvaluator.calculate(this.noiseChunk);
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

        private int aquiferFluidUpperBound(int blockX, int blockZ) {
            int gridXOffset = aquiferGrid(blockX) - this.minAquiferGridX;
            int gridZOffset = aquiferGrid(blockZ) - this.minAquiferGridZ;
            int index = gridXOffset * AQUIFER_HORIZONTAL_GRID_COUNT + gridZOffset;
            int envelope = this.aquiferSurfaceEnvelopes[index];
            if (envelope == UNCOMPUTED_ENVELOPE) {
                envelope = this.computeAquiferSurfaceEnvelope(
                        this.minAquiferGridX + gridXOffset,
                        this.minAquiferGridZ + gridZOffset
                );
                this.aquiferSurfaceEnvelopes[index] = envelope;
            }
            return envelope;
        }

        private int computeAquiferSurfaceEnvelope(int gridX, int gridZ) {
            int envelope = this.request.globalFluidUpperBound();
            // Candidate centers use nextInt(10), then preliminarySurfaceLevel aligns to quart
            // coordinates. Two neighboring aquifer grids therefore cover these 6x6 samples.
            for (int xIndex = 0; xIndex < SURFACE_SAMPLES_PER_AXIS; ++xIndex) {
                int sampleX = surfaceSampleCoordinate(gridX, xIndex);
                for (int zIndex = 0; zIndex < SURFACE_SAMPLES_PER_AXIS; ++zIndex) {
                    int sampleZ = surfaceSampleCoordinate(gridZ, zIndex);
                    int surface = this.noiseChunk.preliminarySurfaceLevel(sampleX, sampleZ);
                    if (surface == Integer.MAX_VALUE) {
                        return Integer.MAX_VALUE;
                    }
                    envelope = Math.max(envelope, surface);
                }
            }
            return envelope;
        }

        private static int aquiferGrid(int blockCoordinate) {
            return Math.floorDiv(blockCoordinate - AQUIFER_XZ_OFFSET, AQUIFER_XZ_SPACING);
        }

        private static int surfaceSampleCoordinate(int gridCoordinate, int sampleIndex) {
            int gridOffset = sampleIndex / SURFACE_SAMPLES_PER_GRID_AXIS;
            int quartOffset = sampleIndex % SURFACE_SAMPLES_PER_GRID_AXIS;
            return (gridCoordinate + gridOffset) * AQUIFER_XZ_SPACING + quartOffset * QUART_SPACING;
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

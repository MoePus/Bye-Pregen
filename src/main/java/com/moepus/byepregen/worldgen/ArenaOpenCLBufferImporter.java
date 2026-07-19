package com.moepus.byepregen.worldgen;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Layout;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.nio.ByteBuffer;
import java.util.Set;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ArenaOpenCLBufferImporter {
    private static final int CHUNK_WIDTH = 16;
    private static final int BLOCK_STATE_MASK = 0x7F;
    private static final int POSTPROCESSING_MASK = 0x80;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Set<Heightmap.Types> WORLDGEN_HEIGHTMAPS = Set.of(
            Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.WORLD_SURFACE_WG
    );

    private ArenaOpenCLBufferImporter() {
    }

    public static void copy(Request request) {
        for (int chunkOffsetZ = 0; chunkOffsetZ < request.batchSize(); ++chunkOffsetZ) {
            for (int chunkOffsetX = 0; chunkOffsetX < request.batchSize(); ++chunkOffsetX) {
                copyChunk(request, chunkOffsetX, chunkOffsetZ);
            }
        }
    }

    private static void copyChunk(Request request, int chunkOffsetX, int chunkOffsetZ) {
        ProtoChunk chunk = request.chunks().get(
                request.startingPos().x + chunkOffsetX,
                request.startingPos().z + chunkOffsetZ
        );
        if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.NOISE)) {
            return;
        }

        new ChunkImportState(request, chunk).copy();
        chunk.setPersistedStatus(ChunkStatus.NOISE);
    }

    public record Request(
            StaticCache2D<ProtoChunk> chunks,
            BlockState[] blockStateMappings,
            int verticalSize,
            int minimumBlockY,
            int horizontalSize,
            ByteBuffer blockBuffer,
            ChunkPos startingPos,
            int batchSize
    ) {
    }

    private static final class ChunkImportState {
        private final Request request;
        private final ProtoChunk chunk;
        private final LevelChunkSection[] sections;
        private final ShortList[] postProcessing;
        private final boolean[] touchedSections;
        private final int bufferStartX;
        private final int bufferStartZ;
        private final int bufferPlaneSize;
        private ArenaBlockStatePalettedContainer currentContainer;
        private int currentSectionIndex;
        private int currentLocalY;
        private int currentLocalZ;
        private int currentBufferRow;

        private ChunkImportState(Request request, ProtoChunk chunk) {
            this.request = request;
            this.chunk = chunk;
            this.sections = chunk.getSections();
            this.postProcessing = chunk.getPostProcessing();
            this.touchedSections = new boolean[this.sections.length];
            int chunkOffsetX = chunk.getPos().x - request.startingPos().x;
            int chunkOffsetZ = chunk.getPos().z - request.startingPos().z;
            this.bufferStartX = chunkOffsetX * CHUNK_WIDTH;
            this.bufferStartZ = chunkOffsetZ * CHUNK_WIDTH;
            this.bufferPlaneSize = request.horizontalSize() * request.horizontalSize();
        }

        private void copy() {
            for (int y = 0; y < this.request.verticalSize(); ++y) {
                this.copyLayer(y);
            }
            this.finishSections();
            Heightmap.primeHeightmaps(this.chunk, WORLDGEN_HEIGHTMAPS);
        }

        private void copyLayer(int y) {
            int blockY = this.request.minimumBlockY() + y;
            this.currentSectionIndex = this.chunk.getSectionIndex(blockY);
            this.currentContainer = (ArenaBlockStatePalettedContainer) this.sections[this.currentSectionIndex].getStates();
            this.touchedSections[this.currentSectionIndex] = true;
            this.currentLocalY = blockY & 15;
            int layerOffset = y * this.bufferPlaneSize;
            for (int z = 0; z < CHUNK_WIDTH; ++z) {
                this.currentLocalZ = z;
                this.currentBufferRow = layerOffset
                        + (this.bufferStartZ + z) * this.request.horizontalSize()
                        + this.bufferStartX;
                this.copyRow();
            }
        }

        private void copyRow() {
            for (int x = 0; x < CHUNK_WIDTH; ++x) {
                this.writeBlock(x, this.request.blockBuffer().get(this.currentBufferRow + x));
            }
        }

        private void writeBlock(int x, byte value) {
            int mappingIndex = value & BLOCK_STATE_MASK;
            BlockState state = this.request.blockStateMappings()[mappingIndex];
            if (state == AIR) {
                return;
            }
            if (state == null) {
                throw new IllegalStateException("OpenCL buffer references an unmapped block state");
            }

            int localIndex = Layout.localIndex(x, this.currentLocalY, this.currentLocalZ);
            this.currentContainer.setRawId(localIndex, ArenaBlockStatePalettedContainer.rawId(state));
            if ((value & POSTPROCESSING_MASK) != 0) {
                this.markForPostprocessing(x);
            }
        }

        private void markForPostprocessing(int x) {
            short packedPosition = (short) (x | this.currentLocalY << 4 | this.currentLocalZ << 8);
            ChunkAccess.getOrCreateOffsetList(this.postProcessing, this.currentSectionIndex).add(packedPosition);
        }

        private void finishSections() {
            for (int i = 0; i < this.touchedSections.length; ++i) {
                if (this.touchedSections[i]) {
                    this.sections[i].recalcBlockCounts();
                }
            }
        }
    }
}

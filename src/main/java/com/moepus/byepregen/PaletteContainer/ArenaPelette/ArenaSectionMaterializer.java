package com.moepus.byepregen;

import com.moepus.byepregen.mixin.LevelChunkSectionAccessor;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class ArenaSectionMaterializer {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int MIN_LOCAL_PALETTE_BITS = 4;
    private static final int MAX_LOCAL_PALETTE_BITS = 8;
    private static final int GLOBAL_PALETTE_BITS = MAX_LOCAL_PALETTE_BITS + 1;
    private static final int INITIAL_PALETTE_CAPACITY = 64;
    private static final int INITIAL_MAP_CAPACITY = 128;
    private static final int MAX_RETAINED_SCRATCH_SIZE = 512;
    private static final int MISSING_LOCAL_ID = -1;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private ArenaSectionMaterializer() {
    }

    public static void materializeChunk(ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            PalettedContainer<BlockState> states = section.getStates();
            if (states instanceof ArenaBlockStatePalettedContainer arenaContainer) {
                ((LevelChunkSectionAccessor) section).byepregen$setStates(materialize(arenaContainer));
                arenaContainer.releaseRawIds();
            }
        }
    }

    public static void releaseChunk(ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            PalettedContainer<BlockState> states = section.getStates();
            if (states instanceof ArenaBlockStatePalettedContainer arenaContainer) {
                arenaContainer.releaseRawIds();
            }
        }
    }

    public static void count(ArenaBlockStatePalettedContainer container, PalettedContainer.CountConsumer<BlockState> consumer) {
        if (container.isUniform()) {
            consumer.accept(Block.stateById(container.uniformRawId()), ArenaBlockStatePalettedContainer.SECTION_SIZE);
            return;
        }

        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        container.countRawIds(counts);
        counts.int2IntEntrySet().forEach(entry -> consumer.accept(Block.stateById(entry.getIntKey()), entry.getIntValue()));
    }

    public static FastBlockStatePalettedContainer materialize(ArenaBlockStatePalettedContainer container) {
        if (!container.isDirty()) {
            return createSingle(AIR);
        }
        if (container.isUniform()) {
            return createSingle(Block.stateById(container.uniformRawId()));
        }

        Scratch scratch = SCRATCH.get();
        try {
            container.forEachRawId(scratch::addRawId);
            if (scratch.paletteSize == 1) {
                return createSingle(Block.stateById(scratch.paletteRawIds[0]));
            }

            FastBlockStatePalettedContainer materialized = createSingle(AIR);
            PalettedContainer.Data<BlockState> data =
                    materialized.createOrReuseData(null, storageBits(scratch.paletteSize));
            fillPalette(data.palette(), scratch);
            writeStorage(data.storage(), scratch, container);
            materialized.data = data;
            materialized.byepregen$updateFastData(data);
            return materialized;
        } finally {
            scratch.clear();
        }
    }

    private static FastBlockStatePalettedContainer createSingle(BlockState state) {
        return new FastBlockStatePalettedContainer(
                Block.BLOCK_STATE_REGISTRY, state, PalettedContainer.Strategy.SECTION_STATES);
    }

    private static int storageBits(int paletteSize) {
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        if (bits > MAX_LOCAL_PALETTE_BITS) {
            return GLOBAL_PALETTE_BITS;
        }
        return Math.max(MIN_LOCAL_PALETTE_BITS, bits);
    }

    private static void fillPalette(Palette<BlockState> palette, Scratch scratch) {
        if (palette instanceof GlobalPalette<?>) {
            return;
        }
        for (int i = 0; i < scratch.paletteSize; ++i) {
            palette.idFor(Block.stateById(scratch.paletteRawIds[i]));
        }
    }

    private static void writeStorage(
            BitStorage storage, Scratch scratch, ArenaBlockStatePalettedContainer container) {
        int bits = storage.getBits();
        if (bits == 0) {
            return;
        }

        boolean globalPalette = scratch.paletteSize > (1 << MAX_LOCAL_PALETTE_BITS);
        scratch.configureWrite(storage, globalPalette);
        container.forEachRawId(scratch::writeRawId);
    }

    private static final class Scratch {
        private Int2IntOpenHashMap rawToLocal = createRawToLocalMap();
        private int[] paletteRawIds = new int[INITIAL_PALETTE_CAPACITY];
        private long[] writeRaw;
        private long writeMask;
        private int writeBits;
        private int writeValuesPerLong;
        private boolean writeGlobalPalette;
        private int paletteSize;

        private void addRawId(int sectionIndex, int rawId) {
            if (rawId < 0) {
                rawId = 0;
            }
            this.addLocalId(rawId);
        }

        private void addLocalId(int rawId) {
            int localId = this.rawToLocal.get(rawId);
            if (localId != MISSING_LOCAL_ID) {
                return;
            }

            localId = this.paletteSize++;
            this.ensurePaletteCapacity(localId);
            this.rawToLocal.put(rawId, localId);
            this.paletteRawIds[localId] = rawId;
        }

        private int localIdFor(int rawId) {
            int localId = this.rawToLocal.get(Math.max(rawId, 0));
            if (localId == MISSING_LOCAL_ID) {
                throw new IllegalStateException("Missing materialized palette entry for raw id " + rawId);
            }
            return localId;
        }

        private void configureWrite(BitStorage storage, boolean globalPalette) {
            this.writeRaw = storage.getRaw();
            this.writeBits = storage.getBits();
            this.writeValuesPerLong = Long.SIZE / this.writeBits;
            this.writeMask = (1L << this.writeBits) - 1L;
            this.writeGlobalPalette = globalPalette;
        }

        private void writeRawId(int sectionIndex, int rawId) {
            int id = this.writeGlobalPalette ? (Math.max(rawId, 0)) : this.localIdFor(rawId);
            int cell = sectionIndex / this.writeValuesPerLong;
            int shift = (sectionIndex - cell * this.writeValuesPerLong) * this.writeBits;
            this.writeRaw[cell] |= ((long)id & this.writeMask) << shift;
        }

        private void clear() {
            if (this.rawToLocal.size() > MAX_RETAINED_SCRATCH_SIZE) {
                this.rawToLocal = createRawToLocalMap();
            } else {
                this.rawToLocal.clear();
            }
            if (this.paletteRawIds.length > MAX_RETAINED_SCRATCH_SIZE) {
                this.paletteRawIds = new int[INITIAL_PALETTE_CAPACITY];
            }
            this.writeRaw = null;
            this.paletteSize = 0;
        }

        private void ensurePaletteCapacity(int localId) {
            if (localId < this.paletteRawIds.length) {
                return;
            }

            this.paletteRawIds = Arrays.copyOf(this.paletteRawIds, this.paletteRawIds.length * 2);
        }

        private static Int2IntOpenHashMap createRawToLocalMap() {
            Int2IntOpenHashMap map = new Int2IntOpenHashMap(INITIAL_MAP_CAPACITY);
            map.defaultReturnValue(MISSING_LOCAL_ID);
            return map;
        }
    }
}

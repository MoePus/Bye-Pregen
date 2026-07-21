package com.moepus.byepregen.PaletteContainer.ArenaPelette;

import static com.moepus.byepregen.PaletteContainer.ArenaPelette.Layout.*;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs.SerializationScratch;
import com.moepus.byepregen.PaletteContainer.FastPalette.FastBlockStatePalettedContainer;
import com.moepus.byepregen.mixin.LevelChunkSectionAccessor;
import com.moepus.byepregen.mixin.accessor.PalettedContainerAccessor;
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

    private ArenaSectionMaterializer() {
    }

    public static void materializeChunk(ChunkAccess chunk) {
        materializeChunkToFast(chunk);
    }

    public static void materializeChunkToFast(ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            PalettedContainer<BlockState> states = section.getStates();
            if (states instanceof ArenaBlockStatePalettedContainer arenaContainer) {
                ((LevelChunkSectionAccessor) section).byepregen$setStates(materialize(arenaContainer));
                arenaContainer.releaseRawIds();
            }
        }
    }

    public static void materializeChunkToVanilla(ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            PalettedContainer<BlockState> states = section.getStates();
            if (states instanceof ArenaBlockStatePalettedContainer arenaContainer) {
                ((LevelChunkSectionAccessor) section).byepregen$setStates(materializeVanilla(arenaContainer));
                arenaContainer.releaseRawIds();
            }
        }
    }

    public static FastBlockStatePalettedContainer materialize(ArenaBlockStatePalettedContainer container) {
        if (container.isUniform()) {
            return createFastSingle(Block.stateById(container.uniformRawId()));
        }

        SerializationScratch scratch = SerializationScratch.get();
        try {
            scratch.collect(container);
            if (scratch.paletteSize() == 1) {
                return createFastSingle(Block.stateById(scratch.paletteRawId(0)));
            }

            FastBlockStatePalettedContainer materialized = createFastSingle(AIR);
            PalettedContainer.Data<BlockState> data = createMaterializedData(materialized, scratch, container);
            ((PalettedContainerAccessor<BlockState>) (Object) materialized).byepregen$setData(data);
            materialized.byepregen$updateFastData(data);
            return materialized;
        } finally {
            scratch.clear();
        }
    }

    private static PalettedContainer<BlockState> materializeVanilla(ArenaBlockStatePalettedContainer container) {
        if (container.isUniform()) {
            return createVanillaSingle(Block.stateById(container.uniformRawId()));
        }

        SerializationScratch scratch = SerializationScratch.get();
        try {
            scratch.collect(container);
            if (scratch.paletteSize() == 1) {
                return createVanillaSingle(Block.stateById(scratch.paletteRawId(0)));
            }

            PalettedContainer<BlockState> materialized = createVanillaSingle(AIR);
            ((PalettedContainerAccessor<BlockState>) (Object) materialized)
                    .byepregen$setData(createMaterializedData(materialized, scratch, container));
            return materialized;
        } finally {
            scratch.clear();
        }
    }

    private static FastBlockStatePalettedContainer createFastSingle(BlockState state) {
        return new FastBlockStatePalettedContainer(
                Block.BLOCK_STATE_REGISTRY,
                state,
                PalettedContainer.Strategy.SECTION_STATES
        );
    }

    private static PalettedContainer<BlockState> createVanillaSingle(BlockState state) {
        return new PalettedContainer<>(
                Block.BLOCK_STATE_REGISTRY,
                state,
                PalettedContainer.Strategy.SECTION_STATES
        );
    }

    private static PalettedContainer.Data<BlockState> createMaterializedData(
            PalettedContainer<BlockState> materialized,
            SerializationScratch scratch,
            ArenaBlockStatePalettedContainer container) {
        PalettedContainer.Data<BlockState> data =
                ((PalettedContainerAccessor<BlockState>) (Object) materialized)
                        .byepregen$invokeCreateOrReuseData(null, storageBits(scratch.paletteSize()));
        fillPalette(data.palette(), scratch);
        writeStorage(data.storage(), scratch, container);
        return data;
    }

    private static int storageBits(int paletteSize) {
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        if (bits > MAX_LOCAL_PALETTE_BITS) {
            return GLOBAL_PALETTE_BITS;
        }
        return Math.max(MIN_LOCAL_PALETTE_BITS, bits);
    }

    private static void fillPalette(Palette<BlockState> palette, SerializationScratch scratch) {
        if (palette instanceof GlobalPalette<?>) {
            return;
        }

        int size = scratch.paletteSize();
        for (int i = 0; i < size; ++i) {
            palette.idFor(Block.stateById(scratch.paletteRawId(i)));
        }
    }

    private static void writeStorage(
            BitStorage storage, SerializationScratch scratch, ArenaBlockStatePalettedContainer container) {
        int bits = storage.getBits();
        if (bits == 0) {
            return;
        }

        long[] output = storage.getRaw();
        int valuesPerLong = Long.SIZE / bits;
        long mask = (1L << bits) - 1L;
        boolean globalPalette = scratch.paletteSize() > (1 << MAX_LOCAL_PALETTE_BITS);
        if (container.hasPagePalettes() && !globalPalette) {
            writePagePaletteStorage(output, valuesPerLong, mask, bits, scratch, container);
            return;
        }

        for (int index = 0; index < SECTION_SIZE; ++index) {
            int rawId = Math.max(container.rawIdAt(index), 0);
            int id = globalPalette ? rawId : scratch.localIdFor(rawId);
            writePackedId(output, valuesPerLong, mask, bits, index, id);
        }
    }

    private static void writePagePaletteStorage(
            long[] output,
            int valuesPerLong,
            long mask,
            int bits,
            SerializationScratch scratch,
            ArenaBlockStatePalettedContainer container) {
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = container.arenaPageBase(page);
            int pageLocalIdOffset = page * PAGE_PALETTE_SIZE;
            int sectionIndex = page << 10;
            for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                int word = container.arenaPaletteWord(base, wordIndex);
                writePackedId(output, valuesPerLong, mask, bits, sectionIndex++,
                        scratch.pageLocalIdAtOffset(pageLocalIdOffset, word & PALETTE_INDEX_MASK));
                writePackedId(output, valuesPerLong, mask, bits, sectionIndex++,
                        scratch.pageLocalIdAtOffset(pageLocalIdOffset, (word >>> 4) & PALETTE_INDEX_MASK));
                writePackedId(output, valuesPerLong, mask, bits, sectionIndex++,
                        scratch.pageLocalIdAtOffset(pageLocalIdOffset, (word >>> 8) & PALETTE_INDEX_MASK));
                writePackedId(output, valuesPerLong, mask, bits, sectionIndex++,
                        scratch.pageLocalIdAtOffset(pageLocalIdOffset, (word >>> 12) & PALETTE_INDEX_MASK));
                writePackedId(output, valuesPerLong, mask, bits, sectionIndex++,
                        scratch.pageLocalIdAtOffset(pageLocalIdOffset, (word >>> 16) & PALETTE_INDEX_MASK));
                writePackedId(output, valuesPerLong, mask, bits, sectionIndex++,
                        scratch.pageLocalIdAtOffset(pageLocalIdOffset, (word >>> 20) & PALETTE_INDEX_MASK));
                writePackedId(output, valuesPerLong, mask, bits, sectionIndex++,
                        scratch.pageLocalIdAtOffset(pageLocalIdOffset, (word >>> 24) & PALETTE_INDEX_MASK));
                writePackedId(output, valuesPerLong, mask, bits, sectionIndex++,
                        scratch.pageLocalIdAtOffset(pageLocalIdOffset, (word >>> 28) & PALETTE_INDEX_MASK));
            }
        }
    }

    private static void writePackedId(
            long[] output, int valuesPerLong, long mask, int bits, int index, int id) {
        int cell = index / valuesPerLong;
        int shift = (index - cell * valuesPerLong) * bits;
        output[cell] |= ((long) id & mask) << shift;
    }
}

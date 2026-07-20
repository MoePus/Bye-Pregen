package com.moepus.byepregen.PaletteContainer.ArenaPelette;

import com.moepus.byepregen.gcfree.BlockStateNbtCache;
import com.moepus.byepregen.gcfree.NbtWriter;
import java.util.Arrays;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

public final class ArenaBlockStateSectionWriter {
    private static final int SECTION_SIZE = ArenaBlockStatePalettedContainer.SECTION_SIZE;
    private static final int PAGE_COUNT = ArenaBlockStatePalettedContainer.PAGE_COUNT;
    private static final int PAGE_PALETTE_SIZE = ArenaBlockStatePalettedContainer.PAGE_PALETTE_SIZE;
    private static final int INDEX_WORDS_PER_PAGE = ArenaBlockStatePalettedContainer.INDEX_WORDS_PER_PAGE;
    private static final int MIN_SECTION_STATE_BITS = 4;
    private static final int INITIAL_PALETTE_CAPACITY = 64;
    private static final int INITIAL_LOOKUP_CAPACITY = 128;
    private static final int HASH_MULTIPLIER = 0x9E3779B9;
    private static final byte[] BLOCK_STATES = NbtWriter.asciiName("block_states");
    private static final byte[] PALETTE = NbtWriter.asciiName("palette");
    private static final byte[] DATA = NbtWriter.asciiName("data");

    private ArenaBlockStateSectionWriter() {}

    public static void write(NbtWriter writer, ArenaBlockStatePalettedContainer container) {
        if (container.isUniform()) {
            writeUniform(writer, container.uniformRawId());
            return;
        }
        Scratch scratch = new Scratch();
        boolean pagePalettes = container.hasPagePalettes();
        if (pagePalettes) {
            scratch.collectPagePalette(container);
        } else {
            container.forEachRawId(scratch::addRawId);
        }
        writer.startCompound(BLOCK_STATES);
        writePalette(writer, scratch);
        if (scratch.paletteSize > 1) {
            writer.startLongArray(DATA, scratch.beginPack());
            if (pagePalettes) {
                scratch.packPagePalette(writer, container);
            } else {
                scratch.pack(writer, container);
            }
            scratch.finishPack(writer);
        }
        writer.finishCompound();
    }

    private static void writeUniform(NbtWriter writer, int rawId) {
        writer.startCompound(BLOCK_STATES);
        writer.startFixedList(PALETTE, 1, Tag.TAG_COMPOUND);
        writer.compoundEntryStart();
        BlockStateNbtCache.writeRawIdEntry(writer, rawId);
        writer.finishCompound();
        writer.finishCompound();
    }

    private static void writePalette(NbtWriter writer, Scratch scratch) {
        writer.startFixedList(PALETTE, scratch.paletteSize, Tag.TAG_COMPOUND);
        for (int i = 0; i < scratch.paletteSize; ++i) {
            writer.compoundEntryStart();
            BlockStateNbtCache.writeRawIdEntry(writer, scratch.paletteRawIds[i]);
            writer.finishCompound();
        }
    }

    public static void writeStateEntry(NbtWriter writer, BlockState state) {
        BlockStateNbtCache.writeStateEntry(writer, state);
    }

    private static int serializedBits(int paletteSize) {
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        return Math.max(MIN_SECTION_STATE_BITS, bits);
    }

    private static final class Scratch {
        private int[] lookupKeys;
        private int[] lookupValues;
        private int[] paletteRawIds = new int[INITIAL_PALETTE_CAPACITY];
        private byte[] pageLocalIds;
        private long writeMask;
        private long packedWord;
        private int bits;
        private int valuesPerLong;
        private int packedShift;
        private int packedValuesInWord;
        private int paletteSize;

        private void addRawId(int sectionIndex, int rawId) {
            this.localId(Math.max(rawId, 0));
        }

        private void collectPagePalette(ArenaBlockStatePalettedContainer container) {
            this.ensurePageLocalIds();
            for (int page = 0; page < PAGE_COUNT; ++page) {
                int base = container.arenaPageBase(page);
                int mask = container.arenaLivePaletteMask(base);
                int offset = page * PAGE_PALETTE_SIZE;
                while (mask != 0) {
                    int paletteIndex = Integer.numberOfTrailingZeros(mask);
                    int rawId = container.arenaPaletteRawId(base, paletteIndex);
                    this.pageLocalIds[offset + paletteIndex] = (byte) this.addPaletteRawId(rawId);
                    mask &= mask - 1;
                }
            }
        }

        private void pack(NbtWriter writer, ArenaBlockStatePalettedContainer container) {
            container.forEachRawId((sectionIndex, rawId) -> this.writePackedRawId(writer, rawId));
        }

        private void packPagePalette(NbtWriter writer, ArenaBlockStatePalettedContainer container) {
            for (int page = 0; page < PAGE_COUNT; ++page) {
                int base = container.arenaPageBase(page);
                int offset = page * PAGE_PALETTE_SIZE;
                for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                    this.writePagePaletteWord(writer, container.arenaPaletteWord(base, wordIndex), offset);
                }
            }
        }

        private int beginPack() {
            this.bits = serializedBits(this.paletteSize);
            this.valuesPerLong = Long.SIZE / this.bits;
            this.writeMask = (1L << this.bits) - 1L;
            this.packedWord = 0L;
            this.packedShift = 0;
            this.packedValuesInWord = 0;
            return (SECTION_SIZE + this.valuesPerLong - 1) / this.valuesPerLong;
        }

        private void finishPack(NbtWriter writer) {
            if (this.packedValuesInWord != 0) {
                writer.writeLongArrayEntry(this.packedWord);
            }
        }

        private void writePackedRawId(NbtWriter writer, int rawId) {
            this.writePackedLocalId(writer, this.localIdFor(Math.max(rawId, 0)));
        }

        private void writePagePaletteWord(NbtWriter writer, int word, int offset) {
            this.writePackedLocalId(writer, this.pageLocalIds[offset + (word & 15)] & 0xFF);
            this.writePackedLocalId(writer, this.pageLocalIds[offset + ((word >>> 4) & 15)] & 0xFF);
            this.writePackedLocalId(writer, this.pageLocalIds[offset + ((word >>> 8) & 15)] & 0xFF);
            this.writePackedLocalId(writer, this.pageLocalIds[offset + ((word >>> 12) & 15)] & 0xFF);
            this.writePackedLocalId(writer, this.pageLocalIds[offset + ((word >>> 16) & 15)] & 0xFF);
            this.writePackedLocalId(writer, this.pageLocalIds[offset + ((word >>> 20) & 15)] & 0xFF);
            this.writePackedLocalId(writer, this.pageLocalIds[offset + ((word >>> 24) & 15)] & 0xFF);
            this.writePackedLocalId(writer, this.pageLocalIds[offset + ((word >>> 28) & 15)] & 0xFF);
        }

        private void writePackedLocalId(NbtWriter writer, int localId) {
            this.packedWord |= ((long) localId & this.writeMask) << this.packedShift;
            if (++this.packedValuesInWord == this.valuesPerLong) {
                writer.writeLongArrayEntry(this.packedWord);
                this.packedWord = 0L;
                this.packedShift = 0;
                this.packedValuesInWord = 0;
            } else {
                this.packedShift += this.bits;
            }
        }

        private int addPaletteRawId(int rawId) {
            rawId = Math.max(rawId, 0);
            for (int i = 0; i < this.paletteSize; ++i) {
                if (this.paletteRawIds[i] == rawId) {
                    return i;
                }
            }
            int localId = this.paletteSize++;
            this.ensurePaletteCapacity(this.paletteSize);
            this.paletteRawIds[localId] = rawId;
            return localId;
        }

        private int localId(int rawId) {
            this.ensureLookup();
            int slot = this.findSlot(rawId);
            int marker = this.lookupValues[slot];
            if (marker != 0) {
                return marker - 1;
            }
            int localId = this.paletteSize;
            this.ensurePaletteCapacity(localId + 1);
            if ((localId + 1) * 2 > this.lookupKeys.length) {
                this.growLookup();
                slot = this.findSlot(rawId);
            }
            this.paletteSize = localId + 1;
            this.lookupKeys[slot] = rawId;
            this.lookupValues[slot] = localId + 1;
            this.paletteRawIds[localId] = rawId;
            return localId;
        }

        private int localIdFor(int rawId) {
            this.ensureLookup();
            int slot = this.findSlot(rawId);
            int marker = this.lookupValues[slot];
            return marker == 0 ? 0 : marker - 1;
        }
        private void ensureLookup() {
            if (this.lookupKeys != null) {
                return;
            }
            this.lookupKeys = new int[INITIAL_LOOKUP_CAPACITY];
            this.lookupValues = new int[INITIAL_LOOKUP_CAPACITY];
        }

        private void ensurePageLocalIds() {
            if (this.pageLocalIds == null) {
                this.pageLocalIds = new byte[PAGE_COUNT * PAGE_PALETTE_SIZE];
            }
        }

        private void ensurePaletteCapacity(int required) {
            if (this.paletteRawIds.length >= required) {
                return;
            }

            int newLength = this.paletteRawIds.length;
            do {
                newLength *= 2;
            } while (newLength < required);
            this.paletteRawIds = Arrays.copyOf(this.paletteRawIds, newLength);
        }

        private void growLookup() {
            this.lookupKeys = new int[this.lookupKeys.length * 2];
            this.lookupValues = new int[this.lookupValues.length * 2];
            for (int i = 0; i < this.paletteSize; ++i) {
                int slot = this.findSlot(this.paletteRawIds[i]);
                this.lookupKeys[slot] = this.paletteRawIds[i];
                this.lookupValues[slot] = i + 1;
            }
        }

        private int findSlot(int rawId) {
            int slot = mix(rawId) & (this.lookupKeys.length - 1);
            while (this.lookupValues[slot] != 0 && this.lookupKeys[slot] != rawId) {
                slot = (slot + 1) & (this.lookupKeys.length - 1);
            }
            return slot;
        }

        private static int mix(int value) {
            int hash = value * HASH_MULTIPLIER;
            return hash ^ (hash >>> 16);
        }
    }
}

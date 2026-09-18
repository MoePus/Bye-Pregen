package com.moepus.byepregen.palette.arena.codec;

import static com.moepus.byepregen.palette.arena.Layout.*;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.network.FriendlyByteBuf;

public final class SerializationScratch {
    private static final ThreadLocal<SerializationScratch> SCRATCH =
            ThreadLocal.withInitial(SerializationScratch::new);

    private final PaletteScratch palette = new PaletteScratch();
    private final PackedLongScratch packer = new PackedLongScratch();

    private SerializationScratch() {
    }

    public static SerializationScratch get() {
        return SCRATCH.get();
    }

    public static int localPaletteBits(int paletteSize) {
        if (paletteSize == 1) {
            return 0;
        }
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        return Math.max(BITS_PER_ENTRY, bits);
    }

    public static int packedLength(int bits) {
        int valuesPerLong = Long.SIZE / bits;
        return (SECTION_SIZE + valuesPerLong - 1) / valuesPerLong;
    }

    public void collect(ArenaBlockStatePalettedContainer container) {
        if (container.hasPagePalettes()) {
            this.palette.collectPagePalette(container);
            return;
        }
        this.palette.collectRawIds(container);
    }

    void collectForNetworkSize(ArenaBlockStatePalettedContainer container) {
        if (container.hasPagePalettes()) {
            this.palette.collectPagePaletteRawIds(container);
            return;
        }
        this.palette.collectRawIds(container);
    }

    public int paletteSize() {
        return this.palette.size();
    }

    public int paletteRawId(int localId) {
        return this.palette.rawId(localId);
    }

    public int localIdFor(int rawId) {
        return this.palette.localIdFor(rawId);
    }

    public int pageLocalIdAtOffset(int offset, int paletteIndex) {
        return this.palette.pageLocalIds()[offset + paletteIndex] & 0xFF;
    }

    public int beginSectionPack() {
        return this.beginPack(localPaletteBits(this.palette.size()));
    }

    int beginPack(int bits) {
        this.packer.begin(bits);
        return packedLength(bits);
    }

    void writeSectionData(NbtWriter writer, ArenaBlockStatePalettedContainer container) {
        this.packSection(container, false, writer::writeLongArrayEntry);
    }

    public void writePayloadData(PayloadWriter writer, ArenaBlockStatePalettedContainer container) {
        this.packSection(container, false, writer::writeLongArrayEntry);
    }

    void writeNetworkData(FriendlyByteBuf buffer, ArenaBlockStatePalettedContainer container, boolean globalPalette) {
        this.packSection(container, globalPalette, buffer::writeLong);
    }

    public void clear() {
        this.palette.clear();
        this.packer.clear();
    }

    @FunctionalInterface
    private interface WordSink {
        void accept(long word);
    }

    /** Packs the section once; the three serializers differ only in where the finished words go. */
    private void packSection(ArenaBlockStatePalettedContainer container, boolean globalPalette, WordSink sink) {
        if (container.hasPagePalettes()) {
            byte[] pageLocalIds = this.palette.pageLocalIds();
            for (int page = 0; page < PAGE_COUNT; ++page) {
                int base = container.arenaPageBase(page);
                int offset = page * PAGE_PALETTE_SIZE;
                for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                    int word = container.arenaPaletteWord(base, wordIndex);
                    this.writePackedLocalId(sink, pageLocalIds[offset + (word & 15)] & 0xFF);
                    this.writePackedLocalId(sink, pageLocalIds[offset + ((word >>> 4) & 15)] & 0xFF);
                    this.writePackedLocalId(sink, pageLocalIds[offset + ((word >>> 8) & 15)] & 0xFF);
                    this.writePackedLocalId(sink, pageLocalIds[offset + ((word >>> 12) & 15)] & 0xFF);
                    this.writePackedLocalId(sink, pageLocalIds[offset + ((word >>> 16) & 15)] & 0xFF);
                    this.writePackedLocalId(sink, pageLocalIds[offset + ((word >>> 20) & 15)] & 0xFF);
                    this.writePackedLocalId(sink, pageLocalIds[offset + ((word >>> 24) & 15)] & 0xFF);
                    this.writePackedLocalId(sink, pageLocalIds[offset + ((word >>> 28) & 15)] & 0xFF);
                }
            }
        } else {
            for (int i = 0; i < SECTION_SIZE; ++i) {
                int rawId = container.rawIdAt(i);
                int localId = globalPalette ? Math.max(rawId, 0) : this.palette.localIdFor(rawId);
                this.writePackedLocalId(sink, localId);
            }
        }
        if (this.packer.hasPendingWord()) {
            sink.accept(this.packer.pendingWord());
        }
    }

    private void writePackedLocalId(WordSink sink, int localId) {
        if (this.packer.write(localId)) {
            sink.accept(this.packer.emittedWord());
        }
    }
}

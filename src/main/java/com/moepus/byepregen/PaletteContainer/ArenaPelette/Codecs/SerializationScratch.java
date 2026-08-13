package com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs;

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
        if (container.hasPagePalettes()) {
            this.packPagePaletteToNbt(writer, container);
        } else {
            this.packRawIdsToNbt(writer, container);
        }
        this.finishNbtPack(writer);
    }

    public void writePayloadData(PayloadWriter writer, ArenaBlockStatePalettedContainer container) {
        if (container.hasPagePalettes()) {
            this.packPagePaletteToPayload(writer, container);
        } else {
            this.packRawIdsToPayload(writer, container);
        }
        this.finishPayloadPack(writer);
    }

    void writeNetworkData(FriendlyByteBuf buffer, ArenaBlockStatePalettedContainer container, boolean globalPalette) {
        if (container.hasPagePalettes()) {
            this.packPagePaletteToBuffer(buffer, container);
        } else {
            this.packRawIdsToBuffer(buffer, container, globalPalette);
        }
        this.finishBufferPack(buffer);
    }

    public void clear() {
        this.palette.clear();
        this.packer.clear();
    }

    private void packRawIdsToNbt(NbtWriter writer, ArenaBlockStatePalettedContainer container) {
        for (int i = 0; i < SECTION_SIZE; ++i) {
            this.writePackedLocalIdToNbt(writer, this.palette.localIdFor(container.rawIdAt(i)));
        }
    }

    private void packRawIdsToPayload(PayloadWriter writer, ArenaBlockStatePalettedContainer container) {
        for (int i = 0; i < SECTION_SIZE; ++i) {
            this.writePackedLocalIdToPayload(writer, this.palette.localIdFor(container.rawIdAt(i)));
        }
    }

    private void packRawIdsToBuffer(
            FriendlyByteBuf buffer, ArenaBlockStatePalettedContainer container, boolean globalPalette) {
        for (int i = 0; i < SECTION_SIZE; ++i) {
            int rawId = container.rawIdAt(i);
            int localId = globalPalette ? Math.max(rawId, 0) : this.palette.localIdFor(rawId);
            this.writePackedLocalIdToBuffer(buffer, localId);
        }
    }

    private void packPagePaletteToNbt(NbtWriter writer, ArenaBlockStatePalettedContainer container) {
        byte[] pageLocalIds = this.palette.pageLocalIds();
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = container.arenaPageBase(page);
            int offset = page * PAGE_PALETTE_SIZE;
            for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                this.writePagePaletteWordToNbt(writer, container.arenaPaletteWord(base, wordIndex), offset, pageLocalIds);
            }
        }
    }

    private void packPagePaletteToPayload(PayloadWriter writer, ArenaBlockStatePalettedContainer container) {
        byte[] pageLocalIds = this.palette.pageLocalIds();
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = container.arenaPageBase(page);
            int offset = page * PAGE_PALETTE_SIZE;
            for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                this.writePagePaletteWordToPayload(writer, container.arenaPaletteWord(base, wordIndex), offset, pageLocalIds);
            }
        }
    }

    private void packPagePaletteToBuffer(FriendlyByteBuf buffer, ArenaBlockStatePalettedContainer container) {
        byte[] pageLocalIds = this.palette.pageLocalIds();
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = container.arenaPageBase(page);
            int offset = page * PAGE_PALETTE_SIZE;
            for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                this.writePagePaletteWordToBuffer(buffer, container.arenaPaletteWord(base, wordIndex), offset, pageLocalIds);
            }
        }
    }

    private void writePagePaletteWordToNbt(NbtWriter writer, int word, int offset, byte[] pageLocalIds) {
        this.writePackedLocalIdToNbt(writer, pageLocalIds[offset + (word & 15)] & 0xFF);
        this.writePackedLocalIdToNbt(writer, pageLocalIds[offset + ((word >>> 4) & 15)] & 0xFF);
        this.writePackedLocalIdToNbt(writer, pageLocalIds[offset + ((word >>> 8) & 15)] & 0xFF);
        this.writePackedLocalIdToNbt(writer, pageLocalIds[offset + ((word >>> 12) & 15)] & 0xFF);
        this.writePackedLocalIdToNbt(writer, pageLocalIds[offset + ((word >>> 16) & 15)] & 0xFF);
        this.writePackedLocalIdToNbt(writer, pageLocalIds[offset + ((word >>> 20) & 15)] & 0xFF);
        this.writePackedLocalIdToNbt(writer, pageLocalIds[offset + ((word >>> 24) & 15)] & 0xFF);
        this.writePackedLocalIdToNbt(writer, pageLocalIds[offset + ((word >>> 28) & 15)] & 0xFF);
    }

    private void writePagePaletteWordToPayload(
            PayloadWriter writer, int word, int offset, byte[] pageLocalIds) {
        this.writePackedLocalIdToPayload(writer, pageLocalIds[offset + (word & 15)] & 0xFF);
        this.writePackedLocalIdToPayload(writer, pageLocalIds[offset + ((word >>> 4) & 15)] & 0xFF);
        this.writePackedLocalIdToPayload(writer, pageLocalIds[offset + ((word >>> 8) & 15)] & 0xFF);
        this.writePackedLocalIdToPayload(writer, pageLocalIds[offset + ((word >>> 12) & 15)] & 0xFF);
        this.writePackedLocalIdToPayload(writer, pageLocalIds[offset + ((word >>> 16) & 15)] & 0xFF);
        this.writePackedLocalIdToPayload(writer, pageLocalIds[offset + ((word >>> 20) & 15)] & 0xFF);
        this.writePackedLocalIdToPayload(writer, pageLocalIds[offset + ((word >>> 24) & 15)] & 0xFF);
        this.writePackedLocalIdToPayload(writer, pageLocalIds[offset + ((word >>> 28) & 15)] & 0xFF);
    }

    private void writePagePaletteWordToBuffer(FriendlyByteBuf buffer, int word, int offset, byte[] pageLocalIds) {
        this.writePackedLocalIdToBuffer(buffer, pageLocalIds[offset + (word & 15)] & 0xFF);
        this.writePackedLocalIdToBuffer(buffer, pageLocalIds[offset + ((word >>> 4) & 15)] & 0xFF);
        this.writePackedLocalIdToBuffer(buffer, pageLocalIds[offset + ((word >>> 8) & 15)] & 0xFF);
        this.writePackedLocalIdToBuffer(buffer, pageLocalIds[offset + ((word >>> 12) & 15)] & 0xFF);
        this.writePackedLocalIdToBuffer(buffer, pageLocalIds[offset + ((word >>> 16) & 15)] & 0xFF);
        this.writePackedLocalIdToBuffer(buffer, pageLocalIds[offset + ((word >>> 20) & 15)] & 0xFF);
        this.writePackedLocalIdToBuffer(buffer, pageLocalIds[offset + ((word >>> 24) & 15)] & 0xFF);
        this.writePackedLocalIdToBuffer(buffer, pageLocalIds[offset + ((word >>> 28) & 15)] & 0xFF);
    }

    private void writePackedLocalIdToNbt(NbtWriter writer, int localId) {
        if (this.packer.write(localId)) {
            writer.writeLongArrayEntry(this.packer.emittedWord());
        }
    }

    private void writePackedLocalIdToPayload(PayloadWriter writer, int localId) {
        if (this.packer.write(localId)) {
            writer.writeLongArrayEntry(this.packer.emittedWord());
        }
    }

    private void writePackedLocalIdToBuffer(FriendlyByteBuf buffer, int localId) {
        if (this.packer.write(localId)) {
            buffer.writeLong(this.packer.emittedWord());
        }
    }

    private void finishNbtPack(NbtWriter writer) {
        if (this.packer.hasPendingWord()) {
            writer.writeLongArrayEntry(this.packer.pendingWord());
        }
    }

    private void finishPayloadPack(PayloadWriter writer) {
        if (this.packer.hasPendingWord()) {
            writer.writeLongArrayEntry(this.packer.pendingWord());
        }
    }

    private void finishBufferPack(FriendlyByteBuf buffer) {
        if (this.packer.hasPendingWord()) {
            buffer.writeLong(this.packer.pendingWord());
        }
    }
}

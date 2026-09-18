package com.moepus.byepregen.palette.arena;

/**
 * Exclusive terrain writer for a fresh air section. Each coordinate is written once;
 * general container writes and histogram queries resume after terrain filling finishes.
 */
public final class ArenaFreshSectionWriter {
    private final ArenaBlockStateStorage storage;
    private final int primaryRawId;
    private final Page[] pages = new Page[Layout.PAGE_COUNT];
    private int[] arena;
    private int[] dense;

    ArenaFreshSectionWriter(ArenaBlockStateStorage storage, int primaryRawId) {
        this.storage = storage;
        this.primaryRawId = primaryRawId;
        for (int i = 0; i < this.pages.length; i++) this.pages[i] = new Page(i);
    }

    public Page page(int index) { return this.pages[index]; }

    private int[] arena() {
        if (this.arena == null) this.arena = this.storage.ensureArena();
        return this.arena;
    }

    private void promoteToDense() {
        this.storage.promoteToDense();
        this.dense = this.storage.denseIdsForFreshWrite();
        this.arena = null;
    }

    public final class Page {
        private final int base;
        private final int sectionStart;
        private int primaryIndex = -1;
        private int otherRawId = -1;
        private int otherIndex;

        private Page(int index) {
            this.base = Layout.pageBase(index);
            this.sectionStart = index * Layout.PAGE_SIZE;
        }

        public void write(int localIndex, int rawId) {
            if (rawId < 0 || rawId == ArenaBlockStatePalettedContainer.AIR_RAW_ID) return;
            // A different page may have promoted the whole section since this page's last write.
            if (ArenaFreshSectionWriter.this.dense != null) {
                ArenaFreshSectionWriter.this.dense[this.sectionStart + localIndex] = rawId;
                return;
            }
            int[] data = ArenaFreshSectionWriter.this.arena();
            int paletteIndex = this.paletteIndex(data, rawId);
            if (paletteIndex < 0) {
                ArenaFreshSectionWriter.this.promoteToDense();
                ArenaFreshSectionWriter.this.dense[this.sectionStart + localIndex] = rawId;
                return;
            }
            Layout.setLocalPaletteIndex(data, this.base, localIndex, paletteIndex);
        }

        private int paletteIndex(int[] data, int rawId) {
            if (rawId == ArenaFreshSectionWriter.this.primaryRawId) {
                if (this.primaryIndex < 0) this.primaryIndex = this.find(data, rawId);
                return this.primaryIndex;
            }
            if (rawId != this.otherRawId) {
                this.otherRawId = rawId;
                this.otherIndex = this.find(data, rawId);
            }
            return this.otherIndex;
        }

        private int find(int[] data, int rawId) {
            return ArenaPaletteConversions.findOrAppendFreshPaletteIndex(data, this.base, rawId);
        }
    }
}

package com.moepus.byepregen.PaletteContainer.ArenaPelette;

public final class Layout {
    public static final int SECTION_SIZE = 4096;

    private static final int PAGE_HEIGHT = 4;
    public static final int PAGE_COUNT = 4;
    public static final int PAGE_SIZE = 16 * 16 * PAGE_HEIGHT;

    public static final int BITS_PER_ENTRY = 4;
    private static final int ENTRIES_PER_WORD = Integer.SIZE / BITS_PER_ENTRY;
    public static final int INDEX_WORDS_PER_PAGE = PAGE_SIZE / ENTRIES_PER_WORD;

    public static final int PAGE_PALETTE_SIZE = 16;
    public static final int PAGE_DEFAULT_OFFSET = INDEX_WORDS_PER_PAGE;
    public static final int EXTRA_PALETTE_OFFSET = PAGE_DEFAULT_OFFSET + 1;
    static final int EXTRA_PALETTE_SIZE = PAGE_PALETTE_SIZE - 1;

    public static final int PAGE_STRIDE = INDEX_WORDS_PER_PAGE + PAGE_PALETTE_SIZE;
    static final int ARENA_INTS = PAGE_COUNT * PAGE_STRIDE;
    static final int PALETTE_INDEX_MASK = PAGE_PALETTE_SIZE - 1;

    private Layout() {
    }

    static int pageLivePaletteMask(int[] arena, int base) {
        int seen = 0;
        for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
            int word = arena[base + wordIndex];
            seen |= 1 << (word & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 4) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 8) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 12) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 16) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 20) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 24) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 28) & PALETTE_INDEX_MASK);
        }
        return seen;
    }

    public static int localPaletteIndex(int[] arena, int base, int local) {
        int word = arena[base + (local >>> 3)];
        int shift = (local & 7) << 2;
        return (word >>> shift) & PALETTE_INDEX_MASK;
    }

    public static void setLocalPaletteIndex(int[] arena, int base, int local, int paletteIndex) {
        int wordIndex = base + (local >>> 3);
        int shift = (local & 7) << 2;
        int oldWord = arena[wordIndex];
        arena[wordIndex] = (oldWord & ~(PALETTE_INDEX_MASK << shift)) | ((paletteIndex & PALETTE_INDEX_MASK) << shift);
    }

    static int pageIndexFromSectionIndex(int sectionIndex) {
        return sectionIndex >>> 10;
    }

    static int pageLocalIndexFromSectionIndex(int sectionIndex) {
        int y = sectionIndex >>> 8;
        return ((y & 3) << 8) | (sectionIndex & 255);
    }

    public static int sectionIndex(int page, int local) {
        int y = (page << 2) | (local >>> 8);
        return (y << 8) | (local & 255);
    }

    public static int pageBase(int page) {
        return page * PAGE_STRIDE;
    }

    public static int localIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}

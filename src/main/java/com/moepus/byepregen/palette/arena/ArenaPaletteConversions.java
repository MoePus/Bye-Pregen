package com.moepus.byepregen.palette.arena;

import static com.moepus.byepregen.palette.arena.Layout.*;

import com.moepus.byepregen.UnsafeIntArrayAccess;
import java.util.Arrays;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class ArenaPaletteConversions {
    private ArenaPaletteConversions() {
    }

    static int[] createArena(int uniformRawId) {
        int[] arena = new int[ARENA_INTS];
        for (int page = 0; page < PAGE_COUNT; ++page) {
            arena[pageBase(page) + PAGE_DEFAULT_OFFSET] = uniformRawId;
        }
        return arena;
    }

    static int[] toDense(int[] arena, int uniformRawId) {
        int[] dense = new int[SECTION_SIZE];
        if (arena == null) {
            Arrays.fill(dense, uniformRawId);
            return dense;
        }
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = pageBase(page);
            for (int local = 0; local < PAGE_SIZE; ++local) {
                int paletteIndex = localPaletteIndex(arena, base, local);
                dense[sectionIndex(page, local)] = rawIdForPaletteIndex(arena, base, paletteIndex);
            }
        }
        return dense;
    }

    static void unpack(Object[] values, BlockState uniformState, int[] arena, int[] dense) {
        if (dense != null) {
            for (int i = 0; i < SECTION_SIZE; ++i) {
                values[i] = Block.stateById(UnsafeIntArrayAccess.get(dense, i));
            }
            return;
        }
        if (arena == null) {
            Arrays.fill(values, uniformState);
            return;
        }
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = pageBase(page);
            for (int local = 0; local < PAGE_SIZE; ++local) {
                int paletteIndex = localPaletteIndex(arena, base, local);
                values[sectionIndex(page, local)] = Block.stateById(
                        rawIdForPaletteIndex(arena, base, paletteIndex));
            }
        }
    }

    static int findPaletteIndex(int[] arena, int base, int rawId) {
        if (rawId == arena[base + PAGE_DEFAULT_OFFSET]) {
            return 0;
        }
        int stored = rawId + 1;
        for (int i = 0; i < EXTRA_PALETTE_SIZE; ++i) {
            if (arena[base + EXTRA_PALETTE_OFFSET + i] == stored) {
                return i + 1;
            }
        }
        return -1;
    }

    static int appendPaletteIndex(int[] arena, int base, int rawId) {
        int stored = rawId + 1;
        for (int i = 0; i < EXTRA_PALETTE_SIZE; ++i) {
            int offset = base + EXTRA_PALETTE_OFFSET + i;
            if (arena[offset] == 0) {
                arena[offset] = stored;
                return i + 1;
            }
        }
        return -1;
    }

    static int findOrAppendFreshPaletteIndex(int[] arena, int base, int rawId) {
        if (rawId == arena[base + PAGE_DEFAULT_OFFSET]) {
            return 0;
        }
        int stored = rawId + 1;
        for (int i = 0; i < EXTRA_PALETTE_SIZE; ++i) {
            int offset = base + EXTRA_PALETTE_OFFSET + i;
            int entry = arena[offset];
            if (entry == stored) {
                return i + 1;
            }
            if (entry == 0) {
                arena[offset] = stored;
                return i + 1;
            }
        }
        return -1;
    }

    static boolean tryReuseDeadSlot(
            int[] arena, int base, int local, int oldPaletteIndex, int newRawId) {
        int seen = 0;
        int twice = 0;
        for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
            int word = arena[base + wordIndex];
            for (int shift = 0; shift < Integer.SIZE; shift += BITS_PER_ENTRY) {
                int bit = 1 << ((word >>> shift) & PALETTE_INDEX_MASK);
                twice |= seen & bit;
                seen |= bit;
            }
        }

        int oldBit = 1 << oldPaletteIndex;
        boolean oldDiesAfterWrite = (seen & oldBit) != 0 && (twice & oldBit) == 0;
        int deadMask = (~seen) & 0xFFFF;
        int extraDead = deadMask & 0xFFFE;
        int newPaletteIndex;
        if (oldDiesAfterWrite) {
            newPaletteIndex = oldPaletteIndex;
        } else if (extraDead != 0) {
            newPaletteIndex = Integer.numberOfTrailingZeros(extraDead);
        } else if ((deadMask & 1) != 0) {
            newPaletteIndex = 0;
        } else {
            return false;
        }

        if (newPaletteIndex == 0) {
            arena[base + PAGE_DEFAULT_OFFSET] = newRawId;
        } else {
            arena[base + EXTRA_PALETTE_OFFSET + newPaletteIndex - 1] = newRawId + 1;
        }
        setLocalPaletteIndex(arena, base, local, newPaletteIndex);
        return true;
    }

    static int rawIdForPaletteIndex(int[] arena, int base, int paletteIndex) {
        return arena[base + PAGE_DEFAULT_OFFSET + paletteIndex]
                - ((paletteIndex + PAGE_PALETTE_SIZE - 1) >>> BITS_PER_ENTRY);
    }

    static int uniformRawId(int[] arena) {
        if (arena == null) {
            return -1;
        }
        int rawId = arena[PAGE_DEFAULT_OFFSET];
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = pageBase(page);
            if (arena[base + PAGE_DEFAULT_OFFSET] != rawId) {
                return -1;
            }
            for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                if (arena[base + wordIndex] != 0) {
                    return -1;
                }
            }
        }
        return rawId;
    }
}

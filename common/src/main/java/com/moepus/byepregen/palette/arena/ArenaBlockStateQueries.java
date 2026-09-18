package com.moepus.byepregen.palette.arena;

import static com.moepus.byepregen.palette.arena.Layout.*;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

final class ArenaBlockStateQueries {
    private ArenaBlockStateQueries() {
    }

    static void getAll(ArenaBlockStatePalettedContainer container, Consumer<BlockState> consumer) {
        switch (container.mode()) {
            case UNIFORM -> consumer.accept(Block.stateById(container.uniformRawId()));
            case PAGE_PALETTE -> getAllPagePaletteStates(container, consumer);
            case DENSE -> {
                for (int rawId : container.denseRawIdCounts().keySet()) {
                    consumer.accept(Block.stateById(rawId));
                }
            }
        }
    }

    static boolean maybeHas(ArenaBlockStatePalettedContainer container, Predicate<BlockState> predicate) {
        return switch (container.mode()) {
            case UNIFORM -> predicate.test(Block.stateById(container.uniformRawId()));
            case PAGE_PALETTE -> maybeHasPagePaletteState(container, predicate);
            case DENSE -> maybeHasDenseRawId(container, predicate);
        };
    }

    static void count(
            ArenaBlockStatePalettedContainer container, PalettedContainer.CountConsumer<BlockState> consumer) {
        switch (container.mode()) {
            case UNIFORM -> consumer.accept(Block.stateById(container.uniformRawId()), SECTION_SIZE);
            case DENSE -> container.denseRawIdCounts().int2IntEntrySet().forEach(
                    entry -> consumer.accept(Block.stateById(entry.getIntKey()), entry.getIntValue()));
            case PAGE_PALETTE -> {
                Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
                countRawIds(container, counts);
                counts.int2IntEntrySet().forEach(
                        entry -> consumer.accept(Block.stateById(entry.getIntKey()), entry.getIntValue()));
            }
        }
    }

    static void forEachRawId(ArenaBlockStatePalettedContainer container, RawIdConsumer consumer) {
        for (int i = 0; i < SECTION_SIZE; ++i) {
            consumer.accept(i, container.rawIdAt(i));
        }
    }

    static void countRawIds(ArenaBlockStatePalettedContainer container, Int2IntOpenHashMap counts) {
        switch (container.mode()) {
            case DENSE -> container.denseRawIdCounts().int2IntEntrySet().forEach(
                    entry -> counts.addTo(entry.getIntKey(), entry.getIntValue()));
            case UNIFORM -> forEachRawId(container, (sectionIndex, rawId) -> counts.addTo(rawId, 1));
            case PAGE_PALETTE -> countPagePaletteRawIds(container, counts);
        }
    }

    private static void countPagePaletteRawIds(
            ArenaBlockStatePalettedContainer container, Int2IntOpenHashMap counts) {
        int[] paletteCounts = new int[PAGE_PALETTE_SIZE];
        for (int page = 0; page < PAGE_COUNT; ++page) {
            Arrays.fill(paletteCounts, 0);
            int base = container.arenaPageBase(page);
            countPagePaletteIndexes(container, base, paletteCounts);
            addPagePaletteCounts(container, base, paletteCounts, counts);
        }
    }

    private static void countPagePaletteIndexes(
            ArenaBlockStatePalettedContainer container, int base, int[] paletteCounts) {
        for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
            int word = container.arenaPaletteWord(base, wordIndex);
            ++paletteCounts[word & PALETTE_INDEX_MASK];
            ++paletteCounts[(word >>> 4) & PALETTE_INDEX_MASK];
            ++paletteCounts[(word >>> 8) & PALETTE_INDEX_MASK];
            ++paletteCounts[(word >>> 12) & PALETTE_INDEX_MASK];
            ++paletteCounts[(word >>> 16) & PALETTE_INDEX_MASK];
            ++paletteCounts[(word >>> 20) & PALETTE_INDEX_MASK];
            ++paletteCounts[(word >>> 24) & PALETTE_INDEX_MASK];
            ++paletteCounts[(word >>> 28) & PALETTE_INDEX_MASK];
        }
    }

    private static void addPagePaletteCounts(
            ArenaBlockStatePalettedContainer container, int base, int[] paletteCounts, Int2IntOpenHashMap counts) {
        for (int paletteIndex = 0; paletteIndex < PAGE_PALETTE_SIZE; ++paletteIndex) {
            int count = paletteCounts[paletteIndex];
            if (count != 0) {
                counts.addTo(container.arenaPaletteRawId(base, paletteIndex), count);
            }
        }
    }

    private static void getAllPagePaletteStates(
            ArenaBlockStatePalettedContainer container, Consumer<BlockState> consumer) {
        IntSet seen = new IntOpenHashSet();
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = container.arenaPageBase(page);
            int livePaletteMask = container.arenaLivePaletteMask(base);
            for (int paletteIndex = 0; paletteIndex < PAGE_PALETTE_SIZE; ++paletteIndex) {
                if ((livePaletteMask & (1 << paletteIndex)) == 0) {
                    continue;
                }

                int rawId = container.arenaPaletteRawId(base, paletteIndex);
                if (seen.add(rawId)) {
                    consumer.accept(Block.stateById(rawId));
                }
            }
        }
    }

    private static boolean maybeHasPagePaletteState(
            ArenaBlockStatePalettedContainer container, Predicate<BlockState> predicate) {
        IntSet seen = new IntOpenHashSet();
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = container.arenaPageBase(page);
            for (int paletteIndex = 0; paletteIndex < PAGE_PALETTE_SIZE; ++paletteIndex) {
                int rawId = container.arenaPaletteRawId(base, paletteIndex);
                if (rawId >= 0 && seen.add(rawId) && predicate.test(Block.stateById(rawId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean maybeHasDenseRawId(
            ArenaBlockStatePalettedContainer container, Predicate<BlockState> predicate) {
        for (int rawId : container.denseRawIdCounts().keySet()) {
            if (predicate.test(Block.stateById(rawId))) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface RawIdConsumer {
        void accept(int sectionIndex, int rawId);
    }
}

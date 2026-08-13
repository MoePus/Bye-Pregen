package com.moepus.byepregen.chunksave.serialize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.junit.jupiter.api.Test;

public final class ChunkSectionSerializationContextTest {
    private ChunkSectionSerializationContextTest() {}

    @Test
    void matchesVanillaPackingStrategies() {
        assertEquivalent(PalettedContainer.Strategy.SECTION_STATES, 1, 4);
        assertEquivalent(PalettedContainer.Strategy.SECTION_STATES, 5, 4);
        assertEquivalent(PalettedContainer.Strategy.SECTION_STATES, 17, 4);
        assertEquivalent(PalettedContainer.Strategy.SECTION_STATES, 300, 4);
        assertEquivalent(PalettedContainer.Strategy.SECTION_BIOMES, 1, 0);
        assertEquivalent(PalettedContainer.Strategy.SECTION_BIOMES, 3, 0);
        assertEquivalent(PalettedContainer.Strategy.SECTION_BIOMES, 9, 0);
    }

    private static void assertEquivalent(
            PalettedContainer.Strategy strategy, int distinctValues, int minimumBits) {
        int registrySize = Math.max(512, distinctValues);
        CrudeIncrementalIntIdentityHashBiMap<Object> registry =
                CrudeIncrementalIntIdentityHashBiMap.create(registrySize);
        List<Object> values = new ArrayList<>(registrySize);
        for (int index = 0; index < registrySize; ++index) {
            Object value = new Object();
            values.add(value);
            registry.add(value);
        }

        PalettedContainer<Object> container = new PalettedContainer<>(registry, values.get(0), strategy);
        fill(container, strategy, values, distinctValues);
        PalettedContainerRO.PackedData<Object> expected = container.pack(registry, strategy);
        ChunkSectionSerializationContext actual = new ChunkSectionSerializationContext();
        actual.pack(container, minimumBits);

        assertPalette(expected.paletteEntries(), actual, distinctValues, strategy);
        long[] expectedStorage = expected.storage().map(stream -> stream.toArray()).orElseGet(() -> new long[0]);
        long[] actualStorage = actual.packedLength() == 0
                ? new long[0]
                : Arrays.copyOf(actual.packed(), actual.packedLength());
        if (!Arrays.equals(expectedStorage, actualStorage)) {
            throw new AssertionError("packed data mismatch for " + strategy + " with " + distinctValues + " values");
        }
    }

    private static void fill(
            PalettedContainer<Object> container, PalettedContainer.Strategy strategy,
            List<Object> values, int distinctValues) {
        int axisSize = strategy == PalettedContainer.Strategy.SECTION_STATES ? 16 : 4;
        int index = 0;
        for (int y = 0; y < axisSize; ++y) {
            for (int z = 0; z < axisSize; ++z) {
                for (int x = 0; x < axisSize; ++x) {
                    container.set(x, y, z, values.get(index++ % distinctValues));
                }
            }
        }
    }

    private static void assertPalette(
            List<Object> expected, ChunkSectionSerializationContext actual,
            int distinctValues, PalettedContainer.Strategy strategy) {
        if (expected.size() != actual.paletteSize()) {
            throw new AssertionError("palette size mismatch for " + strategy + " with " + distinctValues + " values");
        }
        for (int index = 0; index < expected.size(); ++index) {
            if (expected.get(index) != actual.paletteEntry(index)) {
                throw new AssertionError("palette order mismatch at index " + index);
            }
        }
    }
}

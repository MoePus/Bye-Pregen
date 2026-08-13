package com.moepus.byepregen.test;

import com.moepus.byepregen.worldgen.biome.SurfaceBiomeManager;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.junit.jupiter.api.Test;

public final class SurfaceBiomeCacheTest {
    private static final int MIN_BUILD_Y = -64;
    private static final int HEIGHT = 384;
    private static final int QUART_HEIGHT = 96;
    private static final int MIN_QUART_Y = QuartPos.fromBlock(MIN_BUILD_Y);
    private static final int MAX_QUART_Y = MIN_QUART_Y + QUART_HEIGHT - 1;
    private static final long ZOOM_SEED = 0x4F9939F508L;

    private SurfaceBiomeCacheTest() {
    }

    @Test
    void skipsAllInteriorLookupsForUniformBiomes() {
        CountingSource source = new CountingSource(true, Pattern.UNIFORM);
        ChunkPos center = new ChunkPos(0, 0);
        SurfaceBiomeManager manager = createManager(source, center);
        assertEquals(16 * QUART_HEIGHT, source.totalCalls(), "flat array must be populated once");
        assertEquals(9 * (QUART_HEIGHT + 1), manager.certificateCount(), "certificate dimensions");
        assertEquals(manager.certificateCount(), manager.uniformCertificateCount(), "uniform certificate coverage");

        source.clearCalls();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 2; x <= 13; x++) {
            for (int z = 2; z <= 13; z++) {
                for (int y = MIN_BUILD_Y - 8; y < MIN_BUILD_Y + HEIGHT + 8; y++) {
                    manager.getBiome(pos.set(x, y, z));
                }
            }
        }
        assertEquals(0, source.totalCalls(), "uniform interior must bypass the noise biome source");
    }

    @Test
    void matchesBiomeManagerAcrossTheWholeChunk() {
        ChunkPos center = new ChunkPos(-3, 2);
        CountingSource source = new CountingSource(true, Pattern.UNIQUE);
        BiomeManager original = new BiomeManager(source, ZOOM_SEED);
        SurfaceBiomeManager cached = createManager(source, center);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = center.getMinBlockX(); x <= center.getMaxBlockX(); x++) {
            for (int z = center.getMinBlockZ(); z <= center.getMaxBlockZ(); z++) {
                for (int y = MIN_BUILD_Y - 8; y < MIN_BUILD_Y + HEIGHT + 8; y++) {
                    pos.set(x, y, z);
                    assertSame(original.getBiome(pos), cached.getBiome(pos));
                }
            }
        }
        assertEquals(0, cached.uniformCertificateCount(), "unique quart cells must stay on the selector path");
    }

    @Test
    void matchesMixedCertificatesAcrossTheWholeChunk() {
        ChunkPos center = new ChunkPos(-3, -2);
        CountingSource source = new CountingSource(true, Pattern.MIXED);
        BiomeManager original = new BiomeManager(source, ZOOM_SEED);
        SurfaceBiomeManager cached = createManager(source, center);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        if (cached.uniformCertificateCount() <= 0
                || cached.uniformCertificateCount() >= cached.certificateCount()) {
            throw new AssertionError("mixed palette must exercise certificate hits and misses");
        }
        for (int x = center.getMinBlockX(); x <= center.getMaxBlockX(); x++) {
            for (int z = center.getMinBlockZ(); z <= center.getMaxBlockZ(); z++) {
                for (int y = MIN_BUILD_Y - 8; y < MIN_BUILD_Y + HEIGHT + 8; y++) {
                    pos.set(x, y, z);
                    assertSame(original.getBiome(pos), cached.getBiome(pos));
                }
            }
        }
    }

    @Test
    void handlesNonAlignedHeightAndNegativeChunkBoundaries() {
        ChunkPos center = new ChunkPos(-1, -1);
        CountingSource source = new CountingSource(false, Pattern.UNIQUE);
        SurfaceBiomeManager manager = SurfaceBiomeManager.fromSource(new SurfaceBiomeManager.SourceOptions(
                source,
                ZOOM_SEED,
                center,
                LevelHeightAccessor.create(-3, 1)
        ));
        int minQuartX = QuartPos.fromSection(center.x);
        int minQuartZ = QuartPos.fromSection(center.z);

        source.clearCalls();
        assertSame(
                manager.getNoiseBiomeAtQuart(minQuartX, -100, minQuartZ),
                manager.getNoiseBiomeAtQuart(minQuartX, 100, minQuartZ)
        );
        assertEquals(0, source.totalCalls(), "current-chunk Y queries must use the clamped flat cell");

        manager.getNoiseBiomeAtQuart(minQuartX - 1, 37, minQuartZ);
        manager.getNoiseBiomeAtQuart(minQuartX - 1, 37, minQuartZ);
        assertEquals(2, source.callsAt(minQuartX - 1, 37, minQuartZ), "neighbor fallback must preserve Y");
    }

    @Test
    void rejectsExcessiveVerticalSpan() {
        CountingSource source = new CountingSource(false, Pattern.UNIQUE);
        try {
            SurfaceBiomeManager.fromSource(new SurfaceBiomeManager.SourceOptions(
                    source,
                    ZOOM_SEED,
                    new ChunkPos(0, 0),
                    LevelHeightAccessor.create(0, 4097)
            ));
            throw new AssertionError("Expected excessive biome cache height rejection");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("Unsupported biome cache height")) {
                throw new AssertionError("Unexpected rejection: " + expected.getMessage());
            }
        }
    }

    private static SurfaceBiomeManager createManager(CountingSource source, ChunkPos center) {
        return SurfaceBiomeManager.fromSource(new SurfaceBiomeManager.SourceOptions(
                source,
                ZOOM_SEED,
                center,
                LevelHeightAccessor.create(MIN_BUILD_Y, HEIGHT)
        ));
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError("expected identical biome holders");
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private record QuartCell(int x, int y, int z) {
    }

    private enum Pattern {
        UNIFORM,
        UNIQUE,
        MIXED
    }

    private static final class CountingSource implements BiomeManager.NoiseBiomeSource {
        private final Map<QuartCell, Holder<Biome>> biomes = new HashMap<>();
        private final Map<QuartCell, Integer> calls = new HashMap<>();
        private final Holder<Biome> uniformBiome = Holder.direct(null);
        private final boolean clampY;
        private final Pattern pattern;

        private CountingSource(boolean clampY, Pattern pattern) {
            this.clampY = clampY;
            this.pattern = pattern;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            int resolvedY = this.clampY ? Math.max(MIN_QUART_Y, Math.min(y, MAX_QUART_Y)) : y;
            QuartCell cell = new QuartCell(x, resolvedY, z);
            this.calls.merge(cell, 1, Integer::sum);
            if (this.pattern == Pattern.UNIFORM) {
                return this.uniformBiome;
            }
            QuartCell biomeCell = this.pattern == Pattern.MIXED
                    ? new QuartCell(Math.floorDiv(x, 2), Math.floorDiv(resolvedY, 2), Math.floorDiv(z, 2))
                    : cell;
            return this.biomes.computeIfAbsent(biomeCell, ignored -> Holder.direct(null));
        }

        private int totalCalls() {
            return this.calls.values().stream().mapToInt(Integer::intValue).sum();
        }

        private int callsAt(int x, int y, int z) {
            return this.calls.getOrDefault(new QuartCell(x, y, z), 0);
        }

        private void clearCalls() {
            this.calls.clear();
        }
    }
}

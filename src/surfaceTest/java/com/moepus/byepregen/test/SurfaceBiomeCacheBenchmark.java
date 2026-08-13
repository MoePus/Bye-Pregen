package com.moepus.byepregen.test;

import com.moepus.byepregen.worldgen.biome.SurfaceBiomeManager;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class SurfaceBiomeCacheBenchmark {
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int Y_SAMPLES_PER_COLUMN = 28;
    private static final int TRACE_SIZE = 16 * 16 * Y_SAMPLES_PER_COLUMN;
    private static final int BATCH_CHUNKS = 512;
    private static final int WARMUP_ROUNDS = 8;
    private static final int MEASUREMENT_ROUNDS = 12;
    private static final int PROFILE_CALLS = 1 << 15;
    private static final long ZOOM_SEED = 0x4F9939F508L;
    private static final LevelHeightAccessor HEIGHT_ACCESSOR = LevelHeightAccessor.create(MIN_Y, HEIGHT);
    private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN = allocationBean();
    private static final Pattern PATTERN = Pattern.valueOf(
            System.getProperty("byepregen.surfaceBiomeCacheBenchmarkPattern", "NON_UNIFORM")
                    .toUpperCase(Locale.ROOT)
    );
    private static volatile long sink;

    private SurfaceBiomeCacheBenchmark() {
    }

    public static void main(String[] args) {
        Mode mode = Mode.valueOf(System.getProperty("byepregen.surfaceBiomeCacheBenchmarkMode", "OFF")
                .toUpperCase(Locale.ROOT));
        BenchmarkState state = new BenchmarkState();
        int chunkOffset = 0;
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            state.profileReceivers(mode);
            sink ^= state.runBatch(mode, chunkOffset);
            chunkOffset += BATCH_CHUNKS;
        }

        long[] elapsed = new long[MEASUREMENT_ROUNDS];
        long[] allocated = new long[MEASUREMENT_ROUNDS];
        for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
            state.profileReceivers(mode);
            long allocationStart = allocatedBytes();
            long started = System.nanoTime();
            sink ^= state.runBatch(mode, chunkOffset);
            elapsed[round] = System.nanoTime() - started;
            allocated[round] = allocatedBytes() - allocationStart;
            chunkOffset += BATCH_CHUNKS;
        }
        report(mode, elapsed, allocated);
        reportHitRate();
    }

    private static void report(Mode mode, long[] elapsed, long[] allocated) {
        Arrays.sort(elapsed);
        Arrays.sort(allocated);
        long queriesPerRound = (long) BATCH_CHUNKS * TRACE_SIZE;
        double medianNanos = elapsed[elapsed.length / 2] / (double) queriesPerRound;
        double bestNanos = elapsed[0] / (double) queriesPerRound;
        double allocationPerChunk = allocated[allocated.length / 2] / (double) BATCH_CHUNKS;
        System.out.printf(
                Locale.ROOT,
                "mode=%s pattern=%s queriesPerRound=%d bestNsPerQuery=%.3f medianNsPerQuery=%.3f "
                        + "allocatedBytesPerChunk=%.1f sink=%d%n",
                mode,
                PATTERN,
                queriesPerRound,
                bestNanos,
                medianNanos,
                allocationPerChunk,
                sink
        );
    }

    private static void reportHitRate() {
        CountingSource source = new CountingSource(new RegionBiomeSource());
        SurfaceBiomeManager manager = createManager(source, new ChunkPos(0, 0));
        source.reset();
        BenchmarkState state = new BenchmarkState();
        state.runTrace(manager, 0, 0);
        long hits = TRACE_SIZE - source.calls;
        System.out.printf(
                Locale.ROOT,
                "traceRequests=%d delegateMisses=%d flatOrCertificateHits=%d hitRate=%.2f%% "
                        + "uniformCertificates=%d/%d%n",
                TRACE_SIZE,
                source.calls,
                hits,
                hits * 100.0D / TRACE_SIZE,
                manager.uniformCertificateCount(),
                manager.certificateCount()
        );
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean allocationBean)) {
            throw new IllegalStateException("Thread allocation accounting is unavailable");
        }
        allocationBean.setThreadAllocatedMemoryEnabled(true);
        return allocationBean;
    }

    private static long allocatedBytes() {
        return ALLOCATION_BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private enum Mode {
        OFF,
        CACHE_MONO,
        CACHE_TRI
    }

    private enum Pattern {
        UNIFORM,
        NON_UNIFORM
    }

    private static final class BenchmarkState {
        private final RegionBiomeSource source = new RegionBiomeSource(PATTERN);
        private final BiomeManager original = new BiomeManager(this.source, ZOOM_SEED);
        private final BiomeManager secondary = new BiomeManager(new SecondarySource(), ZOOM_SEED);
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final int[] traceX = new int[TRACE_SIZE];
        private final int[] traceY = new int[TRACE_SIZE];
        private final int[] traceZ = new int[TRACE_SIZE];

        private BenchmarkState() {
            int index = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int sample = 0; sample < Y_SAMPLES_PER_COLUMN; sample++) {
                        this.traceX[index] = x;
                        this.traceY[index] = MIN_Y + Math.floorMod(sample * 47 + x * 3 + z * 5, HEIGHT);
                        this.traceZ[index] = z;
                        index++;
                    }
                }
            }
        }

        private int runBatch(Mode mode, int chunkOffset) {
            int checksum = 1;
            for (int chunkIndex = 0; chunkIndex < BATCH_CHUNKS; chunkIndex++) {
                int sequence = chunkOffset + chunkIndex;
                int chunkX = sequence & 63;
                int chunkZ = sequence >>> 6;
                BiomeManager manager = this.manager(mode, new ChunkPos(chunkX, chunkZ));
                checksum = 31 * checksum + this.runTrace(manager, chunkX << 4, chunkZ << 4);
            }
            return checksum;
        }

        private BiomeManager manager(Mode mode, ChunkPos center) {
            if (mode == Mode.OFF) {
                return this.original;
            }
            return createManager(this.source, center);
        }

        private int runTrace(BiomeManager manager, int originX, int originZ) {
            int checksum = 1;
            for (int index = 0; index < TRACE_SIZE; index++) {
                this.pos.set(originX + this.traceX[index], this.traceY[index], originZ + this.traceZ[index]);
                checksum = 31 * checksum + System.identityHashCode(manager.getBiome(this.pos));
            }
            return checksum;
        }

        private void profileReceivers(Mode mode) {
            if (mode == Mode.CACHE_MONO) {
                return;
            }
            for (int index = 0; index < PROFILE_CALLS; index++) {
                this.pos.set(index & 15, MIN_Y + (index & 255), index >>> 4 & 15);
                sink ^= System.identityHashCode(this.secondary.getBiome(this.pos));
                if (mode == Mode.CACHE_TRI) {
                    sink ^= System.identityHashCode(this.original.getBiome(this.pos));
                }
            }
        }
    }

    private static SurfaceBiomeManager createManager(
            BiomeManager.NoiseBiomeSource source,
            ChunkPos center
    ) {
        return SurfaceBiomeManager.fromSource(new SurfaceBiomeManager.SourceOptions(
                source,
                ZOOM_SEED,
                center,
                HEIGHT_ACCESSOR
        ));
    }

    private static class RegionBiomeSource implements BiomeManager.NoiseBiomeSource {
        private static final int CHUNK_MASK = 15;
        private final ChunkBiomes[] chunks = new ChunkBiomes[CHUNK_MASK + 1];

        private RegionBiomeSource() {
            this(PATTERN);
        }

        private RegionBiomeSource(Pattern pattern) {
            for (int index = 0; index < this.chunks.length; index++) {
                this.chunks[index] = new ChunkBiomes(index, pattern);
            }
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            int chunkX = x >> 2;
            int chunkZ = z >> 2;
            ChunkBiomes chunk = this.chunks[(chunkX * 31 + chunkZ) & CHUNK_MASK];
            return chunk.getNoiseBiome(x, y, z);
        }
    }

    private static final class ChunkBiomes {
        private static final int QUARTS_PER_SECTION = 4;
        private static final int MIN_QUART_Y = MIN_Y >> 2;
        private static final int SECTION_COUNT = HEIGHT / 16;
        private final Holder<Biome>[] palette;
        private final PalettedContainer<Holder<Biome>>[] sections;

        @SuppressWarnings("unchecked")
        private ChunkBiomes(int chunkIndex, Pattern pattern) {
            this.palette = (Holder<Biome>[]) new Holder<?>[4];
            IdMapper<Holder<Biome>> registry = new IdMapper<>(this.palette.length);
            for (int index = 0; index < this.palette.length; index++) {
                this.palette[index] = Holder.direct(null);
                registry.add(this.palette[index]);
            }
            this.sections = (PalettedContainer<Holder<Biome>>[]) new PalettedContainer<?>[SECTION_COUNT];
            for (int section = 0; section < this.sections.length; section++) {
                this.sections[section] = createSection(registry, section + chunkIndex, pattern);
            }
        }

        private Holder<Biome> getNoiseBiome(int x, int y, int z) {
            int section = Math.max(0, Math.min((y - MIN_QUART_Y) >> 2, this.sections.length - 1));
            return this.sections[section].get(x & 3, y & 3, z & 3);
        }

        private PalettedContainer<Holder<Biome>> createSection(
                IdMapper<Holder<Biome>> registry,
                int section,
                Pattern pattern
        ) {
            PalettedContainer<Holder<Biome>> container = new PalettedContainer<>(
                    registry,
                    this.palette[0],
                    PalettedContainer.Strategy.SECTION_BIOMES
            );
            for (int x = 0; x < QUARTS_PER_SECTION; x++) {
                for (int y = 0; y < QUARTS_PER_SECTION; y++) {
                    for (int z = 0; z < QUARTS_PER_SECTION; z++) {
                        int paletteIndex = pattern == Pattern.UNIFORM ? 0 : (x + y + z + section) & 3;
                        container.getAndSetUnchecked(x, y, z, this.palette[paletteIndex]);
                    }
                }
            }
            return container;
        }
    }

    private static final class CountingSource implements BiomeManager.NoiseBiomeSource {
        private final BiomeManager.NoiseBiomeSource delegate;
        private long calls;

        private CountingSource(BiomeManager.NoiseBiomeSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            this.calls++;
            return this.delegate.getNoiseBiome(x, y, z);
        }

        private void reset() {
            this.calls = 0L;
        }
    }

    private static final class SecondarySource implements BiomeManager.NoiseBiomeSource {
        private final Holder<Biome> biome = Holder.direct(null);

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            return this.biome;
        }
    }
}

package com.moepus.byepregen.worldgen.biome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

class SurfaceBiomeLookup implements BiomeManager.NoiseBiomeSource {
    static final int QUARTS_PER_CHUNK = 4;
    static final int INTERIOR_QUART_CUBES = QUARTS_PER_CHUNK - 1;

    private static final int MIN_INTERIOR_BLOCK = 2;
    private static final int MAX_INTERIOR_BLOCK = 13;
    private static final int BLOCKS_PER_QUART = 4;
    private static final int MAX_QUART_HEIGHT = 1024;

    private final BiomeManager.NoiseBiomeSource delegate;
    protected final int minQuartX;
    protected final int minQuartZ;
    private final int minBlockX;
    private final int minBlockZ;
    private final int minQuartY;
    private final int quartHeight;
    private final Holder<Biome>[] flatBiomes;
    private final SurfaceBiomeCertificates certificates;

    SurfaceBiomeLookup(LookupInput input) {
        ChunkPos center = input.center();
        this.delegate = input.delegate();
        this.minBlockX = center.getMinBlockX();
        this.minBlockZ = center.getMinBlockZ();
        this.minQuartX = QuartPos.fromSection(center.x);
        this.minQuartY = QuartPos.fromBlock(input.heightAccessor().getMinBuildHeight());
        this.minQuartZ = QuartPos.fromSection(center.z);
        this.quartHeight = requireQuartHeight(input.heightAccessor());
        this.flatBiomes = this.flatten(input.reader());
        this.certificates = SurfaceBiomeCertificateBuilder.build(this.flatBiomes, this.quartHeight);
    }

    static SurfaceBiomeLookup fromChunk(BiomeManager.NoiseBiomeSource delegate, ChunkAccess chunk) {
        return new SurfaceBiomeLookup(inputFromChunk(delegate, chunk));
    }

    static SurfaceBiomeLookup fromSource(
            BiomeManager.NoiseBiomeSource source,
            ChunkPos center,
            LevelHeightAccessor heightAccessor
    ) {
        return new SurfaceBiomeLookup(new LookupInput(source, center, heightAccessor, source::getNoiseBiome));
    }

    static LookupInput inputFromChunk(BiomeManager.NoiseBiomeSource delegate, ChunkAccess chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        FlatBiomeReader reader = (x, y, z) -> {
            int sectionIndex = chunk.getSectionIndex(QuartPos.toBlock(y));
            return sections[sectionIndex].getNoiseBiome(x & 3, y & 3, z & 3);
        };
        return new LookupInput(delegate, chunk.getPos(), chunk, reader);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        int localX = x - this.minQuartX;
        int localZ = z - this.minQuartZ;
        if (!insideChunk(localX) || !insideChunk(localZ)) {
            return this.delegate.getNoiseBiome(x, y, z);
        }

        int localY = Math.clamp(y - this.minQuartY, 0, this.quartHeight - 1);
        return this.flatBiomes[this.flatIndex(localX, localY, localZ)];
    }

    final Holder<Biome> uniformBiome(BlockPos pos) {
        int localBlockX = pos.getX() - this.minBlockX;
        int localBlockZ = pos.getZ() - this.minBlockZ;
        if (!insideInterior(localBlockX) || !insideInterior(localBlockZ)) {
            return null;
        }

        int cubeX = (localBlockX - MIN_INTERIOR_BLOCK) >> 2;
        int cubeZ = (localBlockZ - MIN_INTERIOR_BLOCK) >> 2;
        int baseQuartY = (pos.getY() - MIN_INTERIOR_BLOCK) >> 2;
        int cubeY = Math.clamp(baseQuartY - this.minQuartY + 1, 0, this.quartHeight);
        int certificate = this.certificates.at(cubeX, cubeY, cubeZ);
        return certificate == SurfaceBiomeCertificates.NON_UNIFORM ? null : this.flatBiomes[certificate];
    }

    final boolean isInterior(BlockPos pos) {
        return insideInterior(pos.getX() - this.minBlockX)
                && insideInterior(pos.getZ() - this.minBlockZ);
    }

    boolean containsQuart(int x, int z) {
        return insideChunk(x - this.minQuartX) && insideChunk(z - this.minQuartZ);
    }

    final int uniformCertificateCount() {
        return this.certificates.uniformCount();
    }

    final int certificateCount() {
        return this.certificates.count();
    }

    static boolean supports(LevelHeightAccessor heightAccessor) {
        int quartHeight = quartHeight(heightAccessor);
        return quartHeight > 0 && quartHeight <= MAX_QUART_HEIGHT;
    }

    @SuppressWarnings("unchecked")
    private Holder<Biome>[] flatten(FlatBiomeReader reader) {
        Holder<Biome>[] biomes = (Holder<Biome>[]) new Holder<?>[
                QUARTS_PER_CHUNK * QUARTS_PER_CHUNK * this.quartHeight
        ];
        for (int x = 0; x < QUARTS_PER_CHUNK; x++) {
            for (int z = 0; z < QUARTS_PER_CHUNK; z++) {
                for (int y = 0; y < this.quartHeight; y++) {
                    biomes[this.flatIndex(x, y, z)] = reader.get(
                            this.minQuartX + x,
                            this.minQuartY + y,
                            this.minQuartZ + z
                    );
                }
            }
        }
        return biomes;
    }

    private static int requireQuartHeight(LevelHeightAccessor heightAccessor) {
        int resolved = quartHeight(heightAccessor);
        if (resolved <= 0 || resolved > MAX_QUART_HEIGHT) {
            throw new IllegalArgumentException("Unsupported biome cache height: " + heightAccessor.getHeight());
        }
        return resolved;
    }

    private static int quartHeight(LevelHeightAccessor heightAccessor) {
        int blockHeight = heightAccessor.getHeight();
        if (blockHeight <= 0) {
            return 0;
        }
        long minBlock = heightAccessor.getMinBuildHeight();
        long maxBlock = minBlock + blockHeight - 1L;
        long minQuart = Math.floorDiv(minBlock, BLOCKS_PER_QUART);
        long maxQuart = Math.floorDiv(maxBlock, BLOCKS_PER_QUART);
        long span = maxQuart - minQuart + 1L;
        return span > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) span;
    }

    private int flatIndex(int x, int y, int z) {
        return ((x * QUARTS_PER_CHUNK + z) * this.quartHeight) + y;
    }

    private static boolean insideChunk(int coordinate) {
        return coordinate >= 0 && coordinate < QUARTS_PER_CHUNK;
    }

    private static boolean insideInterior(int coordinate) {
        return coordinate >= MIN_INTERIOR_BLOCK && coordinate <= MAX_INTERIOR_BLOCK;
    }

    @FunctionalInterface
    interface FlatBiomeReader {
        Holder<Biome> get(int x, int y, int z);
    }

    record LookupInput(
            BiomeManager.NoiseBiomeSource delegate,
            ChunkPos center,
            LevelHeightAccessor heightAccessor,
            FlatBiomeReader reader
    ) {
    }
}

package com.moepus.byepregen.worldgen;

import com.moepus.byepregen.jfr.ByepregenJfrEvents;
import com.moepus.byepregen.mixin.accessor.surface.BiomeManagerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class SurfaceBiomeManager extends BiomeManager {
    private static final boolean PROFILE = Boolean.getBoolean("byepregen.surfaceBiomeCacheProfile");
    private static final int QUARTS_PER_CHUNK = 4;
    private static final int INTERIOR_QUART_CUBES = QUARTS_PER_CHUNK - 1;
    private static final int MIN_INTERIOR_BLOCK = 2;
    private static final int MAX_INTERIOR_BLOCK = 13;
    private static final int BLOCKS_PER_QUART = 4;
    private static final int MAX_QUART_HEIGHT = 1024;
    private static final short NON_UNIFORM = -1;

    private final SurfaceBiomeLookup lookup;

    private SurfaceBiomeManager(long biomeZoomSeed, SurfaceBiomeLookup lookup) {
        super(lookup, biomeZoomSeed);
        this.lookup = lookup;
    }

    public static SurfaceBiomeManager fromSource(SourceOptions options) {
        return new SurfaceBiomeManager(
                options.biomeZoomSeed(),
                SurfaceBiomeLookup.fromSource(options.source(), options.center(), options.heightAccessor())
        );
    }

    public static BiomeManager wrapForSurface(BiomeManager biomeManager, ChunkAccess chunk) {
        if (biomeManager instanceof SurfaceBiomeManager
                || biomeManager instanceof ProfiledSurfaceBiomeManager) {
            return biomeManager;
        }

        BiomeManagerAccessor accessor = (BiomeManagerAccessor) (Object) biomeManager;
        BiomeManager.NoiseBiomeSource source = accessor.byepregen$getNoiseBiomeSource();
        if (!(source instanceof WorldGenRegion region)) {
            return biomeManager;
        }

        if (!region.getCenter().equals(chunk.getPos()) || !supports(chunk)) {
            return biomeManager;
        }

        long biomeZoomSeed = accessor.byepregen$getBiomeZoomSeed();
        if (PROFILE) {
            ProfiledSurfaceBiomeLookup lookup = ProfiledSurfaceBiomeLookup.fromChunk(source, chunk);
            return new ProfiledSurfaceBiomeManager(biomeZoomSeed, lookup);
        }
        return new SurfaceBiomeManager(biomeZoomSeed, SurfaceBiomeLookup.fromChunk(source, chunk));
    }

    @Override
    public Holder<Biome> getBiome(BlockPos pos) {
        Holder<Biome> uniform = this.lookup.uniformBiome(pos);
        return uniform != null ? uniform : super.getBiome(pos);
    }

    public static boolean profilingEnabled() {
        return PROFILE;
    }

    public static void commitProfile(BiomeManager biomeManager) {
        if (biomeManager instanceof ProfiledSurfaceBiomeManager profiled) {
            profiled.commitProfile();
        }
    }

    public int uniformCertificateCount() {
        return this.lookup.uniformCertificateCount();
    }

    public int certificateCount() {
        return this.lookup.certificateCount();
    }

    private static boolean supports(LevelHeightAccessor heightAccessor) {
        int quartHeight = quartHeight(heightAccessor);
        return quartHeight > 0 && quartHeight <= MAX_QUART_HEIGHT;
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

    private static class SurfaceBiomeLookup implements BiomeManager.NoiseBiomeSource {
        private final BiomeManager.NoiseBiomeSource delegate;
        protected final int minQuartX;
        protected final int minQuartZ;
        private final int minBlockX;
        private final int minBlockZ;
        private final int minQuartY;
        private final int quartHeight;
        private final Holder<Biome>[] flatBiomes;
        private final short[] uniformCertificates;
        private final int uniformCertificateCount;

        private SurfaceBiomeLookup(LookupInput input) {
            ChunkPos center = input.center();
            this.delegate = input.delegate();
            this.minBlockX = center.getMinBlockX();
            this.minBlockZ = center.getMinBlockZ();
            this.minQuartX = QuartPos.fromSection(center.x);
            this.minQuartY = QuartPos.fromBlock(input.heightAccessor().getMinBuildHeight());
            this.minQuartZ = QuartPos.fromSection(center.z);
            this.quartHeight = requireQuartHeight(input.heightAccessor());
            this.flatBiomes = this.flatten(input.reader());
            this.uniformCertificates = this.buildUniformCertificates();
            this.uniformCertificateCount = countUniform(this.uniformCertificates);
        }

        private static SurfaceBiomeLookup fromChunk(BiomeManager.NoiseBiomeSource delegate, ChunkAccess chunk) {
            LevelChunkSection[] sections = chunk.getSections();
            FlatBiomeReader reader = (x, y, z) -> {
                int sectionIndex = chunk.getSectionIndex(QuartPos.toBlock(y));
                return sections[sectionIndex].getNoiseBiome(x & 3, y & 3, z & 3);
            };
            return new SurfaceBiomeLookup(new LookupInput(delegate, chunk.getPos(), chunk, reader));
        }

        private static SurfaceBiomeLookup fromSource(
                BiomeManager.NoiseBiomeSource source,
                ChunkPos center,
                LevelHeightAccessor heightAccessor
        ) {
            return new SurfaceBiomeLookup(new LookupInput(source, center, heightAccessor, source::getNoiseBiome));
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

        protected final Holder<Biome> uniformBiome(BlockPos pos) {
            int localBlockX = pos.getX() - this.minBlockX;
            int localBlockZ = pos.getZ() - this.minBlockZ;
            if (!insideInterior(localBlockX) || !insideInterior(localBlockZ)) {
                return null;
            }

            int cubeX = (localBlockX - MIN_INTERIOR_BLOCK) >> 2;
            int cubeZ = (localBlockZ - MIN_INTERIOR_BLOCK) >> 2;
            int baseQuartY = (pos.getY() - MIN_INTERIOR_BLOCK) >> 2;
            int cubeY = Math.clamp(baseQuartY - this.minQuartY + 1, 0, this.quartHeight);
            int certificate = this.uniformCertificates[this.certificateIndex(cubeX, cubeY, cubeZ)];
            return certificate == NON_UNIFORM ? null : this.flatBiomes[certificate];
        }

        protected final boolean isInterior(BlockPos pos) {
            return insideInterior(pos.getX() - this.minBlockX)
                    && insideInterior(pos.getZ() - this.minBlockZ);
        }

        protected boolean containsQuart(int x, int z) {
            return insideChunk(x - this.minQuartX) && insideChunk(z - this.minQuartZ);
        }

        private short[] buildUniformCertificates() {
            int certificateHeight = this.quartHeight + 1;
            short[] certificates = new short[
                    INTERIOR_QUART_CUBES * INTERIOR_QUART_CUBES * certificateHeight
            ];
            for (int x = 0; x < INTERIOR_QUART_CUBES; x++) {
                for (int z = 0; z < INTERIOR_QUART_CUBES; z++) {
                    this.buildCertificateColumn(certificates, x, z);
                }
            }
            return certificates;
        }

        private void buildCertificateColumn(short[] certificates, int x, int z) {
            for (int cubeY = 0; cubeY <= this.quartHeight; cubeY++) {
                int lowerY = Math.clamp(cubeY - 1, 0, this.quartHeight - 1);
                int firstIndex = this.flatIndex(x, lowerY, z);
                Holder<Biome> first = this.flatBiomes[firstIndex];
                int certificateIndex = this.certificateIndex(x, cubeY, z);
                int horizontalIndex = x * INTERIOR_QUART_CUBES + z;
                certificates[certificateIndex] = this.allCornersMatch(first, horizontalIndex, cubeY)
                        ? (short) firstIndex
                        : NON_UNIFORM;
            }
        }

        private boolean allCornersMatch(Holder<Biome> first, int horizontalIndex, int cubeY) {
            int x = horizontalIndex / INTERIOR_QUART_CUBES;
            int z = horizontalIndex % INTERIOR_QUART_CUBES;
            int lowerY = Math.clamp(cubeY - 1, 0, this.quartHeight - 1);
            int upperY = Math.clamp(cubeY, 0, this.quartHeight - 1);
            for (int cornerX = x; cornerX <= x + 1; cornerX++) {
                for (int cornerZ = z; cornerZ <= z + 1; cornerZ++) {
                    if (first != this.biomeAt(cornerX, lowerY, cornerZ)
                            || first != this.biomeAt(cornerX, upperY, cornerZ)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private Holder<Biome> biomeAt(int x, int y, int z) {
            return this.flatBiomes[this.flatIndex(x, y, z)];
        }

        protected final int uniformCertificateCount() {
            return this.uniformCertificateCount;
        }

        protected final int certificateCount() {
            return this.uniformCertificates.length;
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

        private static int countUniform(short[] certificates) {
            int count = 0;
            for (short certificate : certificates) {
                if (certificate != NON_UNIFORM) {
                    count++;
                }
            }
            return count;
        }

        private int flatIndex(int x, int y, int z) {
            return ((x * QUARTS_PER_CHUNK + z) * this.quartHeight) + y;
        }

        private int certificateIndex(int x, int y, int z) {
            return ((x * INTERIOR_QUART_CUBES + z) * (this.quartHeight + 1)) + y;
        }

        private static boolean insideChunk(int coordinate) {
            return coordinate >= 0 && coordinate < QUARTS_PER_CHUNK;
        }

        private static boolean insideInterior(int coordinate) {
            return coordinate >= MIN_INTERIOR_BLOCK && coordinate <= MAX_INTERIOR_BLOCK;
        }
    }

    private static final class ProfiledSurfaceBiomeManager extends BiomeManager {
        private final ProfiledSurfaceBiomeLookup profiledLookup;
        private long queries;
        private long interiorQueries;
        private long uniformHits;

        private ProfiledSurfaceBiomeManager(long biomeZoomSeed, ProfiledSurfaceBiomeLookup lookup) {
            super(lookup, biomeZoomSeed);
            this.profiledLookup = lookup;
        }

        @Override
        public Holder<Biome> getBiome(BlockPos pos) {
            Holder<Biome> uniform = this.profiledLookup.uniformBiome(pos);
            this.queries++;
            if (this.profiledLookup.isInterior(pos)) {
                this.interiorQueries++;
            }
            if (uniform != null) {
                this.uniformHits++;
                return uniform;
            }
            return super.getBiome(pos);
        }

        private void commitProfile() {
            ByepregenJfrEvents.commitSurfaceBiomeProfile(new ByepregenJfrEvents.SurfaceBiomeProfile(
                    this.profiledLookup.chunkX,
                    this.profiledLookup.chunkZ,
                    this.profiledLookup.certificateCount(),
                    this.profiledLookup.uniformCertificateCount(),
                    this.queries,
                    this.interiorQueries,
                    this.uniformHits,
                    this.profiledLookup.flatSlowLookups,
                    this.profiledLookup.delegateLookups
            ));
        }
    }

    private static final class ProfiledSurfaceBiomeLookup extends SurfaceBiomeLookup {
        private final int chunkX;
        private final int chunkZ;
        private long flatSlowLookups;
        private long delegateLookups;

        private ProfiledSurfaceBiomeLookup(
                BiomeManager.NoiseBiomeSource delegate,
                ChunkAccess chunk,
                FlatBiomeReader reader
        ) {
            super(new LookupInput(delegate, chunk.getPos(), chunk, reader));
            this.chunkX = chunk.getPos().x;
            this.chunkZ = chunk.getPos().z;
        }

        private static ProfiledSurfaceBiomeLookup fromChunk(
                BiomeManager.NoiseBiomeSource delegate,
                ChunkAccess chunk
        ) {
            LevelChunkSection[] sections = chunk.getSections();
            FlatBiomeReader reader = (x, y, z) -> {
                int sectionIndex = chunk.getSectionIndex(QuartPos.toBlock(y));
                return sections[sectionIndex].getNoiseBiome(x & 3, y & 3, z & 3);
            };
            return new ProfiledSurfaceBiomeLookup(delegate, chunk, reader);
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            if (this.containsQuart(x, z)) {
                this.flatSlowLookups++;
            } else {
                this.delegateLookups++;
            }
            return super.getNoiseBiome(x, y, z);
        }
    }

    @FunctionalInterface
    private interface FlatBiomeReader {
        Holder<Biome> get(int x, int y, int z);
    }

    public record SourceOptions(
            BiomeManager.NoiseBiomeSource source,
            long biomeZoomSeed,
            ChunkPos center,
            LevelHeightAccessor heightAccessor
    ) {
    }

    private record LookupInput(
            BiomeManager.NoiseBiomeSource delegate,
            ChunkPos center,
            LevelHeightAccessor heightAccessor,
            FlatBiomeReader reader
    ) {
    }
}

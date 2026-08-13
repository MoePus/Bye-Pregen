package com.moepus.byepregen.worldgen.biome;

import com.moepus.byepregen.jfr.ByepregenJfrEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;

final class ProfiledSurfaceBiomeManager extends BiomeManager {
    private final ProfiledSurfaceBiomeLookup profiledLookup;
    private long queries;
    private long interiorQueries;
    private long uniformHits;

    private ProfiledSurfaceBiomeManager(long biomeZoomSeed, ProfiledSurfaceBiomeLookup lookup) {
        super(lookup, biomeZoomSeed);
        this.profiledLookup = lookup;
    }

    static ProfiledSurfaceBiomeManager fromChunk(
            long biomeZoomSeed,
            BiomeManager.NoiseBiomeSource delegate,
            ChunkAccess chunk
    ) {
        return new ProfiledSurfaceBiomeManager(
                biomeZoomSeed,
                new ProfiledSurfaceBiomeLookup(delegate, chunk)
        );
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

    void commitProfile() {
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

    private static final class ProfiledSurfaceBiomeLookup extends SurfaceBiomeLookup {
        private final int chunkX;
        private final int chunkZ;
        private long flatSlowLookups;
        private long delegateLookups;

        private ProfiledSurfaceBiomeLookup(BiomeManager.NoiseBiomeSource delegate, ChunkAccess chunk) {
            super(inputFromChunk(delegate, chunk));
            this.chunkX = chunk.getPos().x;
            this.chunkZ = chunk.getPos().z;
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
}

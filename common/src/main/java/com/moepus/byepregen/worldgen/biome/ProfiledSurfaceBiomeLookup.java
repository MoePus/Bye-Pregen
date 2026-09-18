package com.moepus.byepregen.worldgen.biome;

import com.moepus.byepregen.jfr.ByepregenJfrEvents;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Counts what the surface biome cache served, for runs started with
 * {@code -Dbyepregen.surfaceBiomeCacheProfile=true}. The manager asks the lookup once per query, so the
 * counting lives here rather than on a second BiomeManager, which would have to repeat the cache's own
 * fast-path decision and could silently diverge from it.
 */
final class ProfiledSurfaceBiomeLookup extends SurfaceBiomeLookup {
    private final int chunkX;
    private final int chunkZ;
    private long queries;
    private long interiorQueries;
    private long uniformHits;
    private long flatSlowLookups;
    private long delegateLookups;

    ProfiledSurfaceBiomeLookup(BiomeResolver delegate, ChunkAccess chunk) {
        super(inputFromChunk(delegate, chunk));
        this.chunkX = chunk.getPos().x();
        this.chunkZ = chunk.getPos().z();
    }

    @Override
    Holder<Biome> uniformBiome(int x, int y, int z) {
        this.queries++;
        if (this.isInterior(x, z)) {
            this.interiorQueries++;
        }
        Holder<Biome> uniform = super.uniformBiome(x, y, z);
        if (uniform != null) {
            this.uniformHits++;
        }
        return uniform;
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

    void commitProfile() {
        ByepregenJfrEvents.commitSurfaceBiomeProfile(new ByepregenJfrEvents.SurfaceBiomeProfile(
                this.chunkX,
                this.chunkZ,
                this.certificateCount(),
                this.uniformCertificateCount(),
                this.queries,
                this.interiorQueries,
                this.uniformHits,
                this.flatSlowLookups,
                this.delegateLookups
        ));
    }
}

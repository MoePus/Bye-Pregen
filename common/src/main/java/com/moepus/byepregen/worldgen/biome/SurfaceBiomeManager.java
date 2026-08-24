package com.moepus.byepregen.worldgen.biome;

import com.moepus.byepregen.mixin.accessor.surface.BiomeManagerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class SurfaceBiomeManager extends BiomeManager {
    private static final boolean PROFILE = Boolean.getBoolean("byepregen.surfaceBiomeCacheProfile");

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
        if (!region.getCenter().equals(chunk.getPos()) || !SurfaceBiomeLookup.supports(chunk)) {
            return biomeManager;
        }

        long biomeZoomSeed = accessor.byepregen$getBiomeZoomSeed();
        if (PROFILE) {
            return ProfiledSurfaceBiomeManager.fromChunk(biomeZoomSeed, source, chunk);
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

    public record SourceOptions(
            BiomeManager.NoiseBiomeSource source,
            long biomeZoomSeed,
            ChunkPos center,
            LevelHeightAccessor heightAccessor
    ) {
    }
}

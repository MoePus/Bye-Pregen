package com.moepus.byepregen.worldgen.biome;

import com.moepus.byepregen.mixin.accessor.surface.BiomeManagerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
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

    public static BiomeManager wrapForSurface(BiomeManager biomeManager, ChunkAccess chunk, WorldGenRegion region) {
        // The RC2 region binds a resolver method reference. Prove ownership at the
        // terrain entry instead of inspecting the resolver's implementation class.
        if (region == null || biomeManager.getClass() != BiomeManager.class
                || biomeManager != region.getBiomeManager()
                || !region.getCenter().equals(chunk.getPos()) || !SurfaceBiomeLookup.supports(chunk)) {
            return biomeManager;
        }
        BiomeManagerAccessor accessor = (BiomeManagerAccessor) (Object) biomeManager;
        BiomeResolver source = accessor.byepregen$getNoiseBiomeSource();

        long biomeZoomSeed = accessor.byepregen$getBiomeZoomSeed();
        if (PROFILE) {
            return new SurfaceBiomeManager(biomeZoomSeed, new ProfiledSurfaceBiomeLookup(source, chunk));
        }
        return new SurfaceBiomeManager(biomeZoomSeed, SurfaceBiomeLookup.fromChunk(source, chunk));
    }

    @Override
    public Holder<Biome> getBiome(BlockPos pos) {
        return this.getBiome(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public Holder<Biome> getBiome(int x, int y, int z) {
        Holder<Biome> uniform = this.lookup.uniformBiome(x, y, z);
        return uniform != null ? uniform : super.getBiome(x, y, z);
    }

    public static boolean profilingEnabled() {
        return PROFILE;
    }

    public static void commitProfile(BiomeManager biomeManager) {
        if (biomeManager instanceof SurfaceBiomeManager manager
                && manager.lookup instanceof ProfiledSurfaceBiomeLookup profiled) {
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
            BiomeResolver source,
            long biomeZoomSeed,
            ChunkPos center,
            LevelHeightAccessor heightAccessor
    ) {
    }
}

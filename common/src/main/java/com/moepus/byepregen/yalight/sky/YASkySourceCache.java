package com.moepus.byepregen.yalight.sky;

import com.moepus.byepregen.yalight.engine.YAChunkRunCache;
import com.moepus.byepregen.yalight.storage.YALightStorage;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

// Sky light reuses ONE inherited 9-chunk window for two roles that share the same center:
// sky-source lookups (sources()/sourceCode() -> getSkyLightSources()) and light value
// read/write (inherited getUpdatingLight/setUpdatingLight). A getUpdatingLight() on a far
// coordinate recenters the window and evicts the source-code slots, so callers must finish
// their per-chunk source prefetch (e.g. lowestByColumn) before driving propagation that
// can wander to other chunks. YASkyLightEngine already follows this order.
final class YASkySourceCache extends YAChunkRunCache {
    YAChunkSkyLightSources enabledSources(YALightStorage storage, int chunkX, int chunkZ) {
        LightChunk chunk = this.enabledChunk(storage, chunkX, chunkZ);
        return chunk == null ? null : (YAChunkSkyLightSources)chunk.getSkyLightSources();
    }

    YAChunkSkyLightSources sources(LightChunkGetter chunkGetter, int chunkX, int chunkZ) {
        LightChunk chunk = this.chunk(chunkGetter, chunkX, chunkZ);
        return chunk == null ? null : (YAChunkSkyLightSources)chunk.getSkyLightSources();
    }

    int enabledSourceCode(YALightStorage storage, int x, int z) {
        YAChunkSkyLightSources sources = this.enabledSources(
                storage, SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return sources == null ? Integer.MAX_VALUE : sources.sourceCode(x, z);
    }

    int sourceCode(LightChunkGetter chunkGetter, int x, int z) {
        YAChunkSkyLightSources sources = this.sources(chunkGetter, SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return sources == null ? YAChunkSkyLightSources.NO_SOURCE_CODE : sources.sourceCode(x, z);
    }

}

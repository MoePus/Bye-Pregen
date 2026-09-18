package com.moepus.byepregen.worldgentest;

import com.moepus.byepregen.worldgen.biome.SurfaceBiomeManager;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class SurfaceRuntimeProbe {
    private static final LongAdder QUERIES = new LongAdder();
    private SurfaceRuntimeProbe() { }
    public static long queries() { return QUERIES.sum(); }

    public static void verify(BiomeManager original, BiomeManager cached, ChunkAccess chunk) {
        if (!(cached instanceof SurfaceBiomeManager)) return;
        var pos = new BlockPos.MutableBlockPos();
        for (int x : new int[]{0, 2, 13, 15}) {
            for (int z : new int[]{0, 2, 13, 15}) {
                for (int y = chunk.getMinY() - 8; y <= chunk.getMaxY() + 8; y += 7) {
                    pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                    if (original.getBiome(pos) != cached.getBiome(pos)) throw new AssertionError("Biome cache: " + pos);
                    QUERIES.increment();
                }
            }
        }
    }
}

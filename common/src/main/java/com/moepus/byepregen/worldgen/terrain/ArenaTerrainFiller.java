package com.moepus.byepregen.worldgen.terrain;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.palette.arena.Layout;
import com.moepus.byepregen.dfc.runtime.ColumnDensitySampler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

public final class ArenaTerrainFiller {
    private ArenaTerrainFiller() { }

    public static boolean fill(NoiseChunk noise, ChunkAccess chunk, NoiseGeneratorSettings settings,
                               DebugState debugState) {
        DensityVolume volume = noise.volume();
        if (!freshTargets(chunk, volume)) return false;
        var sampler = noise.cachingSamplers().get(settings.noiseRouter().finalDensity());
        if (sampler.sampler() instanceof ColumnDensitySampler columns) {
            try (var session = columns.openColumns(sampler.context(), volume)) {
                DensityBuffer column = DensityBuffer.createUnpooled(volume.sizeY());
                var writer = new ArenaTerrainWriter(chunk, volume, settings.defaultBlock(), noise.aquifer(), debugState);
                for (int z = 0; z < volume.sizeZ(); ++z) {
                    for (int x = 0; x < volume.sizeX(); ++x) {
                        session.evalColumn(x, z, column.values);
                        writer.writeColumn(column, x, z);
                    }
                }
                writer.finish();
            }
            return true;
        }
        try (ScopedDensityBuffer density = sampler.sampleVolume(volume)) {
            var writer = new ArenaTerrainWriter(chunk, volume, settings.defaultBlock(), noise.aquifer(), debugState);
            writer.write(density);
        }
        return true;
    }

    static boolean freshTargets(ChunkAccess chunk, DensityVolume volume) {
        if (!supportedVolume(chunk, volume)) return false;
        // Metadata is initialized from the fill alone. Retrogen or other prefilled
        // sections need the native incremental path, including sections outside the volume.
        for (var section : chunk.getSections()) {
            if (!(section.getStates() instanceof ArenaBlockStatePalettedContainer arena)
                    || !arena.isFreshAirForWorldgen()) return false;
        }
        return true;
    }

    private static boolean supportedVolume(ChunkAccess chunk, DensityVolume volume) {
        return volume.stepBlockX() == 1 && volume.stepBlockY() == 1 && volume.stepBlockZ() == 1
                && volume.sizeY() > 0 && volume.minBlockY() >= chunk.getMinY() && volume.maxBlockY() <= chunk.getMaxY()
                && fullChunkColumns(chunk, volume);
    }

    private static boolean fullChunkColumns(ChunkAccess chunk, DensityVolume volume) {
        return volume.minBlockX() == chunk.getPos().getMinBlockX() && volume.sizeX() == Layout.SECTION_WIDTH
                && volume.minBlockZ() == chunk.getPos().getMinBlockZ() && volume.sizeZ() == Layout.SECTION_WIDTH;
    }

    @FunctionalInterface
    public interface DebugState {
        BlockState apply(int x, int y, int z, BlockState state);
    }
}

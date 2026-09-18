package com.moepus.byepregen.worldgen.terrain;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Classification is local to one fill, so heightmap tags follow the current registries. */
record TerrainBlock(int rawId, long counts, int heightmaps, boolean hasFluid) {
    static final int SURFACE = 1;
    static final int FLOOR = 2;
    static final int ALL_HEIGHTMAPS = SURFACE | FLOOR;
    static final int FLUID_SHIFT = Short.SIZE;
    static final int TICKING_BLOCK_SHIFT = 2 * Short.SIZE;
    static final int TICKING_FLUID_SHIFT = 3 * Short.SIZE;

    static TerrainBlock of(BlockState state) {
        var fluid = state.getFluidState();
        long counts = 0;
        if (!state.isAir()) {
            counts = 1;
            if (state.isRandomlyTicking()) counts |= 1L << TICKING_BLOCK_SHIFT;
            if (!fluid.isEmpty()) {
                counts |= 1L << FLUID_SHIFT;
                if (fluid.isRandomlyTicking()) counts |= 1L << TICKING_FLUID_SHIFT;
            }
        }
        int heightmaps = Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state) ? SURFACE : 0;
        if (Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)) heightmaps |= FLOOR;
        return new TerrainBlock(ArenaBlockStatePalettedContainer.rawId(state), counts, heightmaps, !fluid.isEmpty());
    }
}

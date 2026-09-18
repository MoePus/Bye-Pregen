package com.moepus.byepregen.worldgen.terrain;

import com.moepus.byepregen.palette.arena.ArenaFreshSectionWriter;
import com.moepus.byepregen.palette.arena.Layout;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** One fill under the native terrain locks; all target sections start as uniform air. */
final class ArenaTerrainWriter {
    private final ChunkAccess chunk;
    private final DensityVolume volume;
    private final BlockState defaultBlock;
    private final TerrainBlock defaultInfo;
    private final Aquifer aquifer;
    private final ArenaTerrainFiller.DebugState debugState;
    private final TerrainSection[] sections;
    private final Page[] pages;
    private final Heightmap surface;
    private final Heightmap floor;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final Map<BlockState, TerrainBlock> blockInfo = new IdentityHashMap<>();
    private BlockState lastBlock;
    private TerrainBlock lastInfo;
    private int missingHeightmaps;

    ArenaTerrainWriter(ChunkAccess chunk, DensityVolume volume, BlockState defaultBlock, Aquifer aquifer,
                       ArenaTerrainFiller.DebugState debugState) {
        this.chunk = chunk;
        this.volume = volume;
        this.defaultBlock = defaultBlock;
        this.defaultInfo = TerrainBlock.of(defaultBlock);
        this.aquifer = aquifer;
        this.debugState = debugState;
        this.sections = new TerrainSection[chunk.getSectionsCount()];
        this.pages = this.bindPages();
        this.surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        this.floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Arrays.fill(this.surface.getRawData(), 0L);
        Arrays.fill(this.floor.getRawData(), 0L);
    }

    void write(DensityBuffer density) {
        if (density.size() != this.volume.size()) throw new IllegalArgumentException("Density volume size mismatch");
        for (int z = 0; z < this.volume.sizeZ(); z++) {
            for (int x = 0; x < this.volume.sizeX(); x++) {
                this.writeColumn(density, x, z, this.volume.indexUnchecked(x, 0, z));
            }
        }
        this.finish();
    }

    void finish() {
        for (TerrainSection section : this.sections) {
            if (section != null) section.finish();
        }
    }

    private Page[] bindPages() {
        int first = Math.floorDiv(this.volume.minBlockY(), Layout.PAGE_HEIGHT);
        int last = Math.floorDiv(this.volume.maxBlockY(), Layout.PAGE_HEIGHT);
        Page[] pages = new Page[last - first + 1];
        for (int index = 0; index < pages.length; index++) {
            int baseY = (first + index) * Layout.PAGE_HEIGHT;
            int sectionIndex = this.chunk.getSectionIndex(baseY);
            var section = this.chunk.getSection(sectionIndex);
            if (this.sections[sectionIndex] == null) {
                this.sections[sectionIndex] = new TerrainSection(section, this.defaultInfo.rawId());
            }
            int page = Math.floorMod(first + index, Layout.PAGE_COUNT);
            pages[index] = new Page(this.sections[sectionIndex], this.sections[sectionIndex].page(page),
                    Math.max(baseY, this.volume.minBlockY()),
                    Math.min(baseY + Layout.PAGE_HEIGHT - 1, this.volume.maxBlockY()));
        }
        return pages;
    }

    void writeColumn(DensityBuffer density, int x, int z) {
        if (density.size() != this.volume.sizeY()) throw new IllegalArgumentException("Density column size mismatch");
        this.writeColumn(density, x, z, 0);
    }

    private void writeColumn(DensityBuffer density, int x, int z, int columnOffset) {
        this.missingHeightmaps = TerrainBlock.ALL_HEIGHTMAPS;
        int blockX = this.volume.blockX(x), blockZ = this.volume.blockZ(z);
        for (int index = this.pages.length - 1; index >= 0; index--) {
            this.writePage(density, this.pages[index], x, z, blockX, blockZ, columnOffset);
        }
    }

    private void writePage(DensityBuffer density, Page page, int x, int z, int blockX, int blockZ, int columnOffset) {
        int densityIndex = columnOffset + page.maxY() - this.volume.minBlockY();
        for (int y = page.maxY(); y >= page.minY(); y--) {
            float value = density.get(densityIndex--);
            BlockState state = this.aquifer.computeSubstance(blockX, y, blockZ, (double) value);
            state = this.debugState.apply(blockX, y, blockZ, state == null ? this.defaultBlock : state);
            if (state == Blocks.AIR.defaultBlockState()) continue;
            TerrainBlock info = this.info(state);
            int local = Layout.localIndex(x, y & (Layout.PAGE_HEIGHT - 1), z);
            page.writer().write(local, info.rawId());
            page.section().add(info);
            this.updateHeights(x, y, z, state, info);
            if (this.aquifer.shouldScheduleFluidUpdate() && info.hasFluid()) {
                this.chunk.markPosForPostProcessing(this.pos.set(blockX, y, blockZ));
            }
        }
    }

    private TerrainBlock info(BlockState state) {
        if (state == this.defaultBlock) return this.defaultInfo;
        if (state != this.lastBlock) {
            this.lastBlock = state;
            this.lastInfo = this.blockInfo.computeIfAbsent(state, TerrainBlock::of);
        }
        return this.lastInfo;
    }

    private void updateHeights(int x, int y, int z, BlockState state, TerrainBlock info) {
        int found = this.missingHeightmaps & info.heightmaps();
        if ((found & TerrainBlock.SURFACE) != 0) this.surface.update(x, y, z, state);
        if ((found & TerrainBlock.FLOOR) != 0) this.floor.update(x, y, z, state);
        this.missingHeightmaps &= ~found;
    }

    private record Page(TerrainSection section, ArenaFreshSectionWriter.Page writer, int minY, int maxY) { }
}

package com.moepus.byepregen;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStateNbtReader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class FastBlockStateNbtReader {
    private static final String PALETTE = "palette";
    private static final String DATA = "data";

    private FastBlockStateNbtReader() {
    }

    public static FastBlockStatePalettedContainer read(CompoundTag blockStatesTag) {
        int[] paletteRawIds = ArenaBlockStateNbtReader.readPaletteRawIds(
                blockStatesTag.getList(PALETTE, Tag.TAG_COMPOUND));
        if (paletteRawIds == null) {
            return null;
        }

        long[] packedStorage = blockStatesTag.contains(DATA, Tag.TAG_LONG_ARRAY)
                ? blockStatesTag.getLongArray(DATA)
                : null;
        FastBlockStatePalettedContainer container = new FastBlockStatePalettedContainer(
                Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES
        );
        return container.importVanillaPackedRawIds(paletteRawIds, packedStorage) ? container : null;
    }
}

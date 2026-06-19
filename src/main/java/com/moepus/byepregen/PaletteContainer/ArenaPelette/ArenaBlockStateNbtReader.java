package com.moepus.byepregen;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class ArenaBlockStateNbtReader {
    private static final String PALETTE = "palette";
    private static final String DATA = "data";
    private static final String NAME = "Name";
    private static final String PROPERTIES = "Properties";

    private ArenaBlockStateNbtReader() {
    }

    public static ArenaBlockStatePalettedContainer read(CompoundTag blockStatesTag) {
        int[] paletteRawIds = readPaletteRawIds(blockStatesTag.getList(PALETTE, Tag.TAG_COMPOUND));
        if (paletteRawIds == null) {
            return null;
        }

        long[] packedStorage = blockStatesTag.contains(DATA, Tag.TAG_LONG_ARRAY)
                ? blockStatesTag.getLongArray(DATA)
                : null;
        ArenaBlockStatePalettedContainer container = new ArenaBlockStatePalettedContainer();
        return container.importVanillaPackedRawIds(paletteRawIds, packedStorage) ? container : null;
    }

    static int[] readPaletteRawIds(ListTag paletteTag) {
        if (paletteTag.isEmpty()) {
            return null;
        }

        int[] rawIds = new int[paletteTag.size()];
        for (int i = 0; i < rawIds.length; ++i) {
            BlockState state = readState(paletteTag.getCompound(i));
            if (state == null) {
                return null;
            }

            int rawId = Block.BLOCK_STATE_REGISTRY.getId(state);
            if (rawId < 0) {
                return null;
            }
            rawIds[i] = rawId;
        }
        return rawIds;
    }

    private static BlockState readState(CompoundTag stateTag) {
        Block block = readBlock(stateTag.getString(NAME));
        if (block == null) {
            return null;
        }

        BlockState state = block.defaultBlockState();
        if (!stateTag.contains(PROPERTIES, Tag.TAG_COMPOUND)) {
            return state;
        }

        CompoundTag properties = stateTag.getCompound(PROPERTIES);
        for (String key : properties.getAllKeys()) {
            state = setProperty(state, key, properties.getString(key));
            if (state == null) {
                return null;
            }
        }
        return state;
    }

    private static Block readBlock(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            return null;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        return block.orElse(null);
    }

    private static BlockState setProperty(BlockState state, String key, String valueName) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
        if (property == null) {
            return null;
        }
        return setProperty(state, property, valueName);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setProperty(BlockState state, Property property, String valueName) {
        Optional<? extends Comparable> value = property.getValue(valueName);
        if (value.isEmpty()) {
            return null;
        }
        return state.setValue(property, value.get());
    }
}

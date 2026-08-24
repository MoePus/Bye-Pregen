package com.moepus.byepregen.palette.arena.codec;

import java.util.HashMap;
import java.util.Optional;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class NbtReader {
    private static final String PALETTE = "palette";
    private static final String DATA = "data";
    private static final String NAME = "Name";
    private static final String PROPERTIES = "Properties";

    private NbtReader() {
    }

    public static ArenaBlockStatePalettedContainer read(CompoundTag blockStatesTag) {
        int[] paletteRawIds = readPaletteRawIds(blockStatesTag.getListOrEmpty(PALETTE));
        if (paletteRawIds == null) {
            return null;
        }

        long[] packedStorage = blockStatesTag.getLongArray(DATA).orElse(null);
        ArenaBlockStatePalettedContainer container = new ArenaBlockStatePalettedContainer();
        return container.importVanillaPackedRawIds(paletteRawIds, packedStorage) ? container : null;
    }

    private static int[] readPaletteRawIds(ListTag paletteTag) {
        if (paletteTag.isEmpty()) {
            return null;
        }
        return readPaletteRawIds(paletteTag, new PaletteReadCache());
    }

    private static int[] readPaletteRawIds(ListTag paletteTag, PaletteReadCache cache) {
        int[] rawIds = new int[paletteTag.size()];
        for (int i = 0; i < rawIds.length; ++i) {
            BlockState state = paletteTag.getCompound(i).map(tag -> readState(tag, cache)).orElse(null);
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

    private static BlockState readState(CompoundTag stateTag, PaletteReadCache cache) {
        Block block = cache.readBlock(stateTag.getStringOr(NAME, ""));
        if (block == null) {
            return null;
        }

        BlockState state = block.defaultBlockState();
        CompoundTag properties = stateTag.getCompound(PROPERTIES).orElse(null);
        if (properties == null) {
            return state;
        }

        for (String key : properties.keySet()) {
            state = setProperty(state, key, properties.getStringOr(key, ""), cache);
            if (state == null) {
                return null;
            }
        }
        return state;
    }

    private static BlockState setProperty(
            BlockState state, String key, String valueName, PaletteReadCache cache) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
        if (property == null) {
            return null;
        }
        return setProperty(state, property, valueName, cache);
    }

    private static BlockState setProperty(
            BlockState state, Property<?> property, String valueName, PaletteReadCache cache) {
        Comparable<?> value = cache.readPropertyValue(property, valueName);
        if (value == null) {
            return null;
        }
        return setPropertyValue(state, property, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setPropertyValue(BlockState state, Property property, Comparable value) {
        return state.setValue(property, value);
    }

    private static final class PaletteReadCache {
        private HashMap<String, Block> blocksByName;
        private HashMap<Property<?>, HashMap<String, Comparable<?>>> propertyValues;

        Block readBlock(String name) {
            HashMap<String, Block> blocks = this.blocksByName;
            if (blocks != null) {
                Block cached = blocks.get(name);
                if (cached != null) {
                    return cached;
                }
            }

            Identifier id = Identifier.tryParse(name);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                return null;
            }

            Block value = BuiltInRegistries.BLOCK.getValue(id);
            if (blocks == null) {
                blocks = new HashMap<>();
                this.blocksByName = blocks;
            }
            blocks.put(name, value);
            return value;
        }

        Comparable<?> readPropertyValue(Property<?> property, String valueName) {
            HashMap<Property<?>, HashMap<String, Comparable<?>>> propertyValues = this.propertyValues;
            if (propertyValues == null) {
                propertyValues = new HashMap<>();
                this.propertyValues = propertyValues;
            }

            HashMap<String, Comparable<?>> values = propertyValues.get(property);
            if (values == null) {
                values = new HashMap<>();
                propertyValues.put(property, values);
            }

            Comparable<?> cached = values.get(valueName);
            if (cached != null) {
                return cached;
            }

            Optional<? extends Comparable<?>> value = property.getValue(valueName);
            if (value.isEmpty()) {
                return null;
            }

            Comparable<?> resolved = value.get();
            values.put(valueName, resolved);
            return resolved;
        }
    }
}

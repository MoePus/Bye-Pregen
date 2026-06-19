package com.moepus.byepregen;

import com.moepus.byepregen.gcfree.NbtWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

final class BlockStateNbtCache {
    private static final int STATE_ENTRY_INITIAL_CAPACITY = 128;
    private static final byte[] NAME = NbtWriter.asciiName("Name");
    private static final byte[] PROPERTIES = NbtWriter.asciiName("Properties");
    private static final AtomicReferenceArray<byte[]> RAW_ID_ENTRIES =
            new AtomicReferenceArray<>(Block.BLOCK_STATE_REGISTRY.size());
    private static final ConcurrentHashMap<BlockState, byte[]> STATE_ENTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Block, byte[]> BLOCK_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Property<?>, byte[]> PROPERTY_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, byte[]> VALUE_NAMES = new ConcurrentHashMap<>();

    private BlockStateNbtCache() {}

    static void writeStateEntry(NbtWriter writer, BlockState state) {
        int rawId = Block.BLOCK_STATE_REGISTRY.getId(state);
        if (rawId >= 0) {
            writeRawIdEntry(writer, rawId);
            return;
        }
        writer.write(STATE_ENTRIES.computeIfAbsent(state, BlockStateNbtCache::createStateEntry));
    }

    static void writeRawIdEntry(NbtWriter writer, int rawId) {
        rawId = Math.max(rawId, 0);
        if (rawId >= RAW_ID_ENTRIES.length()) {
            writer.write(STATE_ENTRIES.computeIfAbsent(Block.stateById(rawId), BlockStateNbtCache::createStateEntry));
            return;
        }

        byte[] entry = RAW_ID_ENTRIES.get(rawId);
        if (entry == null) {
            entry = createStateEntry(Block.stateById(rawId));
            if (!RAW_ID_ENTRIES.compareAndSet(rawId, null, entry)) {
                entry = RAW_ID_ENTRIES.get(rawId);
            }
        }
        writer.write(entry);
    }

    private static byte[] createStateEntry(BlockState state) {
        NbtWriter writer = new NbtWriter(STATE_ENTRY_INITIAL_CAPACITY);
        try {
            writeStateEntryUncached(writer, state);
            return writer.toByteArray();
        } finally {
            writer.release();
        }
    }

    private static void writeStateEntryUncached(NbtWriter writer, BlockState state) {
        writer.putString(NAME, blockName(state.getBlock()));
        if (state.getValues().isEmpty()) {
            return;
        }

        writer.startCompound(PROPERTIES);
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            writer.putString(propertyName(entry.getKey()), propertyValueName(entry.getKey(), entry.getValue()));
        }
        writer.finishCompound();
    }

    private static byte[] blockName(Block block) {
        return BLOCK_NAMES.computeIfAbsent(block, key -> NbtWriter.asciiName(BuiltInRegistries.BLOCK.getKey(key).toString()));
    }

    private static byte[] propertyName(Property<?> property) {
        return PROPERTY_NAMES.computeIfAbsent(property, key -> NbtWriter.asciiName(key.getName()));
    }

    private static <T extends Comparable<T>> byte[] propertyValueName(Property<?> property, Comparable<?> value) {
        Property<T> typedProperty = castProperty(property);
        String name = typedProperty.getName(typedProperty.getValueClass().cast(value));
        return VALUE_NAMES.computeIfAbsent(name, NbtWriter::stringBytes);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> Property<T> castProperty(Property<?> property) {
        return (Property<T>) property;
    }
}

package com.moepus.byepregen.serialization.nbt;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class BlockStateNbtCache {
    private static final int STATE_ENTRY_INITIAL_CAPACITY = 128;
    private static final byte[] NAME = NbtWriter.asciiName("id");
    private static final byte[] PROPERTIES = NbtWriter.asciiName("properties");
    private static final byte[] WRAPPER_NAME = NbtWriter.asciiName("");
    private static final AtomicReferenceArray<byte[]> RAW_ID_ENTRIES =
            new AtomicReferenceArray<>(Block.BLOCK_STATE_REGISTRY.size());
    private static final AtomicReferenceArray<byte[]> RAW_ID_FILE_ENTRIES =
            new AtomicReferenceArray<>(Block.BLOCK_STATE_REGISTRY.size());
    private static final AtomicReferenceArray<byte[]> RAW_ID_WRAPPED_FILE_ENTRIES =
            new AtomicReferenceArray<>(Block.BLOCK_STATE_REGISTRY.size());
    private static final ConcurrentHashMap<BlockState, byte[]> STATE_ENTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Block, byte[]> BLOCK_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Property<?>, byte[]> PROPERTY_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, byte[]> VALUE_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Block, byte[]> SHORT_ENTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Block, byte[]> WRAPPED_SHORT_ENTRIES = new ConcurrentHashMap<>();

    private BlockStateNbtCache() {}

    /** The full {@code {id, properties}} body, which only the canonical file forms fall back to. */
    private static byte[] stateEntryBytes(BlockState state) {
        int rawId = Block.BLOCK_STATE_REGISTRY.getId(state);
        if (rawId >= 0) {
            return rawIdEntryBytes(rawId);
        }
        return STATE_ENTRIES.computeIfAbsent(state, BlockStateNbtCache::createStateEntry);
    }

    private static byte[] rawIdEntryBytes(int rawId) {
        rawId = Math.max(rawId, 0);
        if (rawId >= RAW_ID_ENTRIES.length()) {
            return STATE_ENTRIES.computeIfAbsent(Block.stateById(rawId), BlockStateNbtCache::createStateEntry);
        }

        byte[] entry = RAW_ID_ENTRIES.get(rawId);
        if (entry == null) {
            entry = createStateEntry(Block.stateById(rawId));
            if (!RAW_ID_ENTRIES.compareAndSet(rawId, null, entry)) {
                entry = RAW_ID_ENTRIES.get(rawId);
            }
        }
        return entry;
    }

    /**
     * Whether the block-state codec writes this state as a bare block name.
     *
     * <p>{@code BlockState.CODEC} dispatches on the block and falls back to the full compound, so the
     * encoder emits the short form exactly for the block's own default state; the decoder accepts
     * either form, but a serializer that wants to match vanilla NBT has to follow the encoder.
     */
    public static boolean stateUsesShortForm(BlockState state) {
        return state == state.getBlock().defaultBlockState();
    }

    public static boolean rawIdUsesShortForm(int rawId) {
        return stateUsesShortForm(Block.stateById(Math.max(rawId, 0)));
    }

    /** Palette element for chunk NBT: a bare block name, or the full {@code {id, properties}} body. */
    public static byte[] stateFileEntryBytes(BlockState state) {
        return stateUsesShortForm(state) ? shortEntry(state.getBlock()) : stateEntryBytes(state);
    }

    public static byte[] rawIdFileEntryBytes(int rawId) {
        return cached(RAW_ID_FILE_ENTRIES, rawId,
                state -> stateFileEntryBytes(state));
    }

    /**
     * The same element inside a list that has to declare one element type.
     *
     * <p>{@code ListTag} writes a mixed list with element type compound and wraps every other element as
     * {@code {"": value}}, so a shortened block state has to appear as {@code {"": "minecraft:stone"}}.
     */
    public static byte[] stateFileWrappedEntryBytes(BlockState state) {
        return stateUsesShortForm(state) ? wrappedShortEntry(state.getBlock()) : stateEntryBytes(state);
    }

    public static byte[] rawIdFileWrappedEntryBytes(int rawId) {
        return cached(RAW_ID_WRAPPED_FILE_ENTRIES, rawId,
                state -> stateFileWrappedEntryBytes(state));
    }

    private static byte[] cached(
            AtomicReferenceArray<byte[]> cache,
            int rawId,
            java.util.function.Function<BlockState, byte[]> factory
    ) {
        int id = Math.max(rawId, 0);
        if (id >= cache.length()) {
            return factory.apply(Block.stateById(id));
        }
        byte[] entry = cache.get(id);
        if (entry == null) {
            entry = factory.apply(Block.stateById(id));
            if (!cache.compareAndSet(id, null, entry)) {
                entry = cache.get(id);
            }
        }
        return entry;
    }

    private static byte[] shortEntry(Block block) {
        return SHORT_ENTRIES.computeIfAbsent(block,
                key -> NbtWriter.stringBytes(BuiltInRegistries.BLOCK.getKey(key).toString()));
    }

    private static byte[] wrappedShortEntry(Block block) {
        return WRAPPED_SHORT_ENTRIES.computeIfAbsent(block, key -> {
            NbtWriter writer = new NbtWriter(STATE_ENTRY_INITIAL_CAPACITY);
            try {
                writer.putString(WRAPPER_NAME, NbtWriter.stringBytes(BuiltInRegistries.BLOCK.getKey(key).toString()));
                return writer.toByteArray();
            } finally {
                writer.release();
            }
        });
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
        List<Property.Value<?>> values = state.getValues().toList();
        if (values.isEmpty()) {
            return;
        }

        writer.startCompound(PROPERTIES);
        for (Property.Value<?> entry : values) {
            writer.putString(
                    propertyName(entry.property()),
                    propertyValueName(entry.property(), entry.value()));
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

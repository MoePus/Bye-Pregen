package com.moepus.byepregen.palette.arena.codec;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Creates vanilla compound tags backed by a lazily materialized arena payload. */
public final class ByteStreamTag extends CompoundTag {
    private static final int ROOT_HEADER_SIZE = 3;
    private final LazyPayloadMap tags;
    private final byte[] payload;

    private ByteStreamTag(byte[] payload) {
        this(new LazyPayloadMap(payload), payload);
    }

    private ByteStreamTag(LazyPayloadMap tags, byte[] payload) {
        super(tags);
        this.tags = tags;
        this.payload = payload;
    }

    public static ByteStreamTag uniform(int rawId) {
        return new ByteStreamTag(PayloadBuilder.uniform(rawId));
    }

    public static ByteStreamTag packed(
            SerializationScratch scratch, ArenaBlockStatePalettedContainer container) {
        return new ByteStreamTag(PayloadBuilder.packed(scratch, container));
    }

    @Override
    public void write(DataOutput output) throws IOException {
        if (this.tags.isMaterialized()) {
            super.write(output);
            return;
        }
        output.write(this.payload);
    }

    @Override
    public CompoundTag copy() {
        return this.tags.isMaterialized() ? super.copy() : new ByteStreamTag(this.payload);
    }

    private static final class LazyPayloadMap extends AbstractMap<String, Tag> {
        private final byte[] payload;
        private Map<String, Tag> delegate;

        private LazyPayloadMap(byte[] payload) {
            this.payload = payload;
        }

        private boolean isMaterialized() {
            return this.delegate != null;
        }

        @Override
        public Set<Entry<String, Tag>> entrySet() {
            return this.materialized().entrySet();
        }

        @Override
        public Tag get(Object key) {
            return this.materialized().get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return this.materialized().containsKey(key);
        }

        @Override
        public int size() {
            return this.materialized().size();
        }

        @Override
        public Tag put(String key, Tag value) {
            return this.materialized().put(key, value);
        }

        @Override
        public Tag remove(Object key) {
            return this.materialized().remove(key);
        }

        @Override
        public void clear() {
            this.materialized().clear();
        }

        private Map<String, Tag> materialized() {
            if (this.delegate == null) {
                this.delegate = readPayload(this.payload);
            }
            return this.delegate;
        }

        private static Map<String, Tag> readPayload(byte[] payload) {
            byte[] root = new byte[payload.length + ROOT_HEADER_SIZE];
            root[0] = Tag.TAG_COMPOUND;
            System.arraycopy(payload, 0, root, ROOT_HEADER_SIZE, payload.length);
            try {
                CompoundTag tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(root)));
                Map<String, Tag> values = new HashMap<>(tag.size());
                tag.forEach(values::put);
                return values;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to materialize arena block state payload", exception);
            }
        }
    }
}

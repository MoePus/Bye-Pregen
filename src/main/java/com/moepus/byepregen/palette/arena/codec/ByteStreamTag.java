package com.moepus.byepregen.palette.arena.codec;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

public final class ByteStreamTag extends CompoundTag {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ROOT_HEADER_SIZE = 3;
    private static final int PAYLOAD_SCAN_FAILED = -1;
    private static final int ANY_NUMERIC_TAG = 99;
    private static final int MATERIALIZE_STACK_DEPTH = 96;
    private static final Set<String> WARNED_MATERIALIZE_CALLERS = ConcurrentHashMap.newKeySet();

    private final LazyPayloadMap tags;
    private final byte[] payload;

    public static ByteStreamTag uniform(int rawId) {
        return new ByteStreamTag(PayloadBuilder.uniform(rawId));
    }

    public static ByteStreamTag packed(
            SerializationScratch scratch, ArenaBlockStatePalettedContainer container) {
        return new ByteStreamTag(PayloadBuilder.packed(scratch, container));
    }

    private ByteStreamTag(byte[] payload) {
        this(new LazyPayloadMap(payload), payload);
    }

    private ByteStreamTag(LazyPayloadMap tags, byte[] payload) {
        super(tags);
        this.tags = tags;
        this.payload = payload;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        if (!this.tags.canWritePayload()) {
            super.write(output);
            return;
        }
        output.write(this.payload);
    }

    @Override
    public CompoundTag copy() {
        if (!this.tags.canWritePayload()) {
            return super.copy();
        }
        return new ByteStreamTag(this.payload);
    }

    @Override
    public byte getTagType(String key) {
        if (this.tags.canWritePayload()) {
            int type = payloadTagType(this.payload, key);
            if (type != PAYLOAD_SCAN_FAILED) {
                return (byte) type;
            }
        }
        return super.getTagType(key);
    }

    @Override
    public boolean contains(String key) {
        if (this.tags.canWritePayload()) {
            int type = payloadTagType(this.payload, key);
            if (type != PAYLOAD_SCAN_FAILED) {
                return type != Tag.TAG_END;
            }
        }
        return super.contains(key);
    }

    @Override
    public boolean contains(String key, int type) {
        if (this.tags.canWritePayload()) {
            int actualType = payloadTagType(this.payload, key);
            if (actualType != PAYLOAD_SCAN_FAILED) {
                return actualType != Tag.TAG_END
                        && (actualType == type || type == ANY_NUMERIC_TAG && isNumericTag(actualType));
            }
        }
        return super.contains(key, type);
    }

    private static final class LazyPayloadMap extends AbstractMap<String, Tag> {
        private final byte[] payload;
        private Map<String, Tag> delegate;
        private boolean modified;

        LazyPayloadMap(byte[] payload) {
            this.payload = payload;
        }

        boolean canWritePayload() {
            return this.delegate == null && !this.modified;
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
            this.modified = true;
            return this.materialized().put(key, value);
        }

        @Override
        public Tag remove(Object key) {
            this.modified = true;
            return this.materialized().remove(key);
        }

        @Override
        public void clear() {
            this.modified = true;
            this.materialized().clear();
        }

        @Override
        public void putAll(Map<? extends String, ? extends Tag> map) {
            if (!map.isEmpty()) {
                this.modified = true;
            }
            this.materialized().putAll(map);
        }

        private Map<String, Tag> materialized() {
            if (this.delegate == null) {
                warnMaterialize();
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
                Map<String, Tag> map = new HashMap<>(tag.size());
                for (String key : tag.getAllKeys()) {
                    map.put(key, tag.get(key));
                }
                return map;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to materialize arena block state payload", exception);
            }
        }
    }

    private static void warnMaterialize() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (WARNED_MATERIALIZE_CALLERS.add(materializeCallerKey(stack))) {
            LOGGER.warn(materializeStackMessage(stack));
        }
    }

    private static int payloadTagType(byte[] payload, String key) {
        try {
            int offset = 0;
            while (offset < payload.length) {
                int type = payload[offset++] & 0xFF;
                if (type == Tag.TAG_END) {
                    return Tag.TAG_END;
                }
                boolean nameMatches = nameEquals(payload, offset, key);
                offset += Short.BYTES + readUnsignedShort(payload, offset);
                if (nameMatches) {
                    return type;
                }
                offset = skipPayload(payload, offset, type);
            }
        } catch (RuntimeException exception) {
            return PAYLOAD_SCAN_FAILED;
        }
        return Tag.TAG_END;
    }

    private static int skipPayload(byte[] payload, int offset, int type) {
        return switch (type) {
            case Tag.TAG_BYTE -> offset + Byte.BYTES;
            case Tag.TAG_SHORT -> offset + Short.BYTES;
            case Tag.TAG_INT, Tag.TAG_FLOAT -> offset + Integer.BYTES;
            case Tag.TAG_LONG, Tag.TAG_DOUBLE -> offset + Long.BYTES;
            case Tag.TAG_BYTE_ARRAY -> offset + Integer.BYTES + readInt(payload, offset);
            case Tag.TAG_STRING -> offset + Short.BYTES + readUnsignedShort(payload, offset);
            case Tag.TAG_LIST -> skipList(payload, offset);
            case Tag.TAG_COMPOUND -> skipCompound(payload, offset);
            case Tag.TAG_INT_ARRAY -> offset + Integer.BYTES + readInt(payload, offset) * Integer.BYTES;
            case Tag.TAG_LONG_ARRAY -> offset + Integer.BYTES + readInt(payload, offset) * Long.BYTES;
            default -> throw new IllegalArgumentException("Unknown NBT tag type: " + type);
        };
    }

    private static int skipList(byte[] payload, int offset) {
        int elementType = payload[offset] & 0xFF;
        int size = readInt(payload, offset + Byte.BYTES);
        offset += Byte.BYTES + Integer.BYTES;
        for (int i = 0; i < size; ++i) {
            offset = skipPayload(payload, offset, elementType);
        }
        return offset;
    }

    private static int skipCompound(byte[] payload, int offset) {
        while (true) {
            int type = payload[offset++] & 0xFF;
            if (type == Tag.TAG_END) {
                return offset;
            }
            offset += Short.BYTES + readUnsignedShort(payload, offset);
            offset = skipPayload(payload, offset, type);
        }
    }

    private static boolean nameEquals(byte[] payload, int offset, String key) {
        int length = readUnsignedShort(payload, offset);
        if (length != key.length()) {
            return false;
        }
        offset += Short.BYTES;
        for (int i = 0; i < length; ++i) {
            if ((payload[offset + i] & 0xFF) != key.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int readUnsignedShort(byte[] payload, int offset) {
        return ((payload[offset] & 0xFF) << Byte.SIZE) | (payload[offset + 1] & 0xFF);
    }

    private static int readInt(byte[] payload, int offset) {
        return ((payload[offset] & 0xFF) << 24)
                | ((payload[offset + 1] & 0xFF) << 16)
                | ((payload[offset + 2] & 0xFF) << 8)
                | (payload[offset + 3] & 0xFF);
    }

    private static boolean isNumericTag(int type) {
        return type >= Tag.TAG_BYTE && type <= Tag.TAG_DOUBLE;
    }

    private static String materializeCallerKey(StackTraceElement[] stack) {
        for (int i = 2; i < stack.length; ++i) {
            String className = stack[i].getClassName();
            if (isInternalMaterializeFrame(className)) {
                continue;
            }
            return className + "#" + stack[i].getMethodName() + ":" + stack[i].getLineNumber();
        }
        return "unknown";
    }

    private static boolean isInternalMaterializeFrame(String className) {
        return className.startsWith(ByteStreamTag.class.getName())
                || className.equals(CompoundTag.class.getName())
                || className.equals(Thread.class.getName());
    }

    private static String materializeStackMessage(StackTraceElement[] stack) {
        int end = Math.min(stack.length, MATERIALIZE_STACK_DEPTH);
        StringBuilder message = new StringBuilder(
                "Arena block state ByteStreamTag was lazily materialized; "
                        + "falling back to normal CompoundTag writes");
        for (int i = 2; i < end; ++i) {
            message.append(System.lineSeparator())
                    .append("\tat ")
                    .append(stack[i]);
        }
        return message.toString();
    }
}

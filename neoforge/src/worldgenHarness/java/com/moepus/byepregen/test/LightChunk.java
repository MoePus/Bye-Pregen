package com.moepus.byepregen.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.SimpleBitStorage;

final class LightChunk {
    static final int LIGHT_BYTES = 2048;
    static final String BLOCK_LIGHT = "BlockLight";
    static final String SKY_LIGHT = "SkyLight";
    private static final int LIGHT_LAYER_BYTES = LIGHT_BYTES / 16;
    private static final byte[] ZERO_LIGHT = new byte[LIGHT_BYTES];
    private static final byte[] FULL_LIGHT = filledLight(0xFF);
    private static final String[] LIGHT_KEYS = {BLOCK_LIGHT, SKY_LIGHT};

    final Map<LightSectionKey, byte[]> lights = new HashMap<>();
    private final Map<Integer, SectionBlocks> blocks = new HashMap<>();
    boolean lightCorrect;
    String status;

    static LightChunk from(CompoundTag chunkTag) {
        LightChunk chunk = new LightChunk();
        chunk.lightCorrect = chunkTag.getBooleanOr("isLightOn", false);
        chunk.status = chunkTag.getStringOr("Status", "");
        ListTag sections = chunkTag.getListOrEmpty("sections");
        for (int index = 0; index < sections.size(); index++) {
            sections.getCompound(index).ifPresent(chunk::readSection);
        }
        return chunk;
    }

    private void readSection(CompoundTag section) {
        int sectionY = section.getByteOr("Y", (byte) 0);
        section.getCompound("block_states")
                .ifPresent(blockStates -> this.blocks.put(sectionY, SectionBlocks.from(blockStates)));
        for (String lightKey : LIGHT_KEYS) {
            section.getByteArray(lightKey)
                    .ifPresent(light -> this.lights.put(new LightSectionKey(sectionY, lightKey), light));
        }
    }

    byte[] lightOrZero(LightSectionKey key) {
        return this.lights.getOrDefault(key, ZERO_LIGHT);
    }

    String blockStateAt(int sectionY, LocalPosition position) {
        return this.blockSection(sectionY).stateAt(position);
    }

    String terrainDifference(LightChunk other) {
        TreeSet<Integer> sectionKeys = new TreeSet<>(this.blocks.keySet());
        sectionKeys.addAll(other.blocks.keySet());
        for (int sectionY : sectionKeys) {
            String difference = this.blockSection(sectionY).terrainDifference(other.blockSection(sectionY));
            if (difference != null) {
                return "sectionY=" + sectionY + " " + difference;
            }
        }
        return null;
    }

    int lightAt(String layer, int sectionY, LocalPosition position) {
        byte[] bytes = SKY_LIGHT.equals(layer)
                ? this.semanticSkyLayer(sectionY)
                : this.lightOrZero(new LightSectionKey(sectionY, layer));
        if (!isValidLight(bytes)) {
            return -1;
        }
        int nibbleIndex = position.x() | (position.z() << 4) | (position.y() << 8);
        int value = bytes[nibbleIndex >>> 1] & 0xFF;
        return (value >>> ((nibbleIndex & 1) << 2)) & 15;
    }

    byte[] semanticSkyLayer(int sectionY) {
        byte[] light = this.lights.get(new LightSectionKey(sectionY, SKY_LIGHT));
        if (light != null) {
            return light;
        }
        byte[] above = this.firstSkyLayerAbove(sectionY);
        return above == null ? FULL_LIGHT : repeatFirstLayer(above);
    }

    static boolean isValidLight(byte[] bytes) {
        return bytes != null && bytes.length == LIGHT_BYTES;
    }

    private SectionBlocks blockSection(int sectionY) {
        return this.blocks.getOrDefault(sectionY, SectionBlocks.AIR);
    }

    private byte[] firstSkyLayerAbove(int sectionY) {
        byte[] best = null;
        int bestY = Integer.MAX_VALUE;
        for (Map.Entry<LightSectionKey, byte[]> entry : this.lights.entrySet()) {
            LightSectionKey key = entry.getKey();
            if (SKY_LIGHT.equals(key.layer()) && key.sectionY() > sectionY && key.sectionY() < bestY) {
                best = entry.getValue();
                bestY = key.sectionY();
            }
        }
        return best;
    }

    private static byte[] repeatFirstLayer(byte[] bytes) {
        if (!isValidLight(bytes)) {
            return null;
        }
        byte[] repeated = new byte[LIGHT_BYTES];
        for (int layer = 0; layer < 16; layer++) {
            System.arraycopy(bytes, 0, repeated, layer * LIGHT_LAYER_BYTES, LIGHT_LAYER_BYTES);
        }
        return repeated;
    }

    private static byte[] filledLight(int value) {
        byte[] bytes = new byte[LIGHT_BYTES];
        Arrays.fill(bytes, (byte)value);
        return bytes;
    }

    private static final class SectionBlocks {
        private static final SectionBlocks AIR = new SectionBlocks(new String[]{"minecraft:air"}, null);
        private static final SectionBlocks MISSING = new SectionBlocks(new String[]{"missing-palette"}, null);
        private final String[] palette;
        private final SimpleBitStorage storage;

        private SectionBlocks(String[] palette, SimpleBitStorage storage) {
            this.palette = palette;
            this.storage = storage;
        }

        static SectionBlocks from(CompoundTag tag) {
            ListTag paletteTags = tag.getListOrEmpty("palette");
            if (paletteTags.isEmpty()) {
                return MISSING;
            }
            String[] palette = new String[paletteTags.size()];
            for (int index = 0; index < palette.length; index++) {
                palette[index] = paletteTags.getCompound(index)
                        .map(SectionBlocks::stateName)
                        .orElse("invalid-palette-entry");
            }
            if (!tag.contains("data")) {
                return new SectionBlocks(palette, null);
            }
            int bits = Math.max(4, ceilLog2(palette.length));
            try {
                return new SectionBlocks(palette,
                        new SimpleBitStorage(bits, 4096, tag.getLongArray("data").orElseThrow()));
            } catch (RuntimeException exception) {
                return new SectionBlocks(new String[]{"invalid-storage:" + exception.getMessage()}, null);
            }
        }

        String stateAt(LocalPosition position) {
            return this.stateAt(position.x() | (position.z() << 4) | (position.y() << 8));
        }

        String terrainDifference(SectionBlocks other) {
            if (this.storage == null && other.storage == null) {
                return this.palette.length == 1 && other.palette.length == 1
                        && this.palette[0].equals(other.palette[0])
                        ? null : "expected=" + this.stateAt(0) + " actual=" + other.stateAt(0);
            }
            for (int index = 0; index < 4096; index++) {
                String expected = this.stateAt(index);
                String actual = other.stateAt(index);
                if (!expected.equals(actual)) {
                    int x = index & 15;
                    int z = index >>> 4 & 15;
                    int y = index >>> 8 & 15;
                    return "local=" + x + "," + y + "," + z
                            + " expected=" + expected + " actual=" + actual;
                }
            }
            return null;
        }

        private String stateAt(int storageIndex) {
            int paletteIndex = this.storage == null ? 0 : this.storage.get(storageIndex);
            return paletteIndex >= 0 && paletteIndex < this.palette.length
                    ? this.palette[paletteIndex] : "invalid-palette-id:" + paletteIndex;
        }

        private static String stateName(CompoundTag stateTag) {
            String name = stateTag.getStringOr("Name", "");
            if (!stateTag.contains("Properties")) {
                return name;
            }
            CompoundTag properties = stateTag.getCompoundOrEmpty("Properties");
            TreeSet<String> keys = new TreeSet<>(properties.keySet());
            StringBuilder builder = new StringBuilder(name).append('[');
            boolean first = true;
            for (String key : keys) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(key).append('=').append(properties.getStringOr(key, ""));
            }
            return builder.append(']').toString();
        }

        private static int ceilLog2(int value) {
            int result = 0;
            int target = Math.max(1, value - 1);
            while (target > 0) {
                result++;
                target >>>= 1;
            }
            return result;
        }
    }
}

record LightSectionKey(int sectionY, String layer) implements Comparable<LightSectionKey> {
    @Override
    public int compareTo(LightSectionKey other) {
        int yOrder = Integer.compare(this.sectionY, other.sectionY);
        return yOrder != 0 ? yOrder : this.layer.compareTo(other.layer);
    }
}

record LocalPosition(int x, int y, int z) {
}

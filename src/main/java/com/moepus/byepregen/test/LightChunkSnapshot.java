package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.YANibbleArray;
import java.util.Arrays;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

record LightChunkSnapshot(byte[][] block, byte[][] sky) {
    static LightChunkSnapshot capture(ServerLevel level, ChunkPos pos) {
        LevelLightEngine engine = level.getChunkSource().getLightEngine();
        return new LightChunkSnapshot(
                snapshotLayer(engine, LightLayer.BLOCK, pos),
                snapshotLayer(engine, LightLayer.SKY, pos)
        );
    }

    static LightChunkSnapshot captureBlockSection(ServerLevel level, ChunkPos pos, int sectionY) {
        LevelLightEngine engine = level.getChunkSource().getLightEngine();
        byte[][] block = snapshotLayer(engine, LightLayer.BLOCK, pos);
        int retained = sectionY - engine.getMinLightSection();
        for (int i = 0; i < block.length; ++i) {
            if (i != retained) {
                block[i] = null;
            }
        }
        return new LightChunkSnapshot(block, new byte[block.length][]);
    }

    boolean matches(LightChunkSnapshot other) {
        return Arrays.deepEquals(this.block, other.block) && Arrays.deepEquals(this.sky, other.sky);
    }

    String blockLightLoss(LightChunkSnapshot other) {
        for (int section = 0; section < this.block.length; ++section) {
            for (int y = 0; y < 16; ++y) {
                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) {
                        int index = y << 8 | z << 4 | x;
                        int expected = nibbleAt(this.block[section], index);
                        int actual = nibbleAt(other.block[section], index);
                        if (actual < expected) {
                            return "section=" + section + " local=" + x + "," + y + "," + z
                                    + " expected=" + expected + " actual=" + actual;
                        }
                    }
                }
            }
        }
        return null;
    }

    void verifyNoBlockLightLoss(LightChunkSnapshot other, ChunkPos pos) {
        String difference = this.blockLightLoss(other);
        if (difference != null) {
            throw new IllegalStateException("Block light decreased after reconciliation in "
                    + pos + ": " + difference);
        }
    }

    String summary() {
        return "{block=" + nonEmptySections(this.block) + ",sky=" + nonEmptySections(this.sky) + "}";
    }

    String differenceSummary(LightChunkSnapshot other) {
        String blockDifference = layerDifference(this.block, other.block);
        if (blockDifference != null) {
            return "block " + blockDifference;
        }
        String skyDifference = layerDifference(this.sky, other.sky);
        return skyDifference == null ? "none" : "sky " + skyDifference;
    }

    private static byte[][] snapshotLayer(LevelLightEngine engine, LightLayer layer, ChunkPos pos) {
        LayerLightEventListener listener = engine.getLayerListener(layer);
        byte[][] snapshot = new byte[engine.getLightSectionCount()][];
        for (int i = 0; i < snapshot.length; ++i) {
            SectionPos sectionPos = SectionPos.of(pos, engine.getMinLightSection() + i);
            snapshot[i] = snapshotSection(listener.getDataLayerData(sectionPos));
        }
        return snapshot;
    }

    private static byte[] snapshotSection(DataLayer section) {
        if (section == null || section.isEmpty()) {
            return null;
        }
        return section.isDefinitelyFilledWith(15)
                ? YANibbleArray.FULL_LIGHT_DATA.clone()
                : section.getData().clone();
    }

    private static int nonEmptySections(byte[][] sections) {
        if (sections == null) {
            return 0;
        }
        int count = 0;
        for (byte[] section : sections) {
            count += section == null ? 0 : 1;
        }
        return count;
    }

    private static String layerDifference(byte[][] expected, byte[][] actual) {
        for (int section = 0; section < expected.length; ++section) {
            if (!Arrays.equals(expected[section], actual[section])) {
                int byteIndex = firstDifferentByte(expected[section], actual[section]);
                return "section=" + section
                        + " expectedNonZeroBytes=" + nonZeroBytes(expected[section])
                        + " actualNonZeroBytes=" + nonZeroBytes(actual[section])
                        + " firstByte=" + byteIndex
                        + " expectedByte=" + byteAt(expected[section], byteIndex)
                        + " actualByte=" + byteAt(actual[section], byteIndex);
            }
        }
        return null;
    }

    private static int nonZeroBytes(byte[] data) {
        if (data == null) {
            return 0;
        }
        int count = 0;
        for (byte value : data) {
            count += value == 0 ? 0 : 1;
        }
        return count;
    }

    private static int firstDifferentByte(byte[] expected, byte[] actual) {
        for (int i = 0; i < YANibbleArray.SIZE; ++i) {
            if (byteAt(expected, i) != byteAt(actual, i)) {
                return i;
            }
        }
        return -1;
    }

    private static int byteAt(byte[] data, int index) {
        return data == null || index < 0 ? 0 : data[index] & 255;
    }

    private static int nibbleAt(byte[] data, int index) {
        int packed = byteAt(data, index >>> 1);
        return packed >>> ((index & 1) << 2) & 15;
    }
}

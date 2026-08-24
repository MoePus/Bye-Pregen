package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.lighting.LevelLightEngine;

final class LightRestartVerifier {
    private static final int NIBBLE_COUNT = 4096;

    private LightRestartVerifier() {
    }

    static void verifyRuntimeState(ServerLevel level, List<LightRestartSnapshot.Sample> samples) {
        int min = 15;
        int max = 0;
        boolean intermediate = false;
        for (LightRestartSnapshot.Sample sample : samples) {
            if (sample.server() != sample.packet()) {
                throw new IllegalStateException("Packet mismatch at " + sample);
            }
            min = Math.min(min, sample.server());
            max = Math.max(max, sample.server());
            intermediate |= sample.server() > 0 && sample.server() < 15;
        }
        if (min != 0 || max != 15 || !intermediate) {
            throw new IllegalStateException("Fixture lacks 0/15/intermediate sky values: " + samples);
        }
        assertZeroStorage(level, LightRestartProbe.CENTER, LightRestartProbe.ROOF_Y);
        assertZeroStorage(level, LightRestartProbe.RELIGHT, LightRestartProbe.ROOF_Y);
        assertStorage(level, LightRestartProbe.BOUNDARY, LightRestartProbe.BOUNDARY_ROOF_Y);
        assertMixedStorage(level);
        assertPacketSentinel(level, LightRestartProbe.CENTER);
        assertPacketSentinel(level, LightRestartProbe.RELIGHT);
        assertPacketValue(level, new BlockPos(8, 95, 24), 0);
        assertPacketValue(level, new BlockPos(8, 97, 24), 15);
    }

    private static void assertZeroStorage(ServerLevel level, ChunkPos pos, int roofY) {
        YANibbleArray roof = assertStorage(level, pos, roofY);
        if (!isZero(roof)) {
            throw new IllegalStateException("Expected zero skylight storage at " + pos);
        }
    }

    private static YANibbleArray assertStorage(ServerLevel level, ChunkPos pos, int roofY) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        BlockPos roofPosition = new BlockPos(pos.getMinBlockX() + 8, roofY, pos.getMinBlockZ() + 8);
        YAChunkLightData data = ((YAChunkLightAccess)chunk).byepregen$skyLightData();
        YANibbleArray roof = data == null ? null : data.getVisibleSection(roofY >> 4);
        if (!level.getBlockState(roofPosition).is(Blocks.STONE)
                || !chunk.isLightCorrect() || data == null || !data.lightEnabled()
                || roof == null || roof.getVisible(roofPosition.getX(), roofY, roofPosition.getZ()) != 0) {
            throw new IllegalStateException("Sky storage was not rebuilt at " + pos + ": correct="
                    + chunk.isLightCorrect() + " roofBlock=" + level.getBlockState(roofPosition)
                    + " caveSky=" + level.getBrightness(LightLayer.SKY, roofPosition.below())
                    + " data=" + (data != null)
                    + " enabled=" + (data != null && data.lightEnabled())
                    + " roofKind=" + (roof == null ? "missing" : roof.visibleSaveKind()));
        }
        return roof;
    }

    private static void assertMixedStorage(ServerLevel level) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                LightRestartProbe.MIXED.x(), LightRestartProbe.MIXED.z());
        YAChunkLightData data = ((YAChunkLightAccess)chunk).byepregen$skyLightData();
        YANibbleArray section = data == null ? null : data.getVisibleSection(LightRestartProbe.ROOF_Y >> 4);
        if (section == null || section.visibleSaveKind() != YANibbleArray.SAVE_DATA
                || !containsIntermediate(section)) {
            throw new IllegalStateException("Mixed skylight section was not retained at " + LightRestartProbe.MIXED);
        }
    }

    private static boolean containsIntermediate(YANibbleArray nibble) {
        for (int index = 0; index < NIBBLE_COUNT; ++index) {
            int value = nibble.getVisible(index & 15, index >> 8, index >> 4 & 15);
            if (value > 0 && value < 15) {
                return true;
            }
        }
        return false;
    }

    private static boolean isZero(YANibbleArray nibble) {
        for (int index = 0; index < NIBBLE_COUNT; ++index) {
            if (nibble.getVisible(index & 15, index >> 8, index >> 4 & 15) != 0) {
                return false;
            }
        }
        return true;
    }

    private static void assertPacketSentinel(ServerLevel level, ChunkPos pos) {
        LevelLightEngine engine = level.getChunkSource().getLightEngine();
        ClientboundLightUpdatePacketData packet = new ClientboundLightUpdatePacketData(pos, engine, null, null);
        int index = (LightRestartProbe.ROOF_Y >> 4) - engine.getMinLightSection();
        boolean empty = packet.getEmptySkyYMask().get(index);
        boolean zeroUpdate = packet.getSkyYMask().get(index) && packetUpdateIsZero(packet, index);
        if (!empty && !zeroUpdate) {
            throw new IllegalStateException("Packet omitted zero-sky roof sentinel at " + pos + " index=" + index);
        }
    }

    private static void assertPacketValue(ServerLevel level, BlockPos pos, int expected) {
        int actual = LightRestartSnapshot.packetSkyLight(level.getChunkSource().getLightEngine(), pos);
        if (actual != expected) {
            throw new IllegalStateException("Packet skylight mismatch at " + pos
                    + " expected=" + expected + " actual=" + actual);
        }
    }

    private static boolean packetUpdateIsZero(ClientboundLightUpdatePacketData packet, int sectionIndex) {
        int updateIndex = packet.getSkyYMask().get(0, sectionIndex).cardinality();
        byte[] data = packet.getSkyUpdates().get(updateIndex);
        for (byte value : data) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    static void verifyCompressedFixture(ServerLevel level) {
        verifyCompressedChunk(level, LightRestartProbe.CENTER);
        verifyCompressedChunk(level, LightRestartProbe.RELIGHT);
    }

    private static void verifyCompressedChunk(ServerLevel level, ChunkPos pos) {
        ChunkAccess chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write();
        ListTag sections = tag.getListOrEmpty("sections");
        for (int i = 0; i < sections.size(); ++i) {
            CompoundTag section = sections.getCompound(i).orElseGet(CompoundTag::new);
            if (section.getByteOr("Y", (byte) 0) == (byte)(LightRestartProbe.ROOF_Y >> 4)
                    && section.contains("SkyLight")) {
                throw new IllegalStateException("Fixture unexpectedly saved zero-sky roof at " + pos);
            }
        }
    }

    static void verifyRelightFixtureInvalid(ServerLevel level) {
        ChunkAccess chunk = level.getChunkSource().getChunkNow(
                LightRestartProbe.RELIGHT.x(), LightRestartProbe.RELIGHT.z());
        CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write();
        if (tag.getBooleanOr("isLightOn", false)) {
            throw new IllegalStateException("Relight fixture unexpectedly saved isLightOn=true");
        }
    }

}

package com.moepus.byepregen.storagetest;

import com.moepus.byepregen.arenatest.HarnessResult;
import com.moepus.byepregen.chunksave.serialize.GcFreeChunkSerializer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

public final class StorageSerializationVerifier {
    private static final int BLENDING_COLUMNS = 16;
    private StorageSerializationVerifier() { }

    public static void run(MinecraftServer server) {
        HarnessResult.run(server, "byepregen.storageHarness.result", () -> verify(server.overworld()));
    }

    static int verify(ServerLevel level) throws Exception {
        int count = 0;
        for (ChunkStatus status : ChunkStatus.getStatusList()) {
            if (status == ChunkStatus.FULL) continue;
            ProtoChunk chunk = fixture(level, status, count++);
            compare(level, chunk);
        }
        compare(level, level.getChunk(71, -73));
        verifyFallback(level);
        return count + 1;
    }

    private static ProtoChunk fixture(ServerLevel level, ChunkStatus status, int index) {
        ChunkPos pos = new ChunkPos(400 + index, -400);
        float[] heights = new float[BLENDING_COLUMNS];
        Arrays.fill(heights, Float.MAX_VALUE);
        heights[0] = 63.25f;
        BlendingData blending = BlendingData.unpack(new BlendingData.Packed(0, 16, Optional.of(heights)));
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.palettedContainerFactory(), blending);
        chunk.setPersistedStatus(status);
        chunk.setInhabitedTime(12345);
        chunk.setLightCorrect(true);
        for (int i = 0; i < 300; i++) {
            var state = Block.stateById(i * 3);
            chunk.getSection(1).setBlockState(i & 15, i >>> 8, (i >>> 4) & 15, state);
        }
        BlockPos chest = new BlockPos(pos.getMinBlockX(), chunk.getMinY(), pos.getMinBlockZ());
        chunk.getSection(0).setBlockState(0, 0, 0, Blocks.CHEST.defaultBlockState());
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:pig");
        entity.putString("fixture_extension", "preserved entity field");
        chunk.addEntity(entity);
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", "minecraft:chest");
        blockEntity.putInt("x", chest.getX());
        blockEntity.putInt("y", chest.getY());
        blockEntity.putInt("z", chest.getZ());
        blockEntity.putString("fixture_extension", "preserved block entity field");
        chunk.setBlockEntityNbt(blockEntity);
        chunk.getBlockTicks().schedule(new ScheduledTick<>(Blocks.CHEST, chest, 13, TickPriority.HIGH, 1));
        chunk.getFluidTicks().schedule(new ScheduledTick<>(Fluids.WATER, chest.above(), 27, TickPriority.LOW, 2));
        chunk.markPosForPostProcessing(chest);
        chunk.markPosForPostProcessing(chest);
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        if (status.isBefore(ChunkStatus.TERRAIN)) {
            CompoundTag retro = new CompoundTag();
            retro.putString("target_status", "minecraft:terrain");
            retro.putLongArray("missing_bedrock", new long[]{1, 2, 3, 4});
            chunk.setBelowZeroRetrogen(BelowZeroRetrogen.CODEC.parse(NbtOps.INSTANCE, retro).getOrThrow());
        }
        return chunk;
    }

    private static void compare(ServerLevel level, ChunkAccess chunk) throws Exception {
        CompoundTag expected = SerializableChunkData.copyOf(level, chunk).write();
        CompoundTag actual;
        try (var input = new DataInputStream(new ByteArrayInputStream(GcFreeChunkSerializer.serializeRaw(level, chunk)))) {
            actual = NbtIo.read(input);
            if (input.available() != 0) throw new AssertionError("Trailing raw bytes");
        }
        normalizeSections(level, expected);
        normalizeSections(level, actual);
        if (!expected.equals(actual)) {
            for (String key : expected.keySet()) {
                if (!java.util.Objects.equals(expected.get(key), actual.get(key))) {
                    throw new AssertionError("Raw field differs: " + key + " at " + chunk.getPersistedStatus()
                            + " expected=" + expected.get(key) + " actual=" + actual.get(key));
                }
            }
            throw new AssertionError("Raw save added unexpected fields: " + actual.keySet());
        }
    }

    private static void normalizeSections(ServerLevel level, CompoundTag root) {
        for (var entry : root.getListOrEmpty("sections")) {
            CompoundTag section = (CompoundTag) entry;
            if (section.contains("block_states")) normalizeBlocks(level, section);
            if (section.contains("biomes")) normalizeBiomes(level, section);
        }
    }

    private static void normalizeBlocks(ServerLevel level, CompoundTag section) {
        var blocks = level.palettedContainerFactory().blockStatesContainerCodec()
                .parse(NbtOps.INSTANCE, section.get("block_states")).getOrThrow();
        int[] states = new int[4096];
        for (int i = 0; i < states.length; i++) states[i] = Block.getId(blocks.get(i & 15, i >>> 8, i >>> 4 & 15));
        section.putIntArray("block_states", states);
    }

    private static void normalizeBiomes(ServerLevel level, CompoundTag section) {
        var biomes = level.palettedContainerFactory().biomeContainerCodec()
                .parse(NbtOps.INSTANCE, section.get("biomes")).getOrThrow();
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 64; i++) ids.append(biomes.get(i & 3, i >>> 4, i >>> 2 & 3).unwrapKey().orElseThrow()).append(';');
        section.putString("biomes", ids.toString());
    }

    private static void verifyFallback(ServerLevel level) {
        ProtoChunk unknown = new ProtoChunk(new ChunkPos(1, 1), UpgradeData.EMPTY, level,
                level.palettedContainerFactory(), null) { };
        if (GcFreeChunkSerializer.shouldUseGcFree(unknown)) {
            throw new AssertionError("Unknown chunk subclass must use native serialization");
        }
    }
}

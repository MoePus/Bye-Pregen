package com.moepus.byepregen.test;

import com.moepus.byepregen.chunksave.serialize.GcFreeChunkSerializer;
import com.moepus.byepregen.chunksave.storage.ChunkSavingCompression;
import com.moepus.byepregen.chunksave.storage.RawChunkData;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

final class ChunkNbtParity {
    static final String MODE = "chunk_nbt_parity";
    private static final String RESULT_PROPERTY = "byepregen.chunkNbtParityResult";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCHEDULED_TICK_DELAY = 200;

    private ChunkNbtParity() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ChunkNbtParity::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        server.execute(() -> run(server));
    }

    private static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            LevelChunk generated = level.getChunk(0, 0);
            int generatedBytes = assertParity(level, generated, "generated", Coverage.NONE);

            LevelChunk enriched = level.getChunk(1, 0);
            enrich(level, enriched);
            int enrichedBytes = assertParity(level, enriched, "enriched", Coverage.ENRICHED);

            ProtoChunk proto = createPostprocessProto(level);
            int protoBytes = assertParity(level, proto, "proto-postprocess", Coverage.POSTPROCESS);

            writeResult("PASS\nscenarios=generated,enriched,proto-postprocess\nrawBytes="
                    + generatedBytes + "," + enrichedBytes + "," + protoBytes + "\n");
            LOGGER.info("BYEPREGEN_CHUNK_NBT_PARITY_PASS");
        } catch (Throwable throwable) {
            writeFailure(throwable);
            LOGGER.error("BYEPREGEN_CHUNK_NBT_PARITY_FAIL", throwable);
        } finally {
            server.halt(false);
        }
    }

    private static void enrich(ServerLevel level, LevelChunk chunk) {
        int baseX = chunk.getPos().getMinBlockX() + 1;
        int baseZ = chunk.getPos().getMinBlockZ() + 1;
        int y = Math.clamp(level.getSeaLevel(), level.getMinBuildHeight() + 16, level.getMaxBuildHeight() - 16);
        BlockPos chest = new BlockPos(baseX, y, baseZ);
        BlockPos water = chest.east();
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        if (chunk.getBlockEntity(chest) == null) {
            throw new AssertionError("enriched chunk is missing its chest block entity");
        }
        level.scheduleTick(chest, Blocks.CHEST, SCHEDULED_TICK_DELAY);
        level.scheduleTick(water, Fluids.WATER, SCHEDULED_TICK_DELAY);
    }

    private static ProtoChunk createPostprocessProto(ServerLevel level) {
        ChunkPos pos = new ChunkPos(2, 0);
        ProtoChunk chunk = new ProtoChunk(
                pos,
                UpgradeData.EMPTY,
                level,
                level.registryAccess().registryOrThrow(Registries.BIOME),
                null
        );
        int y = Math.clamp(level.getSeaLevel(), level.getMinBuildHeight() + 16, level.getMaxBuildHeight() - 16);
        BlockPos block = new BlockPos(pos.getMinBlockX() + 1, y, pos.getMinBlockZ() + 1);
        chunk.setBlockState(block, Blocks.STONE.defaultBlockState(), false);
        chunk.markPosForPostprocessing(block.above());
        return chunk;
    }

    private static int assertParity(
            ServerLevel level,
            ChunkAccess chunk,
            String scenario,
            Coverage coverage
    ) throws Exception {
        CompoundTag vanilla = ChunkSerializer.write(level, chunk);
        assertCoverage(vanilla, coverage);
        RawChunkData rawData = GcFreeChunkSerializer.serializeRawData(level, chunk);
        byte[] raw = rawData.toByteArray();
        CompoundTag rawTag = parse(raw, scenario + " raw data");
        ChunkNbtComparator.assertEquivalent(vanilla, rawTag, scenario + " raw NBT");

        byte[] boundedRaw = boundedCopy(rawData);
        byte[] compressed = compress(boundedRaw);
        byte[] inflated = inflate(compressed);
        if (!Arrays.equals(boundedRaw, inflated)) {
            throw new AssertionError(scenario + " DEFLATE round-trip changed raw bytes");
        }
        ChunkNbtComparator.assertEquivalent(
                vanilla, parse(inflated, scenario + " inflated data"), scenario + " inflated NBT");
        return raw.length;
    }

    private static byte[] boundedCopy(RawChunkData rawData) {
        byte[] padded = Arrays.copyOf(rawData.bytes(), rawData.length() + 16);
        byte[] bounded = new RawChunkData(padded, rawData.length()).toByteArray();
        if (!Arrays.equals(rawData.toByteArray(), bounded)) {
            throw new AssertionError("RawChunkData.toByteArray changed bounded raw bytes");
        }
        return bounded;
    }

    private static void assertCoverage(CompoundTag tag, Coverage coverage) {
        if (coverage == Coverage.NONE) {
            return;
        }
        if (coverage == Coverage.ENRICHED) {
            assertNonEmptyList(tag, "block_entities");
            assertNonEmptyList(tag, "block_ticks");
            assertNonEmptyList(tag, "fluid_ticks");
            return;
        }
        ListTag postProcessing = tag.getList("PostProcessing", Tag.TAG_LIST);
        for (int index = 0; index < postProcessing.size(); ++index) {
            if (!postProcessing.getList(index).isEmpty()) {
                return;
            }
        }
        throw new AssertionError("enriched chunk has no postprocessing entries");
    }

    private static void assertNonEmptyList(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        if (!(value instanceof ListTag list) || list.isEmpty()) {
            throw new AssertionError("enriched chunk has no " + key + " entries");
        }
    }

    private static CompoundTag parse(byte[] bytes, String stage) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            CompoundTag tag = NbtIo.read(input);
            if (input.available() != 0) {
                throw new AssertionError(stage + " has " + input.available() + " trailing bytes");
            }
            return tag;
        }
    }

    private static byte[] compress(byte[] raw) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream output = ChunkSavingCompression.wrap(RegionFileVersion.VERSION_DEFLATE, compressed)) {
            output.write(raw);
        }
        return compressed.toByteArray();
    }

    private static byte[] inflate(byte[] compressed) throws Exception {
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            return input.readAllBytes();
        }
    }

    private static void writeResult(String result) throws Exception {
        Path path = resultPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, result, StandardCharsets.UTF_8);
    }

    private static void writeFailure(Throwable throwable) {
        try {
            writeResult("FAIL\n" + throwable + "\n");
        } catch (Exception writeFailure) {
            throwable.addSuppressed(writeFailure);
        }
    }

    private static Path resultPath() {
        String value = System.getProperty(RESULT_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing -D" + RESULT_PROPERTY);
        }
        return Path.of(value);
    }

    private enum Coverage {
        NONE,
        ENRICHED,
        POSTPROCESS
    }
}

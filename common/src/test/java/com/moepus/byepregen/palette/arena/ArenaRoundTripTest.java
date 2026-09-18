package com.moepus.byepregen.palette.arena;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import com.moepus.byepregen.palette.arena.codec.NbtReader;
import com.moepus.byepregen.palette.arena.codec.StateCodec;
import com.moepus.byepregen.palette.arena.materialize.ArenaSectionMaterializer;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ArenaRoundTripTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preservesBlocksCountsAndCodecsAcrossPaletteTransitions() {
        for (int paletteSize : new int[]{1, 16, 17, 257}) verifyRoundTrip(paletteSize);
    }

    private static void verifyRoundTrip(int paletteSize) {
        ArenaBlockStatePalettedContainer arena = new ArenaBlockStatePalettedContainer();
        Random random = new Random(1234);
        for (int i = 0; i < Layout.SECTION_SIZE; ++i) {
            arena.setRawId(i, random.nextInt(paletteSize));
        }
        Map<BlockState, Integer> expectedCounts = new HashMap<>();
        for (int i = 0; i < Layout.SECTION_SIZE; ++i) {
            expectedCounts.merge(Block.stateById(arena.rawIdAt(i)), 1, Integer::sum);
        }
        Map<BlockState, Integer> counts = new HashMap<>();
        arena.count(counts::put);
        assertEquals(expectedCounts, counts);
        HashSet<BlockState> palette = new HashSet<>();
        arena.forEachInPalette(palette::add);
        assertEquals(expectedCounts.keySet(), palette);
        assertBlocks(arena, arena.copy());
        assertBlocks(arena, ArenaSectionMaterializer.materialize(arena));
        verifyNetwork(arena);
        verifyDiskAndGenericCodec(arena);
    }

    private static void verifyNetwork(ArenaBlockStatePalettedContainer arena) {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            arena.write(bytes);
            assertEquals(arena.getSerializedSize(), bytes.readableBytes());
            assertEquals(arena.bitsPerEntry(), bytes.getUnsignedByte(0));
            PalettedContainer<BlockState> vanilla = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), strategy());
            vanilla.read(bytes);
            assertEquals(0, bytes.readableBytes());
            assertBlocks(arena, vanilla);
            bytes.clear();
            vanilla.write(bytes);
            ArenaBlockStatePalettedContainer restored = new ArenaBlockStatePalettedContainer();
            restored.read(bytes);
            assertEquals(0, bytes.readableBytes());
            assertBlocks(arena, restored);
        } finally {
            bytes.release();
        }
    }

    private static void verifyDiskAndGenericCodec(ArenaBlockStatePalettedContainer arena) {
        var vanilla = PalettedContainer.codecRW(BlockState.CODEC, strategy(), Blocks.AIR.defaultBlockState());
        var codec = new StateCodec(vanilla);
        CompoundTag tag = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, arena).getOrThrow();
        assertBlocks(arena, NbtReader.read(tag));
        assertBlocks(arena, vanilla.parse(NbtOps.INSTANCE, tag).getOrThrow());
        CompoundTag vanillaTag = (CompoundTag) vanilla.encodeStart(NbtOps.INSTANCE,
                ArenaSectionMaterializer.materialize(arena)).getOrThrow();
        assertBlocks(arena, NbtReader.read(vanillaTag));
        var json = codec.encodeStart(JsonOps.INSTANCE, arena).getOrThrow();
        assertBlocks(arena, vanilla.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    private static Strategy<BlockState> strategy() {
        return Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
    }

    private static void assertBlocks(ArenaBlockStatePalettedContainer expected, PalettedContainer<BlockState> actual) {
        assertNotNull(actual);
        for (int i = 0; i < Layout.SECTION_SIZE; ++i) {
            assertSame(Block.stateById(expected.rawIdAt(i)), actual.get(i & 15, i >>> 8, (i >>> 4) & 15),
                    "block " + i);
        }
    }
}

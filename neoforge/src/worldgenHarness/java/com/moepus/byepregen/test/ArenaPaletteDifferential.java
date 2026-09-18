package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.HarnessResultFile;
import com.moepus.byepregen.harness.HarnessServerLifecycle;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.palette.arena.codec.NbtReader;
import com.moepus.byepregen.palette.arena.codec.StateCodec;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

final class ArenaPaletteDifferential {
    static final String MODE = "arena_palette";
    private static final String RESULT_PROPERTY = "byepregen.arenaPaletteDifferentialResult";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HarnessServerLifecycle.FailureOptions FAILURE =
            new HarnessServerLifecycle.FailureOptions(
                    RESULT_PROPERTY,
                    LOGGER,
                    "BYEPREGEN_ARENA_PALETTE_DIFFERENTIAL_FAIL"
            );
    private static final int SECTION_SIZE = 16 * 16 * 16;
    private static final List<BlockState> STATES = List.of(
            Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(),
            Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.BEDROCK.defaultBlockState(), Blocks.WATER.defaultBlockState(),
            Blocks.LAVA.defaultBlockState(), Blocks.SAND.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(), Blocks.GOLD_ORE.defaultBlockState(),
            Blocks.IRON_ORE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(),
            Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
            Blocks.SPONGE.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
            Blocks.LAPIS_ORE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()
    );

    private ArenaPaletteDifferential() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ArenaPaletteDifferential::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        server.execute(() -> run(server));
    }

    private static void run(MinecraftServer server) {
        HarnessServerLifecycle.execute(server, FAILURE, () -> {
            assertScenario(1);
            assertScenario(8);
            assertScenario(STATES.size());
            HarnessResultFile.write(RESULT_PROPERTY, "PASS\nscenarios=uniform,page-palette,dense\n");
            LOGGER.info("BYEPREGEN_ARENA_PALETTE_DIFFERENTIAL_PASS");
        });
    }

    private static void assertScenario(int distinctStates) throws Exception {
        Pair pair = createPair(distinctStates);
        assertContents(pair.vanilla(), pair.arena(), "initial " + distinctStates);
        assertQueries(pair.vanilla(), pair.arena(), distinctStates);
        assertNetworkRoundTrip(pair.vanilla(), pair.arena(), distinctStates);
        assertNbtRoundTrip(pair.vanilla(), pair.arena(), distinctStates);
        mutate(pair, distinctStates);
        assertContents(pair.vanilla(), pair.arena(), "mutated " + distinctStates);
        assertQueries(pair.vanilla(), pair.arena(), distinctStates);
        assertCopyIsolation(pair.arena());
    }

    /**
     * Encodes the Arena container through the block-state codec, writes the tag to NBT bytes and reads it
     * back, so the palette element encoding is checked the way a chunk file would be.
     */
    private static void assertNbtRoundTrip(
            PalettedContainer<BlockState> vanilla,
            ArenaBlockStatePalettedContainer arena,
            int distinctStates
    ) throws Exception {
        var blockStates = PalettedContainer.codecRW(
                BlockState.CODEC, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                Blocks.AIR.defaultBlockState());
        var codec = new StateCodec(blockStates);
        CompoundTag tag = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, arena).getOrThrow();
        assertContents(arena, NbtReader.read(tag), "nbt direct " + distinctStates);

        CompoundTag reloaded;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(tag, output);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            reloaded = NbtIo.read(input);
        }
        assertContents(arena, blockStates.parse(NbtOps.INSTANCE, reloaded).getOrThrow(),
                "nbt bytes " + distinctStates);

        // The vanilla container must serialize to a tag our reader understands, which is the other half
        // of the bidirectional check the block-state codec switch demands.
        CompoundTag vanillaTag = (CompoundTag) blockStates.encodeStart(NbtOps.INSTANCE, vanilla).getOrThrow();
        assertContents(arena, NbtReader.read(vanillaTag), "nbt vanilla " + distinctStates);
    }

    private static Pair createPair(int distinctStates) {
        PalettedContainer<BlockState> vanilla = new PalettedContainer<>(
                STATES.getFirst(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        ArenaBlockStatePalettedContainer arena = new ArenaBlockStatePalettedContainer();
        for (int index = 0; index < SECTION_SIZE; ++index) {
            BlockState state = STATES.get(index % distinctStates);
            set(vanilla, index, state);
            set(arena, index, state);
        }
        return new Pair(vanilla, arena);
    }

    private static void mutate(Pair pair, int distinctStates) {
        int stateCount = Math.max(2, distinctStates);
        for (int mutation = 0; mutation < 512; ++mutation) {
            int index = Math.floorMod(mutation * 977 + 37, SECTION_SIZE);
            BlockState state = STATES.get(Math.floorMod(mutation * 13 + 5, stateCount));
            assertSame(
                    getAndSet(pair.vanilla(), index, state),
                    getAndSet(pair.arena(), index, state),
                    "old state at mutation " + mutation
            );
        }
    }

    private static void assertQueries(
            PalettedContainer<BlockState> vanilla,
            ArenaBlockStatePalettedContainer arena,
            int distinctStates
    ) {
        assertEquals(allStates(vanilla), allStates(arena), "getAll for " + distinctStates);
        assertEquals(counts(vanilla), counts(arena), "count for " + distinctStates);
        for (BlockState state : STATES) {
            boolean expected = vanilla.maybeHas(candidate -> candidate == state);
            boolean actual = arena.maybeHas(candidate -> candidate == state);
            assertEquals(expected, actual, "maybeHas for raw id " + Block.getId(state));
        }
    }

    private static void assertNetworkRoundTrip(
            PalettedContainer<BlockState> vanilla,
            ArenaBlockStatePalettedContainer arena,
            int distinctStates
    ) {
        FriendlyByteBuf vanillaBytes = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf arenaBytes = new FriendlyByteBuf(Unpooled.buffer());
        vanilla.write(vanillaBytes);
        arena.write(arenaBytes);
        assertEquals(arena.getSerializedSize(), arenaBytes.readableBytes(), "arena serialized size");
        assertEquals(vanilla.getSerializedSize(), vanillaBytes.readableBytes(), "vanilla serialized size");

        FriendlyByteBuf vanillaCopy = new FriendlyByteBuf(vanillaBytes.copy());
        FriendlyByteBuf arenaCopy = new FriendlyByteBuf(arenaBytes.copy());
        try {
            ArenaBlockStatePalettedContainer fromVanilla = new ArenaBlockStatePalettedContainer();
            fromVanilla.read(vanillaCopy);
            PalettedContainer<BlockState> fromArena = new PalettedContainer<>(
                    STATES.getFirst(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
            fromArena.read(arenaCopy);
            assertContents(vanilla, fromVanilla, "vanilla to arena " + distinctStates);
            assertContents(fromArena, arena, "arena to vanilla " + distinctStates);
        } finally {
            vanillaCopy.release();
            arenaCopy.release();
            vanillaBytes.release();
            arenaBytes.release();
        }
    }

    private static void assertCopyIsolation(ArenaBlockStatePalettedContainer arena) {
        ArenaBlockStatePalettedContainer copy = (ArenaBlockStatePalettedContainer) arena.copy();
        assertContents(arena, copy, "copy");
        BlockState copyState = copy.get(0, 0, 0);
        BlockState replacement = copyState == STATES.get(1) ? STATES.get(2) : STATES.get(1);
        arena.set(0, 0, 0, replacement);
        assertSame(copyState, copy.get(0, 0, 0), "copy isolation");
    }

    private static void assertContents(
            PalettedContainer<BlockState> expected,
            PalettedContainer<BlockState> actual,
            String stage
    ) {
        for (int index = 0; index < SECTION_SIZE; ++index) {
            assertSame(get(expected, index), get(actual, index), stage + " at " + index);
        }
    }

    private static Set<BlockState> allStates(PalettedContainer<BlockState> container) {
        Set<BlockState> states = new HashSet<>();
        container.getAll(states::add);
        return states;
    }

    private static Map<BlockState, Integer> counts(PalettedContainer<BlockState> container) {
        Map<BlockState, Integer> counts = new HashMap<>();
        container.count(counts::put);
        return counts;
    }

    private static BlockState get(PalettedContainer<BlockState> container, int index) {
        return container.get(index & 15, index >>> 8, index >>> 4 & 15);
    }

    private static void set(PalettedContainer<BlockState> container, int index, BlockState state) {
        container.set(index & 15, index >>> 8, index >>> 4 & 15, state);
    }

    private static BlockState getAndSet(
            PalettedContainer<BlockState> container,
            int index,
            BlockState state
    ) {
        return container.getAndSet(index & 15, index >>> 8, index >>> 4 & 15, state);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected same instance");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private record Pair(
            PalettedContainer<BlockState> vanilla,
            ArenaBlockStatePalettedContainer arena
    ) {
    }
}

package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.HarnessResultFile;
import com.moepus.byepregen.harness.HarnessServerLifecycle;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
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
        MinecraftForge.EVENT_BUS.addListener(ArenaPaletteDifferential::onServerStarted);
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

    private static void assertScenario(int distinctStates) {
        Pair pair = createPair(distinctStates);
        assertContents(pair.vanilla(), pair.arena(), "initial " + distinctStates);
        assertQueries(pair.vanilla(), pair.arena(), distinctStates);
        assertNetworkRoundTrip(pair.vanilla(), pair.arena(), distinctStates);
        mutate(pair, distinctStates);
        assertContents(pair.vanilla(), pair.arena(), "mutated " + distinctStates);
        assertQueries(pair.vanilla(), pair.arena(), distinctStates);
        assertCopyIsolation(pair.arena());
    }

    private static Pair createPair(int distinctStates) {
        PalettedContainer<BlockState> vanilla = new PalettedContainer<>(
                Block.BLOCK_STATE_REGISTRY, STATES.get(0), PalettedContainer.Strategy.SECTION_STATES);
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
        // Vanilla 1.20.1 overcounts the ZeroBitStorage long-array length by one byte.
        assertEquals(vanillaBytes.readableBytes(), arenaBytes.readableBytes(), "network payload size");

        FriendlyByteBuf vanillaCopy = new FriendlyByteBuf(vanillaBytes.copy());
        FriendlyByteBuf arenaCopy = new FriendlyByteBuf(arenaBytes.copy());
        try {
            ArenaBlockStatePalettedContainer fromVanilla = new ArenaBlockStatePalettedContainer();
            fromVanilla.read(vanillaCopy);
            PalettedContainer<BlockState> fromArena = new PalettedContainer<>(
                    Block.BLOCK_STATE_REGISTRY, STATES.get(0), PalettedContainer.Strategy.SECTION_STATES);
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

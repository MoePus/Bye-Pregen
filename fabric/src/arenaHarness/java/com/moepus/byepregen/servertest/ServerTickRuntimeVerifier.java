package com.moepus.byepregen.servertest;

import com.moepus.byepregen.arenatest.HarnessResult;
import com.moepus.byepregen.server.tick.ChunkTickPermutationIterator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ServerTickRuntimeVerifier {
    private ServerTickRuntimeVerifier() { }

    public static void run(MinecraftServer server) {
        HarnessResult.run(server, "byepregen.serverTickHarness.result", () -> verify(server.overworld()));
    }

    public static int verify(ServerLevel level) throws Exception {
        LevelChunk chunk = level.getChunk(-3, 5);
        Field context = field(ServerLevel.class, "byepregen$currentTickChunk");
        Method height = method(ServerLevel.class, "byepregen$getCurrentChunkHeightmapPos");
        level.tickChunk(chunk, 0);
        assertCached(level.getChunkSource(), chunk);
        if (context.get(level) != null) throw new AssertionError("Tick scope leaked");
        context.set(level, chunk);
        int cases;
        try {
            level.tickChunk(level.getChunk(-2, 5), 0);
            if (context.get(level) != chunk) throw new AssertionError("Nested tick scope was not restored");
            cases = verifyHeightmap(level, height, chunk);
        } finally {
            context.set(level, null);
        }
        BlockPos pos = chunk.getPos().getWorldPosition();
        assertHeight(level, height, pos);
        verifyPermutation(level.getChunkSource(), chunk);
        return cases + 1;
    }

    private static int verifyHeightmap(ServerLevel level, Method method, LevelChunk chunk) throws Exception {
        int cases = 0;
        var min = chunk.getPos().getWorldPosition();
        for (int x = -1; x <= 16; x++) {
            for (int z = -1; z <= 16; z++) {
                assertHeight(level, method, min.offset(x, 31, z));
                cases++;
            }
        }
        return cases;
    }

    private static void assertHeight(ServerLevel level, Method method, BlockPos pos) throws Exception {
        var type = Heightmap.Types.MOTION_BLOCKING;
        var expected = level.getHeightmapPos(type, pos);
        var actual = method.invoke(level, level, type, pos);
        if (!expected.equals(actual)) throw new AssertionError("Weather height differs at " + pos);
    }

    private static void assertCached(ServerChunkCache cache, LevelChunk chunk) throws Exception {
        var positions = (long[]) field(ServerChunkCache.class, "lastChunkPos").get(cache);
        var statuses = (ChunkStatus[]) field(ServerChunkCache.class, "lastChunkStatus").get(cache);
        var chunks = (ChunkAccess[]) field(ServerChunkCache.class, "lastChunk").get(cache);
        if (positions[0] != chunk.getPos().pack() || statuses[0] != ChunkStatus.FULL || chunks[0] != chunk
                || positions[1] != chunk.getPos().pack() || statuses[1] != ChunkStatus.BIOMES || chunks[1] != chunk) {
            throw new AssertionError("Chunk cache warmup mismatch");
        }
    }

    private static void verifyPermutation(ServerChunkCache cache, LevelChunk chunk) throws Exception {
        var prepare = method(ServerChunkCache.class, "byepregen$prepareChunkTickPermutation");
        var iterate = method(ServerChunkCache.class, "byepregen$iterateChunkTickPermutation");
        var chunks = List.of(chunk, new Object(), new Object());
        var random = RandomSource.create(7);
        var expected = RandomSource.create(7);
        expected.nextInt(); expected.nextInt(); expected.nextInt();
        prepare.invoke(cache, chunks, random);
        var iterator = (ChunkTickPermutationIterator) iterate.invoke(cache, chunks);
        var visited = new HashSet<>();
        iterator.forEachRemaining(visited::add);
        if (visited.size() != chunks.size() || !visited.containsAll(chunks)
                || random.nextLong() != expected.nextLong()) {
            throw new AssertionError("Transformed permutation mismatch");
        }
    }

    private static Field field(Class<?> type, String suffix) throws Exception {
        Field field = Arrays.stream(type.getDeclaredFields()).filter(f -> f.getName().endsWith(suffix))
                .findFirst().orElseThrow(() -> new NoSuchFieldException(suffix));
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String suffix) throws Exception {
        Method method = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().endsWith(suffix))
                .findFirst().orElseThrow(() -> new NoSuchMethodException(suffix));
        method.setAccessible(true);
        return method;
    }
}

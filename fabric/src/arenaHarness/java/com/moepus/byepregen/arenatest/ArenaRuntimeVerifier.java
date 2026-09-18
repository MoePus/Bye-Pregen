package com.moepus.byepregen.arenatest;

import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.dfctest.DfcRuntimeVerifier;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ArenaRuntimeVerifier {
    private static final int[][] CHUNKS = {{101, -103}, {-107, 109}};
    private static final Map<String, String> TERRAIN = new ConcurrentHashMap<>();
    /** Guards the biome part of the chunk hash: a single biome everywhere means it proved nothing. */
    private static final Set<Integer> SNAPSHOT_BIOMES = ConcurrentHashMap.newKeySet();
    private static final LongAdder ARENA_FILLS = new LongAdder();

    private ArenaRuntimeVerifier() { }

    public static void verify(MinecraftServer server) {
        Path result = Path.of(System.getProperty("byepregen.arenaHarness.result"));
        try {
            TreeMap<String, String> hashes = new TreeMap<>();
            for (ServerLevel level : server.getAllLevels()) {
                verifyLevel(level, hashes);
            }
            com.moepus.byepregen.worldgentest.WorldgenRuntimeVerifier.checkSurfacePaths();
            Files.createDirectories(result.getParent());
            StringBuilder output = new StringBuilder("PASS\n");
            hashes.forEach((key, value) -> output.append(key).append('=').append(value).append('\n'));
            Files.writeString(result, output, StandardCharsets.UTF_8);
            if (!Boolean.getBoolean("byepregen.arenaHarness.reload")) {
                if (ConfigManager.getConfig().worldgen().arena().enabled() && ARENA_FILLS.sum() == 0) {
                    throw new AssertionError("Arena terrain filler did not execute");
                }
                if (ConfigManager.getConfig().worldgen().arena().densityColumnCompiler()
                        && DfcRuntimeVerifier.volumes() + DfcRuntimeVerifier.columns() == 0) {
                    throw new AssertionError("Compiled density volumes did not execute during terrain generation");
                }
                if (ConfigManager.getConfig().worldgen().arena().densityColumnCompiler()
                        && DfcRuntimeVerifier.columns() == 0) {
                    throw new AssertionError("Column density plan did not execute during terrain generation");
                }
                if (TERRAIN.size() != CHUNKS.length * 3) throw new AssertionError("Missing terrain snapshots: " + TERRAIN);
                if (SNAPSHOT_BIOMES.size() < 2) {
                    throw new AssertionError("Chunk snapshots saw " + SNAPSHOT_BIOMES.size()
                            + " distinct biome(s), so the biome hash compares nothing");
                }
                StringBuilder terrain = new StringBuilder("PASS\n");
                new TreeMap<>(TERRAIN).forEach((key, value) -> terrain.append(key).append('=').append(value).append('\n'));
                Files.writeString(result.resolveSibling(result.getFileName() + ".terrain"), terrain);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                Files.writeString(result, "FAIL\n" + failure, StandardCharsets.UTF_8);
            } catch (Exception writeFailure) {
                failure.addSuppressed(writeFailure);
            }
        } finally {
            server.halt(false);
        }
    }

    private static void verifyLevel(ServerLevel level, TreeMap<String, String> hashes) throws Exception {
        var arena = ConfigManager.getConfig().worldgen().arena();
        boolean expectArena = arena.enabled() && arena.runtime().server();
        for (int[] pos : CHUNKS) {
            LevelChunk chunk = level.getChunk(pos[0], pos[1]);
            dumpChunk(level, chunk);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (LevelChunkSection section : chunk.getSections()) {
                boolean actualArena = section.getStates() instanceof ArenaBlockStatePalettedContainer;
                if (actualArena != expectArena) throw new AssertionError("Unexpected storage: " + section.getStates());
                hashSection(digest, section);
            }
            for (Heightmap.Types type : ChunkStatus.FINAL_HEIGHTMAPS) {
                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) addInt(digest, chunk.getHeight(type, x, z));
                }
            }
            hashes.put(level.dimension().toString() + '/' + pos[0] + ',' + pos[1],
                    HexFormat.of().formatHex(digest.digest()));
        }
    }

    public static void recordArenaFill(boolean usedArena) {
        if (usedArena) ARENA_FILLS.increment();
    }

    public static void recordTerrain(String settings, ChunkAccess chunk) {
        boolean selected = false;
        for (int[] pos : CHUNKS) {
            selected |= pos[0] == chunk.getPos().x() && pos[1] == chunk.getPos().z();
        }
        if (!selected) return;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (LevelChunkSection section : chunk.getSections()) hashSection(digest, section);
            for (Heightmap.Types type : new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG}) {
                for (int index = 0; index < 256; ++index) addInt(digest, chunk.getHeight(type, index & 15, index >>> 4));
            }
            for (var positions : chunk.getPostProcessing()) {
                addInt(digest, positions == null ? 0 : positions.size());
                if (positions != null) positions.forEach(value -> addInt(digest, value));
            }
            TERRAIN.put(settings + '/' + chunk.getPos().x() + ',' + chunk.getPos().z(),
                    HexFormat.of().formatHex(digest.digest()));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not capture terrain", failure);
        }
    }

    private static void dumpChunk(ServerLevel level, LevelChunk chunk) throws Exception {
        Path result = Path.of(System.getProperty("byepregen.arenaHarness.result"));
        Path directory = result.resolveSibling(result.getFileName() + ".chunks");
        Files.createDirectories(directory);
        String dimension = level.dimension().identifier().getPath();
        Path file = directory.resolve(dimension + '-' + chunk.getPos().x() + '-' + chunk.getPos().z() + ".bin");
        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(chunk.getMinY());
            output.writeInt(chunk.getSectionsCount());
            for (LevelChunkSection section : chunk.getSections()) {
                for (int index = 0; index < 4096; ++index) {
                    output.writeInt(Block.getId(section.getBlockState(index & 15, index >>> 8, (index >>> 4) & 15)));
                }
            }
        }
        Path states = directory.resolve("states.txt");
        if (!Files.exists(states)) {
            StringBuilder mapping = new StringBuilder();
            for (int id = 0; id < Block.BLOCK_STATE_REGISTRY.size(); ++id) {
                mapping.append(id).append('=').append(Block.stateById(id)).append('\n');
            }
            Files.writeString(states, mapping);
        }
    }

    private static void hashSection(MessageDigest digest, LevelChunkSection section) {
        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) addInt(digest, Block.getId(section.getBlockState(x, y, z)));
            }
        }
        // Biome palettes are not covered by the block states, so a DFC regression that shifted biome
        // edges would otherwise go unnoticed by the baseline/optimized terrain comparison.
        for (int y = 0; y < 4; ++y) {
            for (int z = 0; z < 4; ++z) {
                for (int x = 0; x < 4; ++x) {
                    int biome = biomeId(section.getNoiseBiome(x, y, z));
                    SNAPSHOT_BIOMES.add(biome);
                    addInt(digest, biome);
                }
            }
        }
    }

    /** Registry identity, so the hash stays comparable between runs. */
    private static int biomeId(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier().toString().hashCode()).orElse(0);
    }

    private static void addInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}

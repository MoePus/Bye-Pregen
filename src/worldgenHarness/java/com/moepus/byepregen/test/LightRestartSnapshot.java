package com.moepus.byepregen.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

record LightRestartSnapshot(List<LightRestartSnapshot.Sample> samples, List<LightRestartSnapshot.ChunkDigest> digests) {
    private static final String FORMAT_HEADER = "# YA light restart snapshot v2";

    static LightRestartSnapshot capture(ServerLevel level) {
        LevelLightEngine engine = level.getChunkSource().getLightEngine();
        List<Sample> samples = captureSamples(engine);
        List<ChunkDigest> digests = new ArrayList<>();
        for (int z = -LightRestartProbe.LOAD_RADIUS; z <= LightRestartProbe.LOAD_RADIUS; ++z) {
            for (int x = -LightRestartProbe.LOAD_RADIUS; x <= LightRestartProbe.LOAD_RADIUS; ++x) {
                digests.add(digestChunk(engine, new ChunkPos(x, z)));
            }
        }
        return new LightRestartSnapshot(List.copyOf(samples), List.copyOf(digests));
    }

    private static List<Sample> captureSamples(LevelLightEngine engine) {
        List<Sample> samples = new ArrayList<>();
        LayerLightEventListener sky = engine.getLayerListener(LightLayer.SKY);
        for (BlockPos pos : probePositions()) {
            int server = sky.getLightValue(pos);
            int packet = packetSkyLight(engine, pos);
            samples.add(new Sample(pos.getX(), pos.getY(), pos.getZ(), server, packet));
        }
        return samples;
    }

    private static Set<BlockPos> probePositions() {
        Set<BlockPos> positions = new LinkedHashSet<>();
        addColumn(positions, new BlockPos(8, 0, 8), new int[]{80, 89, 94, 96, 111, 112});
        addColumn(positions, new BlockPos(15, 0, 8), new int[]{80, 89, 94});
        addColumn(positions, new BlockPos(16, 0, 8), new int[]{80, 89, 94});
        addColumn(positions, new BlockPos(8, 0, 24), new int[]{80, 94, 95, 97, 111, 112});
        positions.add(LightRestartProbe.OPENING.below());
        for (int x = LightRestartProbe.OPENING.getX(); x <= LightRestartProbe.OPENING.getX() + 32; ++x) {
            positions.add(new BlockPos(x, LightRestartProbe.CAVE_Y, LightRestartProbe.OPENING.getZ()));
        }
        return positions;
    }

    private static void addColumn(Set<BlockPos> positions, BlockPos column, int[] heights) {
        for (int y : heights) {
            positions.add(new BlockPos(column.getX(), y, column.getZ()));
        }
    }

    private static ChunkDigest digestChunk(LevelLightEngine engine, ChunkPos chunkPos) {
        MessageDigest digest = sha256();
        LayerLightEventListener sky = engine.getLayerListener(LightLayer.SKY);
        ClientboundLightUpdatePacketData packet = new ClientboundLightUpdatePacketData(chunkPos, engine, null, null);
        byte[][] packetSections = packetSections(engine.getLightSectionCount(), packet);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long zero = 0;
        long full = 0;
        long intermediate = 0;
        int minY = engine.getMinLightSection() << 4;
        int maxY = (engine.getMinLightSection() + engine.getLightSectionCount()) << 4;
        for (int y = minY; y < maxY; ++y) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); ++z) {
                for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); ++x) {
                    pos.set(x, y, z);
                    int server = sky.getLightValue(pos);
                    int packetValue = packetSkyLight(engine, packetSections, pos);
                    if (server != packetValue) {
                        throw new IllegalStateException("Packet mismatch at " + pos
                                + " server=" + server + " packet=" + packetValue);
                    }
                    digest.update((byte)server);
                    zero += server == 0 ? 1 : 0;
                    full += server == 15 ? 1 : 0;
                    intermediate += server > 0 && server < 15 ? 1 : 0;
                }
            }
        }
        return new ChunkDigest(chunkPos.x, chunkPos.z, HexFormat.of().formatHex(digest.digest()),
                zero, full, intermediate);
    }

    static int packetSkyLight(LevelLightEngine engine, BlockPos pos) {
        ClientboundLightUpdatePacketData packet = new ClientboundLightUpdatePacketData(
                new ChunkPos(pos), engine, null, null);
        return packetSkyLight(engine, packetSections(engine.getLightSectionCount(), packet), pos);
    }

    private static int packetSkyLight(LevelLightEngine engine, byte[][] sections, BlockPos pos) {
        int index = (pos.getY() >> 4) - engine.getMinLightSection();
        int readY = pos.getY();
        if (index < 0) {
            index = 0;
            readY = 0;
        }
        while (index < sections.length) {
            byte[] data = sections[index++];
            if (data != null) {
                return data.length == 0 ? 0 : nibble(data, pos.atY(readY));
            }
            readY = 0;
        }
        return 15;
    }

    private static byte[][] packetSections(int count, ClientboundLightUpdatePacketData packet) {
        byte[][] sections = new byte[count][];
        BitSet updates = packet.getSkyYMask();
        BitSet empty = packet.getEmptySkyYMask();
        int updateIndex = 0;
        for (int i = 0; i < count; ++i) {
            if (updates.get(i)) {
                sections[i] = packet.getSkyUpdates().get(updateIndex++);
            } else if (empty.get(i)) {
                sections[i] = new byte[0];
            }
        }
        return sections;
    }

    private static int nibble(byte[] data, BlockPos pos) {
        int index = (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | pos.getX() & 15;
        int value = data[index >>> 1] & 255;
        return value >>> ((index & 1) << 2) & 15;
    }

    void write(Path path) throws IOException {
        List<String> lines = new ArrayList<>(this.samples.size() + this.digests.size() + 1);
        lines.add(FORMAT_HEADER);
        this.samples.forEach(sample -> lines.add(sample.encode()));
        this.digests.forEach(digest -> lines.add(digest.encode()));
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    static LightRestartSnapshot read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !FORMAT_HEADER.equals(lines.get(0))) {
            throw new IOException("Unsupported light restart snapshot: " + path);
        }
        List<Sample> samples = new ArrayList<>();
        List<ChunkDigest> digests = new ArrayList<>();
        for (int i = 1; i < lines.size(); ++i) {
            String line = lines.get(i);
            if (line.startsWith("S,")) {
                samples.add(Sample.parse(line));
            } else if (line.startsWith("D,")) {
                digests.add(ChunkDigest.parse(line));
            } else {
                throw new IOException("Invalid light restart snapshot line: " + line);
            }
        }
        return new LightRestartSnapshot(List.copyOf(samples), List.copyOf(digests));
    }

    String firstDifference(LightRestartSnapshot actual) {
        String sampleDifference = firstDifference(this.samples, actual.samples, "sample");
        return sampleDifference != null ? sampleDifference
                : firstDifference(this.digests, actual.digests, "chunk digest");
    }

    private static String firstDifference(List<?> expected, List<?> actual, String label) {
        int count = Math.min(expected.size(), actual.size());
        for (int i = 0; i < count; ++i) {
            if (!expected.get(i).equals(actual.get(i))) {
                return "Restart " + label + " mismatch expected=" + expected.get(i) + " actual=" + actual.get(i);
            }
        }
        return expected.size() == actual.size() ? null
                : "Restart " + label + " count mismatch expected=" + expected.size() + " actual=" + actual.size();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record Sample(int x, int y, int z, int server, int packet) {
        private String encode() {
            return "S," + this.x + "," + this.y + "," + this.z + "," + this.server + "," + this.packet;
        }

        private static Sample parse(String value) {
            String[] parts = value.split(",");
            if (parts.length != 6) {
                throw new IllegalArgumentException("Invalid light restart sample: " + value);
            }
            return new Sample(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
        }
    }

    record ChunkDigest(int x, int z, String hash, long zero, long full, long intermediate) {
        private String encode() {
            return "D," + this.x + "," + this.z + "," + this.hash + ","
                    + this.zero + "," + this.full + "," + this.intermediate;
        }

        private static ChunkDigest parse(String value) {
            String[] parts = value.split(",");
            if (parts.length != 7) {
                throw new IllegalArgumentException("Invalid light restart digest: " + value);
            }
            return new ChunkDigest(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3],
                    Long.parseLong(parts[4]), Long.parseLong(parts[5]), Long.parseLong(parts[6]));
        }
    }
}

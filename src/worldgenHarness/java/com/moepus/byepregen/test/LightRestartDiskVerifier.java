package com.moepus.byepregen.test;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

public final class LightRestartDiskVerifier {
    private static final int LIGHT_BYTES = 2048;
    private static final NibbleExpectation BOUNDARY_ROOF_BOTTOM =
            new NibbleExpectation(nibbleIndex(8, 0, 8), 0, "boundary roof bottom");
    private static final NibbleExpectation BOUNDARY_ROOF_TOP =
            new NibbleExpectation(nibbleIndex(8, 1, 8), 15, "boundary roof top");
    private static final RegionStorageInfo REGION_INFO =
            new RegionStorageInfo("light-restart-disk", Level.OVERWORLD, "chunk");

    private LightRestartDiskVerifier() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <world-dir> <relight-is-light-on>");
        }
        Path worldDir = Path.of(args[0]).toAbsolutePath().normalize();
        boolean relightLightOn = Boolean.parseBoolean(args[1]);
        verifyRoofChunk(worldDir, LightRestartProbe.CENTER, true);
        verifyRoofChunk(worldDir, LightRestartProbe.RELIGHT, relightLightOn);
        verifyBoundaryChunk(worldDir);
        verifyMixedChunk(worldDir);
        System.out.println("Light restart disk fixture passed: world=" + worldDir
                + " relightLightOn=" + relightLightOn);
    }

    private static void verifyRoofChunk(Path worldDir, ChunkPos pos, boolean expectedLightOn) throws IOException {
        CompoundTag tag = readChunk(worldDir, pos);
        assertLightOn(tag, pos, expectedLightOn);
        assertSkyKind(tag, LightRestartProbe.ROOF_Y >> 4, SkyKind.MISSING);
        SkyKind above = skyKind(tag, (LightRestartProbe.ROOF_Y >> 4) + 1);
        if (above != SkyKind.MISSING && above != SkyKind.FULL) {
            throw new IllegalStateException("Expected missing/full skylight above roof at " + pos + ", got " + above);
        }
    }

    private static void verifyBoundaryChunk(Path worldDir) throws IOException {
        ChunkPos pos = LightRestartProbe.BOUNDARY;
        CompoundTag tag = readChunk(worldDir, pos);
        assertLightOn(tag, pos, true);
        int caveSection = LightRestartProbe.ROOF_Y >> 4;
        int roofSection = LightRestartProbe.BOUNDARY_ROOF_Y >> 4;
        assertSkyKind(tag, caveSection, SkyKind.MISSING);
        assertSkyKind(tag, roofSection, SkyKind.MIXED);
        byte[] sky = skyBytes(tag, roofSection);
        assertNibble(sky, BOUNDARY_ROOF_BOTTOM);
        assertNibble(sky, BOUNDARY_ROOF_TOP);
    }

    private static void verifyMixedChunk(Path worldDir) throws IOException {
        ChunkPos pos = LightRestartProbe.MIXED;
        CompoundTag tag = readChunk(worldDir, pos);
        assertLightOn(tag, pos, true);
        assertSkyKind(tag, LightRestartProbe.ROOF_Y >> 4, SkyKind.MIXED);
    }

    private static void assertLightOn(CompoundTag tag, ChunkPos pos, boolean expected) {
        boolean actual = tag.getBoolean("isLightOn");
        if (actual != expected) {
            throw new IllegalStateException("isLightOn mismatch at " + pos
                    + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertSkyKind(CompoundTag tag, int sectionY, SkyKind expected) {
        SkyKind actual = skyKind(tag, sectionY);
        if (actual != expected) {
            ChunkPos pos = new ChunkPos(tag.getInt("xPos"), tag.getInt("zPos"));
            throw new IllegalStateException("SkyLight kind mismatch at " + pos + " section=" + sectionY
                    + " expected=" + expected + " actual=" + actual);
        }
    }

    private static SkyKind skyKind(CompoundTag tag, int sectionY) {
        byte[] sky = skyBytes(tag, sectionY);
        if (sky == null) {
            return SkyKind.MISSING;
        }
        boolean zero = true;
        boolean full = true;
        for (byte value : sky) {
            zero &= value == 0;
            full &= value == (byte)-1;
        }
        if (zero) {
            return SkyKind.ZERO;
        }
        return full ? SkyKind.FULL : SkyKind.MIXED;
    }

    private static byte[] skyBytes(CompoundTag tag, int sectionY) {
        ListTag sections = tag.getList("sections", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < sections.size(); ++i) {
            CompoundTag section = sections.getCompound(i);
            if (section.getByte("Y") == (byte)sectionY
                    && section.contains("SkyLight", CompoundTag.TAG_BYTE_ARRAY)) {
                byte[] sky = section.getByteArray("SkyLight");
                if (sky.length != LIGHT_BYTES) {
                    throw new IllegalStateException("Invalid SkyLight length at section " + sectionY + ": " + sky.length);
                }
                return sky;
            }
        }
        return null;
    }

    private static void assertNibble(byte[] data, NibbleExpectation expectation) {
        int packed = data[expectation.index() >>> 1] & 255;
        int actual = packed >>> ((expectation.index() & 1) << 2) & 15;
        if (actual != expectation.value()) {
            throw new IllegalStateException(expectation.description()
                    + " expected=" + expectation.value() + " actual=" + actual);
        }
    }

    private static int nibbleIndex(int localX, int localY, int localZ) {
        return localY << 8 | localZ << 4 | localX;
    }

    private static CompoundTag readChunk(Path worldDir, ChunkPos pos) throws IOException {
        int regionX = Math.floorDiv(pos.x, 32);
        int regionZ = Math.floorDiv(pos.z, 32);
        Path regionDir = worldDir.resolve("region");
        Path regionPath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
        if (!Files.isRegularFile(regionPath)) {
            throw new IOException("Missing region file: " + regionPath);
        }
        try (RegionFile region = new RegionFile(REGION_INFO, regionPath, regionDir, false);
             DataInputStream input = region.getChunkDataInputStream(pos)) {
            if (input == null) {
                throw new IOException("Missing chunk " + pos + " in " + regionPath);
            }
            return NbtIo.read(input, NbtAccounter.unlimitedHeap());
        }
    }

    private enum SkyKind {
        MISSING,
        ZERO,
        FULL,
        MIXED
    }

    private record NibbleExpectation(int index, int value, String description) {
    }
}

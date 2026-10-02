package com.moepus.byepregen.test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** Test-only fault injection after clean baseline capture and server shutdown. */
public final class LightRestartInvalidDataFixture {
    private static final int LIGHT_BYTES = 2048;
    private static final RegionStorageInfo REGION_INFO =
            new RegionStorageInfo("light-restart-invalid", Level.OVERWORLD, "chunk");

    private LightRestartInvalidDataFixture() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected <disposable-restart-world>");
        }
        install(Path.of(args[0]));
    }

    private static void install(Path world) throws IOException {
        ChunkPos pos = LightRestartProbe.RELIGHT;
        Path directory = world.resolve("region");
        Path file = directory.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Missing restart fixture region: " + file);
        }
        try (RegionFile region = new RegionFile(REGION_INFO, file, directory, false)) {
            CompoundTag chunk;
            try (DataInputStream input = region.getChunkDataInputStream(pos)) {
                if (input == null) {
                    throw new IOException("Missing invalid-light fixture: " + pos);
                }
                chunk = NbtIo.read(input, NbtAccounter.unlimitedHeap());
            }
            if (chunk.getBoolean("isLightOn")) {
                throw new IllegalStateException("Expected invalid fixture before light fault injection");
            }
            poison(chunk);
            try (DataOutputStream output = region.getChunkDataOutputStream(pos)) {
                NbtIo.write(chunk, output);
            }
        }
        System.out.println("Installed invalid BlockLight/SkyLight: interior high, full high, stale zero; chunk=" + pos);
    }

    private static void poison(CompoundTag chunk) {
        ListTag sections = chunk.getList("sections", CompoundTag.TAG_COMPOUND);
        byte[] high = new byte[LIGHT_BYTES];
        Arrays.fill(high, (byte)-1);
        // Enclosed interior errors cannot be found by a chunk-border scan.
        byte[] island = new byte[LIGHT_BYTES];
        for (int y = 6; y <= 9; ++y) {
            for (int z = 6; z <= 9; ++z) {
                Arrays.fill(island, (y << 7) + (z << 3) + 3, (y << 7) + (z << 3) + 5, (byte)-1);
            }
        }
        for (String layer : new String[]{"BlockLight", "SkyLight"}) {
            section(sections, 3).putByteArray(layer, island.clone());
            section(sections, 4).putByteArray(layer, high.clone());
        }
        // Real local emitter at y=88 and direct sky at y=112 must be rebuilt too.
        section(sections, 5).putByteArray("BlockLight", new byte[LIGHT_BYTES]);
        section(sections, 7).putByteArray("SkyLight", new byte[LIGHT_BYTES]);
    }

    private static CompoundTag section(ListTag sections, int sectionY) {
        for (int i = 0; i < sections.size(); ++i) {
            CompoundTag section = sections.getCompound(i);
            if (section.getByte("Y") == sectionY) {
                return section;
            }
        }
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte)sectionY);
        sections.add(section);
        return section;
    }
}

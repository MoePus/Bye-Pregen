package com.moepus.byepregen.test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class LightGoldenPrepareRelight {
    private static final RegionStorageInfo REGION_INFO = new RegionStorageInfo("light-golden-prepare-relight", Level.OVERWORLD, "chunk");
    static final String CHUNK_LIST_FILE = "byepregen-light-golden-chunks.txt";
    private static final double RELIGHT_RADIUS = Double.parseDouble(System.getProperty(
            "byepregen.lightGolden.prepareRadius",
            Double.toString(Double.POSITIVE_INFINITY)
    ));
    private static final int RELIGHT_CHUNK_RADIUS = Double.isFinite(RELIGHT_RADIUS)
            ? (int)Math.ceil(RELIGHT_RADIUS / 16.0D)
            : Integer.MAX_VALUE;

    private LightGoldenPrepareRelight() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: LightGoldenPrepareRelight <source-world-dir> <vanilla-world-dir> <ya-world-dir>");
            System.exit(2);
        }

        Path sourceWorld = Paths.get(args[0]).toAbsolutePath().normalize();
        Path vanillaWorld = Paths.get(args[1]).toAbsolutePath().normalize();
        Path yaWorld = Paths.get(args[2]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceWorld)) {
            throw new IOException("Source world directory does not exist: " + sourceWorld);
        }

        copyWorldForRelight(sourceWorld, vanillaWorld);
        copyWorldForRelight(sourceWorld, yaWorld);
        System.out.println("Prepared relight worlds from " + sourceWorld);
        System.out.println("  vanilla: " + vanillaWorld);
        System.out.println("  ya:      " + yaWorld);
    }

    private static void copyWorldForRelight(Path sourceWorld, Path targetWorld) throws IOException {
        deleteTree(targetWorld);
        copyTree(sourceWorld, targetWorld);
        stripLightData(targetWorld);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void copyTree(Path sourceRoot, Path targetRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path source : stream.toList()) {
                Path target = targetRoot.resolve(sourceRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void stripLightData(Path worldDir) throws IOException {
        Path chunkList = worldDir.resolve(CHUNK_LIST_FILE);
        try (Stream<Path> stream = Files.walk(worldDir, 6);
             BufferedWriter writer = Files.newBufferedWriter(chunkList)) {
            int chunks = 0;
            int relightChunks = 0;
            for (Path region : stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .filter(path -> path.getParent() != null && path.getParent().getFileName().toString().equals("region"))
                    .toList()) {
                StripCounts counts = stripRegion(region, writer);
                chunks += counts.stripped;
                relightChunks += counts.relightListed;
            }
            System.out.println("Stripped saved light from " + chunks + " chunk(s) in " + worldDir);
            System.out.println("Wrote " + relightChunks + " relight target chunk(s) to " + chunkList);
        }
    }

    private static StripCounts stripRegion(Path regionPath, BufferedWriter chunkList) throws IOException {
        RegionCoords region = RegionCoords.parse(regionPath.getFileName().toString());
        int chunks = 0;
        int relightChunks = 0;
        try (RegionFile regionFile = new RegionFile(REGION_INFO, regionPath, regionPath.getParent(), false)) {
            for (int localZ = 0; localZ < 32; ++localZ) {
                for (int localX = 0; localX < 32; ++localX) {
                    ChunkPos pos = new ChunkPos(region.x * 32 + localX, region.z * 32 + localZ);
                    CompoundTag chunkTag;
                    try (DataInputStream input = regionFile.getChunkDataInputStream(pos)) {
                        if (input == null) {
                            continue;
                        }
                        chunkTag = NbtIo.read(input, NbtAccounter.unlimitedHeap());
                    }
                    stripChunkLight(chunkTag);
                    try (DataOutputStream output = regionFile.getChunkDataOutputStream(pos)) {
                        NbtIo.write(chunkTag, output);
                    }
                    if (shouldRelight(pos)) {
                        chunkList.write(Integer.toString(pos.x));
                        chunkList.write(' ');
                        chunkList.write(Integer.toString(pos.z));
                        chunkList.newLine();
                        ++relightChunks;
                    }
                    ++chunks;
                }
            }
        }
        return new StripCounts(chunks, relightChunks);
    }

    private static boolean shouldRelight(ChunkPos pos) {
        return Math.abs(pos.x) <= RELIGHT_CHUNK_RADIUS && Math.abs(pos.z) <= RELIGHT_CHUNK_RADIUS;
    }

    private static void stripChunkLight(CompoundTag chunkTag) {
        // INITIALIZE_LIGHT owns sky-source initialization and non-empty section
        // registration. Rewind to its parent so vanilla and YA both rerun that
        // stage before the LIGHT task recomputes saved nibbles.
        chunkTag.putString("Status", "minecraft:features");
        chunkTag.remove("isLightOn");
        ListTag sections = chunkTag.getList("sections", 10);
        for (int i = 0; i < sections.size(); ++i) {
            CompoundTag section = sections.getCompound(i);
            section.remove("BlockLight");
            section.remove("SkyLight");
        }
    }

    private record RegionCoords(int x, int z) {
        static RegionCoords parse(String fileName) throws IOException {
            if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) {
                throw new IOException("Invalid region file name: " + fileName);
            }
            String[] parts = fileName.substring(2, fileName.length() - 4).split("\\.");
            if (parts.length != 2) {
                throw new IOException("Invalid region file name: " + fileName);
            }
            return new RegionCoords(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
    }

    private record StripCounts(int stripped, int relightListed) {
    }
}

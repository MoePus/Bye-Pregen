package com.moepus.byepregen.harness;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record RegionCoordinates(int x, int z) {
    private static final Pattern FILE_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    public static final int CHUNKS_PER_AXIS = 32;

    public static RegionCoordinates parse(String fileName) throws IOException {
        Matcher matcher = FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            throw new IOException("Invalid region file name: " + fileName);
        }
        try {
            return new RegionCoordinates(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            );
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid region coordinates: " + fileName, exception);
        }
    }

    public static boolean isRegionFileName(String fileName) {
        return FILE_NAME.matcher(fileName).matches();
    }

    public ChunkKey chunkAt(int localX, int localZ) {
        return new ChunkKey(
                this.x * CHUNKS_PER_AXIS + localX,
                this.z * CHUNKS_PER_AXIS + localZ
        );
    }
}

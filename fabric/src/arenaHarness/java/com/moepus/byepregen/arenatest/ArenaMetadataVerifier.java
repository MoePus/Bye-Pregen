package com.moepus.byepregen.arenatest;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ArenaMetadataVerifier {
    private static final Field[] COUNTS = fields();
    private static final EnumSet<Heightmap.Types> TYPES = EnumSet.of(
            Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);

    private ArenaMetadataVerifier() { }

    public static void verify(ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            short[] actual = counts(section);
            section.recalcBlockCounts();
            if (!Arrays.equals(actual, counts(section))) throw new AssertionError("Arena section counts differ from native recount");
        }
        long[] surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).getRawData().clone();
        long[] floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).getRawData().clone();
        Heightmap.primeHeightmaps(chunk, TYPES);
        if (!Arrays.equals(surface, chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).getRawData())
                || !Arrays.equals(floor, chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).getRawData())) {
            throw new AssertionError("Arena fill heightmaps differ from native scan");
        }
    }

    public static short[] counts(LevelChunkSection section) {
        try {
            short[] result = new short[COUNTS.length];
            for (int i = 0; i < result.length; i++) result[i] = COUNTS[i].getShort(section);
            return result;
        } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }

    private static Field[] fields() {
        String[] names = {"nonEmptyBlockCount", "fluidCount", "tickingBlockCount", "tickingFluidCount"};
        Field[] fields = new Field[names.length];
        try {
            for (int i = 0; i < names.length; i++) {
                fields[i] = LevelChunkSection.class.getDeclaredField(names[i]);
                fields[i].setAccessible(true);
            }
            return fields;
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
}

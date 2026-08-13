package com.moepus.byepregen.test;

import com.moepus.byepregen.chunksave.serialize.GcFreeChunkSerializer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.server.level.ServerLevel;

record LightTorchNbtProbe(int source, int adjacent, int distant, int nonZero) {
    static LightTorchNbtProbe capture(ServerLevel level, ChunkAccess chunk, BlockPos source) {
        byte[] raw = GcFreeChunkSerializer.serializeRaw(level, chunk);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
            CompoundTag root = NbtIo.read(input);
            return fromRoot(root, source);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect GC-free chunk NBT", exception);
        }
    }

    boolean hasExpectedTorch() {
        return this.source == 14 && this.adjacent == 13 && this.distant == 12;
    }

    private static LightTorchNbtProbe fromRoot(CompoundTag root, BlockPos source) {
        ListTag sections = root.getList("sections", 10);
        int sectionY = source.getY() >> 4;
        for (int i = 0; i < sections.size(); ++i) {
            CompoundTag section = sections.getCompound(i);
            if (section.getByte("Y") == (byte)sectionY) {
                return fromBytes(section.getByteArray("BlockLight"), source);
            }
        }
        return new LightTorchNbtProbe(0, 0, 0, 0);
    }

    private static LightTorchNbtProbe fromBytes(byte[] data, BlockPos source) {
        int localY = source.getY() & 15;
        int sourceIndex = localY << 8 | (source.getZ() & 15) << 4 | source.getX() & 15;
        int adjacentIndex = localY << 8 | (source.getZ() & 15) << 4 | source.east().getX() & 15;
        int distantIndex = localY << 8 | (source.getZ() & 15) << 4 | source.east(2).getX() & 15;
        int nonZero = 0;
        for (int index = 0; index < 4096; ++index) {
            nonZero += nibble(data, index) == 0 ? 0 : 1;
        }
        return new LightTorchNbtProbe(
                nibble(data, sourceIndex), nibble(data, adjacentIndex), nibble(data, distantIndex), nonZero);
    }

    private static int nibble(byte[] data, int index) {
        if (data.length != 2048) {
            return 0;
        }
        int packed = data[index >>> 1] & 255;
        return packed >>> ((index & 1) << 2) & 15;
    }
}

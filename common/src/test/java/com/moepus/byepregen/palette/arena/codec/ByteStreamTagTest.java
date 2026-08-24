package com.moepus.byepregen.palette.arena.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

public final class ByteStreamTagTest {
    private ByteStreamTagTest() {
    }

    @Test
    void materializesUniformPayloadAsVanillaNbt() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int rawId = BuiltInRegistries.BLOCK.getId(Blocks.STONE);
        CompoundTag blockStates = ByteStreamTag.uniform(rawId);
        ListTag palette = blockStates.getListOrEmpty("palette");
        CompoundTag entry = palette.getCompound(0).orElseThrow();

        if (palette.size() != 1) {
            throw new AssertionError("uniform palette should have one entry");
        }
        if (!"minecraft:stone".equals(entry.getStringOr("Name", ""))) {
            throw new AssertionError("uniform palette state did not survive materialization");
        }
        if (blockStates.contains("data")) {
            throw new AssertionError("uniform palette should not contain packed data");
        }
    }

    @Test
    void writesAndCopiesUniformPayloadWithoutMaterializing() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int rawId = BuiltInRegistries.BLOCK.getId(Blocks.STONE);
        byte[] expected = PayloadBuilder.uniform(rawId);
        ByteStreamTag original = ByteStreamTag.uniform(rawId);
        CompoundTag copy = original.copy();

        assertInstanceOf(ByteStreamTag.class, copy);
        assertArrayEquals(expected, writePayload(original));
        assertArrayEquals(expected, writePayload(copy));
    }

    private static byte[] writePayload(CompoundTag tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        tag.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
}

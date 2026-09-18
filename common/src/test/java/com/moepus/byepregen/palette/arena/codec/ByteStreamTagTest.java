package com.moepus.byepregen.palette.arena.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

        if (palette.size() != 1) {
            throw new AssertionError("uniform palette should have one entry");
        }
        // A default state is the bare block name, which is what BlockState.CODEC encodes.
        assertInstanceOf(StringTag.class, palette.get(0));
        if (!"minecraft:stone".equals(((StringTag) palette.get(0)).value())) {
            throw new AssertionError("uniform palette state did not survive materialization");
        }
        if (blockStates.contains("data")) {
            throw new AssertionError("uniform palette should not contain packed data");
        }
    }

    @Test
    void materializesNonDefaultStateAsFullCompound() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BlockState state = Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        CompoundTag blockStates = ByteStreamTag.uniform(Block.BLOCK_STATE_REGISTRY.getId(state));
        ListTag palette = blockStates.getListOrEmpty("palette");
        CompoundTag entry = palette.getCompound(0).orElseThrow();

        assertEquals("minecraft:oak_log", entry.getStringOr("id", ""));
        assertEquals("x", entry.getCompoundOrEmpty("properties").getStringOr("axis", ""));
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
        assertTrue(expected.length > 0);
    }

    private static byte[] writePayload(CompoundTag tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        tag.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
}

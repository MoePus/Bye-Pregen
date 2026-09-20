package com.moepus.byepregen.test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockStateNbtNameTest {
    @Test
    void compactAndCompoundStatesCompareEqually() {
        CompoundTag full = new CompoundTag();
        full.putString("id", "minecraft:stone");
        assertEquals("minecraft:stone", BlockStateNbtName.describe(StringTag.valueOf("minecraft:stone")));
        assertEquals("minecraft:stone", BlockStateNbtName.describe(full));
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("", StringTag.valueOf("minecraft:stone"));
        assertEquals("minecraft:stone", BlockStateNbtName.describe(wrapper));
    }

    @Test
    void propertiesAreCanonicalAcrossFormatsAndInsertionOrder() {
        CompoundTag properties = new CompoundTag();
        properties.putString("waterlogged", "false");
        properties.putString("type", "bottom");
        CompoundTag modern = new CompoundTag();
        modern.putString("id", "minecraft:stone_slab");
        modern.put("properties", properties);
        CompoundTag legacy = new CompoundTag();
        legacy.putString("Name", "minecraft:stone_slab");
        legacy.put("Properties", properties);
        assertEquals("minecraft:stone_slab[type=bottom,waterlogged=false]", BlockStateNbtName.describe(modern));
        assertEquals(BlockStateNbtName.describe(modern), BlockStateNbtName.describe(legacy));
    }

    @Test
    void malformedStatesFailInsteadOfMakingDifferentTerrainLookEqual() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateNbtName.describe(IntTag.valueOf(1)));
        assertThrows(IllegalArgumentException.class, () -> BlockStateNbtName.describe(new CompoundTag()));
    }
}

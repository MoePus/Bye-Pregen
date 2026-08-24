package com.moepus.byepregen.mixin.nbt;

import com.moepus.byepregen.mixin.accessor.nbt.CompoundTagAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.io.DataInput;
import java.io.IOException;
import java.util.Map;

@Mixin(targets = "net.minecraft.nbt.CompoundTag$1")
public abstract class CompoundTagLoadSizingMixin {
    private static final long COMPOUND_OVERHEAD_BYTES = 48L;
    private static final long STRING_OVERHEAD_BYTES = 28L;
    private static final long STRING_CHARACTER_BYTES = 2L;
    private static final long UNIQUE_ENTRY_BYTES = 36L;
    private static final int INLINE_ENTRY_LIMIT = 4;

    /**
     * @author MoePus
     * @reason Avoid eager hash-map backing arrays for tiny NBT compounds while keeping larger compounds on fast hash maps.
     */
    @Overwrite
    private static CompoundTag loadCompound(DataInput input, NbtAccounter accounter) throws IOException {
        accounter.accountBytes(COMPOUND_OVERHEAD_BYTES);
        String k0 = null;
        String k1 = null;
        String k2 = null;
        String k3 = null;
        Tag v0 = null;
        Tag v1 = null;
        Tag v2 = null;
        Tag v3 = null;
        int size = 0;
        Object2ObjectOpenHashMap<String, Tag> overflow = null;

        while (true) {
            byte type = input.readByte();
            if (type == 0) {
                if (overflow != null) {
                    return CompoundTagAccessor.byepregen$new(overflow);
                }
                Object2ObjectArrayMap<String, Tag> tags = new Object2ObjectArrayMap<>(size);
                if (size > 0) tags.put(k0, v0);
                if (size > 1) tags.put(k1, v1);
                if (size > 2) tags.put(k2, v2);
                if (size > 3) tags.put(k3, v3);
                return CompoundTagAccessor.byepregen$new(tags);
            }

            String name = input.readUTF();
            accounter.accountBytes(STRING_OVERHEAD_BYTES);
            accounter.accountBytes(STRING_CHARACTER_BYTES, name.length());
            Tag tag = CompoundTagAccessor.byepregen$readNamedTagData(
                    TagTypes.getType(type), name, input, accounter
            );

            if (overflow != null) {
                byepregen$put(overflow, name, tag, accounter);
            } else if (size < INLINE_ENTRY_LIMIT) {
                if (byepregen$isUniqueInlineName(size, name, k0, k1, k2)) {
                    accounter.accountBytes(UNIQUE_ENTRY_BYTES);
                }
                switch (size) {
                    case 0 -> {
                        k0 = name;
                        v0 = tag;
                    }
                    case 1 -> {
                        k1 = name;
                        v1 = tag;
                    }
                    case 2 -> {
                        k2 = name;
                        v2 = tag;
                    }
                    default -> {
                        k3 = name;
                        v3 = tag;
                    }
                }
            } else {
                overflow = new Object2ObjectOpenHashMap<>(8);
                overflow.put(k0, v0);
                overflow.put(k1, v1);
                overflow.put(k2, v2);
                overflow.put(k3, v3);
                byepregen$put(overflow, name, tag, accounter);
            }
            size++;
        }
    }

    @Unique
    private static boolean byepregen$isUniqueInlineName(int size, String name, String k0, String k1, String k2) {
        return size == 0
                || !name.equals(k0)
                && (size == 1 || !name.equals(k1))
                && (size == 2 || !name.equals(k2));
    }

    @Unique
    private static void byepregen$put(Map<String, Tag> tags, String name, Tag tag, NbtAccounter accounter) {
        if (tags.put(name, tag) == null) {
            accounter.accountBytes(UNIQUE_ENTRY_BYTES);
        }
    }
}

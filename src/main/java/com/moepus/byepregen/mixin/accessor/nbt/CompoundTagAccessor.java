package com.moepus.byepregen.mixin.accessor.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.DataInput;
import java.io.IOException;
import java.util.Map;

@Mixin(CompoundTag.class)
public interface CompoundTagAccessor {
    @Invoker("<init>")
    static CompoundTag byepregen$new(Map<String, Tag> tags) {
        throw new AssertionError();
    }

    @Invoker("readNamedTagData")
    static Tag byepregen$readNamedTagData(
            TagType<?> type,
            String name,
            DataInput input,
            NbtAccounter accounter
    ) throws IOException {
        throw new AssertionError();
    }
}

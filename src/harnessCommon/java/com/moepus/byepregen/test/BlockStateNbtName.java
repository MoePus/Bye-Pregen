package com.moepus.byepregen.test;

import java.util.StringJoiner;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Canonical state names for comparing saved terrain across palette encodings. */
final class BlockStateNbtName {
    private BlockStateNbtName() {
    }

    static String describe(Tag tag) {
        if (tag instanceof StringTag string) {
            return string.getAsString();
        }
        if (!(tag instanceof CompoundTag state)) {
            throw new IllegalArgumentException("Invalid block palette entry: " + tag);
        }
        Tag wrapped = state.get("");
        if (wrapped != null) {
            return describe(wrapped);
        }
        String name = state.contains("id", Tag.TAG_STRING) ? state.getString("id") : state.getString("Name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Missing block id in palette entry: " + state);
        }
        CompoundTag properties = state.contains("properties", Tag.TAG_COMPOUND)
                ? state.getCompound("properties") : state.getCompound("Properties");
        if (properties.isEmpty()) {
            return name;
        }
        StringJoiner description = new StringJoiner(",", name + "[", "]");
        for (String key : new TreeSet<>(properties.getAllKeys())) {
            description.add(key + "=" + properties.getString(key));
        }
        return description.toString();
    }
}

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
            return string.asString().orElseThrow();
        }
        if (!(tag instanceof CompoundTag state)) {
            throw new IllegalArgumentException("Invalid block palette entry: " + tag);
        }
        Tag wrapped = state.get("");
        if (wrapped != null) {
            return describe(wrapped);
        }
        String name = state.getStringOr("id", state.getStringOr("Name", ""));
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Missing block id in palette entry: " + state);
        }
        CompoundTag properties = state.getCompound("properties")
                .orElseGet(() -> state.getCompoundOrEmpty("Properties"));
        if (properties.isEmpty()) {
            return name;
        }
        StringJoiner description = new StringJoiner(",", name + "[", "]");
        for (String key : new TreeSet<>(properties.keySet())) {
            description.add(key + "=" + properties.getStringOr(key, ""));
        }
        return description.toString();
    }
}

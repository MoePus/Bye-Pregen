package com.moepus.byepregen.serialization.nbt;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

public final class BiomeNbtCache {
    private static final ConcurrentHashMap<Identifier, byte[]> BIOME_NAMES = new ConcurrentHashMap<>();

    private BiomeNbtCache() {}

    public static byte[] nameBytes(Holder<Biome> biome) {
        Identifier identifier = biome.unwrapKey().orElseThrow().identifier();
        return BIOME_NAMES.computeIfAbsent(identifier, key -> NbtWriter.stringBytes(key.toString()));
    }
}

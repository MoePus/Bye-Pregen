package com.moepus.byepregen.serialization.nbt;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public final class BiomeNbtCache {
    private static final ConcurrentHashMap<ResourceLocation, byte[]> BIOME_NAMES = new ConcurrentHashMap<>();

    private BiomeNbtCache() {}

    public static byte[] nameBytes(Holder<Biome> biome) {
        ResourceLocation location = biome.unwrapKey().orElseThrow().location();
        return BIOME_NAMES.computeIfAbsent(location, key -> NbtWriter.stringBytes(key.toString()));
    }
}

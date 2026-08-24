package com.moepus.byepregen.harness;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

public final class RegionStorageInfos {
    private static final ResourceKey<net.minecraft.world.level.Level> OVERWORLD = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.withDefaultNamespace("overworld")
    );

    private RegionStorageInfos() {
    }

    public static RegionStorageInfo overworld(String purpose) {
        return new RegionStorageInfo(purpose, OVERWORLD, "chunk");
    }
}

package com.moepus.byepregen.mixin.accessor.server.tick;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = NaturalSpawner.SpawnState.class, remap = false)
public interface NaturalSpawnerSpawnStateAccessor {
    @Invoker("<init>")
    static NaturalSpawner.SpawnState byepregen$newSpawnState(
            int spawnableChunkCount,
            Object2IntOpenHashMap<MobCategory> mobCategoryCounts,
            PotentialCalculator spawnPotential,
            LocalMobCapCalculator localMobCapCalculator) {
        throw new AssertionError();
    }
}

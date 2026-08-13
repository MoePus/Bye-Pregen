package com.moepus.byepregen.mixin.server.tick.compat.sable;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.moepus.byepregen.MixinGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@MixinGate(requiredMods = "sable")
@Mixin(value = NaturalSpawner.class, remap = false)
public abstract class SableNaturalSpawnerMixin {
    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getNearestPlayer(DDDDZ)Lnet/minecraft/world/entity/player/Player;"
            ),
            require = 0
    )
    private static Player byepregen$getNearestPlayerWithoutSable(
            ServerLevel level,
            double x,
            double y,
            double z,
            double maxDistance,
            boolean affectsSpawning,
            @Share("byepregen$nearestPlayerDistance") LocalDoubleRef nearestDistance) {
        double closestDistanceSqr = Double.MAX_VALUE;
        Player closestPlayer = null;
        for (ServerPlayer player : level.players()) {
            double distanceSqr = byepregen$plainDistanceToSqr(player, x, y, z);
            if (!player.isSpectator() && distanceSqr < closestDistanceSqr) {
                closestDistanceSqr = distanceSqr;
                closestPlayer = player;
                nearestDistance.set(distanceSqr);
            }
        }
        return closestPlayer;
    }

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
            ),
            require = 0
    )
    private static double byepregen$distanceToSqrWithoutSable(
            Player player,
            double x,
            double y,
            double z,
            @Share("byepregen$nearestPlayerDistance") LocalDoubleRef nearestDistance) {
        return nearestDistance.get();
    }

    @Unique
    private static double byepregen$plainDistanceToSqr(Player player, double x, double y, double z) {
        double deltaX = player.getX() - x;
        double deltaY = player.getY() - y;
        double deltaZ = player.getZ() - z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }
}

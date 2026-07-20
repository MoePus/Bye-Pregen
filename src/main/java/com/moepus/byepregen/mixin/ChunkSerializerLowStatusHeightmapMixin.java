package com.moepus.byepregen.mixin;

import java.util.Set;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerLowStatusHeightmapMixin {
    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/Heightmap;primeHeightmaps(Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/Set;)V"
            ),
            require = 1
    )
    private static void byepregen$skipLowStatusHeightmapRepair(ChunkAccess chunk, Set<Heightmap.Types> types) {
        if (types.isEmpty()) {
            return;
        }

        ChunkStatus status = chunk.getStatus();
        if (status != null && !status.isOrAfter(ChunkStatus.NOISE)) {
            return;
        }

        Heightmap.primeHeightmaps(chunk, types);
    }
}

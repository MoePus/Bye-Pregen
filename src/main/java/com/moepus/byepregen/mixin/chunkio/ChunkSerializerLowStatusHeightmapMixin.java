package com.moepus.byepregen.mixin.chunkio;

import java.util.Set;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ChunkSerializer.class, remap = false)
public abstract class ChunkSerializerLowStatusHeightmapMixin {
    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/Heightmap;primeHeightmaps(Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/Set;)V",
                    remap = false
            ),
            require = 1
    )
    private static void byepregen$skipLowStatusHeightmapRepair(ChunkAccess chunk, Set<Heightmap.Types> types) {
        if (types.isEmpty()) {
            return;
        }

        ChunkStatus status = chunk.getPersistedStatus();
        if (status != null && status.isBefore(ChunkStatus.NOISE)) {
            return;
        }

        Heightmap.primeHeightmaps(chunk, types);
    }
}

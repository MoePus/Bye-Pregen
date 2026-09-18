package com.moepus.byepregen.mixin.chunkio;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import java.util.Set;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE)
@Mixin(value = SerializableChunkData.class, remap = false)
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
    private void byepregen$skipLowStatusHeightmapRepair(ChunkAccess chunk, Set<Heightmap.Types> types) {
        if (types.isEmpty()) {
            return;
        }

        ChunkStatus status = chunk.getPersistedStatus();
        if (status != null && status.isBefore(ChunkStatus.TERRAIN) && byepregen$isEmpty(chunk)) {
            return;
        }

        Heightmap.primeHeightmaps(chunk, types);
    }

    private static boolean byepregen$isEmpty(ChunkAccess chunk) {
        // Retrogen and nonstandard early chunks can already contain blocks.
        for (var section : chunk.getSections()) {
            if (!section.hasOnlyAir()) return false;
        }
        return true;
    }
}

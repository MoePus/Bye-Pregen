package com.moepus.byepregen.mixin.arena;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.palette.arena.materialize.ArenaSectionMaterializer;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(
        feature = MixinFeature.ARENA,
        config = ConfigFlag.MATERIALIZE_ARENA_LEVEL_CHUNK
)
@Mixin(LevelChunk.class)
public abstract class LevelChunkArenaMixin {
    @InjectLite(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("RETURN")
    )
    private void byepregen$materializeArenaSections(
            ServerLevel level, ProtoChunk protoChunk, @Nullable LevelChunk.PostLoadProcessor postLoad) {
        ArenaSectionMaterializer.materializeChunk((LevelChunk) (Object) this);
    }
}

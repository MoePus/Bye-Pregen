package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaSectionMaterializer;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkArenaMixin {
    @Inject(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("RETURN")
    )
    private void byepregen$materializeArenaSections(ServerLevel level, ProtoChunk protoChunk, @Nullable LevelChunk.PostLoadProcessor postLoad, CallbackInfo ci) {
        ArenaSectionMaterializer.materializeChunk((LevelChunk) (Object) this);
    }
}

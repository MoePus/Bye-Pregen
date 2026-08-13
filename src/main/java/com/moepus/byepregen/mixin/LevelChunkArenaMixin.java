package com.moepus.byepregen.mixin;

import com.moepus.byepregen.Config;
import com.moepus.byepregen.ConfigParser;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaSectionMaterializer;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkArenaMixin {
    @InjectLite(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("RETURN")
    )
    private void byepregen$materializeArenaSections(
            ServerLevel level, ProtoChunk protoChunk, @Nullable LevelChunk.PostLoadProcessor postLoad) {
        Config config = ConfigParser.getConfig();
        if (!config.enableArenaPalette) {
            return;
        }

        if (!config.enableServerRuntimeArenaPalette) {
            ArenaSectionMaterializer.materializeChunk((LevelChunk) (Object) this);
        }
    }
}

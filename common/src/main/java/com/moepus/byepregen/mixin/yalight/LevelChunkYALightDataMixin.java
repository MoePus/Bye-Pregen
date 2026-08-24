package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkYALightDataMixin {
    @InjectLite(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("RETURN")
    )
    private void byepregen$copyYALightDataFromProto(
            ServerLevel level,
            ProtoChunk chunk,
            @Nullable LevelChunk.PostLoadProcessor postLoad
    ) {
        YAChunkLightAccess source = (YAChunkLightAccess)chunk;
        YAChunkLightAccess target = (YAChunkLightAccess)this;
        target.byepregen$setYALightData(LightLayer.BLOCK, source.byepregen$yaLightData(LightLayer.BLOCK, false));
        target.byepregen$setYALightData(LightLayer.SKY, source.byepregen$yaLightData(LightLayer.SKY, false));
    }
}

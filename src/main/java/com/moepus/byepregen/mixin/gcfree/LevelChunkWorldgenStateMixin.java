package com.moepus.byepregen.mixin.gcfree;

import com.moepus.byepregen.gcfree.WorldgenChunkState;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkWorldgenStateMixin implements WorldgenChunkState {
    @Unique
    private boolean byepregen$freshWorldgenChunk;

    @Inject(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("RETURN")
    )
    private void byepregen$markFreshWorldgenChunk(ServerLevel level, ProtoChunk protoChunk, @Nullable LevelChunk.PostLoadProcessor postLoad, CallbackInfo ci) {
        this.byepregen$freshWorldgenChunk = true;
    }

    @Override
    public boolean byepregen$isFreshWorldgenChunk() {
        return this.byepregen$freshWorldgenChunk;
    }

    @Override
    public void byepregen$setFreshWorldgenChunk(boolean fresh) {
        this.byepregen$freshWorldgenChunk = fresh;
    }
}

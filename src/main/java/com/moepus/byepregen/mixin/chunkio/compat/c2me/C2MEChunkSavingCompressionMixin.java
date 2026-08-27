package com.moepus.byepregen.mixin.chunkio.compat.c2me;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.chunksave.storage.ChunkSavingCompression;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@MixinGate(requiredMods = "c2me")
@Mixin(targets = "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageThread", remap = false)
public abstract class C2MEChunkSavingCompressionMixin {
    @Redirect(
            method = "lambda$writeChunk$12",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFileVersion;"
                            + "wrap(Ljava/io/OutputStream;)Ljava/io/OutputStream;",
                    remap = true),
            require = 1,
            allow = 1,
            remap = false)
    private static OutputStream byepregen$wrap(
            RegionFileVersion version, OutputStream output) throws IOException {
        return ChunkSavingCompression.wrap(version, output);
    }
}

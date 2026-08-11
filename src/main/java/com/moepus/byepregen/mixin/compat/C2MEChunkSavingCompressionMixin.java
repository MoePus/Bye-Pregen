package com.moepus.byepregen.mixin.compat;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.gcfree.ChunkSavingCompression;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(requiredMods = "c2me_rewrites_chunkio")
@Mixin(targets = "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageThread", remap = false)
public abstract class C2MEChunkSavingCompressionMixin {
    @Redirect(
            method = "lambda$writeChunk$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFileVersion;"
                            + "wrap(Ljava/io/OutputStream;)Ljava/io/OutputStream;"),
            require = 0)
    private static OutputStream byepregen$wrap(
            RegionFileVersion version, OutputStream output) throws IOException {
        return ChunkSavingCompression.wrap(version, output);
    }
}

package com.moepus.byepregen.mixin.chunkio;

import com.moepus.byepregen.chunksave.storage.ChunkSavingCompression;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RegionFile.class)
public abstract class RegionFileCompressionMixin {
    @Redirect(
            method = "getChunkDataOutputStream",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFileVersion;"
                            + "wrap(Ljava/io/OutputStream;)Ljava/io/OutputStream;"))
    private OutputStream byepregen$wrap(
            RegionFileVersion version, OutputStream output) throws IOException {
        return ChunkSavingCompression.wrap(version, output);
    }
}

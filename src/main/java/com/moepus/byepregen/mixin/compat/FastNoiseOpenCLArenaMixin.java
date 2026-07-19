package com.moepus.byepregen.mixin.compat;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.ArenaOpenCLBufferImporter;
import java.nio.ByteBuffer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.codeberg.zenxarch.fastnoise.ocl.FastCopyBufferDataIntoChunks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@MixinGate(requiredMods = "zfastnoise")
@Mixin(value = FastCopyBufferDataIntoChunks.class, remap = false)
public abstract class FastNoiseOpenCLArenaMixin {
    /**
     * @author MoePus
     * @reason Would be removed once C2ME-OCL's API become stable
     */
    @Overwrite
    public static void copyData(
            StaticCache2D<ProtoChunk> chunks,
            BlockState[] blockStateMappings,
            int verticalSize,
            NoiseGeneratorSettings settings,
            int horizontalSize,
            ByteBuffer blockBuffer,
            ChunkPos startingPos,
            int batchSize
    ) {
        ArenaOpenCLBufferImporter.copy(new ArenaOpenCLBufferImporter.Request(
                chunks,
                blockStateMappings,
                verticalSize,
                settings.noiseSettings().minY(),
                horizontalSize,
                blockBuffer,
                startingPos,
                batchSize
        ));
    }
}

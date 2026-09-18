package com.moepus.byepregen.mixin.arena.compat.c2meocl;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.worldgen.arena.ArenaOpenCLBufferImporter;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Writes the OpenCL block buffer into arena storage from the OpenCL module's own write step.
 *
 * <p>The module ({@code c2me-opts-accel-opencl}) hands that buffer to a provider class it looks up by
 * name through a method handle, and keeps its own per-section fallback for when the lookup found
 * nothing: {@code CLServerBatchedBiomeNoiseContext.writeBlocks} branches on the handle being non-null
 * and calls the provider wrapper. Both halves are taken over here - the field read yields a handle bound
 * to our own entry point so the write step always runs, and the provider call is replaced with the arena
 * import - which makes our importer the writer whether or not the provider mod is installed, instead of
 * only when it is. Neither this file nor the build refers to that mod, and the target is named as a
 * string under {@code @Pseudo} so the mixin is skipped while the OpenCL module is absent.
 */
@MixinGate(feature = MixinFeature.ARENA)
@Pseudo
@Mixin(targets = "com.ishland.c2me.opts.accel.opencl.common.gen.CLServerBatchedBiomeNoiseContext", remap = false)
public abstract class C2MEOclArenaMixin {
    @WrapOperation(
            method = "writeBlocks",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETSTATIC,
                    target = "Lcom/ishland/c2me/opts/accel/opencl/common/integration/zfastnoise/"
                            + "ZFastNoiseBindings;MH_FastCopyBufferDataIntoChunks$copyData:"
                            + "Ljava/lang/invoke/MethodHandle;"
            )
    )
    private static MethodHandle byepregen$ownWritePath(Operation<MethodHandle> original) {
        return ArenaOpenCLBufferImporter.writeHandle();
    }

    @WrapOperation(
            method = "writeBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ishland/c2me/opts/accel/opencl/common/integration/zfastnoise/"
                            + "ZFastNoiseBindings;call_FastCopyBufferDataIntoChunks$copyData("
                            + "Lnet/minecraft/util/StaticCache2D;"
                            + "[Lnet/minecraft/world/level/block/state/BlockState;"
                            + "I"
                            + "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
                            + "I"
                            + "Ljava/nio/ByteBuffer;"
                            + "Lnet/minecraft/world/level/ChunkPos;"
                            + "I)V"
            )
    )
    private static void byepregen$importToArena(
            StaticCache2D<ProtoChunk> chunks,
            BlockState[] blockStateMappings,
            int verticalSize,
            NoiseGeneratorSettings settings,
            int horizontalSize,
            ByteBuffer blockBuffer,
            ChunkPos startingPos,
            int batchSize,
            Operation<Void> original
    ) {
        ArenaOpenCLBufferImporter.copyIntoChunks(
                chunks,
                blockStateMappings,
                verticalSize,
                settings,
                horizontalSize,
                blockBuffer,
                startingPos,
                batchSize
        );
    }
}

package com.moepus.byepregen.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.moepus.byepregen.ArenaBlockStateNbtReader;
import com.moepus.byepregen.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.FastBlockStateNbtReader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = ChunkSerializer.class, remap = false)
public abstract class ChunkSerializerArenaReadMixin {
    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
                    ordinal = 0,
                    remap = false
            ),
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=block_states", ordinal = 0),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;<init>(Lnet/minecraft/world/level/chunk/PalettedContainer;Lnet/minecraft/world/level/chunk/PalettedContainerRO;)V",
                            remap = false
                    )
            ),
            require = 1,
            remap = false
    )
    private static DataResult<PalettedContainer<BlockState>> byepregen$readArenaBlockStates(
            Codec<PalettedContainer<BlockState>> codec,
            DynamicOps<?> ops,
            Object input,
            ServerLevel level,
            PoiManager poiManager,
            RegionStorageInfo storageInfo,
            ChunkPos chunkPos,
            CompoundTag chunkTag
    ) {
        boolean protoChunk = ChunkSerializer.getChunkTypeFromTag(chunkTag) == ChunkType.PROTOCHUNK;
        if (input instanceof CompoundTag blockStatesTag) {
            PalettedContainer<BlockState> direct = protoChunk
                    ?  ArenaBlockStateNbtReader.read(blockStatesTag)
                    : FastBlockStateNbtReader.read(blockStatesTag);
            if (direct != null) {
                return DataResult.success(direct);
            }
        }

        return byepregen$vanillaParse(codec, ops, input);
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DataResult<PalettedContainer<BlockState>> byepregen$vanillaParse(
            Codec<PalettedContainer<BlockState>> codec, DynamicOps<?> ops, Object input) {
        return codec.parse((DynamicOps) ops, input);
    }
}

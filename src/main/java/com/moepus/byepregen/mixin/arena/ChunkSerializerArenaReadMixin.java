package com.moepus.byepregen.mixin.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigParser;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs.NbtReader;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs.StateCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = ChunkSerializer.class, remap = false)
public abstract class ChunkSerializerArenaReadMixin {
    @Mutable
    @Shadow
    @Final
    private static Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC;

    @InjectLite(method = "<clinit>", at = @At("RETURN"))
    private static void byepregen$replaceBlockStateCodec() {
        BLOCK_STATE_CODEC = new StateCodec(BLOCK_STATE_CODEC);
    }

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
            require = 1
    )
    private static DataResult<PalettedContainer<BlockState>> byepregen$readBlockStates(
            Codec<PalettedContainer<BlockState>> codec,
            DynamicOps<?> ops,
            Object input,
            ServerLevel level,
            PoiManager poiManager,
            RegionStorageInfo storageInfo,
            ChunkPos chunkPos,
            CompoundTag chunkTag
    ) {
        if (input instanceof CompoundTag blockStatesTag) {
            Config config = ConfigParser.getConfig();
            ChunkType chunkType = ChunkSerializer.getChunkTypeFromTag(chunkTag);
            if (byepregen$shouldReadArena(config, chunkType)) {
                ArenaBlockStatePalettedContainer arena = NbtReader.read(blockStatesTag);
                if (arena != null) {
                    return DataResult.success(arena);
                }
            }
        }

        return byepregen$parseFallback(codec, ops, input);
    }

    @Unique
    private static boolean byepregen$shouldReadArena(Config config, ChunkType chunkType) {
        if (!config.enableArenaPalette) {
            return false;
        }
        if (chunkType == ChunkType.PROTOCHUNK) {
            return true;
        }
        return config.enableServerRuntimeArenaPalette;
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DataResult<PalettedContainer<BlockState>> byepregen$parseFallback(
            Codec<PalettedContainer<BlockState>> codec, DynamicOps<?> ops, Object input) {
        return ((Codec) codec).parse((DynamicOps) ops, input);
    }
}

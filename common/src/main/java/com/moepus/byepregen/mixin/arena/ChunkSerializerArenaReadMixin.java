package com.moepus.byepregen.mixin.arena;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.palette.arena.codec.NbtReader;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@MixinGate(feature = MixinFeature.ARENA)
@Mixin(value = SerializableChunkData.class, remap = false)
public abstract class ChunkSerializerArenaReadMixin {
    @Redirect(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;map(Ljava/util/function/Function;)Ljava/util/Optional;",
                    remap = false
            ),
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=block_states"),
                    to = @At(value = "CONSTANT", args = "stringValue=biomes")
            ),
            require = 1
    )
    private static Optional<PalettedContainer<BlockState>> byepregen$readBlockStates(
            Optional<CompoundTag> input,
            Function<CompoundTag, PalettedContainer<BlockState>> decoder,
            @Local ChunkStatus status
    ) {
        if (input.isPresent()) {
            Config config = ConfigManager.getConfig();
            if (byepregen$shouldReadArena(config, status.getChunkType())) {
                ArenaBlockStatePalettedContainer arena = NbtReader.read(input.get());
                if (arena != null) {
                    return Optional.of(arena);
                }
            }
        }
        return input.map(decoder);
    }

    @Unique
    private static boolean byepregen$shouldReadArena(Config config, ChunkType chunkType) {
        if (!config.worldgen().arena().enabled()) {
            return false;
        }
        if (chunkType == ChunkType.PROTOCHUNK) {
            return true;
        }
        return config.worldgen().arena().runtime().server();
    }
}

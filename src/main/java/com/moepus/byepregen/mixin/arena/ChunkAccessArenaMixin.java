package com.moepus.byepregen.mixin.arena;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.ARENA)
@Mixin(value = ChunkAccess.class, remap = false)
public abstract class ChunkAccessArenaMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;replaceMissingSections(Lnet/minecraft/core/Registry;[Lnet/minecraft/world/level/chunk/LevelChunkSection;)V"
            )
    )
    private void byepregen$replaceMissingSections(
            Registry<Biome> biomeRegistry,
            LevelChunkSection[] sections,
            ChunkPos chunkPos,
            UpgradeData upgradeData,
            LevelHeightAccessor heightAccessor,
            Registry<Biome> constructorBiomeRegistry,
            long inhabitedTime,
            @Nullable LevelChunkSection[] providedSections,
            @Nullable BlendingData blendingData
    ) {
        Config config = ConfigManager.getConfig();
        boolean isProtoChunk = (Object) this instanceof ProtoChunk;
        for (int i = 0; i < sections.length; ++i) {
            if (sections[i] == null) {
                sections[i] = new LevelChunkSection(
                        this.byepregen$createStateContainer(config, isProtoChunk, heightAccessor),
                        this.byepregen$createBiomeContainer(biomeRegistry)
                );
            }
        }
    }

    @Unique
    private PalettedContainer<BlockState> byepregen$createStateContainer(
            Config config, boolean isProtoChunk, LevelHeightAccessor heightAccessor) {
        if (byepregen$shouldUseArena(config, isProtoChunk, heightAccessor)) {
            return new ArenaBlockStatePalettedContainer();
        }
        return new PalettedContainer<>(
                Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES
        );
    }

    @Unique
    private boolean byepregen$shouldUseArena(Config config, boolean isProtoChunk, LevelHeightAccessor heightAccessor) {
        if (!config.worldgen().arena().enabled()) {
            return false;
        }
        if (isProtoChunk) {
            return true;
        }
        if (heightAccessor instanceof Level level && level.isClientSide()) {
            return config.worldgen().arena().runtime().client();
        }
        return config.worldgen().arena().runtime().server();
    }

    @Unique
    private PalettedContainer<Holder<Biome>> byepregen$createBiomeContainer(Registry<Biome> biomeRegistry) {
        return new PalettedContainer<>(
                biomeRegistry.asHolderIdMap(),
                biomeRegistry.getHolderOrThrow(Biomes.PLAINS),
                PalettedContainer.Strategy.SECTION_BIOMES
        );
    }
}

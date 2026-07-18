package com.moepus.byepregen.mixin;

import com.moepus.byepregen.Config;
import com.moepus.byepregen.ConfigParser;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.PaletteContainer.FastPalette.FastBlockStatePalettedContainer;
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
        if ((Object) this instanceof ProtoChunk && providedSections == null && ConfigParser.getConfig().enableArenaPalette) {
            this.byepregen$replaceWithArenaSections(biomeRegistry, sections);
            return;
        }

        this.byepregen$replaceWithNormalSections(biomeRegistry, sections, heightAccessor);
    }

    @Unique
    private void byepregen$replaceWithArenaSections(Registry<Biome> biomeRegistry, LevelChunkSection[] sections) {
        for (int i = 0; i < sections.length; ++i) {
            if (sections[i] == null) {
                sections[i] = new LevelChunkSection(
                        new ArenaBlockStatePalettedContainer(),
                        this.byepregen$createBiomeContainer(biomeRegistry)
                );
            }
        }
    }

    @Unique
    private void byepregen$replaceWithNormalSections(
            Registry<Biome> biomeRegistry, LevelChunkSection[] sections, LevelHeightAccessor heightAccessor) {
        for (int i = 0; i < sections.length; ++i) {
            if (sections[i] == null) {
                sections[i] = new LevelChunkSection(
                        this.byepregen$createStateContainer(heightAccessor),
                        this.byepregen$createBiomeContainer(biomeRegistry)
                );
            }
        }
    }

    @Unique
    private PalettedContainer<BlockState> byepregen$createStateContainer(LevelHeightAccessor heightAccessor) {
        Config config = ConfigParser.getConfig();
        if (heightAccessor instanceof Level level && level.isClientSide()) {
            if (config.enableArenaPalette && config.enableClientArenaPalette) {
                return new ArenaBlockStatePalettedContainer();
            }
            return new FastBlockStatePalettedContainer(
                    Block.BLOCK_STATE_REGISTRY,
                    Blocks.AIR.defaultBlockState(),
                    PalettedContainer.Strategy.SECTION_STATES
            );
        }

        if (config.enableArenaPalette && config.enableServerRuntimeArenaPalette) {
            return new ArenaBlockStatePalettedContainer();
        }
        return new FastBlockStatePalettedContainer(
                Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES
        );
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

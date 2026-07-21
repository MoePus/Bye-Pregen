package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessArenaMixin {
    @Shadow
    @Final
    protected LevelChunkSection[] sections;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void byepregen$replaceMissingSections(
            ChunkPos chunkPos,
            UpgradeData upgradeData,
            LevelHeightAccessor heightAccessor,
            Registry<Biome> constructorBiomeRegistry,
            long inhabitedTime,
            @Nullable LevelChunkSection[] providedSections,
            @Nullable BlendingData blendingData,
            CallbackInfo ci
    ) {
        LevelChunkSection[] sections = this.sections;
        if ((Object) this instanceof ProtoChunk && providedSections == null) {
            this.byepregen$replaceAllWithArenaSections(constructorBiomeRegistry, sections);
            return;
        }

        if (providedSections == null || providedSections.length != sections.length) {
            this.byepregen$replaceAllWithNormalSections(constructorBiomeRegistry, sections);
            return;
        }

        this.byepregen$replaceMissingNormalSections(constructorBiomeRegistry, sections, providedSections);
    }

    @Unique
    private void byepregen$replaceAllWithArenaSections(Registry<Biome> biomeRegistry, LevelChunkSection[] sections) {
        for (int i = 0; i < sections.length; ++i) {
            sections[i] = new LevelChunkSection(
                    new ArenaBlockStatePalettedContainer(),
                    this.byepregen$createBiomeContainer(biomeRegistry)
            );
        }
    }

    @Unique
    private void byepregen$replaceAllWithNormalSections(Registry<Biome> biomeRegistry, LevelChunkSection[] sections) {
        for (int i = 0; i < sections.length; ++i) {
            sections[i] = new LevelChunkSection(
                    this.byepregen$createStateContainer(),
                    this.byepregen$createBiomeContainer(biomeRegistry)
            );
        }
    }

    @Unique
    private void byepregen$replaceMissingNormalSections(
            Registry<Biome> biomeRegistry, LevelChunkSection[] sections, LevelChunkSection[] providedSections
    ) {
        for (int i = 0; i < sections.length; ++i) {
            if (providedSections[i] == null) {
                sections[i] = new LevelChunkSection(
                        this.byepregen$createStateContainer(),
                        this.byepregen$createBiomeContainer(biomeRegistry)
                );
            }
        }
    }

    @Unique
    private PalettedContainer<BlockState> byepregen$createStateContainer() {
        return new PalettedContainer<>(
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

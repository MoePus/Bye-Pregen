package com.moepus.byepregen.mixin.compat;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import me.cortex.voxy.common.voxelization.ILightingSupplier;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(requiredMods = "voxy")
@Pseudo
@Mixin(targets = "me.cortex.voxy.common.voxelization.WorldConversionFactory", remap = false)
public abstract class VoxyWorldConversionFactoryMixin {
    private static final int BIOME_INDEX_MASK = 0b1100_1100_1100;
    private static final ThreadLocal<int[]> BIOME_CACHE = ThreadLocal.withInitial(() -> new int[4 * 4 * 4]);
    private static final MethodHandle GET_BLOCK_ID = byepregen$findMapperMethod(
            "getIdForBlockState", BlockState.class);
    private static final MethodHandle GET_BIOME_ID = byepregen$findMapperMethod(
            "getIdForBiome", Holder.class);

    @InjectLite(
            method = "convert(Lme/cortex/voxy/common/voxelization/VoxelizedSection;Lme/cortex/voxy/common/world/other/Mapper;Lnet/minecraft/world/level/chunk/PalettedContainer;Lnet/minecraft/world/level/chunk/PalettedContainerRO;Lme/cortex/voxy/common/voxelization/ILightingSupplier;ZJ)Lme/cortex/voxy/common/voxelization/VoxelizedSection;",
            at = @At("HEAD"),
            cancel = true,
            cancelOnNonNull = true,
            require = 0
    )
    private static VoxelizedSection byepregen$convertArena(
            VoxelizedSection section,
            Mapper mapper,
            PalettedContainer<BlockState> blockContainer,
            PalettedContainerRO<Holder<Biome>> biomeContainer,
            ILightingSupplier lightSupplier,
            boolean shouldZoom,
            long zoomSeed
    ) {
        if (blockContainer instanceof ArenaBlockStatePalettedContainer arena) {
            return byepregen$convertArena(section, mapper, arena, biomeContainer, lightSupplier);
        }
        return null;
    }

    private static VoxelizedSection byepregen$convertArena(
            VoxelizedSection section,
            Mapper mapper,
            ArenaBlockStatePalettedContainer blockContainer,
            PalettedContainerRO<Holder<Biome>> biomeContainer,
            ILightingSupplier lightSupplier
    ) {
        int[] biomes = BIOME_CACHE.get();
        for (int i = 0; i < biomes.length; ++i) {
            int x = i & 3;
            int y = i >>> 4;
            int z = (i >>> 2) & 3;
            biomes[i] = byepregen$getBiomeId(mapper, biomeContainer.get(x, y, z));
        }

        int nonAirCount = 0;
        long[] data = section.section;
        for (int i = 0; i < 4096; ++i) {
            byte light = lightSupplier.supply(i & 15, (i >>> 8) & 15, (i >>> 4) & 15);
            int blockId = byepregen$getBlockId(mapper, Block.stateById(blockContainer.rawIdAt(i)));
            if (blockId == 0) {
                data[i] = Mapper.airWithLight(light);
            } else {
                ++nonAirCount;
                data[i] = Mapper.composeMappingId(light, blockId, biomes[Integer.compress(i, BIOME_INDEX_MASK)]);
            }
        }
        section.lvl0NonAirCount = nonAirCount;
        return section;
    }

    private static MethodHandle byepregen$findMapperMethod(String name, Class<?> parameterType) {
        try {
            return MethodHandles.publicLookup().findVirtual(
                    Mapper.class, name, MethodType.methodType(int.class, parameterType));
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static int byepregen$getBlockId(Mapper mapper, BlockState state) {
        try {
            return (int) GET_BLOCK_ID.invokeExact(mapper, state);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to map Voxy block state", throwable);
        }
    }

    private static int byepregen$getBiomeId(Mapper mapper, Holder<Biome> biome) {
        try {
            return (int) GET_BIOME_ID.invokeExact(mapper, biome);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to map Voxy biome", throwable);
        }
    }
}

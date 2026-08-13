package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.worldgen.feature.FastPlacedFeature;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.MixinGate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.RandomPatchFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(config = "enablePlacedFeatureMixin")
@Mixin(value = RandomPatchFeature.class, remap = false)
public abstract class RandomPatchFeatureMixin {
    @Redirect(
            method = "place",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;place("
                            + "Lnet/minecraft/world/level/WorldGenLevel;"
                            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                            + "Lnet/minecraft/util/RandomSource;"
                            + "Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean byepregen$placeNested(
            PlacedFeature feature,
            WorldGenLevel level,
            ChunkGenerator generator,
            RandomSource random,
            BlockPos pos
    ) {
        FastPlacementContext context = FastPlacementContext.current();
        if (context == null) {
            return feature.place(level, generator, random, pos);
        }
        return ((FastPlacedFeature) (Object) feature).byepregen$placeWithContext(context, random, pos);
    }
}

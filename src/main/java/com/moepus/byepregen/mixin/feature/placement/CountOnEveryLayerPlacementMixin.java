package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = CountOnEveryLayerPlacement.class, remap = false)
public abstract class CountOnEveryLayerPlacementMixin implements FastPlacementModifier {
    @Shadow
    @Final
    private IntProvider count;

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        int layer = 0;
        boolean found;
        do {
            found = this.byepregen$collectLayer(context, x, z, layer, nextIndex);
            layer++;
        } while (found);
    }

    @Unique
    private boolean byepregen$collectLayer(FastPlacementContext context, int x, int z, int layer, int nextIndex) {
        boolean found = false;
        for (int i = 0, size = this.count.sample(context.random()); i < size; i++) {
            int targetX = x + context.random().nextInt(16);
            int targetZ = z + context.random().nextInt(16);
            int height = context.placementContext().getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
            int groundY = this.byepregen$findOnGroundYPosition(context, targetX, height, targetZ, layer);
            if (groundY != Integer.MAX_VALUE) {
                context.apply(nextIndex, targetX, groundY, targetZ);
                found = true;
            }
        }
        return found;
    }

    @Unique
    private int byepregen$findOnGroundYPosition(FastPlacementContext context, int x, int y, int z, int layer) {
        PlacementContext placementContext = context.placementContext();
        BlockPos.MutableBlockPos pos = context.modifierPos(x, y, z);
        int foundLayers = 0;
        BlockState previousState = placementContext.getBlockState(pos);

        for (int currentY = y; currentY >= placementContext.getMinBuildHeight() + 1; currentY--) {
            pos.setY(currentY - 1);
            BlockState currentState = placementContext.getBlockState(pos);
            if (!byepregen$isEmpty(currentState) && byepregen$isEmpty(previousState) && !currentState.is(Blocks.BEDROCK)) {
                if (foundLayers == layer) {
                    return pos.getY() + 1;
                }
                foundLayers++;
            }
            previousState = currentState;
        }

        return Integer.MAX_VALUE;
    }

    @Unique
    private static boolean byepregen$isEmpty(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA);
    }
}

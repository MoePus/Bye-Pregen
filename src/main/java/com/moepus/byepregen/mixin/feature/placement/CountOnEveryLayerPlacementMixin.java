package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.PlanCompatiblePlacementModifier;
import java.util.ArrayList;
import java.util.List;
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
public abstract class CountOnEveryLayerPlacementMixin implements PlanCompatiblePlacementModifier {
    @Shadow
    @Final
    private IntProvider count;

    @Override
    public boolean byepregen$mayProduceMultipleOrigins() {
        return true;
    }

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        List<BlockPos> positions = new ArrayList<>();
        int layer = 0;
        boolean found;
        do {
            found = false;
            for (int i = 0; i < this.count.sample(context.random()); i++) {
                int targetX = x + context.random().nextInt(16);
                int targetZ = z + context.random().nextInt(16);
                int height = context.placementContext().getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
                int groundY = this.byepregen$findOnGroundYPosition(context, targetX, height, targetZ, layer);
                if (groundY != Integer.MAX_VALUE) {
                    positions.add(new BlockPos(targetX, groundY, targetZ));
                    found = true;
                }
            }
            layer++;
        } while (found);

        for (BlockPos pos : positions) {
            context.apply(nextIndex, pos.getX(), pos.getY(), pos.getZ());
        }
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

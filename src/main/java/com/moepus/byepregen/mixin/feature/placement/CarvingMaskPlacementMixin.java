package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.PlanCompatiblePlacementModifier;
import com.moepus.byepregen.mixin.accessor.worldgen.feature.CarvingMaskAccessor;
import java.util.BitSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.CarvingMaskPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(CarvingMaskPlacement.class)
public abstract class CarvingMaskPlacementMixin implements PlanCompatiblePlacementModifier {
    @Shadow
    @Final
    private GenerationStep.Carving step;

    @Override
    public boolean byepregen$mayProduceMultipleOrigins() {
        return true;
    }

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        CarvingMask mask = context.placementContext().getCarvingMask(chunkPos, this.step);
        CarvingMaskAccessor accessor = (CarvingMaskAccessor)mask;
        BitSet bits = accessor.byepregen$getMask();
        int minY = accessor.byepregen$getMinY();

        for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1)) {
            int localX = index & 15;
            int localZ = index >> 4 & 15;
            int localY = index >> 8;
            context.apply(nextIndex, chunkPos.getBlockX(localX), minY + localY, chunkPos.getBlockZ(localZ));
        }
    }
}

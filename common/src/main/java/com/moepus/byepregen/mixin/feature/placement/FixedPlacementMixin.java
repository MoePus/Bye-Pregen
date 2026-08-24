package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.placement.FixedPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(config = ConfigFlag.PLACED_FEATURES)
@Mixin(value = FixedPlacement.class, remap = false)
public abstract class FixedPlacementMixin implements FastPlacementModifier {
    @Shadow
    @Final
    private List<BlockPos> positions;

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        int chunkX = SectionPos.blockToSectionCoord(x);
        int chunkZ = SectionPos.blockToSectionCoord(z);
        for (int i = 0, size = this.positions.size(); i < size; i++) {
            BlockPos pos = this.positions.get(i);
            if (byepregen$isSameChunk(chunkX, chunkZ, pos)) {
                context.apply(nextIndex, pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }

    @Unique
    private static boolean byepregen$isSameChunk(int chunkX, int chunkZ, BlockPos pos) {
        return chunkX == SectionPos.blockToSectionCoord(pos.getX()) && chunkZ == SectionPos.blockToSectionCoord(pos.getZ());
    }
}

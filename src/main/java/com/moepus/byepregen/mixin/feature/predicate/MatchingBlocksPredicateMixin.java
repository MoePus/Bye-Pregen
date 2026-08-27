package com.moepus.byepregen.mixin.feature.predicate;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.MatchingBlocksPredicate")
public abstract class MatchingBlocksPredicateMixin {
    @Shadow
    @Final
    private HolderSet<Block> blocks;

    @Unique
    private Block[] byepregen$fastBlocks;

    @InjectLite(method = "<init>", at = @At("TAIL"))
    private void byepregen$initFastBlocks(Vec3i offset, HolderSet<Block> blocks) {
        List<Holder<Block>> holders = blocks.unwrap().right().orElse(null);
        if (holders == null) {
            return;
        }

        Block[] directBlocks = new Block[holders.size()];
        for (int i = 0, size = holders.size(); i < size; ++i) {
            directBlocks[i] = holders.get(i).value();
        }

        this.byepregen$fastBlocks = directBlocks;
    }

    /**
     * @author MoePus, Codex
     * @reason Avoid HolderSet/Holder indirection in hot worldgen block predicate checks.
     */
    @Overwrite
    protected boolean test(BlockState state) {
        Block[] fastBlocks = this.byepregen$fastBlocks;
        if (fastBlocks != null) {
            Block block = state.getBlock();
            int size = fastBlocks.length;
            if (size == 1) {
                return block == fastBlocks[0];
            }
            if (size == 2) {
                return block == fastBlocks[0] || block == fastBlocks[1];
            }
            for (int i = 0; i < size; ++i) {
                if (block == fastBlocks[i]) {
                    return true;
                }
            }
            return false;
        }

        return state.is(this.blocks);
    }
}

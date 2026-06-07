package com.moepus.byepregen.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = StateTestingPredicate.class)
public abstract class StateTestingPredicateMixin {
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> bpg$mutablePos = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Shadow
    @Final
    protected Vec3i offset;

    @Shadow
    protected abstract boolean test(BlockState state);

    /**
     * @author moepus
     * @reason Avoid allocating a fresh immutable BlockPos for every worldgen block predicate offset check.
     */
    @Overwrite
    public final boolean test(final WorldGenLevel level, final BlockPos pos) {
        if (this.offset == Vec3i.ZERO) {
            return this.test(level.getBlockState(pos));
        }

        final BlockPos.MutableBlockPos mutablePos = bpg$mutablePos.get();
        mutablePos.set(pos.getX() + this.offset.getX(), pos.getY() + this.offset.getY(), pos.getZ() + this.offset.getZ());
        return this.test(level.getBlockState(mutablePos));
    }
}

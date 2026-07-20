package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.YABlockLightEngine;
import com.moepus.byepregen.yalight.YASkyLightEngine;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = LightEngine.class, remap = false)
public abstract class LightEngineYASuperMixin {
    @Unique
    private static final LongOpenHashSet byepregen$dummyNodeSet = new LongOpenHashSet();

    @Unique
    private static final LongArrayFIFOQueue byepregen$dummyDecreaseQueue = new LongArrayFIFOQueue();

    @Unique
    private static final LongArrayFIFOQueue byepregen$dummyIncreaseQueue = new LongArrayFIFOQueue();

    @Unique
    private static final BlockPos.MutableBlockPos byepregen$dummyMutablePos = new BlockPos.MutableBlockPos();

    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "(IF)Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;")
    )
    private LongOpenHashSet byepregen$skipNodeSet(int expected, float loadFactor) {
        return this.byepregen$isYAEngine() ? byepregen$dummyNodeSet : new LongOpenHashSet(expected, loadFactor);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "()Lit/unimi/dsi/fastutil/longs/LongArrayFIFOQueue;",
                    ordinal = 0
            )
    )
    private LongArrayFIFOQueue byepregen$skipDecreaseQueue() {
        return this.byepregen$isYAEngine() ? byepregen$dummyDecreaseQueue : new LongArrayFIFOQueue();
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "()Lit/unimi/dsi/fastutil/longs/LongArrayFIFOQueue;",
                    ordinal = 1
            )
    )
    private LongArrayFIFOQueue byepregen$skipIncreaseQueue() {
        return this.byepregen$isYAEngine() ? byepregen$dummyIncreaseQueue : new LongArrayFIFOQueue();
    }

    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "()Lnet/minecraft/core/BlockPos$MutableBlockPos;")
    )
    private BlockPos.MutableBlockPos byepregen$skipMutablePos() {
        return this.byepregen$isYAEngine() ? byepregen$dummyMutablePos : new BlockPos.MutableBlockPos();
    }

    private boolean byepregen$isYAEngine() {
        Object self = this;
        return self instanceof YABlockLightEngine || self instanceof YASkyLightEngine;
    }
}

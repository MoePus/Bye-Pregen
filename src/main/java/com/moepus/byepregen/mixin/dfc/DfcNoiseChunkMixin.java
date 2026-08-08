package com.moepus.byepregen.mixin.dfc;

import com.ishland.c2me.opts.dfc.common.ducks.IDfcObjectCacheCapable;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.moepus.byepregen.dfc.ColumnCompiledDensityFunction;
import com.moepus.byepregen.dfc.FinalDensityColumnProvider;
import com.moepus.byepregen.dfc.column.ColumnEvaluationContext;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(NoiseChunk.class)
public abstract class DfcNoiseChunkMixin implements FinalDensityColumnProvider {
    @Unique private ColumnCompiledDensityFunction byepregen$finalDensityColumnRoot;
    @Unique private ColumnEvaluationContext byepregen$finalDensityColumnContext;

    @ModifyVariable(
            method = "<init>",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lcom/google/common/collect/ImmutableList;builder()"
                                    + "Lcom/google/common/collect/ImmutableList$Builder;"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;oreVeinsEnabled()Z"
                    )
            ),
            at = @At("STORE"),
            ordinal = 0
    )
    private DensityFunction byepregen$captureFinalDensityColumnRoot(DensityFunction root) {
        if (root instanceof ColumnCompiledDensityFunction columnRoot
                && columnRoot.byepregen$hasColumnMethod()) {
            this.byepregen$finalDensityColumnRoot = columnRoot;
        }
        return root;
    }

    @Override
    public boolean byepregen$hasFinalDensityColumn() {
        return this.byepregen$finalDensityColumnRoot != null;
    }

    @Override
    public void byepregen$evalFinalDensityColumn(
            double[] output,
            int blockX,
            int blockZ,
            int minY,
            int cellHeight,
            ColumnEvaluationContext.InterpolationProvider interpolationProvider
    ) {
        ColumnCompiledDensityFunction root = this.byepregen$finalDensityColumnRoot;
        if (root == null) {
            throw new IllegalStateException("Final density has no compiled column method");
        }
        ColumnEvaluationContext context = this.byepregen$getColumnContext();
        context.prepare(output, blockX, blockZ, minY, cellHeight, interpolationProvider);
        try {
            root.byepregen$evalColumn(context);
        } finally {
            context.clear();
        }
    }

    @Unique
    private ColumnEvaluationContext byepregen$getColumnContext() {
        ColumnEvaluationContext context = this.byepregen$finalDensityColumnContext;
        if (context == null) {
            DfcObjectCache cache = ((IDfcObjectCacheCapable) this).c2me$getDfcObjectCache();
            context = new ColumnEvaluationContext(cache);
            this.byepregen$finalDensityColumnContext = context;
        }
        return context;
    }
}

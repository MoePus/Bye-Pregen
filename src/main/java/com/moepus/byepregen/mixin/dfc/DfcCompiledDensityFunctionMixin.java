package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.ishland.c2me.opts.dfc.common.gen.jvm.CompiledDensityFunction;
import com.ishland.c2me.opts.dfc.common.gen.jvm.CompiledEntry;
import com.moepus.byepregen.dfc.ColumnCompiledDensityFunction;
import com.moepus.byepregen.dfc.ColumnCompiledEntry;
import com.moepus.byepregen.dfc.column.ColumnEvaluationContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(feature = MixinFeature.DFC)
@Mixin(value = CompiledDensityFunction.class, remap = false)
public abstract class DfcCompiledDensityFunctionMixin implements ColumnCompiledDensityFunction {
    @Shadow @Final private int compiledIndex;
    @Shadow private CompiledEntry compiledEntry;

    @Override
    public boolean byepregen$hasColumnMethod() {
        return this.compiledEntry instanceof ColumnCompiledEntry entry
                && entry.byepregen$hasColumn(this.compiledIndex);
    }

    @Override
    public void byepregen$evalColumn(ColumnEvaluationContext context) {
        if (!(this.compiledEntry instanceof ColumnCompiledEntry entry)
                || !entry.byepregen$hasColumn(this.compiledIndex)) {
            throw new IllegalStateException("This compiled density root has no column method");
        }
        entry.byepregen$evalColumn(this.compiledIndex, context);
    }
}

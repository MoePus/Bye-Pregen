package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.moepus.byepregen.dfc.ColumnCodegenContextAccess;
import com.moepus.byepregen.dfc.column.ColumnCodegenHooks;
import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@MixinGate(feature = MixinFeature.DFC)
@Mixin(value = BytecodeGen.Context.class, remap = false)
public abstract class DfcBytecodeGenContextMixin implements ColumnCodegenContextAccess {
    @Unique private final Map<Integer, String> byepregen$columnMethods = new LinkedHashMap<>();
    @Unique private boolean byepregen$columnCodegenFinished;

    @Inject(method = "registerRoot", at = @At("RETURN"))
    private void byepregen$registerColumnRoot(
            String suffix,
            AstNode node,
            CallbackInfoReturnable<Integer> cir
    ) {
        ColumnCodegenHooks.registerColumnRoot(
                (BytecodeGen.Context) (Object) this,
                suffix,
                node,
                cir.getReturnValueI(),
                this.byepregen$columnMethods
        );
    }

    @Override
    public void byepregen$finishColumnCodegen() {
        if (this.byepregen$columnCodegenFinished) {
            return;
        }
        this.byepregen$columnCodegenFinished = true;
        ColumnCodegenHooks.finish(
                (BytecodeGen.Context) (Object) this,
                this.byepregen$columnMethods
        );
    }
}

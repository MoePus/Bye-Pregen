package com.moepus.byepregen.mixin.dfc;

import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.moepus.byepregen.dfc.ColumnCodegenContextAccess;
import com.moepus.byepregen.dfc.column.ColumnCodegenHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BytecodeGen.class, remap = false)
public abstract class DfcBytecodeGenMixin {
    @ModifyArg(
            method = "initContext",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/objectweb/asm/ClassWriter;visit(IILjava/lang/String;Ljava/lang/String;"
                            + "Ljava/lang/String;[Ljava/lang/String;)V"
            ),
            index = 5
    )
    private static String[] byepregen$addColumnEntryInterface(String[] interfaces) {
        return ColumnCodegenHooks.addEntryInterface(interfaces);
    }

    @Inject(
            method = "finalizeCompilation",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/objectweb/asm/ClassWriter;toByteArray()[B",
                    shift = At.Shift.BEFORE
            )
    )
    private static void byepregen$finishColumnCodegen(
            BytecodeGen.Context context,
            CallbackInfoReturnable<?> cir
    ) {
        ((ColumnCodegenContextAccess) context).byepregen$finishColumnCodegen();
    }
}

package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.moepus.byepregen.dfc.runtime.ColumnCodegenContextAccess;
import com.moepus.byepregen.dfc.codegen.ColumnCodegenHooks;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@MixinGate(feature = MixinFeature.DFC)
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

    @InjectLite(
            method = "finalizeCompilation",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/objectweb/asm/ClassWriter;toByteArray()[B",
                    shift = At.Shift.BEFORE
            )
    )
    private static void byepregen$finishColumnCodegen(BytecodeGen.Context context) {
        ((ColumnCodegenContextAccess) context).byepregen$finishColumnCodegen();
    }
}

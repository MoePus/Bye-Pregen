package com.moepus.byepregen.mixin.compat;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.statuses.ServerBlockTicking", remap = false)
public abstract class C2MEServerBlockTickingMixin {
    @Redirect(
            method = "upgradeToThis",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/ishland/c2me/rewrites/chunksystem/common/Config;filterFluidPostProcessing:Z",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 0
    )
    private boolean c6c$disableC2MEFluidPostProcessingFilter() {
        return false;
    }
}

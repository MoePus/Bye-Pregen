package com.moepus.byepregen.startup.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public abstract class ServerShutdownMixin {
    @Redirect(method = "waitUntilNextTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private void byepregen$waitWhileRunning(MinecraftServer server, BooleanSupplier ready) {
        // Synchronous fixtures can halt before tick deadlines are initialized. Drain shutdown work
        // without waiting for another tick; stopServer still performs the normal chunk save/close.
        if (server.isRunning()) server.managedBlock(ready);
    }
}

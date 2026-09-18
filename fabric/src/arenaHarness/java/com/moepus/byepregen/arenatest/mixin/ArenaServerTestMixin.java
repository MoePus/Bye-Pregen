package com.moepus.byepregen.arenatest.mixin;

import com.moepus.byepregen.arenatest.ArenaRuntimeVerifier;
import com.moepus.byepregen.featuretest.FeatureRuntimeVerifier;
import com.moepus.byepregen.dfctest.DfcRuntimeVerifier;
import com.moepus.byepregen.storagetest.StorageSerializationVerifier;
import com.moepus.byepregen.palettetest.PaletteRuntimeVerifier;
import com.moepus.byepregen.servertest.ServerTickRuntimeVerifier;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ArenaServerTestMixin {
    @Unique private static final int byepregen$startupTicks = 2;
    @Unique private int byepregen$ticks;
    @Unique private boolean byepregen$tested;

    @Redirect(method = "waitUntilNextTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private void byepregen$waitWhileRunning(MinecraftServer server, java.util.function.BooleanSupplier ready) {
        // Fixtures request shutdown inside tickServer. During shutdown, drain tasks without waiting
        // for another tick deadline; stopServer still waits for chunks and performs its full save/close.
        if (server.isRunning()) server.managedBlock(ready);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void byepregen$verifyArena(CallbackInfo callback) {
        // Let the server initialize tick/task deadlines before synchronous fixtures can request shutdown.
        if (this.byepregen$tested || ++this.byepregen$ticks < byepregen$startupTicks) return;
        this.byepregen$tested = true;
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (System.getProperty("byepregen.worldgenHarness.result") != null) {
            com.moepus.byepregen.worldgentest.WorldgenRuntimeVerifier.verify(server);
        } else if (System.getProperty("byepregen.dfcHarness.result") != null) {
            DfcRuntimeVerifier.verify(server);
        } else if (System.getProperty("byepregen.featureHarness.result") != null) {
            FeatureRuntimeVerifier.verify(server);
        } else if (System.getProperty("byepregen.storageHarness.result") != null) {
            StorageSerializationVerifier.run(server);
        } else if (System.getProperty("byepregen.paletteHarness.result") != null) {
            PaletteRuntimeVerifier.run(server);
        } else if (System.getProperty("byepregen.serverTickHarness.result") != null) {
            ServerTickRuntimeVerifier.run(server);
        } else {
            ArenaRuntimeVerifier.verify(server);
        }
    }
}

package com.moepus.byepregen.startup;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(value = ServerStartupProbe.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class ServerStartupProbe {
    static final String MOD_ID = "byepregen_startup_harness";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ServerStartupProbe() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        server.execute(() -> finish(server));
    }

    private static void finish(MinecraftServer server) {
        try {
            StartupResult.pass("server", "levels=" + server.getAllLevels().spliterator().getExactSizeIfKnown());
            LOGGER.info("BYEPREGEN_STARTUP_SERVER_PASS");
        } catch (Throwable throwable) {
            StartupResult.fail(throwable);
            LOGGER.error("BYEPREGEN_STARTUP_FAIL server", throwable);
        } finally {
            server.halt(false);
        }
    }
}

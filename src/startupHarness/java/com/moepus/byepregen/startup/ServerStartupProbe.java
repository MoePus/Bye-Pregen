package com.moepus.byepregen.startup;

import com.moepus.byepregen.harness.HarnessServerLifecycle;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

public final class ServerStartupProbe {
    static final String MOD_ID = "byepregen_startup_harness";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HarnessServerLifecycle.FailureOptions FAILURE =
            new HarnessServerLifecycle.FailureOptions(
                    StartupResult.RESULT_PROPERTY,
                    LOGGER,
                    "BYEPREGEN_STARTUP_FAIL server"
            );

    public ServerStartupProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        server.execute(() -> finish(server));
    }

    private static void finish(MinecraftServer server) {
        HarnessServerLifecycle.execute(server, FAILURE, () -> {
            StartupResult.pass("server", "levels=" + server.getAllLevels().spliterator().getExactSizeIfKnown());
            LOGGER.info("BYEPREGEN_STARTUP_SERVER_PASS");
        });
    }
}

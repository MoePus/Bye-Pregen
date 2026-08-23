package com.moepus.byepregen.startup;

import com.moepus.byepregen.harness.HarnessServerLifecycle;
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
    private static final HarnessServerLifecycle.FailureOptions FAILURE =
            new HarnessServerLifecycle.FailureOptions(
                    StartupResult.RESULT_PROPERTY,
                    LOGGER,
                    "BYEPREGEN_STARTUP_FAIL server"
            );

    public ServerStartupProbe() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
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

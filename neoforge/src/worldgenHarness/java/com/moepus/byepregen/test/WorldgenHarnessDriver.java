package com.moepus.byepregen.test;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

final class WorldgenHarnessDriver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = WorldgenHarnessProperties.get("world", "minecraft:overworld");
    private static final String MODE = WorldgenHarnessProperties.get("mode", "chunky");
    private static final WorldgenHarnessController HARNESS = new WorldgenHarnessController(MODE, WORLD);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static LightFuzzRun lightFuzzRun;

    private WorldgenHarnessDriver() {
    }

    static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(WorldgenHarnessDriver::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(WorldgenHarnessDriver::onServerStarted);
        NeoForge.EVENT_BUS.addListener(WorldgenHarnessDriver::onServerTickPost);
        LOGGER.info("Registered ByePregen worldgen harness driver");
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        GameRules gameRules = server.getGameRules();
        if ("light_fuzz".equals(MODE)) {
            gameRules.set(GameRules.RANDOM_TICK_SPEED, 0, server);
        }
        LOGGER.info("Configured game rules for ByePregen worldgen harness");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!FastTickRuntimeProbe.verifyBeforeWorldgen()) {
            HARNESS.failAndStop(server, "Fast tick runtime probe failed before worldgen");
            return;
        }
        switch (MODE) {
            case "chunky" -> ChunkyGenerationRun.start(server, HARNESS);
            case "relight" -> LightRelightRun.run(server, HARNESS);
            case "light_fuzz" -> lightFuzzRun = LightFuzzRun.start(
                    server,
                    HARNESS,
                    LightFuzzOptions.fromSystemProperties()
            );
            default -> HARNESS.failAndStop(server, "Unknown ByePregen worldgen test mode: " + MODE);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        LightFuzzRun run = lightFuzzRun;
        if (run != null && run.owns(event.getServer())) {
            run.tick();
            if (run.isComplete()) {
                lightFuzzRun = null;
            }
        }
    }
}

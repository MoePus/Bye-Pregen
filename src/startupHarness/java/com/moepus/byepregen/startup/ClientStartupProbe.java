package com.moepus.byepregen.startup;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

public final class ClientStartupProbe {
    static final String MOD_ID = "byepregen_startup_harness";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int STABLE_TICKS = 5;
    private static final int TIMEOUT_TICKS = 1_200;
    private int ticks;
    private int stableTicks;
    private boolean finished;

    public ClientStartupProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (this.finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ++this.ticks;
        if (minecraft.getWindow() != null && minecraft.screen != null) {
            ++this.stableTicks;
        }
        if (this.stableTicks >= STABLE_TICKS) {
            this.finished = true;
            passAndStop(minecraft);
        } else if (this.ticks >= TIMEOUT_TICKS) {
            this.finished = true;
            failAndStop(minecraft);
        }
    }

    private static void passAndStop(Minecraft minecraft) {
        try {
            StartupResult.pass("client", "screen=" + minecraft.screen.getClass().getName());
            LOGGER.info("BYEPREGEN_STARTUP_CLIENT_PASS");
        } catch (Throwable throwable) {
            StartupResult.fail(throwable);
            LOGGER.error("BYEPREGEN_STARTUP_FAIL client", throwable);
        } finally {
            minecraft.stop();
        }
    }

    private static void failAndStop(Minecraft minecraft) {
        IllegalStateException failure = new IllegalStateException("Client did not reach a stable screen");
        StartupResult.fail(failure);
        LOGGER.error("BYEPREGEN_STARTUP_FAIL client", failure);
        minecraft.stop();
    }
}

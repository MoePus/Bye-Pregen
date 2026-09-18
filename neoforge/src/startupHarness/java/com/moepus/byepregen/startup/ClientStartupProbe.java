package com.moepus.byepregen.startup;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ErrorScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = ClientStartupProbe.MOD_ID, dist = Dist.CLIENT)
public final class ClientStartupProbe {
    static final String MOD_ID = "byepregen_startup_harness";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int STABLE_TICKS = 5;
    private static final int TIMEOUT_TICKS = 1_200;
    private int ticks;
    private int stableTicks;
    private boolean finished;
    private boolean reportedIssues;
    private int warnings;

    public ClientStartupProbe() {
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (this.finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ++this.ticks;
        if (!this.reportedIssues) {
            this.reportedIssues = true;
            if (this.reportLoadingIssues()) {
                this.finishWithFailure(minecraft, "Client has fatal mod loading issues");
                return;
            }
        }
        if (minecraft.gui.screen() instanceof ErrorScreen) {
            this.finishWithFailure(minecraft, "Client reached " + minecraft.gui.screen().getClass().getName());
            return;
        }
        boolean ready = minecraft.getWindow() != null && minecraft.gui.overlay() == null
                && minecraft.gui.screen() instanceof TitleScreen;
        this.stableTicks = ready ? this.stableTicks + 1 : 0;
        if (this.stableTicks >= STABLE_TICKS) {
            this.finished = true;
            this.passAndStop(minecraft);
        } else if (this.ticks >= TIMEOUT_TICKS) {
            this.finishWithFailure(minecraft, "Client did not finish loading and reach the title screen: "
                    + minecraft.gui.screen());
        }
    }

    private boolean reportLoadingIssues() {
        boolean fatal = false;
        for (var issue : ModLoader.getLoadingIssues()) {
            String message = FMLTranslations.translateIssueEnglish(issue);
            if (issue.severity() == ModLoadingIssue.Severity.ERROR) {
                LOGGER.error("Client loading issue: {}", message);
                fatal = true;
            } else {
                ++this.warnings;
                LOGGER.warn("Client loading warning: {}", message);
            }
        }
        return fatal;
    }

    private void passAndStop(Minecraft minecraft) {
        try {
            StartupResult.pass("client", "screen=" + minecraft.gui.screen().getClass().getName()
                    + "\nresourcesLoaded=true\nstableTicks=" + this.stableTicks
                    + "\nloadingErrors=0\nloadingWarnings=" + this.warnings);
            LOGGER.info("BYEPREGEN_STARTUP_CLIENT_PASS");
        } catch (Throwable throwable) {
            StartupResult.fail(throwable);
            LOGGER.error("BYEPREGEN_STARTUP_FAIL client", throwable);
        } finally {
            minecraft.stop();
        }
    }

    private void finishWithFailure(Minecraft minecraft, String message) {
        this.finished = true;
        IllegalStateException failure = new IllegalStateException(message);
        StartupResult.fail(failure);
        LOGGER.error("BYEPREGEN_STARTUP_FAIL client", failure);
        minecraft.stop();
    }
}

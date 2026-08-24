package com.moepus.byepregen.fabric.test;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public final class AttachmentRoundTripHarness implements ModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EXPECTED_VALUE = 0x2612;
    private static final AttachmentType<Integer> TEST_ATTACHMENT = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("byepregen_attachment_harness", "round_trip"),
            Codec.INT
    );

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(AttachmentRoundTripHarness::run);
    }

    private static void run(MinecraftServer server) {
        String phase = System.getProperty("byepregen.attachmentHarness.phase", "");
        try {
            LevelChunk chunk = server.overworld().getChunk(0, 0);
            if ("write".equals(phase)) {
                ((AttachmentTarget) chunk).setAttached(TEST_ATTACHMENT, EXPECTED_VALUE);
                server.saveEverything(true, true, true);
                finish(server, "WRITE_PASS");
            } else if ("verify".equals(phase)) {
                Integer actual = ((AttachmentTarget) chunk).getAttached(TEST_ATTACHMENT);
                if (!Integer.valueOf(EXPECTED_VALUE).equals(actual)) {
                    throw new IllegalStateException("Expected attachment " + EXPECTED_VALUE + ", got " + actual);
                }
                finish(server, "VERIFY_PASS");
            } else {
                throw new IllegalArgumentException("Unknown attachment harness phase: " + phase);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Fabric attachment round-trip failed", throwable);
            writeResult("FAIL: " + throwable);
            server.halt(false);
        }
    }

    private static void finish(MinecraftServer server, String result) {
        writeResult(result);
        LOGGER.info("ByePregen Fabric attachment harness: {}", result);
        server.halt(false);
    }

    private static void writeResult(String value) {
        Path result = Path.of(System.getProperty("byepregen.attachmentHarness.result"));
        try {
            Files.createDirectories(result.toAbsolutePath().getParent());
            Files.writeString(result, value + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write attachment harness result", exception);
        }
    }
}

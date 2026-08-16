package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.HarnessResultFile;
import com.mojang.logging.LogUtils;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

final class WorldgenHarnessController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESULT_PROPERTY = "byepregen.testWorldGen.runResult";
    private static final long PROGRESS_LOG_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final long SHUTDOWN_WATCHDOG_SECONDS = 60L;

    private final String mode;
    private final String world;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicLong nextProgressLog = new AtomicLong();

    WorldgenHarnessController(String mode, String world) {
        this.mode = mode;
        this.world = world;
    }

    String world() {
        return this.world;
    }

    boolean isStopping() {
        return this.stopping.get();
    }

    boolean acquireProgressLog() {
        long now = System.nanoTime();
        long next = this.nextProgressLog.get();
        return now >= next && this.nextProgressLog.compareAndSet(next, now + PROGRESS_LOG_NANOS);
    }

    void failAndStop(MinecraftServer server, String message) {
        LOGGER.error("ByePregen worldgen test failed: {}", message);
        this.finishAndStop(server, RunCompletion.failure(message));
    }

    void succeedAndStop(MinecraftServer server, String detail) {
        this.finishAndStop(server, RunCompletion.success(detail));
    }

    private void finishAndStop(MinecraftServer server, RunCompletion completion) {
        if (this.stopping.compareAndSet(false, true)) {
            server.executeIfPossible(() -> this.stopServer(server, completion));
        }
    }

    private void stopServer(MinecraftServer server, RunCompletion requestedCompletion) {
        RunCompletion completion = requestedCompletion;
        try {
            server.saveAllChunks(false, true, true);
        } catch (Throwable throwable) {
            LOGGER.error("Failed to save ByePregen test world before shutdown", throwable);
            completion = completion.withSaveFailure(throwable);
        }
        try {
            HarnessResultFile.write(RESULT_PROPERTY, completion.format(this.mode));
        } catch (Throwable throwable) {
            LOGGER.error("Failed to write ByePregen worldgen result", throwable);
        }
        server.halt(false);
        forceExitIfShutdownStalls();
    }

    private static void forceExitIfShutdownStalls() {
        Thread watchdog = new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(SHUTDOWN_WATCHDOG_SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            LOGGER.warn("ByePregen worldgen test did not exit after shutdown request; forcing JVM exit");
            System.exit(1);
        }, "ByePregen test shutdown watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private record RunCompletion(boolean success, String detail) {
        private static RunCompletion success(String detail) {
            return new RunCompletion(true, detail);
        }

        private static RunCompletion failure(String message) {
            return new RunCompletion(false, "message=" + message);
        }

        private RunCompletion withSaveFailure(Throwable throwable) {
            return new RunCompletion(false, this.detail + "\nsaveFailure=" + throwable);
        }

        private String format(String mode) {
            return (this.success ? "PASS" : "FAIL") + "\nmode=" + mode + "\n" + this.detail + "\n";
        }
    }
}

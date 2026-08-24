package com.moepus.byepregen.harness;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class HarnessServerLifecycle {
    private HarnessServerLifecycle() {
    }

    public static void execute(
            MinecraftServer server,
            FailureOptions failure,
            CheckedRunnable action
    ) {
        try {
            action.run();
        } catch (Throwable throwable) {
            HarnessResultFile.writeFailure(failure.resultProperty(), throwable);
            failure.logger().error(failure.logMarker(), throwable);
        } finally {
            server.halt(false);
        }
    }

    public record FailureOptions(String resultProperty, Logger logger, String logMarker) {
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }
}

package com.moepus.byepregen.arenatest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

/**
 * Runs one requested harness probe and records its outcome for the Gradle run task.
 *
 * <p>Every {@code byepregen.*Harness.result} dispatcher branch writes {@code PASS} or
 * {@code FAIL} to the file named by that property and then halts the server, so the run
 * task can assert the result without parsing the log.
 */
public final class HarnessResult {
    private HarnessResult() { }

    public static void run(MinecraftServer server, String property, Probe probe) {
        Path result = Path.of(System.getProperty(property));
        try {
            int cases = probe.execute();
            Files.createDirectories(result.getParent());
            Files.writeString(result, "PASS\ncases=" + cases + '\n', StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                Files.writeString(result, "FAIL\n" + failure, StandardCharsets.UTF_8);
            } catch (Exception writeFailure) {
                failure.addSuppressed(writeFailure);
            }
        } finally {
            server.halt(false);
        }
    }

    public interface Probe {
        int execute() throws Exception;
    }
}

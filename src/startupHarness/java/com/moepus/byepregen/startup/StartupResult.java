package com.moepus.byepregen.startup;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class StartupResult {
    private static final String RESULT_PROPERTY = "byepregen.startupResult";

    private StartupResult() {
    }

    static void pass(String side, String detail) {
        write("PASS\nside=" + side + "\n" + detail + "\n");
    }

    static void fail(Throwable throwable) {
        try {
            write("FAIL\n" + throwable + "\n");
        } catch (RuntimeException writeFailure) {
            throwable.addSuppressed(writeFailure);
        }
    }

    private static void write(String result) {
        String value = System.getProperty(RESULT_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing -D" + RESULT_PROPERTY);
        }
        Path path = Path.of(value);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, result, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write startup result " + path, exception);
        }
    }
}

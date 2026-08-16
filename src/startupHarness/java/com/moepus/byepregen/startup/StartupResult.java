package com.moepus.byepregen.startup;

import com.moepus.byepregen.harness.HarnessResultFile;

final class StartupResult {
    private static final String RESULT_PROPERTY = "byepregen.startupResult";

    private StartupResult() {
    }

    static void pass(String side, String detail) {
        write("PASS\nside=" + side + "\n" + detail + "\n");
    }

    static void fail(Throwable throwable) {
        HarnessResultFile.writeFailure(RESULT_PROPERTY, throwable);
    }

    private static void write(String result) {
        try {
            HarnessResultFile.write(RESULT_PROPERTY, result);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write startup result", exception);
        }
    }
}

package com.moepus.byepregen.test;

public final class TestWorldGen {
    public static final String ENABLED_PROPERTY = "byepregen.testWorldGen";
    private static final String CHUNKY_PROVIDER = "org.popcraft.chunky.ChunkyProvider";

    private TestWorldGen() {
    }

    public static void registerIfEnabled() {
        if (!WorldgenHarnessProperties.isEnabled()) {
            return;
        }
        String mode = WorldgenHarnessProperties.get("mode", "chunky");
        if (ArenaPaletteDifferential.MODE.equals(mode)) {
            ArenaPaletteDifferential.register();
            return;
        }
        if (ChunkNbtParity.MODE.equals(mode)) {
            ChunkNbtParity.register();
            return;
        }
        if (LightRestartProbe.MODE.equals(mode)) {
            LightRestartProbe.register();
            return;
        }
        if ("chunky".equals(mode) && !hasClass(CHUNKY_PROVIDER)) {
            throw new IllegalStateException("ByePregen test worldgen requested, but Chunky is not loaded");
        }
        WorldgenHarnessDriver.register();
    }

    private static boolean hasClass(String className) {
        try {
            Class.forName(className, false, TestWorldGen.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}

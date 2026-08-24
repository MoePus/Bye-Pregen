package com.moepus.byepregen.test;

import java.util.Locale;

record LightFuzzOptions(long seed, String variant, boolean probes) {
    private static final long DEFAULT_SEED = 0x59A11E71C0DEL;

    static LightFuzzOptions fromSystemProperties() {
        return new LightFuzzOptions(
                WorldgenHarnessProperties.getLong("lightFuzzSeed", DEFAULT_SEED),
                WorldgenHarnessProperties.get("lightFuzzVariant", "default").toLowerCase(Locale.ROOT),
                WorldgenHarnessProperties.getBoolean("lightFuzzProbes", false)
        );
    }
}

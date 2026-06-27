package com.moepus.byepregen.bootstrap;

import com.moepus.byepregen.test.TestWorldGen;

public final class TestWorldGenBootstrap {
    private TestWorldGenBootstrap() {
    }

    public static void register() {
        TestWorldGen.registerIfEnabled();
    }
}

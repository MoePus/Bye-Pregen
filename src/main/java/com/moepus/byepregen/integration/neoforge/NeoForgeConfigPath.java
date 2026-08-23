package com.moepus.byepregen.integration.neoforge;

import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

public final class NeoForgeConfigPath {
    private static final String CONFIG_FILE_NAME = "byepregen.toml";

    private NeoForgeConfigPath() {
    }

    public static Path resolve() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
    }
}

package com.moepus.byepregen.integration.forge;

import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgeConfigPath {
    private static final String CONFIG_FILE_NAME = "byepregen.toml";

    private ForgeConfigPath() {
    }

    public static Path resolve() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
    }
}

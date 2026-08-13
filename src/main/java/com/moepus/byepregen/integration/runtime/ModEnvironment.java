package com.moepus.byepregen.integration.runtime;

import java.util.Objects;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.spongepowered.asm.service.MixinService;

public final class ModEnvironment {
    private ModEnvironment() {
    }

    public static boolean isModLoaded(String modId) {
        Objects.requireNonNull(modId, "modId");
        LoadingModList modList = LoadingModList.get();
        if (modList.getModFileById(modId) != null) {
            return true;
        }
        return modList.getPlugins().stream()
                .filter(ModFileInfo.class::isInstance)
                .map(ModFileInfo.class::cast)
                .anyMatch(file -> file.getMods().stream().anyMatch(mod -> mod.getModId().equals(modId)));
    }

    public static boolean isClassAvailable(String className) {
        Objects.requireNonNull(className, "className");
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}

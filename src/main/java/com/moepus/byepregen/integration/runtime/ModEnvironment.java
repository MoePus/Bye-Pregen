package com.moepus.byepregen.integration.runtime;

import java.io.IOException;
import java.util.Objects;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
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
        return isClassAvailable(
                className,
                name -> MixinService.getService().getBytecodeProvider().getClassNode(name)
        );
    }

    static boolean isClassAvailable(String className, ClassNodeLookup lookup) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(lookup, "lookup");
        try {
            lookup.get(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect class availability: " + className, exception);
        }
    }

    @FunctionalInterface
    interface ClassNodeLookup {
        ClassNode get(String className) throws ClassNotFoundException, IOException;
    }
}

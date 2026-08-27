package com.moepus.byepregen.integration.runtime;

import java.io.IOException;
import java.util.Objects;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

public final class ModEnvironment {
    private ModEnvironment() {
    }

    public static boolean isModLoaded(String modId) {
        Objects.requireNonNull(modId, "modId");
        return LoadingModList.get().getModFileById(modId) != null;
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

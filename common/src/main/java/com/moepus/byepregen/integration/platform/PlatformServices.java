package com.moepus.byepregen.integration.platform;

import java.util.Iterator;
import java.util.ServiceLoader;

public final class PlatformServices {
    private PlatformServices() {
    }

    public static PlatformBridge get() {
        return Holder.INSTANCE;
    }

    private static PlatformBridge load() {
        ServiceLoader<PlatformBridge> loader = ServiceLoader.load(
                PlatformBridge.class,
                PlatformBridge.class.getClassLoader()
        );
        Iterator<PlatformBridge> providers = loader.iterator();
        if (!providers.hasNext()) {
            throw new IllegalStateException("No ByePregen platform bridge provider is installed");
        }
        PlatformBridge provider = providers.next();
        if (providers.hasNext()) {
            PlatformBridge duplicate = providers.next();
            throw new IllegalStateException("Multiple ByePregen platform bridge providers are installed: "
                    + provider.getClass().getName() + ", " + duplicate.getClass().getName());
        }
        return provider;
    }

    private static final class Holder {
        private static final PlatformBridge INSTANCE = load();
    }
}

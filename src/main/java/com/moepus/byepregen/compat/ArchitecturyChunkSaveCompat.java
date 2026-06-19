package com.moepus.byepregen.compat;

import com.moepus.byepregen.mixin.accessor.ArchitecturyEventImplAccessor;
import dev.architectury.event.events.common.ChunkEvent;
import java.util.ArrayList;
import java.util.List;

public final class ArchitecturyChunkSaveCompat {
    private ArchitecturyChunkSaveCompat() {
    }

    public static boolean hasSaveDataListeners() {
        return !saveDataListenerClassNames().isEmpty();
    }

    public static List<String> saveDataListenerClassNames() {
        if (!(ChunkEvent.SAVE_DATA instanceof ArchitecturyEventImplAccessor accessor)) {
            return List.of(ChunkEvent.SAVE_DATA.getClass().getName());
        }

        ArrayList<?> listeners = accessor.byepregen$listeners();
        ArrayList<String> classNames = new ArrayList<>(listeners.size());
        for (Object listener : listeners) {
            classNames.add(className(listener));
        }
        return classNames;
    }

    private static String className(Object listener) {
        return listener == null ? "null" : listener.getClass().getName();
    }
}

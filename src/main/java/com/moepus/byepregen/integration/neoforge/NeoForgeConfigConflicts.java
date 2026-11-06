package com.moepus.byepregen.integration.neoforge;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NeoForgeConfigConflicts {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeConfigConflicts.class);

    private NeoForgeConfigConflicts() {
    }

    public static Map<String, Set<String>> read() {
        LoadingModList modList = LoadingModList.get();
        Set<IModFileInfo> files = new LinkedHashSet<>(modList.getModFiles());
        files.addAll(modList.getPlugins());
        Map<String, Set<String>> conflicts = new LinkedHashMap<>();
        for (IModFileInfo file : files) {
            readFile(file, conflicts);
        }
        return conflicts;
    }

    private static void readFile(IModFileInfo file, Map<String, Set<String>> conflicts) {
        Object metadata = file.getConfig().getConfigElement("byepregen").orElse(null);
        if (metadata == null) {
            return;
        }
        List<String> modIds = file.getMods().stream().map(IModInfo::getModId).toList();
        if (!(metadata instanceof Map<?, ?> table)) {
            LOGGER.warn("Ignoring invalid byepregen metadata from mods {}: expected a table", modIds);
            return;
        }
        Object value = table.get("conflict");
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> paths)) {
            LOGGER.warn("Ignoring invalid byepregen.conflict from mods {}: expected an array of config paths", modIds);
            return;
        }
        for (Object entry : paths) {
            if (entry instanceof String path) {
                conflicts.computeIfAbsent(path, key -> new LinkedHashSet<>()).addAll(modIds);
            } else {
                LOGGER.warn("Ignoring non-string byepregen.conflict entry {} from mods {}", entry, modIds);
            }
        }
    }
}

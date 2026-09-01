package com.moepus.byepregen.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;

public final class ConfigLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path path;

    public ConfigLoader(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public Config load() {
        if (Files.notExists(this.path)) {
            Config defaults = Config.defaults();
            this.save(defaults);
            return defaults;
        }

        try {
            Config loaded;
            try (Reader reader = Files.newBufferedReader(this.path, StandardCharsets.UTF_8)) {
                loaded = read(TomlFormat.instance().createParser().parse(reader));
            }
            this.save(loaded);
            return loaded;
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Failed to load ByePregen config {}; using defaults", this.path, exception);
            return Config.defaults();
        }
    }

    public boolean save(Config value) {
        Objects.requireNonNull(value, "value");
        Path temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
        try {
            Path parent = this.path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            write(temporary, document(value));
            replace(temporary, this.path);
            return true;
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Failed to save ByePregen config {}", this.path, exception);
            return false;
        } finally {
            deleteTemporary(temporary);
        }
    }

    public Path path() {
        return this.path;
    }

    private static Config read(UnmodifiableConfig source) {
        return read(option -> settingValue(source, option));
    }

    static Config fromOverrides(Map<String, String> source) {
        Objects.requireNonNull(source, "source");
        Map<ConfigOption, BooleanSetting> values = new EnumMap<>(ConfigOption.class);
        source.forEach((path, value) -> {
            ConfigOption option = ConfigOption.fromPath(path);
            try {
                values.put(option, BooleanSetting.parse(value));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid ByePregen test config value at " + path, exception);
            }
        });
        return read(option -> values.getOrDefault(option, BooleanSetting.DEFAULT));
    }

    private static Config read(Function<ConfigOption, BooleanSetting> source) {
        return Config.builder()
                .debug(new Config.Debug(source.apply(ConfigOption.DEBUG_DISABLE_WORLDGEN_FEATURES)))
                .worldgen(readWorldgen(source))
                .server(new Config.Server(new Config.FastChunkTicking(
                        source.apply(ConfigOption.FAST_CHUNK_TICKING))))
                .chunkSaving(new Config.ChunkSaving(
                        source.apply(ConfigOption.GC_FREE_WORLDGEN),
                        source.apply(ConfigOption.RETAIN_BUFFER)))
                .lighting(new Config.Lighting(new Config.Ya(source.apply(ConfigOption.YA_LIGHT))))
                .build();
    }

    private static Config.Worldgen readWorldgen(Function<ConfigOption, BooleanSetting> source) {
        return new Config.Worldgen(
                new Config.PlacedFeatures(
                        source.apply(ConfigOption.PLACED_FEATURES_ENABLED),
                        source.apply(ConfigOption.MEMOIZED_DISK_PLAN),
                        source.apply(ConfigOption.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)),
                new Config.Arena(
                        source.apply(ConfigOption.ARENA_ENABLED),
                        source.apply(ConfigOption.DENSITY_COLUMN_COMPILER),
                        new Config.ArenaRuntime(
                                source.apply(ConfigOption.SERVER_RUNTIME_ARENA))),
                new Config.Surface(source.apply(ConfigOption.SURFACE_BIOME_CACHE)),
                new Config.Misc(source.apply(ConfigOption.FLAT_CACHE_ACCESS))
        );
    }

    private static BooleanSetting settingValue(UnmodifiableConfig source, ConfigOption option) {
        Object value = rawValue(source, option.path());
        if (value == null) {
            return BooleanSetting.DEFAULT;
        }
        try {
            return BooleanSetting.parse(value);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid ByePregen config value at {}: expected Default, True, or False; using Default",
                    option.path());
            return BooleanSetting.DEFAULT;
        }
    }

    private static Object rawValue(UnmodifiableConfig source, String path) {
        String[] segments = path.split("\\.");
        UnmodifiableConfig current = source;
        for (int index = 0; index < segments.length - 1; index++) {
            Object child = current.getRaw(List.of(segments[index]));
            if (child == null) {
                return null;
            }
            if (!(child instanceof UnmodifiableConfig childConfig)) {
                String prefix = String.join(".", List.of(segments).subList(0, index + 1));
                LOGGER.warn("Invalid ByePregen config table at {}; using defaults", prefix);
                return null;
            }
            current = childConfig;
        }
        return current.getRaw(List.of(segments[segments.length - 1]));
    }

    private static CommentedConfig document(Config value) {
        CommentedConfig root = newDocument();
        addDebug(root, value.debug());
        addWorldgen(root, value.worldgen());
        addServer(root, value.server());
        addChunkSaving(root, value.chunkSaving());
        addLighting(root, value.lighting());
        return root;
    }

    private static void addDebug(CommentedConfig root, Config.Debug value) {
        CommentedConfig debug = table(root, "debug",
                "Use 'Default' for the stated default, or 'True'/'False' to override it.\n"
                        + "All options in this file are read at startup and require a game restart.");
        option(debug, "disable-worldgen-features", value.disableWorldgenFeaturesSetting(),
                "Default: False\nDebug use only. Change this only if you fully understand what it does.");
    }

    private static void addWorldgen(CommentedConfig root, Config.Worldgen value) {
        CommentedConfig worldgen = table(root, "worldgen", "Optimizations used while generating new chunks.");
        CommentedConfig placed = table(worldgen, "placed-features",
                "Optimizations for vegetation, ores, lakes, and other placed features.");
        option(placed, "enabled", value.placedFeatures().enabledSetting(),
                "Default: False\nUses direct loops and reusable placement state to avoid temporary streams,\n"
                        + "iterators, and block positions. Experimental; modded features may be placed incorrectly\n"
                        + "if they rely on details of the vanilla placement path.");
        option(placed, "local-optimizations", value.placedFeatures().localOptimizationsSetting(),
                "Default: True\nApplies mathematically equivalent local placement and predicate optimizations\n"
                        + "without replacing the complete placed-feature pipeline.");
        option(placed, "memoized-disk-plan", value.placedFeatures().memoizedDiskPlanSetting(),
                "Default: True\nReuses block-predicate results while disk features such as clay and sand\n"
                        + "are placed, trading a small amount of temporary memory for fewer block lookups.");

        CommentedConfig arena = table(worldgen, "arena",
                "Page-based block-state storage optimized for chunk generation.");
        option(arena, "enabled", value.arena().enabledSetting(),
                "Default: True\nStores section block states in compact page-based palettes and batches terrain\n"
                        + "writes directly into them. Mods that directly access vanilla palettes may malfunction.");
        option(arena, "density-column-compiler", value.arena().densityColumnCompilerSetting(),
                "Default: True\nCompiles final-density graphs into JVM code that evaluates complete vertical\n"
                        + "columns at once. The first use adds compilation work and creates generated JVM classes.");
        option(arena, "server-runtime", value.arena().runtime().serverSetting(),
                "Default: False\nKeeps Arena block storage after chunks finish generation instead of converting\n"
                        + "it to vanilla storage. Server mods that directly access vanilla palettes may malfunction.");

        CommentedConfig surface = table(worldgen, "surface",
                "Optimizations used while applying topsoil, beaches, bedrock, and other surface layers.");
        option(surface, "biome-cache", value.surface().biomeCacheSetting(),
                "Default: True\nCaches each chunk's quart-biome grid and skips repeated biome zoom lookups in\n"
                        + "uniform areas. Uses additional temporary memory while the chunk surface is generated.");

        CommentedConfig misc = table(worldgen, "misc", "Independent world-generation optimizations.");
        option(misc, "flat-cache-access", value.misc().flatCacheAccessSetting(),
                "Default: True\nLets compiled density columns read NoiseChunk flat-cache values directly.\n"
                        + "When disabled, density functions are evaluated through their standard compute method.");
    }

    private static void addServer(CommentedConfig root, Config.Server value) {
        CommentedConfig server = table(root, "server", "Dedicated and integrated server optimizations.");
        CommentedConfig ticking = table(server, "fast-chunk-ticking",
                "Experimental replacement for the server's loaded-chunk tick loop.");
        option(ticking, "enabled", value.fastChunkTicking().enabledSetting(),
                "Default: False\nReuses arrays, lists, and chunk lookups during chunk ticking, natural spawning,\n"
                        + "and weather updates. Experimental; it may affect spawning, weather, or modded tick logic.");
    }

    private static void addChunkSaving(CommentedConfig root, Config.ChunkSaving value) {
        CommentedConfig saving = table(root, "chunk-saving", "Chunk serialization and storage options.");
        option(saving, "gc-free-worldgen", value.gcFreeWorldgenSetting(),
                "Default: False\nSerializes eligible chunks directly to compressed NBT bytes instead of building\n"
                        + "an intermediate NBT object tree. Mods that inspect chunk NBT during saving may be affected.\n"
                        + "Compatibility on Minecraft 1.20.1 is very limited. Enabling this option is not recommended\n"
                        + "when C2ME or one of its ports is installed.");
        option(saving, "retain-buffer", value.retainBufferSetting(),
                "Default: True\nReuses one NBT writer and compressor per saving worker thread, releasing buffers\n"
                        + "larger than 512 KiB. Reduces allocation churn but retains memory on each worker thread.");
    }

    private static void addLighting(CommentedConfig root, Config.Lighting value) {
        CommentedConfig lighting = table(root, "lighting", "Lighting-engine options.");
        CommentedConfig ya = table(lighting, "ya",
                "Performance patches for Minecraft's vanilla block-light and sky-light engines.");
        option(ya, "enabled", value.ya().enabledSetting(),
                "Default: False\nExperimental. Patches vanilla light storage and scheduling, making light\n"
                        + "calculations about 4.5x faster and light-data reads over 3x faster. On clients, this may\n"
                        + "also provide a small improvement to 1% low FPS.");
    }

    private static CommentedConfig newDocument() {
        return TomlFormat.instance().createConfig(LinkedHashMap::new);
    }

    private static CommentedConfig table(CommentedConfig parent, String name, String comment) {
        CommentedConfig child = parent.createSubConfig();
        parent.set(name, child);
        parent.setComment(name, formatComment(comment));
        return child;
    }

    private static void option(CommentedConfig table, String name, BooleanSetting value, String comment) {
        table.set(name, value.serializedValue());
        table.setComment(name, formatComment(comment));
    }

    private static String formatComment(String comment) {
        return " " + comment.replace("\n", "\n ");
    }

    private static void write(Path path, CommentedConfig document) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            TomlWriter tomlWriter = TomlFormat.instance().createWriter();
            tomlWriter.setIndent("    ");
            tomlWriter.write(document, writer);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTemporary(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            LOGGER.debug("Failed to remove temporary ByePregen config {}", temporary, exception);
        }
    }
}

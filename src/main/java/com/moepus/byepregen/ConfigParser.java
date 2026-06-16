package com.moepus.byepregen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.Supplier;
import net.neoforged.fml.loading.FMLPaths;

public final class ConfigParser {
    private static Config config;
    static Supplier<String> configPath = () -> String.valueOf(Paths.get(
            String.valueOf(FMLPaths.CONFIGDIR.get()),
            "byepregen.json"
    ));

    private ConfigParser() {
    }

    public static Config getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }

    public static void loadConfig() {
        Gson gson = new Gson();
        File configFile = new File(configPath.get());
        if (!configFile.exists()) {
            config = new Config();
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            config = gson.fromJson(reader, Config.class);
            if (config == null) {
                config = new Config();
            }
            saveConfig();
        } catch (JsonIOException | JsonSyntaxException | IOException e) {
            e.printStackTrace();
            config = new Config();
        }
    }

    public static void saveConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(configPath.get())) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

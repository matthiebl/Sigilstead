package com.heartstead.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.heartstead.Heartstead;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads {@link HsConfig} from {@code config/heartstead.json}, writing the defaults on first run.
 * A missing or malformed file falls back to {@link HsConfig#DEFAULT} rather than crashing startup.
 */
public final class HsConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static HsConfig config;

    private HsConfigManager() {
    }

    public static HsConfig get() {
        if (config == null) {
            config = load();
        }
        return config;
    }

    private static HsConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("heartstead.json");

        if (Files.exists(path)) {
            try {
                JsonElement json = JsonParser.parseString(Files.readString(path));
                DataResult<HsConfig> result = HsConfig.CODEC.parse(JsonOps.INSTANCE, json);
                var parsed = result.result();
                if (parsed.isPresent()) {
                    return parsed.get();
                }
                Heartstead.LOG.warn("Malformed heartstead.json ({}), using defaults", result.error().orElseThrow().message());
            } catch (IOException | RuntimeException e) {
                Heartstead.LOG.warn("Failed to read heartstead.json, using defaults", e);
            }
            return HsConfig.DEFAULT;
        }

        write(path, HsConfig.DEFAULT);
        return HsConfig.DEFAULT;
    }

    private static void write(Path path, HsConfig value) {
        try {
            JsonElement json = HsConfig.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json));
        } catch (IOException | RuntimeException e) {
            Heartstead.LOG.warn("Failed to write default heartstead.json", e);
        }
    }
}

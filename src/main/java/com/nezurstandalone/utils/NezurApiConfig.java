package com.nezurstandalone.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Optional API keys in config/nezur-acg/api.json (not committed).
 */
public final class NezurApiConfig {

    private static final File API_FILE = new File("config/nezur-acg/api.json");
    private static final String DEFAULT_PITPANDA_KEY = "";

    private static String pitPandaKey = DEFAULT_PITPANDA_KEY;

    private NezurApiConfig() {
    }

    public static void load() {
        ConfigManager.init();
        if (!API_FILE.exists()) {
            pitPandaKey = DEFAULT_PITPANDA_KEY;
            save();
            return;
        }
        try (FileReader reader = new FileReader(API_FILE)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (root.has("pitPandaKey")) {
                String key = root.get("pitPandaKey").getAsString();
                if (key != null && !key.trim().isEmpty()) {
                    pitPandaKey = key.trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getPitPandaKey() {
        return pitPandaKey;
    }

    public static void setPitPandaKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            pitPandaKey = DEFAULT_PITPANDA_KEY;
        } else {
            pitPandaKey = key.trim();
        }
        save();
    }

    public static void save() {
        ConfigManager.init();
        JsonObject root = new JsonObject();
        root.addProperty("pitPandaKey", pitPandaKey);
        try {
            AtomicConfigFiles.write(API_FILE.toPath(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

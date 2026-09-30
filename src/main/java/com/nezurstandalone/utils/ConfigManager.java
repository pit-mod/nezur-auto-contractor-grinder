package com.nezurstandalone.utils;

import com.google.gson.*;
import com.nezurstandalone.Nezur;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.ColorSetting;
import com.nezurstandalone.settings.InputSetting;
import com.nezurstandalone.settings.KeybindSetting;
import com.nezurstandalone.settings.ModeSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.settings.Setting;

import java.io.*;
import java.awt.Color;
import java.util.Map;

public class ConfigManager {
    private static final File CONFIG_DIR = new File("config/nezur-acg");
    private static final File MODULES_FILE = new File(CONFIG_DIR, "modules.json");
    private static final File FRIENDS_FILE = new File(CONFIG_DIR, "friends.json");
    private static final File TRUCE_FILE = new File(CONFIG_DIR, "truce.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private enum PlayerListType {
        FRIEND,
        TRUCE
    }

    public static void init() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
    }

    private static boolean playerListsMigrated;

    public static void loadConfig() {
        init();
        NezurApiConfig.load();
        playerListsMigrated = false;
        if (!MODULES_FILE.exists()) {
            try (InputStream defaults=ConfigManager.class.getResourceAsStream("/nezur-default-modules.json")) {
                if(defaults==null)throw new IOException("Bundled defaults missing");
                java.nio.file.Files.copy(defaults,MODULES_FILE.toPath());
                loadModules();
            } catch(IOException failure) {
                ConfigPresets.importFromCode(ConfigPresets.DEFAULT_CONFIG_CODE, "default config");
                ConfigPresets.importPreset("default config");
            }
            persistAll();
        } else {
        HudPositionManager.load();
        loadModules();
        loadPlayerMap(FRIENDS_FILE, PlayerListType.FRIEND);
        loadPlayerMap(TRUCE_FILE, PlayerListType.TRUCE);
        }
        if (playerListsMigrated) {
            savePlayerMap(FRIENDS_FILE, FriendManager.getFriends());
            savePlayerMap(TRUCE_FILE, TruceManager.getTrucePlayers());
        }
    }

    public static void saveConfig() {
        ConfigSaveDebouncer.markDirty();
    }

    public static void persistAll() {
        init();
        java.util.Map<java.nio.file.Path,byte[]> bundle=new java.util.LinkedHashMap<>();
        bundle.put(MODULES_FILE.toPath(),jsonBytes(moduleSnapshot()));
        bundle.put(new File(CONFIG_DIR,"hud_positions.json").toPath(),jsonBytes(HudPositionManager.snapshotJson()));
        bundle.put(FRIENDS_FILE.toPath(),jsonBytes(playerSnapshot(FriendManager.getFriends())));
        bundle.put(TRUCE_FILE.toPath(),jsonBytes(playerSnapshot(TruceManager.getTrucePlayers())));
        try {AtomicConfigFiles.replaceBundle(bundle);} catch(IOException failure){throw new UncheckedIOException(failure);}
    }

    private static byte[] jsonBytes(JsonObject root) {return GSON.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8);}

    private static JsonObject moduleSnapshot() {
        JsonObject root = new JsonObject();
        for (Module module : Nezur.moduleManager.getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("toggled", module.isEnabledRequested());
            moduleJson.addProperty("keybind", module.keybind.code);

            JsonObject settingsJson = new JsonObject();
            for (Setting setting : module.settings) {
                settingsJson.add(setting.name, com.nezurstandalone.settings.SettingCodec.encode(setting));
            }
            if (module.settings.size() > 0) {
                moduleJson.add("settings", settingsJson);
            }
            root.add(module.getName(), moduleJson);
        }

        return root;
    }

    private static void loadModules() {
        if (!MODULES_FILE.exists()) return;

        try (FileReader reader = new FileReader(MODULES_FILE)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();

            for (Module module : Nezur.moduleManager.getModules()) {
                if (root.has(module.getName())) {
                    JsonObject moduleJson = root.getAsJsonObject(module.getName());
                    
                    if (moduleJson.has("toggled")) {
                        module.setToggledFromConfig(moduleJson.get("toggled").getAsBoolean());
                    }
                    if (moduleJson.has("keybind")) {
                        module.keybind.code = moduleJson.get("keybind").getAsInt();
                    }

                    if (moduleJson.has("settings")) {
                        JsonObject settingsJson = moduleJson.getAsJsonObject("settings");
                        HudPositionManager.migrateFromSettings(module.getName(), settingsJson);
                        for (Setting setting : module.settings) {
                            String jsonKey = settingsJson.has(setting.name) ? setting.name : ("Hide in HUD".equals(setting.name) && settingsJson.has("Hid in HUD") ? "Hid in HUD" : null);
                            if (jsonKey != null) {
                                com.nezurstandalone.settings.SettingCodec.decode(setting, settingsJson.get(jsonKey)).run();
                            }
                        }
                    }
                }
            }
            // Keybinds were written straight into the field above, which skips the setter that
            // normally invalidates the lookup - so rebuild it once the whole file is applied.
            KeybindRegistry.markStale();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JsonObject playerSnapshot(Map<String,String> players) {
        JsonObject root=new JsonObject();for(Map.Entry<String,String> entry:players.entrySet())root.addProperty(entry.getKey(),entry.getValue());return root;
    }

    private static void savePlayerMap(File file, Map<String,String> players) {
        try {AtomicConfigFiles.write(file.toPath(),jsonBytes(playerSnapshot(players)));}
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }

    private static void loadPlayerMap(File file, PlayerListType type) {
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            JsonElement parsed = new JsonParser().parse(reader);

            if (parsed.isJsonArray()) {
                loadLegacyNameArray(parsed.getAsJsonArray(), type);
                playerListsMigrated = true;
                return;
            }

            if (!parsed.isJsonObject()) return;

            JsonObject root = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                loadEntry(type, entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadEntry(PlayerListType type, String uuidKey, String displayName) {
        switch (type) {
            case FRIEND:
                FriendManager.loadEntry(uuidKey, displayName);
                break;
            case TRUCE:
                TruceManager.loadEntry(uuidKey, displayName);
                break;
        }
    }

    private static void clearList(PlayerListType type) {
        switch (type) {
            case FRIEND:
                FriendManager.clear();
                break;
            case TRUCE:
                TruceManager.clear();
                break;
        }
    }

    private static void addToList(PlayerListType type, String name, String uuid) {
        switch (type) {
            case FRIEND:
                FriendManager.add(name, uuid);
                break;
            case TRUCE:
                TruceManager.add(name, uuid);
                break;
        }
    }

    private static void loadLegacyNameArray(JsonArray array, PlayerListType type) {
        clearList(type);

        AsyncExecutor.runAsync(() -> {
            for (JsonElement element : array) {
                String name = element.getAsString();
                MojangCache.fetchUuidAsync(name, uuid -> {
                    if (uuid == null) {
                        System.out.println("[Nezur] Could not resolve UUID for " + name + " during migration.");
                    } else {
                        addToList(type, name, uuid);
                    }
                });
            }
        });
    }
}

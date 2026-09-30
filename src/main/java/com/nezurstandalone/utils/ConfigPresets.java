package com.nezurstandalone.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nezurstandalone.Nezur;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.ColorSetting;
import com.nezurstandalone.settings.InputSetting;
import com.nezurstandalone.settings.KeybindSetting;
import com.nezurstandalone.settings.ModeSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.settings.Setting;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Export/import module toggle + settings presets under config/nezur-acg/presets/.
 */
public final class ConfigPresets {

    public static final String DEFAULT_CONFIG_CODE = "Nezur-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";

    private static final File PRESET_DIR = new File("config/nezur-acg/presets");

    private ConfigPresets() {
    }

    public static boolean exportPreset(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        ConfigManager.init();
        if (!PRESET_DIR.exists()) {
            PRESET_DIR.mkdirs();
        }
        File out = new File(PRESET_DIR, sanitize(name) + ".json");
        try {
            ConfigManager.persistAll();
            Files.copy(new File("config/nezur-acg/modules.json").toPath(), out.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            copyIfExists("config/nezur-acg/friends.json", PRESET_DIR, sanitize(name) + "_friends.json");
            copyIfExists("config/nezur-acg/truce.json", PRESET_DIR, sanitize(name) + "_truce.json");
            copyIfExists("config/nezur-acg/hud_positions.json", PRESET_DIR, sanitize(name) + "_hud.json");
            NotificationManager.show("\u00a7aExported preset bundle: \u00a7f" + out.getName(), 4000);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            NotificationManager.show("\u00a7cExport failed.", 3000);
            return false;
        }
    }

    public static boolean importPreset(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String safe=sanitize(name);
        File preset=new File(PRESET_DIR,safe+".json");
        if (!preset.exists()) return false;
        try {
            PresetTransaction prepared=PresetTransaction.prepare(Nezur.moduleManager.getModules(),
                    BoundedJson.readObject(preset.toPath()),readSidecar(safe,"friends"),readSidecar(safe,"truce"),readSidecar(safe,"hud"));
            NotificationManager.setSuppressModuleToggle(true);
            try { prepared.apply(); } finally { NotificationManager.setSuppressModuleToggle(false); }
            ConfigSaveDebouncer.markDirty();
            NotificationManager.show("Preset imported: "+preset.getName(),4000);
            return true;
        } catch (Exception failure) {
            java.util.logging.Logger.getLogger(ConfigPresets.class.getName()).log(java.util.logging.Level.WARNING,"Preset import rejected; no partial config accepted",failure);
            NotificationManager.show("Preset import failed.",3000);
            return false;
        }
    }

    private static JsonObject readSidecar(String name,String suffix) throws java.io.IOException {
        File file=new File(PRESET_DIR,name+"_"+suffix+".json");
        return file.exists()?BoundedJson.readObject(file.toPath()):new JsonObject();
    }

    private static String sanitize(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static void copyIfExists(String src, File dir, String destName) {
        File source = new File(src);
        if (!source.exists()) {
            return;
        }
        try {
            Files.copy(source.toPath(), new File(dir, destName).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }
    
    public static java.util.List<String> listPresets() {
        java.util.List<String> presets = new java.util.ArrayList<>();
        if (!PRESET_DIR.exists()) return presets;
        File[] files = PRESET_DIR.listFiles((dir, name) -> name.endsWith(".json") && !name.endsWith("_kos.json") && !name.endsWith("_friends.json") && !name.endsWith("_truce.json") && !name.endsWith("_hud.json"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                presets.add(name.substring(0, name.length() - 5)); // remove .json
            }
        }
        return presets;
    }

    public static boolean deletePreset(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        File preset = new File(PRESET_DIR, sanitize(name) + ".json");
        if (!preset.exists()) return false;
        
        boolean deleted = preset.delete();
        File friends = new File(PRESET_DIR, sanitize(name) + "_friends.json");
        if (friends.exists()) friends.delete();
        File truce = new File(PRESET_DIR, sanitize(name) + "_truce.json");
        if (truce.exists()) truce.delete();
        File hud = new File(PRESET_DIR, sanitize(name) + "_hud.json");
        if (hud.exists()) hud.delete();
        
        return deleted;
    }

    public static boolean renamePreset(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.trim().isEmpty() || newName.trim().isEmpty()) return false;
        
        String safeOld = sanitize(oldName);
        String safeNew = sanitize(newName);
        if (safeOld.equals(safeNew)) return false;
        
        File oldPreset = new File(PRESET_DIR, safeOld + ".json");
        if (!oldPreset.exists()) return false;
        
        File newPreset = new File(PRESET_DIR, safeNew + ".json");
        if (newPreset.exists()) return false;
        
        boolean renamed = oldPreset.renameTo(newPreset);
        if (renamed) {
            renameIfExists(safeOld + "_friends.json", safeNew + "_friends.json");
            renameIfExists(safeOld + "_truce.json", safeNew + "_truce.json");
            renameIfExists(safeOld + "_hud.json", safeNew + "_hud.json");
        }
        return renamed;
    }

    private static void renameIfExists(String oldName, String newName) {
        File oldFile = new File(PRESET_DIR, oldName);
        if (oldFile.exists()) {
            oldFile.renameTo(new File(PRESET_DIR, newName));
        }
    }

    public static String exportToCode(String name, boolean incModules, boolean incFriends, boolean incTruce, boolean incHud) {
        if (name == null || name.trim().isEmpty()) return null;
        File preset = new File(PRESET_DIR, sanitize(name) + ".json");
        if (!preset.exists()) return null;

        JsonObject root = new JsonObject();
        
        if (incModules) root.add("modules", parseFileOrEmpty(preset));
        
        
        if (incFriends) {
            File friends = new File(PRESET_DIR, sanitize(name) + "_friends.json");
            if (friends.exists()) root.add("friends", parseFileOrEmpty(friends));
        }
        
        if (incTruce) {
            File truce = new File(PRESET_DIR, sanitize(name) + "_truce.json");
            if (truce.exists()) root.add("truce", parseFileOrEmpty(truce));
        }
        
        if (incHud) {
            File hud = new File(PRESET_DIR, sanitize(name) + "_hud.json");
            if (hud.exists()) root.add("hud", parseFileOrEmpty(hud));
        }

        String json = new com.google.gson.Gson().toJson(root);
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos);
            gzip.write(json.getBytes("UTF-8"));
            gzip.close();
            String b64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            return "Nezur-" + b64;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static com.google.gson.JsonElement parseFileOrEmpty(File file) {
        try (FileReader reader = new FileReader(file)) {
            return new JsonParser().parse(reader);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static boolean importFromCode(String code, String newName) {
        if (newName == null || newName.trim().isEmpty()) return false;
        try {
            JsonObject root = PresetCode.decode(code);
            // Ignore deprecated list data in old shared codes without loading or persisting it.
            root.remove("kos");
            java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList("modules","friends","truce","hud"));
            for (java.util.Map.Entry<String,com.google.gson.JsonElement> entry : root.entrySet()) {
                if (!allowed.contains(entry.getKey()) || !entry.getValue().isJsonObject()) throw new java.io.IOException("Invalid preset section");
            }
            String safeName = sanitize(newName);
            java.util.Map<java.nio.file.Path,byte[]> bundle = new java.util.LinkedHashMap<>();
            // Empty sidecars explicitly replace omitted lists; the visible main file commits last.
            String[] sections={"friends","truce","hud","modules"};
            com.google.gson.Gson gson=new com.google.gson.Gson();
            for(String section:sections){
                com.google.gson.JsonElement value=root.has(section)?root.get(section):new JsonObject();
                String suffix=section.equals("modules")?"":"_"+section;
                bundle.put(new File(PRESET_DIR,safeName+suffix+".json").toPath(),gson.toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            AtomicConfigFiles.replaceBundle(bundle);
            return true;
        } catch (Exception failure) {
            java.util.logging.Logger.getLogger(ConfigPresets.class.getName()).log(java.util.logging.Level.WARNING,"Preset import rejected",failure);
            return false;
        }
    }

    private static void saveElementToFile(com.google.gson.JsonElement element, File file) {
        try {
            AtomicConfigFiles.write(file.toPath(),new com.google.gson.Gson().toJson(element).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

}

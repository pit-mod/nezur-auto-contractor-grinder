package com.nezurstandalone.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class HudPositionManager {
    private static final File POSITIONS_FILE = new File("config/nezur-acg/hud_positions.json");

    public static class PositionData {
        public double x;
        public double y;
        public double sw;
        public double sh;

        public PositionData(double x, double y, double sw, double sh) {
            this.x = x;
            this.y = y;
            this.sw = sw;
            this.sh = sh;
        }
    }

    private static final Map<String, PositionData> positions = new HashMap<>();

    public static Map<String, PositionData> snapshot() {
        Map<String, PositionData> copy = new HashMap<>();
        for (Map.Entry<String, PositionData> e : positions.entrySet()) {
            PositionData p = e.getValue();copy.put(e.getKey(),new PositionData(p.x,p.y,p.sw,p.sh));
        }
        return copy;
    }

    public static void restore(Map<String, PositionData> snapshot) {
        positions.clear();
        for (Map.Entry<String, PositionData> e : snapshot.entrySet()) {
            PositionData p=e.getValue();positions.put(e.getKey(),new PositionData(p.x,p.y,p.sw,p.sh));
        }
    }

    public static double getX(String key, double defaultX) {
        PositionData pos = positions.get(key);
        if (pos == null) return defaultX;
        if (pos.sw <= 0) return pos.x;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();
                double curSw = sr.getScaledWidth();
                if (curSw > 0) {
                    return pos.x * (curSw / pos.sw);
                }
            }
        } catch (Exception ignored) {}
        return pos.x;
    }

    public static double getY(String key, double defaultY) {
        PositionData pos = positions.get(key);
        if (pos == null) return defaultY;
        if (pos.sh <= 0) return pos.y;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();
                double curSh = sr.getScaledHeight();
                if (curSh > 0) {
                    return pos.y * (curSh / pos.sh);
                }
            }
        } catch (Exception ignored) {}
        return pos.y;
    }

    public static void set(String key, double x, double y) {
        double sw = 0;
        double sh = 0;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();
                sw = sr.getScaledWidth();
                sh = sr.getScaledHeight();
            }
        } catch (Exception ignored) {}
        set(key, x, y, sw, sh);
    }

    public static void set(String key, double x, double y, double sw, double sh) {
        positions.put(key, new PositionData(x, y, sw, sh));
    }

    public static void setBounded(String key, double x, double y, int width, int height, boolean centerAnchored) {
        int[] clamped = HudBounds.clamp((int) x, (int) y, width, height, centerAnchored);
        set(key, clamped[0], clamped[1]);
    }

    public static void clampSavedPositions(com.nezurstandalone.module.ModuleManager moduleManager) {
        // Dynamic clamping is performed at render time in DraggableHud
    }

    public static void migrateFromSettings(String key, JsonObject settingsJson) {
        if (settingsJson == null || positions.containsKey(key)) {
            return;
        }
        if (settingsJson.has("X Pos") && settingsJson.has("Y Pos")) {
            set(key,
                    settingsJson.get("X Pos").getAsDouble(),
                    settingsJson.get("Y Pos").getAsDouble());
        }
    }

    public static void load() {
        positions.clear();
        ConfigManager.init();
        if (!POSITIONS_FILE.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(POSITIONS_FILE)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject obj = entry.getValue().getAsJsonObject();
                if (obj.has("x") && obj.has("y")) {
                    double x = obj.get("x").getAsDouble();
                    double y = obj.get("y").getAsDouble();
                    double sw = obj.has("sw") ? obj.get("sw").getAsDouble() : 0.0;
                    double sh = obj.has("sh") ? obj.get("sh").getAsDouble() : 0.0;

                    if (sw == 0.0 || sh == 0.0) {
                        try {
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc != null) {
                                ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();
                                sw = sr.getScaledWidth();
                                sh = sr.getScaledHeight();
                            }
                        } catch (Exception ignored) {}
                    }

                    set(entry.getKey(), x, y, sw, sh);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JsonObject snapshotJson() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, PositionData> entry : positions.entrySet()) {
            JsonObject pos = new JsonObject();
            PositionData p = entry.getValue();
            pos.add("x", new JsonPrimitive(p.x));
            pos.add("y", new JsonPrimitive(p.y));
            if (p.sw > 0) pos.add("sw", new JsonPrimitive(p.sw));
            if (p.sh > 0) pos.add("sh", new JsonPrimitive(p.sh));
            root.add(entry.getKey(), pos);
        }
        return root;
    }

    public static void save() {
        ConfigManager.init();
        try {
            AtomicConfigFiles.write(POSITIONS_FILE.toPath(),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(snapshotJson()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
    }
}

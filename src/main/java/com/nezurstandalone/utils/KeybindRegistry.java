package com.nezurstandalone.utils;

import com.nezurstandalone.Nezur;
import com.nezurstandalone.module.Module;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O(1) keybind lookup instead of scanning all modules each key press.
 */
public final class KeybindRegistry {

    private static Map<Integer, List<Module>> byKey = new HashMap<>();
    private static boolean stale = true;

    private KeybindRegistry() {
    }

    public static void markStale() {
        stale = true;
    }

    public static void rebuildIfNeeded() {
        if (!stale || Nezur.moduleManager == null) {
            return;
        }
        Map<Integer, List<Module>> map = new HashMap<>();
        for (Module module : Nezur.moduleManager.getModules()) {
            int code = module.keybind.code;
            if (code != Keyboard.KEY_NONE) {
                map.computeIfAbsent(code, k -> new ArrayList<>()).add(module);
            }
            for (com.nezurstandalone.settings.KeybindSetting extra : module.getExtraKeybinds()) {
                if (extra.code == Keyboard.KEY_NONE || extra.code == code) {
                    continue;
                }
                map.computeIfAbsent(extra.code, k -> new ArrayList<>()).add(module);
            }
        }
        byKey = map;
        stale = false;
    }

    public static List<Module> getModulesForKey(int key) {
        rebuildIfNeeded();
        List<Module> list = byKey.get(key);
        return list != null ? list : java.util.Collections.emptyList();
    }
}

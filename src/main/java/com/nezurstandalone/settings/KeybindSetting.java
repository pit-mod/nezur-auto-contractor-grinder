package com.nezurstandalone.settings;

public class KeybindSetting extends Setting {
    public int code;
    
    public KeybindSetting(int code) {
        super("Keybind");
        this.code = code;
    }

    public KeybindSetting(String name, int code) {
        super(name);
        this.code = code;
    }

    /**
     * Rebinding has to invalidate the keybind lookup, not just notify listeners.
     *
     * <p>{@code KeybindRegistry} caches a key-to-module map and only rebuilds when something marks
     * it stale, and the only things that did were a module toggle and client start-up. A key set in
     * the ClickGUI therefore did nothing until the module was toggled once by hand - which rebuilt
     * the map as a side effect - and that was the whole "I have to enable it once before the bind
     * works" bug. Marking the registry stale here makes a new bind live immediately, and marking
     * the config dirty means it survives a restart.
     */
    public void setKey(int code) {
        if (this.code != code) {
            this.code = code;
            notifyChange();
            com.nezurstandalone.utils.KeybindRegistry.markStale();
            com.nezurstandalone.utils.ConfigSaveDebouncer.markDirty();
        }
    }
}

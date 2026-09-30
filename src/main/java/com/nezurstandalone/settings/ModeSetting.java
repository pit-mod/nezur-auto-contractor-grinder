package com.nezurstandalone.settings;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting {
    public int index;
    public List<String> modes;
    
    public ModeSetting(String name, String defaultMode, String... modes) {
        super(name);
        this.modes = Arrays.asList(modes);
        this.index = this.modes.indexOf(defaultMode);
        if (this.index == -1) this.index = 0;
    }
    
    public String getMode() {
        return modes.get(index);
    }
    
    public void cycle() {
        index = (index + 1) % modes.size();
        changed();
    }

    public void setMode(String mode) {
        int i = modes.indexOf(mode);
        if (i != -1 && i != index) {
            index = i;
            changed();
        }
    }

    /**
     * A mode change has to reach the config, not just the listeners. Only module toggles used to
     * mark the config dirty, so a dropdown picked in the ClickGUI was written to disk purely by
     * luck - whenever some other action happened to trigger a save before the game closed - and
     * otherwise came back on the next launch as the default.
     */
    private void changed() {
        notifyChange();
        com.nezurstandalone.utils.ConfigSaveDebouncer.markDirty();
    }
}


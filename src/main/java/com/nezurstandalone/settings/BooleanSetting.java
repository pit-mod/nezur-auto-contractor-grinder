package com.nezurstandalone.settings;

public class BooleanSetting extends Setting {
    public boolean enabled;
    
    public BooleanSetting(String name, boolean defaultValue) {
        super(name);
        this.enabled = defaultValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            notifyChange();
            com.nezurstandalone.utils.ConfigSaveDebouncer.markDirty();
        }
    }
    
    public void toggle() {
        setEnabled(!this.enabled);
    }
}

package com.nezurstandalone.settings;

public class InputSetting extends Setting {
    private String content;
    
    public InputSetting(String name, String defaultValue) {
        super(name);
        this.content = defaultValue == null ? "" : defaultValue;
    }

    public void setContent(String content) {
        String next = content == null ? "" : content;
        if(next.equals(this.content))return;
        this.content = next;
        notifyChange();
        com.nezurstandalone.utils.ConfigSaveDebouncer.markDirty();
    }
    
    public String getContent() {
        return this.content;
    }
}


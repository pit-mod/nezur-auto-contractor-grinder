package com.nezurstandalone.settings;

import java.awt.Color;

public class ColorSetting extends Setting {
    public Color color;
    private final Color defaultColor;

    public ColorSetting(String name, Color defaultColor) {
        super(name);
        this.color = defaultColor;
        this.defaultColor = defaultColor;
    }

    public Color getColor() {
        return color;
    }

    public Color getDefaultColor() {
        return defaultColor;
    }

    public void setColor(Color color) {
        Color next = color == null ? defaultColor : color;
        if (!next.equals(this.color)) {
            this.color = next;
            notifyChange();
            com.nezurstandalone.utils.ConfigSaveDebouncer.markDirty();
        }
    }
}


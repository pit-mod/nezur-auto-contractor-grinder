package com.nezurstandalone.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class Setting {
    public String name;
    public boolean visible = true;
    private final List<Consumer<Setting>> changeListeners = new ArrayList<>();
    
    public Setting(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Setting addChangeListener(Consumer<Setting> listener) {
        if (listener != null) {
            this.changeListeners.add(listener);
        }
        return this;
    }

    protected void notifyChange() {
        for (Consumer<Setting> listener : changeListeners) {
            try {
                listener.accept(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

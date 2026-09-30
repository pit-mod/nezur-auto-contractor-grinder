package com.nezurstandalone.settings;

public class ButtonSetting extends Setting {
    private final Runnable action;

    public ButtonSetting(String name, Runnable action) {
        super(name);
        this.action = action;
    }

    public void run() {
        if (action != null) {
            action.run();
        }
    }
}

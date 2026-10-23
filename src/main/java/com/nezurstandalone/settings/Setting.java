package com.nezurstandalone.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class Setting {
    private static final ThreadLocal<Boolean> restoring=ThreadLocal.withInitial(()->false);
    public static boolean isRestoring(){return restoring.get();}
    public static void restore(Runnable mutation){
        boolean previous=restoring.get();restoring.set(true);
        try{mutation.run();}finally{restoring.set(previous);}
    }
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

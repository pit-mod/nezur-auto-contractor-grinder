package com.nezurstandalone.module;

import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.KeybindSetting;
import com.nezurstandalone.settings.Setting;
import com.nezurstandalone.utils.ConfigSaveDebouncer;
import com.nezurstandalone.utils.HudStackManager;
import com.nezurstandalone.utils.KeybindRegistry;
import com.nezurstandalone.utils.NotificationManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class Module {
    protected Minecraft mc = Minecraft.getMinecraft();
    private String name;
    private String description;
    private Category category;
    private boolean toggled;
    private boolean dangerous;
    private boolean settingsOnly;
    private boolean onEventBus;
    
    public KeybindSetting keybind = new KeybindSetting(Keyboard.KEY_NONE);
    private final BooleanSetting hideInHud = new BooleanSetting("Hide in HUD", false) {
        @Override
        public boolean isVisible() {
            if (com.nezurstandalone.Nezur.moduleManager == null) return false;
            Module hud = com.nezurstandalone.Nezur.moduleManager.getModuleByClass(com.nezurstandalone.module.impl.render.HUD.class);
            return hud != null && hud.isToggled();
        }
    };
    public List<Setting> settings = new ArrayList<>();

    public Module() {
        this.settings.add(keybind);
        this.settings.add(hideInHud);
        this.hideInHud.addChangeListener(s -> Module.bumpToggleRevision());
        if (getClass().isAnnotationPresent(ModuleInfo.class)) {
            ModuleInfo info = getClass().getAnnotation(ModuleInfo.class);
            this.name = info.name();
            this.description = info.description();
            this.category = info.category();
            this.dangerous = info.dangerous();
            if (info.keybind() != Keyboard.KEY_NONE) {
                this.keybind.setKey(info.keybind());
            }
        }
    }

    public Module(String name, String description, Category category) {
        this();
        if (this.name == null) {
            this.name = name;
            this.description = description;
            this.category = category;
        }
    }

    public void addSetting(Setting setting) {
        this.settings.add(setting);
    }

    public void removeSetting(Setting setting) {
        this.settings.remove(setting);
    }

    public void addSettings(Setting... settings) {
        for (Setting s : settings) {
            this.settings.add(s);
        }
    }

    public List<Setting> getSettings() {
        return settings;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Category getCategory() { return category; }
    public boolean isDangerous() { return dangerous; }
    protected void markDangerous() { this.dangerous = true; }

    /**
     * A settings-only module carries tunables but is never turned on. The category row opens
     * its options instead of toggling, and it can never register on the event bus. Used for the
     * Pathfinder panel, which configures a shared engine rather than being a feature in itself.
     */
    public boolean isSettingsOnly() { return settingsOnly; }
    protected void markSettingsOnly() { this.settingsOnly = true; }
    public boolean isHiddenInHud() { return hideInHud.enabled; }
    
    public boolean isEnabledRequested() { return toggled; }

    public boolean isToggled() { return toggled; }

    /** Restores saved on/off from config without running enable/disable yet. */
    /**
      * Bumped whenever any module's toggled state changes.
      *
      * <p>Lets a consumer cache work derived from "which modules are on" and rebuild only when
      * that set actually changed, instead of every frame.
      */
    private static int toggleRevision = 0;

    public static int getToggleRevision() {
        return toggleRevision;
    }

    public static void bumpToggleRevision() {
        toggleRevision++;
    }

    public void setToggledFromConfig(boolean toggled) {
        this.toggled = toggled;
        toggleRevision++;
    }

    /** Runs onEnable for modules that were saved as enabled (call once the client is in-game). */
    public void applyLoadedState() {
        if (!settingsOnly && toggled && !onEventBus) {
            activateSafely();
        }
    }

    protected void registerOnEventBus() {
        if (!onEventBus) {
            onEventBus = true;
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    protected void unregisterFromEventBus() {
        if (onEventBus) {
            onEventBus = false;
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }

    public void setToggled(boolean toggled) {
        if (settingsOnly) {
            return; // configures a shared engine; there is nothing to switch on.
        }
        if (this.toggled == toggled) {
            return;
        }
        this.toggled = toggled;
        toggleRevision++;
        if (toggled) activateSafely();
        else deactivateSafely();

        if (this instanceof DraggableHud) {
            HudStackManager.markDirty();
        }
        ConfigSaveDebouncer.markDirty();
        KeybindRegistry.markStale();
        NotificationManager.showModuleToggle(name, this.toggled);
    }

    private void activateSafely() {
        try {
            registerOnEventBus();
            onEnable();
        } catch (Throwable failure) {
            // An enable hook may have acquired resources before failing.
            this.toggled = false;
            toggleRevision++;
            deactivateSafely();
            java.util.logging.Logger.getLogger(Module.class.getName()).log(
                    java.util.logging.Level.WARNING, "Module enable failed: " + name, failure);
            ConfigSaveDebouncer.markDirty();
            KeybindRegistry.markStale();
        }
    }

    private void deactivateSafely() {
        this.toggled = false;
        try {
            com.nezurstandalone.control.Cleanup.run(
                    this::onDisable,
                    () -> com.nezurstandalone.control.SessionResets.reset(this),
                    () -> com.nezurstandalone.control.InventoryOwner.release(this, true),
                    () -> com.nezurstandalone.control.GuiLease.release(this),
                    () -> com.nezurstandalone.input.GuardedInput.cancel(this),
                    () -> com.nezurstandalone.control.CommandCoordinator.cancel(this),
                    this::unregisterFromEventBus);
        } catch (Throwable failure) {
            java.util.logging.Logger.getLogger(Module.class.getName()).log(
                    java.util.logging.Level.WARNING, "Module cleanup failed: " + name, failure);
        }
    }

    public void toggle() {
        setToggled(!toggled);
    }
    
    protected void onEnable() {}
    protected void onDisable() {}

    public void onKey(int key) {}

    /**
     * Extra keys this module wants {@link #onKey} called for, beyond {@link #keybind}.
     *
     * <p>Without this a secondary bind is inert: the registry only ever indexed the primary
     * keybind, so a module could expose a second {@code KeybindSetting} in its settings list,
     * have the user configure it, and never receive the key. Secondary binds do <em>not</em>
     * toggle the module - that stays the primary bind's job - so a module handling one is
     * responsible for whatever it wants to happen.
     */
    public java.util.List<KeybindSetting> getExtraKeybinds() {
        return java.util.Collections.emptyList();
    }
}


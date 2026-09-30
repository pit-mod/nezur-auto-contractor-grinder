package com.nezurstandalone;

import com.nezurstandalone.gui.ClickGUI;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.utils.KeybindRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class KeyHandler {
    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        if (Minecraft.getMinecraft().currentScreen instanceof ClickGUI) {
            return;
        }
        if (Keyboard.isCreated() && Keyboard.getEventKeyState()) {
            int key = Keyboard.getEventKey();
            // Opening key is configurable from the ClickGUI module; Right Shift stays the default.
            int openKey = com.nezurstandalone.module.impl.render.ClickGuiSettings.openKeyCode();
            if (openKey != Keyboard.KEY_NONE && key == openKey) {
                Minecraft.getMinecraft().displayGuiScreen(new ClickGUI());
                return;
            }
            
            if (key != Keyboard.KEY_NONE) {
                List<Module> bound = KeybindRegistry.getModulesForKey(key);
                for (Module m : bound) {
                    m.onKey(key);
                    // Secondary binds reach onKey but must not flip the module themselves.
                    if (key == m.keybind.code) {
                        m.toggle();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onMouseInput(net.minecraftforge.fml.common.gameevent.InputEvent.MouseInputEvent event) {
        if (Minecraft.getMinecraft().currentScreen instanceof ClickGUI) {
            return;
        }
        if (org.lwjgl.input.Mouse.isCreated() && org.lwjgl.input.Mouse.getEventButtonState()) {
            int button = org.lwjgl.input.Mouse.getEventButton();
            if (button != -1) {
                int code = -100 + button;
                List<Module> bound = KeybindRegistry.getModulesForKey(code);
                for (Module m : bound) {
                    m.onKey(code);
                    if (code == m.keybind.code) {
                        m.toggle();
                    }
                }
            }
        }
    }
}

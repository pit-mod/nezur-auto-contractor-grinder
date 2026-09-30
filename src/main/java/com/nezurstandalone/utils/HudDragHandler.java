package com.nezurstandalone.utils;

import com.nezurstandalone.Nezur;
import com.nezurstandalone.module.DraggableHud;
import com.nezurstandalone.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

public class HudDragHandler {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private DraggableHud dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    private void finishDrag() {
        if (dragging != null) {
            HudPositionSaveDebouncer.markDirty();
            HudStackManager.markDirty();
            dragging = null;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!(MC.currentScreen instanceof GuiChat)) {
            finishDrag();
            return;
        }
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT
                && event.type != RenderGameOverlayEvent.ElementType.CHAT) {
            return;
        }

        int mouseX = Utils.getMouseX();
        int mouseY = Utils.getMouseY();

        if (Mouse.isButtonDown(0)) {
            if (dragging == null) {
                for (Module module : Nezur.moduleManager.getModules()) {
                    if (!(module instanceof DraggableHud) || !module.isToggled()) {
                        continue;
                    }
                    DraggableHud hud = (DraggableHud) module;
                    if (isOverHud(hud, mouseX, mouseY)) {
                        dragging = hud;
                        dragOffsetX = mouseX - hud.getRenderX();
                        dragOffsetY = mouseY - hud.getRenderY();
                        break;
                    }
                }
            }
            if (dragging != null) {
                HudPositionManager.setBounded(
                        dragging.getHudKey(),
                        mouseX - dragOffsetX,
                        mouseY - dragOffsetY,
                        Math.max(1, dragging.getHudWidth()),
                        Math.max(1, dragging.getHudHeight()),
                        dragging.isHudCenterAnchored());
                HudStackManager.markDirty();
            }
        } else {
            finishDrag();
        }
    }

    private static boolean isOverHud(DraggableHud hud, int mouseX, int mouseY) {
        int w = Math.max(20, hud.isHudVisible() ? hud.getHudWidth() : 80);
        int h = Math.max(10, hud.isHudVisible() ? hud.getHudHeight() : 20);
        int x = hud.getRenderX();
        int y = hud.getRenderY();
        if (hud.isHudCenterAnchored()) {
            int half = w / 2;
            return mouseX >= x - half && mouseX <= x + half && mouseY >= y && mouseY <= y + h;
        }
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}

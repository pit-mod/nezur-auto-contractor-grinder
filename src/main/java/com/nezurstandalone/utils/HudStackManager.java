package com.nezurstandalone.utils;

import com.nezurstandalone.Nezur;
import com.nezurstandalone.gui.hud.HudEditorScreen;
import com.nezurstandalone.module.DraggableHud;
import com.nezurstandalone.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Packs visible HUD lists in saved order, using their actual rendered bounds.
 */
public class HudStackManager {
    private static final Map<String, Integer> resolvedY = new HashMap<>();
    private static final Map<String, com.nezurstandalone.control.HudListLayout.Box> measured = new HashMap<>();
    private static boolean dirty=true;
    private static final java.util.List<DraggableHud> auxiliary = new java.util.ArrayList<>();
    private static final java.util.List<Module> hudModules = new java.util.ArrayList<>();
    public static void registerAuxiliary(DraggableHud hud) { auxiliary.add(hud); }

    public static void markDirty() {
        dirty=true;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || Nezur.moduleManager == null) {
            resolvedY.clear();
            measured.clear();
            return;
        }
        if (mc.currentScreen instanceof HudEditorScreen) {
            resolvedY.clear();
            measured.clear();
            return;
        }
        rebuild();
    }

    private static void rebuild() {
        resolvedY.clear();
        measured.clear();
        hudModules.clear();
        dirty=false;
        for(Module module:Nezur.moduleManager.getModules()){
            if(!(module instanceof DraggableHud))continue;
            hudModules.add(module);
            if(!module.isToggled())continue;
            DraggableHud hud=(DraggableHud)module;
            measured.put(hud.getHudKey(),measure(hud));
        }
        for(DraggableHud hud:auxiliary)if(hud.isHudVisible()){
            measured.put(hud.getHudKey(),measure(hud));
        }
        arrange();
    }
    private static com.nezurstandalone.control.HudListLayout.Box measure(DraggableHud hud){
        boolean visible=hud.isHudVisible();
        if(!visible){
            int x=HudBounds.clampX(hud.getHudX(),1,hud.isHudCenterAnchored())+(hud.isHudCenterAnchored()?ScreenScale.width()/2:0);
            return new com.nezurstandalone.control.HudListLayout.Box(hud.getHudKey(),x,hud.getHudY(),1,0,hud.isStackedList(),false);
        }
        int width=Math.max(Math.max(1,hud.getHudWidth()),hud.getHudEditorWidth())+1,height=visible?Math.max(1,hud.getHudHeight())+1:0;
        // Normalize center-relative left edges into the same screen space as every other HUD.
        int x=hud.getRenderX()+(hud.isHudCenterAnchored()?ScreenScale.width()/2:0);
        return new com.nezurstandalone.control.HudListLayout.Box(hud.getHudKey(),x,hud.getHudY(),width,height,hud.isStackedList(),visible);
    }
    private static void arrange(){
        resolvedY.clear();
        resolvedY.putAll(com.nezurstandalone.control.HudListLayout.arrange(new java.util.ArrayList<>(measured.values()),ScreenScale.height()));
    }
    private static boolean update(DraggableHud hud){
        com.nezurstandalone.control.HudListLayout.Box now=measure(hud),before=measured.get(hud.getHudKey());
        if(before==null||before.x!=now.x||before.y!=now.y||before.width!=now.width||before.height!=now.height||before.visible!=now.visible){
            measured.put(hud.getHudKey(),now);return true;
        }
        return false;
    }

    public static int getStackedY(String hudKey, int configY) {
        if (Minecraft.getMinecraft().currentScreen instanceof HudEditorScreen) {
            return configY;
        }
        return resolvedY.getOrDefault(hudKey, configY);
    }

    public static int getStackedY(DraggableHud hud) {
        if (Minecraft.getMinecraft().currentScreen instanceof HudEditorScreen)
            return HudBounds.clampY(hud.getHudY(), Math.max(1,hud.getHudHeight()));
        // Content can change during render (target animations, counters and music).
        // Re-measure before resolving rather than using last frame's rectangle.
        if (Nezur.moduleManager != null) {
            if(dirty)rebuild();
            boolean changed=false;
            // Also check predecessors: a widget can be drawn before its newly resized neighbor.
            for(Module module:hudModules){
                if(module.isToggled())changed|=update((DraggableHud)module);
                else changed|=measured.remove(((DraggableHud)module).getHudKey())!=null;
            }
            for(DraggableHud extra:auxiliary)changed|=update(extra);
            if(!measured.containsKey(hud.getHudKey())&&!(hud instanceof Module))changed|=update(hud);
            if(changed)arrange();
        }
        return resolvedY.getOrDefault(hud.getHudKey(),
                HudBounds.clampY(hud.getHudY(), Math.max(1,hud.getHudHeight())));
    }
}

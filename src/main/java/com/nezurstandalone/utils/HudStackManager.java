package com.nezurstandalone.utils;

import com.nezurstandalone.Nezur;
import com.nezurstandalone.gui.hud.HudEditorScreen;
import com.nezurstandalone.module.DraggableHud;
import com.nezurstandalone.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps on-screen HUD modules from overlapping by pushing them down within the same column in-game.
 * Sorts stacked lists descending by content height so lists with the most entries appear on top
 * and lists with the least entries appear at the bottom.
 */
public class HudStackManager {
    private static final int COLUMN_WIDTH = 80;
    private static final int GAP = 5;

    private static final Map<String, Integer> resolvedY = new HashMap<>();
    private static final Map<Integer, Integer> columnBottoms = new HashMap<>();

    public static void markDirty() {
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || Nezur.moduleManager == null) {
            resolvedY.clear();
            columnBottoms.clear();
            return;
        }
        if (mc.currentScreen instanceof HudEditorScreen) {
            resolvedY.clear();
            columnBottoms.clear();
            return;
        }
        rebuild();
    }

    private static void rebuild() {
        resolvedY.clear();
        columnBottoms.clear();

        List<DraggableHud> visible = new ArrayList<>();
        for (Module module : Nezur.moduleManager.getModules()) {
            if (!module.isToggled() || !(module instanceof DraggableHud)) {
                continue;
            }
            DraggableHud hud = (DraggableHud) module;
            if (hud.isHudVisible()) {
                visible.add(hud);
            }
        }

        // Sort stacked lists so modules with the MOST active entries/height are placed on top (first),
        // and modules with the LEAST entries/height are placed on bottom (last).
        Collections.sort(visible, Comparator
                .comparingInt((DraggableHud hud) -> hud.getHudY() / 30)
                .thenComparing((a, b) -> Integer.compare(b.getHudHeight(), a.getHudHeight()))
                .thenComparingInt(DraggableHud::getHudX)
                .thenComparing(DraggableHud::getHudKey));

        for (DraggableHud hud : visible) {
            int width = Math.max(1, hud.getHudWidth());
            int height = Math.max(1, hud.getHudHeight());
            int x = HudBounds.clampX(hud.getHudX(), width, hud.isHudCenterAnchored());
            int configY = hud.getHudY();
            int column = x / COLUMN_WIDTH;
            int previousBottom = columnBottoms.getOrDefault(column, 0);
            int startY = HudBounds.clampY(Math.max(configY, previousBottom), height);
            columnBottoms.put(column, startY + height + GAP);
            resolvedY.put(hud.getHudKey(), startY);
        }
    }

    public static int getStackedY(String hudKey, int configY) {
        if (Minecraft.getMinecraft().currentScreen instanceof HudEditorScreen) {
            return configY;
        }
        return resolvedY.getOrDefault(hudKey, configY);
    }

    public static int getStackedY(DraggableHud hud) {
        return getStackedY(hud.getHudKey(), hud.getHudY());
    }
}

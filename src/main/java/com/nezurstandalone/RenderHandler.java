package com.nezurstandalone;

import com.nezurstandalone.module.DraggableHud;
import com.nezurstandalone.module.Module;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RenderHandler {

    private static ScaledResolution cachedResolution;
    private static int cachedResW = -1;
    private static int cachedResH = -1;
    private static final List<DraggableHud> cachedListModules = new ArrayList<>();
    private static int lastSortHash;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return;

        Minecraft mc = Minecraft.getMinecraft();
        // Was a local cache keyed only on display size, so it survived a GUI-scale change and
        // handed out a stale resolution. ScreenScale watches the scale and unicode flag too.
        ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();

        int sortHash = 0;
        cachedListModules.clear();
        for (Module m : Nezur.moduleManager.getModules()) {
            if (!m.isToggled()) continue;
            if (m instanceof DraggableHud) {
                DraggableHud hud = (DraggableHud) m;
                if (hud.isStackedList()) {
                    cachedListModules.add(hud);
                    sortHash = sortHash * 31 + hud.getConfigY();
                }
            }
        }

        if (sortHash != lastSortHash) {
            cachedListModules.sort(Comparator
                    .comparingInt(DraggableHud::getConfigY)
                    .thenComparing(hud -> ((Module) hud).getName()));
            lastSortHash = sortHash;
        }

        for (DraggableHud hud : cachedListModules) {
            hud.renderStacked();
        }
    }
}

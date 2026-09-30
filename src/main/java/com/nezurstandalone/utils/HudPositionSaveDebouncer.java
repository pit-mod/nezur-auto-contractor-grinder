package com.nezurstandalone.utils;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Debounces HUD position disk writes (same pattern as ConfigSaveDebouncer).
 */
public class HudPositionSaveDebouncer {

    private static boolean dirty;
    private static int ticksSinceDirty;

    public static void markDirty() {
        dirty = true;
        ticksSinceDirty = 0;
    }

    public static void flushNow() {
        if (!dirty) {
            return;
        }
        dirty = false;
        HudPositionManager.save();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !dirty) {
            return;
        }
        ticksSinceDirty++;
        if (ticksSinceDirty >= 40) {
            flushNow();
        }
    }
}

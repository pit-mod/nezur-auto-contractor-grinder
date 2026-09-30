package com.nezurstandalone.utils;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Batches config writes instead of saving on every module toggle.
 */
public class ConfigSaveDebouncer {

    private static volatile boolean dirty;
    private static long revision;
    private static int ticksSinceDirty;

    public static synchronized void markDirty() {
        revision++;
        dirty = true;
        ticksSinceDirty = 0;
    }

    public static synchronized void flushNow() {
        if (!dirty) return;
        long savingRevision=revision;
        try {
            ConfigManager.persistAll();
            dirty = revision != savingRevision;
        } catch (RuntimeException failure) {
            dirty = true;
            java.util.logging.Logger.getLogger(ConfigSaveDebouncer.class.getName()).log(java.util.logging.Level.WARNING,"Config save failed; retained for retry",failure);
        } finally {ticksSinceDirty=0;}
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        synchronized (ConfigSaveDebouncer.class) {
            if (event.phase != TickEvent.Phase.END || !dirty) return;
            if (++ticksSinceDirty >= 40) flushNow();
        }
    }
}

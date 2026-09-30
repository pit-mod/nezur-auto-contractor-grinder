package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/**
 * The shared, cached {@link ScaledResolution}.
 *
 * <p>Constructing one is not free - it reads the display size and the GUI-scale setting and
 * loops to find the scale factor - and the mod was constructing a fresh one at fourteen
 * separate places on every rendered frame, all of which would produce an identical object.
 *
 * <p>Two partial caches already existed. {@code GuiDraw} had a correct one; {@code
 * RenderHandler} had a copy that only watched the display width and height, so it went stale
 * when the player changed GUI scale or toggled unicode and kept handing out a resolution that
 * no longer matched the screen. Sharing one implementation removes that class of bug rather
 * than just the allocations.
 */
public final class ScreenScale {

    private static ScaledResolution cached;
    private static int cachedWidth;
    private static int cachedHeight;
    private static int cachedGuiScale;
    private static boolean cachedUnicode;

    private ScreenScale() {
    }

    /**
     * The current resolution, rebuilt only when something it depends on actually changed.
     *
     * <p>Callers must not hold the returned object across frames.
     */
    public static ScaledResolution get() {
        Minecraft mc = Minecraft.getMinecraft();
        if (cached == null
                || mc.displayWidth != cachedWidth
                || mc.displayHeight != cachedHeight
                || mc.gameSettings.guiScale != cachedGuiScale
                || mc.isUnicode() != cachedUnicode) {
            cached = new ScaledResolution(mc);
            cachedWidth = mc.displayWidth;
            cachedHeight = mc.displayHeight;
            cachedGuiScale = mc.gameSettings.guiScale;
            cachedUnicode = mc.isUnicode();
        }
        return cached;
    }

    public static int width() {
        return get().getScaledWidth();
    }

    public static int height() {
        return get().getScaledHeight();
    }

    /** Drops the cache. Only needed if something changes the display outside the game settings. */
    public static void invalidate() {
        cached = null;
    }
}

package com.nezurstandalone.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Plays a screen's dismissal <em>after</em> the screen itself is gone.
 *
 * <p>A {@link net.minecraft.client.gui.GuiScreen} owns the mouse and the keyboard for as long
 * as it is open, so animating a close from inside {@code drawScreen} costs the player every
 * frame of that animation — they cannot walk, look or attack until it finishes. Instead the
 * screen closes immediately, handing control straight back, and passes its last visual state
 * here; the dismissal is then drawn as a HUD overlay over the live, fully interactive game.
 */
public final class GuiExitAnimator {

    /** Implemented by any nezur screen that wants an animated dismissal. */
    public interface ExitRenderer {
        /**
         * Draws one frame of the dismissal.
         *
         * @param progress 0 at the moment of closing, 1 when the animation is over
         * @param delta    seconds since the previous frame
         */
        void renderExit(float progress, float delta);
    }

    /** Short on purpose: the world is already interactive behind it. */
    private static final long DURATION_MS = 240L;

    private static ExitRenderer current;
    private static long startMs;
    private static long lastFrameMs;

    public static void play(ExitRenderer renderer) {
        if (!com.nezurstandalone.module.impl.render.ClickGuiSettings.animationsOn()) { cancel(); return; }
        current = renderer;
        startMs = GuiAnim.millis();
        lastFrameMs = startMs;
    }

    /** Drops any in-flight dismissal — call when a screen opens so the two never overlap. */
    public static void cancel() {
        current = null;
    }

    public static boolean isPlaying() {
        return current != null;
    }

    /**
     * The overlay event is the only place a finished animation clears itself, and it stops
     * firing the moment the world goes away — so leaving a world mid-dismissal would pin the
     * closed screen in a static field until the next join. Drop it here instead.
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        cancel();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        ExitRenderer renderer = current;
        if (renderer == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            current = null;
            return;
        }
        if (mc.currentScreen != null) {
            // Something else took the screen (chat, inventory, a reopened GUI) — the
            // dismissal has been overtaken and would only draw on top of it.
            current = null;
            return;
        }

        long now = GuiAnim.millis();
        float progress = (now - startMs) * com.nezurstandalone.module.impl.render.ClickGuiSettings.animationSpeed() / (float) DURATION_MS;
        if (progress >= 1f) {
            current = null;
            return;
        }
        float delta = Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        GuiDraw.resetState();
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        try {
            renderer.renderExit(GuiAnim.clamp01(progress), delta);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GuiDraw.endClip();
        }
    }
}

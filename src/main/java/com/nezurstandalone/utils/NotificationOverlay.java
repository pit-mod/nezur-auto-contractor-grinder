package com.nezurstandalone.utils;

import com.nezurstandalone.gui.GuiDraw;
import com.nezurstandalone.gui.GuiTheme;
import com.nezurstandalone.gui.GuiAnim;
import com.nezurstandalone.gui.physics.PhysicsSpring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Toast notifications styled to match the ClickGUI's dark glass panels and grant a smoother exit.
 */
public class NotificationOverlay {

    private static final int MARGIN_X = 12;
    private static final int MARGIN_Y = 26;
    private static final int GAP = 7;
    private static final int MIN_WIDTH = 120;
    private static final int BOX_H = 22;

    private final Map<NotificationManager.Toast, PhysicsSpring> ySprings = new HashMap<>();
    private final PhysicsSpring.DeltaTimer timer = PhysicsSpring.DeltaTimer.create();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        List<NotificationManager.Toast> toasts = NotificationManager.getActiveToasts();
        if (toasts.isEmpty()) {
            ySprings.clear();
            return;
        }

        long now = System.currentTimeMillis();
        NotificationManager.pruneExpired(now);

        ySprings.keySet().retainAll(toasts);

        ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();
        FontRenderer fr = mc.fontRendererObj;
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        float targetY = screenH - MARGIN_Y;
        float dt = timer.tickSeconds();

        for (int i = 0; i < toasts.size(); i++) {
            NotificationManager.Toast toast = toasts.get(i);
            toast.updateState(now, dt);

            float appear = toast.spring.getCurrentValue();
            if (appear <= 0.005f) continue;

            int rawW = fr.getStringWidth(toast.message);
            int boxW = Math.max(MIN_WIDTH, rawW + 28);

            targetY -= BOX_H;

            PhysicsSpring ySpring = ySprings.get(toast);
            if (ySpring == null) {
                // Initialize spring at starting position (lower) to bounce into place
                ySpring = PhysicsSpring.iOSFluid(targetY + 20f);
                ySprings.put(toast, ySpring);
            }
            ySpring.setTarget(targetY);
            ySpring.update(dt);

            float drawY = ySpring.getCurrentValue();

            targetY -= GAP;

            float ease = GuiAnim.outCubic(appear);
            float slide = (1f - ease) * 20f;
            float drawX = screenW - MARGIN_X - boxW + slide;

            GuiDraw.shadow(drawX, drawY, drawX + boxW, drawY + BOX_H, GuiTheme.PANEL_RADIUS, 5, (int) (120 * appear));
            GuiDraw.glass(drawX, drawY, drawX + boxW, drawY + BOX_H, GuiTheme.PANEL_RADIUS,
                    GuiTheme.GLASS_BODY, GuiTheme.GLASS_BORDER, 1f, appear);

            GuiDraw.roundedRect(drawX + 1.5f, drawY + 4f, drawX + 3f, drawY + BOX_H - 4f, 0.75f,
                    GuiDraw.withAlpha(toast.accentColor, appear));

            float dotPulse = 0.75f + 0.25f * GuiAnim.pulse(1.4f);
            float dotY = drawY + BOX_H / 2f;
            GuiDraw.glow(drawX + 11f, dotY, 6.5f * dotPulse, GuiDraw.withAlpha(toast.accentColor, 0.4f * appear), 4);
            GuiDraw.circle(drawX + 11f, dotY, 2.5f, GuiDraw.withAlpha(toast.accentColor, appear));

            GuiDraw.textScaled(toast.message, drawX + 18f, drawY + (BOX_H - 8f) / 2f + 0.5f, 1f,
                    GuiDraw.withAlpha(GuiTheme.TEXT, appear), false);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}

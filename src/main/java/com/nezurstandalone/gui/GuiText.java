package com.nezurstandalone.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

public final class GuiText {
    private GuiText() {
    }

    public static void drawFit(String text, int x, int y, int availableWidth, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        int safeWidth = Math.max(1, availableWidth);
        int textWidth = fr.getStringWidth(text);
        if (textWidth <= safeWidth) {
            fr.drawStringWithShadow(text, x, y, color);
            return;
        }

        float scale = (float) safeWidth / (float) textWidth;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0f);
        GlStateManager.scale(scale, scale, 1.0f);
        fr.drawStringWithShadow(text, 0.0f, 0.0f, color);
        GlStateManager.popMatrix();
    }
}

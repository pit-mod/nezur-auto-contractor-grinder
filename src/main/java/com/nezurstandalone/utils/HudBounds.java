package com.nezurstandalone.utils;

public final class HudBounds {
    private HudBounds() {
    }

    public static int clampX(int x, int width) {
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
        return Math.max(0, Math.min(x, sr.getScaledWidth() - width));
    }

    public static int clampX(int x, int width, boolean centerAnchored) {
        if (!centerAnchored) {
            return clampX(x, width);
        }
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
        int screenCenterX = sr.getScaledWidth() / 2;
        int absoluteX = screenCenterX + x;
        int clampedAbsX = Math.max(0, Math.min(absoluteX, sr.getScaledWidth() - width));
        return clampedAbsX - screenCenterX;
    }

    public static int clampY(int y, int height) {
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
        return Math.max(0, Math.min(y, sr.getScaledHeight() - height));
    }

    public static int[] clamp(int x, int y, int width, int height, boolean centerAnchored) {
        return new int[]{clampX(x, width, centerAnchored), clampY(y, height)};
    }
}

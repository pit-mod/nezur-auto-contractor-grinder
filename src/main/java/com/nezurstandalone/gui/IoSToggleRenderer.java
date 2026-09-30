package com.nezurstandalone.gui;

/**
 * iOS-style toggle pill. {@code progress} drives both the track colour and the knob travel
 * so a single animated float gives the whole switch its motion.
 */
public final class IoSToggleRenderer {

    public static final int TRACK_W = 16;
    public static final int TRACK_H = 9;

    private IoSToggleRenderer() {
    }

    public static void draw(float x, float y, float progress) {
        draw(x, y, TRACK_W, TRACK_H, progress, GuiTheme.TOGGLE_ON, 1f);
    }

    public static void draw(float x, float y, float width, float height, float progress, int onColor, float alpha) {
        float colorT = GuiDraw.clamp01(progress);
        float knobT = colorT;

        int track = GuiDraw.lerpColor(GuiTheme.TOGGLE_OFF, onColor, colorT);
        GuiDraw.pill(x, y, width, height, GuiDraw.withAlpha(track, alpha));

        float inset = 1.5f;
        float knobR = (height - inset * 2f) / 2f;
        float travel = width - inset * 2f - knobR * 2f;
        float knobX = x + inset + knobR + travel * knobT;
        float knobY = y + height / 2f;

        GuiDraw.circle(knobX, knobY + 0.4f, knobR, GuiDraw.withAlpha(0x40000000, alpha));
        GuiDraw.circle(knobX, knobY, knobR, GuiDraw.withAlpha(GuiTheme.TOGGLE_KNOB, alpha));
    }
}

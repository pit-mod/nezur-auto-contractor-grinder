package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.settings.RangeSetting;
import org.lwjgl.input.Mouse;

/**
 * Dual-handle range slider. Same shape language as {@link SliderComponent}: label and a
 * value pill on top, a capsule track below — here with the filled segment spanning between
 * two spring-driven knobs.
 */
public class RangeSliderComponent extends Component {

    private static final int TRACK_INSET = 7;

    private final RangeSetting rangeSet;
    private int draggingKnob; // 0 = none, 1 = min, 2 = max

    private final PhysicsSpring minPos = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring maxPos = PhysicsSpring.iOSFluid(1f);
    private final PhysicsSpring minScale = PhysicsSpring.iOSFluid(1f);
    private final PhysicsSpring maxScale = PhysicsSpring.iOSFluid(1f);
    private final PhysicsSpring hover = PhysicsSpring.iOSFluid(0f);

    private long lastTimeMs = GuiAnim.millis();
    private boolean primed;

    public RangeSliderComponent(RangeSetting setting) {
        super(setting);
        this.rangeSet = setting;
    }

    @Override
    public int getPreferredHeight() {
        return SliderComponent.ROW_HEIGHT;
    }

    private float trackX() {
        return x + TRACK_INSET;
    }

    private float trackW() {
        return Math.max(4f, width - TRACK_INSET * 2f);
    }

    private float ratioOf(double value) {
        double span = rangeSet.maxBound - rangeSet.minBound;
        if (span <= 0) {
            return 0f;
        }
        return (float) Math.max(0.0, Math.min(1.0, (value - rangeSet.minBound) / span));
    }

    @Override
    public void render(int mouseX, int mouseY) {
        long now = GuiAnim.millis();
        float delta = Math.min(0.1f, (now - lastTimeMs) / 1000.0f);
        lastTimeMs = now;

        if (!Mouse.isButtonDown(0)) {
            draggingKnob = 0;
        }
        boolean hovered = isHovered(mouseX, mouseY);
        boolean active = hovered || draggingKnob != 0;

        if (!primed) {
            primed = true;
            minPos.snapTo(ratioOf(rangeSet.minVal));
            maxPos.snapTo(ratioOf(rangeSet.maxVal));
        }

        if (draggingKnob != 0) {
            updateDragPosition(mouseX);
        }

        hover.setTarget(active ? 1f : 0f);
        minPos.setTarget(ratioOf(rangeSet.minVal));
        maxPos.setTarget(ratioOf(rangeSet.maxVal));
        minScale.setTarget(draggingKnob == 1 ? 1.45f : (hovered ? 1.15f : 1f));
        maxScale.setTarget(draggingKnob == 2 ? 1.45f : (hovered ? 1.15f : 1f));
        hover.update(delta);
        minPos.update(delta);
        maxPos.update(delta);
        minScale.update(delta);
        maxScale.update(delta);

        float hoverT = GuiAnim.clamp01(hover.getCurrentValue());

        if (hoverT > 0.01f) {
            GuiDraw.roundedRect(x + 1, y + 1, x + width - 1, y + height - 1, 3f,
                    alphaColor(GuiDraw.withAlpha(GuiTheme.ROW_BG_HOVER, hoverT)));
        }

        // --- Top line: label + range pill ------------------------------------
        String valStr = format(rangeSet.minVal) + " - " + format(rangeSet.maxVal);
        float pillTextW = GuiDraw.textWidthScaled(valStr, 0.75f);
        float pillW = pillTextW + 9f;
        float pillH = 9f;
        float pillX = x + width - TRACK_INSET - pillW;
        float pillY = y + 3f;
        float pillScale = 1f + 0.12f * hoverT;

        GuiDraw.textFitScaled(rangeSet.name, x + TRACK_INSET, y + 4f, 1f,
                pillX - 5f - (x + TRACK_INSET), alphaColor(GuiTheme.SETTINGS_TEXT), false);

        GuiDraw.pushScale(pillX + pillW / 2f, pillY + pillH / 2f, pillScale, pillScale);
        GuiDraw.pill(pillX, pillY, pillW, pillH,
                alphaColor(GuiDraw.lerpColor(0x33FFFFFF, GuiTheme.ACCENT_MUTED, hoverT)));
        GuiDraw.textScaled(valStr, pillX + (pillW - pillTextW) / 2f, pillY + 1.5f, 0.75f,
                alphaColor(GuiDraw.lerpColor(GuiTheme.TEXT_DIM, 0xFFFFFFFF, hoverT)), false);
        GuiDraw.popMatrix();

        // --- Track ------------------------------------------------------------
        float tx = trackX();
        float tw = trackW();
        float th = 3.5f + 1.5f * hoverT;
        float ty = y + height - 7f - th / 2f;

        GuiDraw.pill(tx, ty, tw, th, alphaColor(0xFF232329));

        float lo = tx + tw * GuiAnim.clamp01(minPos.getCurrentValue());
        float hi = tx + tw * GuiAnim.clamp01(maxPos.getCurrentValue());
        if (hi - lo > 0.5f) {
            int c1 = GuiDraw.lerpColor(0xFF2A6FD6, GuiTheme.ACCENT, hoverT);
            int c2 = GuiDraw.lerpColor(GuiTheme.ACCENT, 0xFF6FB4FF, hoverT);
            GuiDraw.roundedRectGradient(lo, ty, hi, ty + th, th / 2f, alphaColor(c1), alphaColor(c2));
        }

        drawKnob(lo, ty + th / 2f, minScale.getCurrentValue(), hoverT);
        drawKnob(hi, ty + th / 2f, maxScale.getCurrentValue(), hoverT);
    }

    private void drawKnob(float cx, float cy, float scale, float hoverT) {
        float r = 3.4f * scale;
        GuiDraw.circle(cx, cy + 0.5f, r, alphaColor(0x55000000));
        GuiDraw.circle(cx, cy, r, alphaColor(0xFFFFFFFF));
        GuiDraw.circle(cx, cy, r * 0.42f, alphaColor(GuiTheme.ACCENT));
    }

    private String format(double value) {
        if (rangeSet.decimalPlaces <= 0) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(java.util.Locale.US, "%." + rangeSet.decimalPlaces + "f", value);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isHovered(mouseX, mouseY) || mouseButton != 0) {
            return;
        }
        float tx = trackX();
        float tw = trackW();
        float lo = tx + tw * ratioOf(rangeSet.minVal);
        float hi = tx + tw * ratioOf(rangeSet.maxVal);
        // Grab whichever handle is nearer to the click.
        draggingKnob = Math.abs(mouseX - lo) <= Math.abs(mouseX - hi) ? 1 : 2;
        (draggingKnob == 1 ? minScale : maxScale).setVelocity(0f);
        updateDragPosition(mouseX);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingKnob != 0 && clickedMouseButton == 0) {
            updateDragPosition(mouseX);
        }
    }

    private void updateDragPosition(int mouseX) {
        float tx = trackX();
        float tw = trackW();
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - tx) / (double) tw));
        double value = rangeSet.minBound + ratio * (rangeSet.maxBound - rangeSet.minBound);
        if (draggingKnob == 1) {
            rangeSet.setMinVal(value);
        } else if (draggingKnob == 2) {
            rangeSet.setMaxVal(value);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        draggingKnob = 0;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}

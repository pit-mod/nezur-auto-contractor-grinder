package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.settings.NumberSetting;
import org.lwjgl.input.Mouse;

/**
 * Value slider: label and a value pill on the top line, a full-width capsule track beneath.
 * The fill and the knob are spring-driven, so dragging feels weighted and releasing settles
 * rather than snapping.
 */
public class SliderComponent extends Component {

    static final int ROW_HEIGHT = 25;
    private static final int TRACK_INSET = 7;

    private final NumberSetting numSet;
    private boolean dragging;

    private final PhysicsSpring fill = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring knobScale = PhysicsSpring.iOSFluid(1f);
    private final PhysicsSpring hover = PhysicsSpring.iOSFluid(0f);
    private final GuiAnim.Ripple ripple = new GuiAnim.Ripple();

    private long lastTimeMs = GuiAnim.millis();
    private boolean primed;

    public SliderComponent(NumberSetting setting) {
        super(setting);
        this.numSet = setting;
    }

    @Override
    public int getPreferredHeight() {
        return ROW_HEIGHT;
    }

    private float trackX() {
        return x + TRACK_INSET;
    }

    private float trackW() {
        return Math.max(4f, width - TRACK_INSET * 2f);
    }

    private float valueRatio() {
        double span = numSet.max - numSet.min;
        if (span <= 0) {
            return 0f;
        }
        return (float) Math.max(0.0, Math.min(1.0, (numSet.value - numSet.min) / span));
    }

    @Override
    public void render(int mouseX, int mouseY) {
        long now = GuiAnim.millis();
        float delta = Math.min(0.1f, (now - lastTimeMs) / 1000.0f);
        lastTimeMs = now;

        if (!Mouse.isButtonDown(0)) {
            dragging = false;
        }
        boolean hovered = isHovered(mouseX, mouseY);
        boolean active = hovered || dragging;

        if (!primed) {
            primed = true;
            fill.snapTo(valueRatio());
        }

        hover.setTarget(active ? 1f : 0f);
        knobScale.setTarget(dragging ? 1.45f : (hovered ? 1.2f : 1f));
        fill.setTarget(valueRatio());
        hover.update(delta);
        knobScale.update(delta);
        fill.update(delta);
        ripple.update(delta, 3.2f);

        float hoverT = GuiAnim.clamp01(hover.getCurrentValue());

        if (hoverT > 0.01f) {
            GuiDraw.roundedRect(x + 1, y + 1, x + width - 1, y + height - 1, 3f,
                    alphaColor(GuiDraw.withAlpha(GuiTheme.ROW_BG_HOVER, hoverT)));
        }

        // --- Top line: label + value pill -----------------------------------
        String valStr = format(numSet.value);
        float pillScale = 1f + 0.12f * hoverT;
        float pillTextW = GuiDraw.textWidthScaled(valStr, 0.75f);
        float pillW = pillTextW + 9f;
        float pillH = 9f;
        float pillX = x + width - TRACK_INSET - pillW;
        float pillY = y + 3f;

        GuiDraw.textFitScaled(numSet.name, x + TRACK_INSET, y + 4f, 1f,
                pillX - 5f - (x + TRACK_INSET), alphaColor(GuiTheme.SETTINGS_TEXT), false);

        GuiDraw.pushScale(pillX + pillW / 2f, pillY + pillH / 2f, pillScale, pillScale);
        int pillBg = GuiDraw.lerpColor(0x33FFFFFF, GuiTheme.ACCENT_MUTED, hoverT);
        GuiDraw.pill(pillX, pillY, pillW, pillH, alphaColor(pillBg));
        GuiDraw.textScaled(valStr, pillX + (pillW - pillTextW) / 2f, pillY + 1.5f, 0.75f,
                alphaColor(GuiDraw.lerpColor(GuiTheme.TEXT_DIM, 0xFFFFFFFF, hoverT)), false);
        GuiDraw.popMatrix();

        // --- Track ----------------------------------------------------------
        float tx = trackX();
        float tw = trackW();
        float th = 3.5f + 1.5f * hoverT;
        float ty = y + height - 7f - th / 2f;

        if (dragging) {
            updateDragValue(mouseX);
        }

        GuiDraw.pill(tx, ty, tw, th, alphaColor(0xFF232329));

        float t = GuiAnim.clamp01(fill.getCurrentValue());
        float fillW = tw * t;
        if (fillW > 0.5f) {
            int c1 = GuiDraw.lerpColor(0xFF2A6FD6, GuiTheme.ACCENT, hoverT);
            int c2 = GuiDraw.lerpColor(GuiTheme.ACCENT, 0xFF6FB4FF, hoverT);
            GuiDraw.roundedRectGradient(tx, ty, tx + fillW, ty + th, th / 2f,
                    alphaColor(c1), alphaColor(c2));
        }

        // --- Knob -----------------------------------------------------------
        float knobX = tx + fillW;
        float knobY = ty + th / 2f;
        float knobR = 3.4f * knobScale.getCurrentValue();

        if (ripple.isActive()) {
            GuiDraw.ripple(ripple.getX(), ripple.getY(), 16f, ripple.getProgress(),
                    alphaColor(GuiTheme.ACCENT));
        }
        GuiDraw.circle(knobX, knobY + 0.5f, knobR, alphaColor(0x55000000));
        GuiDraw.circle(knobX, knobY, knobR, alphaColor(0xFFFFFFFF));
        GuiDraw.circle(knobX, knobY, knobR * 0.42f, alphaColor(GuiTheme.ACCENT));
    }

    private String format(double value) {
        if (numSet.decimalPlaces <= 0) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(java.util.Locale.US, "%." + numSet.decimalPlaces + "f", value);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isHovered(mouseX, mouseY) && mouseButton == 0) {
            dragging = true;
            knobScale.setVelocity(0f);
            ripple.trigger(mouseX, mouseY);
            updateDragValue(mouseX);
        }
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging && clickedMouseButton == 0) {
            updateDragValue(mouseX);
        }
    }

    private void updateDragValue(int mouseX) {
        float tx = trackX();
        float tw = trackW();
        if (mouseX <= tx) {
            numSet.setValue(numSet.min);
        } else if (mouseX >= tx + tw) {
            numSet.setValue(numSet.max);
        } else {
            double ratio = (mouseX - tx) / (double) tw;
            numSet.setValue(numSet.min + ratio * (numSet.max - numSet.min));
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}

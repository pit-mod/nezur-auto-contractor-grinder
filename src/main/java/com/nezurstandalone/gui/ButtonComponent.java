package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.settings.ButtonSetting;

/**
 * A real pressable button rather than a text row: rounded surface that lifts and tints on
 * hover, squashes on press, and emits a ripple from the click point.
 */
public class ButtonComponent extends Component {

    private static final int ROW_HEIGHT = 20;
    private static final int INSET = 6;

    private final ButtonSetting setting;

    private final PhysicsSpring hover = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring press = PhysicsSpring.iOSFluid(1f);
    private final GuiAnim.Ripple ripple = new GuiAnim.Ripple();

    private long lastTimeMs = GuiAnim.millis();
    private long pressedAtMs;

    public ButtonComponent(ButtonSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public int getPreferredHeight() {
        return ROW_HEIGHT;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        long now = GuiAnim.millis();
        float delta = Math.min(0.1f, (now - lastTimeMs) / 1000.0f);
        lastTimeMs = now;

        boolean hovered = isHovered(mouseX, mouseY);
        boolean held = now - pressedAtMs < 120L;

        hover.setTarget(hovered ? 1f : 0f);
        press.setTarget(held ? 0.955f : 1f);
        hover.update(delta);
        press.update(delta);
        ripple.update(delta, 2.6f);

        float hoverT = GuiAnim.clamp01(hover.getCurrentValue());
        float scale = press.getCurrentValue();

        float bx = x + INSET;
        float by = y + 2.5f;
        float bw = width - INSET * 2f;
        float bh = height - 5f;
        float cx = bx + bw / 2f;
        float cy = by + bh / 2f;

        GuiDraw.pushScale(cx, cy, scale, scale);

        if (hoverT > 0.01f) {
            GuiDraw.roundedRect(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 4.5f,
                    alphaColor(GuiDraw.withAlpha(GuiTheme.ACCENT, 0.28f * hoverT)));
        }

        int bg = GuiDraw.lerpColor(GuiTheme.CHIP_BG, 0xFF1E3A63, hoverT);
        int border = GuiDraw.lerpColor(GuiTheme.PANEL_BORDER, GuiTheme.ACCENT, hoverT);
        GuiDraw.roundedRect(bx, by, bx + bw, by + bh, 3.5f, alphaColor(bg), alphaColor(border));

        if (ripple.isActive()) {
            GuiDraw.beginClip(bx, by, bw, bh);
            GuiDraw.ripple(ripple.getX(), ripple.getY(), bw * 0.75f, ripple.getProgress(),
                    alphaColor(GuiTheme.ACCENT));
            GuiDraw.endClip();
        }

        String label = setting.name;
        float textW = GuiDraw.textWidth(label);
        int textColor = GuiDraw.lerpColor(GuiTheme.TEXT_DIM, 0xFFFFFFFF, hoverT);
        GuiDraw.textFitScaled(label, cx - textW / 2f, cy - 4f, 1f, bw - 8f,
                alphaColor(textColor), false);

        GuiDraw.popMatrix();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && isHovered(mouseX, mouseY)) {
            pressedAtMs = GuiAnim.millis();
            press.setVelocity(0f);
            ripple.trigger(mouseX, mouseY);
            setting.run();
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}

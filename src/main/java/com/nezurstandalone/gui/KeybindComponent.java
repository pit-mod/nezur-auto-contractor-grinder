package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.settings.KeybindSetting;
import org.lwjgl.input.Keyboard;

/**
 * Keybind pill. While listening it pulses with the accent colour and shows animated dots so
 * it is obvious the GUI is waiting for a key.
 */
public class KeybindComponent extends Component {

    private static final int ROW_HEIGHT = 16;
    private static final float TEXT_SCALE = 0.85f;

    public static KeybindComponent currentlyBinding = null;

    private final KeybindSetting keySet;
    private boolean binding;

    private final PhysicsSpring hover = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring pillWidth = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring pop = PhysicsSpring.iOSFluid(1f);

    private long lastTimeMs = GuiAnim.millis();
    private boolean primed;

    public KeybindComponent(KeybindSetting setting) {
        super(setting);
        this.keySet = setting;
    }

    @Override
    public int getPreferredHeight() {
        return ROW_HEIGHT;
    }

    private String keyName() {
        if (keySet.code < 0) {
            return "MB" + (keySet.code + 100);
        }
        if (keySet.code == Keyboard.KEY_NONE) {
            return "NONE";
        }
        String name = Keyboard.getKeyName(keySet.code);
        return name == null || name.isEmpty() ? "NONE" : name;
    }

    private String displayText() {
        if (!binding) {
            return keyName();
        }
        int dots = (int) (GuiAnim.time() * 3f) % 4;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dots; i++) {
            sb.append('.');
        }
        return sb.length() == 0 ? "." : sb.toString();
    }

    @Override
    public void render(int mouseX, int mouseY) {
        long now = GuiAnim.millis();
        float delta = Math.min(0.1f, (now - lastTimeMs) / 1000.0f);
        lastTimeMs = now;

        boolean hovered = isHovered(mouseX, mouseY);
        String text = displayText();
        // Width follows the resting label so the pill does not jitter with the dots.
        float targetW = GuiDraw.textWidthScaled(binding ? "..." : keyName(), TEXT_SCALE) + 12f;

        if (!primed) {
            primed = true;
            pillWidth.snapTo(targetW);
        }

        hover.setTarget(hovered || binding ? 1f : 0f);
        pillWidth.setTarget(targetW);
        pop.setTarget(1f);
        hover.update(delta);
        pillWidth.update(delta);
        pop.update(delta);

        float hoverT = GuiAnim.clamp01(hover.getCurrentValue());

        if (hoverT > 0.01f) {
            GuiDraw.roundedRect(x + 1, y + 1, x + width - 1, y + height - 1, 3f,
                    alphaColor(GuiDraw.withAlpha(GuiTheme.ROW_BG_HOVER, hoverT)));
        }

        float pillW = Math.max(16f, pillWidth.getCurrentValue());
        float pillH = 11f;
        float pillX = x + width - 6f - pillW;
        float pillY = y + (height - pillH) / 2f;

        GuiDraw.textFitScaled(keySet.name, x + GuiTheme.PADDING_X, y + (height - 8) / 2f, 1f,
                pillX - 5f - (x + GuiTheme.PADDING_X), alphaColor(GuiTheme.SETTINGS_TEXT), false);

        float scale = pop.getCurrentValue();
        GuiDraw.pushScale(pillX + pillW / 2f, pillY + pillH / 2f, scale, scale);

        if (binding) {
            float glow = 0.35f + 0.45f * GuiAnim.pulse(1.1f);
            GuiDraw.roundedRect(pillX - 1.5f, pillY - 1.5f, pillX + pillW + 1.5f, pillY + pillH + 1.5f,
                    pillH / 2f + 1.5f, alphaColor(GuiDraw.withAlpha(GuiTheme.ACCENT, glow)));
        }

        int bg = binding ? 0xFF1E3A63 : GuiDraw.lerpColor(0xFF1A1A21, 0xFF233A5E, hoverT);
        int border = binding ? GuiTheme.ACCENT : GuiDraw.lerpColor(GuiTheme.PANEL_BORDER, GuiTheme.ACCENT, hoverT);
        GuiDraw.roundedRect(pillX, pillY, pillX + pillW, pillY + pillH, pillH / 2f,
                alphaColor(bg), alphaColor(border));

        float textW = GuiDraw.textWidthScaled(text, TEXT_SCALE);
        GuiDraw.textScaled(text, pillX + (pillW - textW) / 2f, pillY + 2f, TEXT_SCALE,
                alphaColor(binding ? 0xFFFFFFFF : GuiDraw.lerpColor(GuiTheme.TEXT_DIM, 0xFFFFFFFF, hoverT)),
                false);

        GuiDraw.popMatrix();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (binding) {
            if (mouseButton == 0 && !isHovered(mouseX, mouseY)) {
                stopBinding();
                return;
            }
            InputComponent.clearFocus();
            keySet.setKey(-100 + mouseButton);
            stopBinding();
            return;
        }

        if (isHovered(mouseX, mouseY) && mouseButton == 0) {
            if (currentlyBinding != null) {
                currentlyBinding.binding = false;
            }
            binding = true;
            currentlyBinding = this;
            pop.setVelocity(0f);
            InputComponent.clearFocus();
        }
    }

    public static void clearBinding() {
        if (currentlyBinding != null) currentlyBinding.stopBinding();
    }

    private void stopBinding() {
        binding = false;
        if (currentlyBinding == this) {
            currentlyBinding = null;
        }
        pop.setVelocity(0f);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!binding) {
            return;
        }
        InputComponent.clearFocus();
        if (keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_ESCAPE) {
            keySet.setKey(Keyboard.KEY_NONE);
        } else {
            keySet.setKey(keyCode);
        }
        stopBinding();
    }
}

package com.nezurstandalone.gui;

import com.nezurstandalone.settings.BooleanSetting;

public class BooleanComponent extends Component {
    private final BooleanSetting boolSet;

    // Animation States
    private float checkAnim = 0.0f;
    private float clickPulse = 0.0f;
    private float hoverAnim = 0.0f;
    private long lastTimeMs = GuiAnim.millis();

    public BooleanComponent(BooleanSetting setting) {
        super(setting);
        this.boolSet = setting;
        this.checkAnim = setting.enabled ? 1.0f : 0.0f;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        long now = GuiAnim.millis();
        float delta = Math.min(0.1f, (now - lastTimeMs) / 1000.0f);
        lastTimeMs = now;

        boolean hovered = isHovered(mouseX, mouseY);

        // Smooth Lerp Animations
        float targetCheck = boolSet.enabled ? 1.0f : 0.0f;
        checkAnim = GuiAnim.approach(checkAnim, targetCheck, 16.0f, delta);
        if (!boolSet.enabled && checkAnim < 0.02f) checkAnim = 0.0f;
        if (boolSet.enabled && checkAnim > 0.98f) checkAnim = 1.0f;

        clickPulse = GuiAnim.approach(clickPulse, 0.0f, 12.0f, delta);
        hoverAnim = GuiAnim.approach(hoverAnim, hovered ? 1.0f : 0.0f, 14.0f, delta);

        if (hoverAnim > 0.01f) {
            GuiDraw.roundedRect(x + 1, y + 1, x + width - 1, y + height - 1, 3f,
                    alphaColor(GuiDraw.withAlpha(GuiTheme.ROW_BG_HOVER, hoverAnim)));
        }

        // Setting label
        drawSmartText(boolSet.name, x + GuiTheme.PADDING_X, y + (height - 8) / 2, alphaColor(GuiTheme.SETTINGS_TEXT), 24);

        // iOS-style pill, matching the module rows. The click pulse squashes it briefly.
        float squash = 1.0f + 0.12f * clickPulse;
        float trackW = IoSToggleRenderer.TRACK_W * squash;
        float trackH = IoSToggleRenderer.TRACK_H;
        float trackX = x + width - trackW - 6;
        float trackY = y + (height - trackH) / 2.0f;
        IoSToggleRenderer.draw(trackX, trackY, trackW, trackH, checkAnim, GuiTheme.TOGGLE_ON, rowAlpha);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (isHovered(mouseX, mouseY) && mouseButton == 0) {
            boolSet.toggle();
            clickPulse = 1.0f;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {}

    @Override
    public void keyTyped(char typedChar, int keyCode) {}
}

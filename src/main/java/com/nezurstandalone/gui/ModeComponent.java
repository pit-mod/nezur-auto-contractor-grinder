package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.settings.ModeSetting;

/**
 * Mode selector pill. Cycling swaps the label with a vertical slide — the outgoing value
 * rises out of the pill while the incoming one climbs into place — and the pill itself
 * springs to the new label's width.
 */
public class ModeComponent extends Component {

    private static final int ROW_HEIGHT = 16;
    private static final float TEXT_SCALE = 0.85f;

    private final ModeSetting modeSet;

    private final PhysicsSpring hover = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring pillWidth = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring pop = PhysicsSpring.iOSFluid(1f);

    private String shownMode;
    private String outgoingMode;
    private float swapProgress = 1f;
    private long lastTimeMs = GuiAnim.millis();
    private boolean primed;

    public ModeComponent(ModeSetting setting) {
        super(setting);
        this.modeSet = setting;
        this.shownMode = setting.getMode();
    }

    @Override
    public int getPreferredHeight() {
        return ROW_HEIGHT;
    }

    private float pillWidthFor(String text) {
        return GuiDraw.textWidthScaled(text, TEXT_SCALE) + 12f;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        long now = GuiAnim.millis();
        float delta = Math.min(0.1f, (now - lastTimeMs) / 1000.0f);
        lastTimeMs = now;

        // The setting can also change from config loads, so reconcile every frame.
        String current = modeSet.getMode();
        if (!current.equals(shownMode) && swapProgress >= 1f) {
            outgoingMode = shownMode;
            shownMode = current;
            swapProgress = 0f;
            pop.setVelocity(0f);
        }
        if (swapProgress < 1f) {
            swapProgress = GuiAnim.approach(swapProgress, 1f, 6.0f, delta);
        }

        if (!primed) {
            primed = true;
            pillWidth.snapTo(pillWidthFor(shownMode));
        }

        boolean hovered = isHovered(mouseX, mouseY);
        hover.setTarget(hovered ? 1f : 0f);
        pillWidth.setTarget(pillWidthFor(shownMode));
        pop.setTarget(1f);
        hover.update(delta);
        pillWidth.update(delta);
        pop.update(delta);

        float hoverT = GuiAnim.clamp01(hover.getCurrentValue());

        if (hoverT > 0.01f) {
            GuiDraw.roundedRect(x + 1, y + 1, x + width - 1, y + height - 1, 3f,
                    alphaColor(GuiDraw.withAlpha(GuiTheme.ROW_BG_HOVER, hoverT)));
        }

        float pillW = Math.max(14f, pillWidth.getCurrentValue());
        float pillH = 11f;
        float pillX = x + width - 6f - pillW;
        float pillY = y + (height - pillH) / 2f;

        GuiDraw.textFitScaled(modeSet.name, x + GuiTheme.PADDING_X, y + (height - 8) / 2f, 1f,
                pillX - 5f - (x + GuiTheme.PADDING_X), alphaColor(GuiTheme.SETTINGS_TEXT), false);

        float scale = pop.getCurrentValue();
        GuiDraw.pushScale(pillX + pillW / 2f, pillY + pillH / 2f, scale, scale);

        int bg = GuiDraw.lerpColor(0xFF1A1A21, 0xFF233A5E, hoverT);
        int border = GuiDraw.lerpColor(GuiTheme.PANEL_BORDER, GuiTheme.ACCENT, hoverT);
        GuiDraw.roundedRect(pillX, pillY, pillX + pillW, pillY + pillH, pillH / 2f,
                alphaColor(bg), alphaColor(border));

        // Slide the old label out and the new one in, clipped to the pill.
        GuiDraw.beginClip(pillX, pillY, pillW, pillH);
        float eased = GuiAnim.outCubic(swapProgress);
        int textColor = GuiDraw.lerpColor(GuiTheme.TEXT_DIM, 0xFFFFFFFF, hoverT);

        if (outgoingMode != null && swapProgress < 1f) {
            float outW = GuiDraw.textWidthScaled(outgoingMode, TEXT_SCALE);
            GuiDraw.textScaled(outgoingMode, pillX + (pillW - outW) / 2f,
                    pillY + 2f - pillH * eased, TEXT_SCALE,
                    alphaColor(GuiDraw.withAlpha(textColor, 1f - eased)), false);
        }
        float inW = GuiDraw.textWidthScaled(shownMode, TEXT_SCALE);
        GuiDraw.textScaled(shownMode, pillX + (pillW - inW) / 2f,
                pillY + 2f + pillH * (1f - eased), TEXT_SCALE,
                alphaColor(GuiDraw.withAlpha(textColor, eased)), false);
        GuiDraw.endClip();

        GuiDraw.popMatrix();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isHovered(mouseX, mouseY)) {
            return;
        }
        if (mouseButton == 0) {
            modeSet.cycle();
        } else if (mouseButton == 1) {
            // Right-click steps backwards through the list.
            modeSet.setMode(modeSet.modes.get(
                    (modeSet.index - 1 + modeSet.modes.size()) % modeSet.modes.size()));
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}

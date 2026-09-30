package com.nezurstandalone.gui;

import com.nezurstandalone.settings.Setting;

public abstract class Component {
    public Setting setting;
    public int x, y, width, height;
    
    public Component(Setting setting) {
        this.setting = setting;
    }
    
    public void updatePosition(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Rows are one line by default; taller controls (sliders, buttons) override this. */
    public int getPreferredHeight() {
        return GuiTheme.ROW_H;
    }

    /**
     * Extra height this control claims below its base row, e.g. an open dropdown's option list.
     * Zero for everything else, so the settings panel keeps its exact spacing unless a control is
     * actively expanded. The panel adds this to the row height so following rows are pushed down.
     */
    public int getExtraHeight() {
        return 0;
    }
    
    public abstract void render(int mouseX, int mouseY);
    public abstract void mouseClicked(int mouseX, int mouseY, int mouseButton);
    public abstract void mouseReleased(int mouseX, int mouseY, int state);

    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
    }
    public abstract void keyTyped(char typedChar, int keyCode);
    
    public boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    protected float rowAlpha = 1f;
    protected float rowSlideOffset = 0f;

    /**
     * Click acknowledgement for a settings control, mirroring the flash a module row gives in
     * the category panels. The category rows had it and the options in the settings panel did
     * not, so a click on a toggle or slider registered with no visual echo at all.
     */
    private float press;

    /** Fired by the palette when this control is clicked. */
    public void press() {
        press = 1f;
    }

    /** Decays the flash; call once per frame with the frame delta. */
    public void tickPress(float delta) {
        if (press > 0f) {
            press = Math.max(0f, press - delta * 5.5f);
        }
    }

    /** Current flash strength, 0..1. */
    public float pressLevel() {
        return press;
    }

    public void setRowAnim(float alpha, float slideOffset) {
        this.rowAlpha = alpha;
        this.rowSlideOffset = slideOffset;
    }

    protected void drawSmartText(String text, int drawX, int drawY, int color, int reserveRightPx) {
        int available = Math.max(1, width - reserveRightPx - (drawX - x));
        GuiText.drawFit(text, drawX, drawY, available, alphaColor(color));
    }

    protected int alphaColor(int color) {
        if (rowAlpha >= 0.999f) {
            return color;
        }
        int a = (color >> 24) & 0xFF;
        a = (int) (a * rowAlpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    protected String animPrefix = "";

    public void setAnimPrefix(String prefix) {
        this.animPrefix = prefix == null ? "" : prefix;
    }
}


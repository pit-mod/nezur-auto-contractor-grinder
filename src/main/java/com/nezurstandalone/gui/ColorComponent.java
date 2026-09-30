package com.nezurstandalone.gui;

import com.nezurstandalone.settings.ColorSetting;
import com.nezurstandalone.utils.RenderUtils;

import java.awt.Color;

public class ColorComponent extends Component {
    private final ColorSetting colorSet;
    private static ColorPickerPopup activePicker = null;

    public ColorComponent(ColorSetting setting) {
        super(setting);
        this.colorSet = setting;
    }

    public static ColorPickerPopup getActivePicker() {
        return activePicker;
    }

    public static void closePicker() {
        activePicker = null;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        boolean hovered = isHovered(mouseX, mouseY);
        if (hovered) {
            RenderUtils.drawRect(x, y, x + width, y + height, alphaColor(GuiTheme.ROW_BG_HOVER));
        }

        // Label on left
        drawSmartText(colorSet.name, x + GuiTheme.PADDING_X, y + (height - 8) / 2, alphaColor(GuiTheme.SETTINGS_TEXT), 20);

        Color c = colorSet.getColor();

        // Meteor style Color preview box (20x10) on right
        int boxW = 20;
        int boxH = 10;
        int boxX = x + width - boxW - 4;
        int boxY = y + (height - boxH) / 2;

        RenderUtils.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, c.getRGB());
        RenderUtils.drawOutline(boxX, boxY, boxX + boxW, boxY + boxH, alphaColor(hovered ? GuiTheme.ACCENT : GuiTheme.BORDER));
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isHovered(mouseX, mouseY)) return;

        if (mouseButton == 2) {
            colorSet.setColor(colorSet.getDefaultColor());
            return;
        }

        // Open Meteor-style Color Picker Popup
        activePicker = new ColorPickerPopup(colorSet);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {}

    @Override
    public void keyTyped(char typedChar, int keyCode) {}
}

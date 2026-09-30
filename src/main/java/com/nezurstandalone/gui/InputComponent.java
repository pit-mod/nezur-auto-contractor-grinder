package com.nezurstandalone.gui;

import com.nezurstandalone.settings.InputSetting;
import com.nezurstandalone.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

public class InputComponent extends Component {
    private static InputComponent active;

    private final InputSetting input;
    private final TextInputHelper editor = new TextInputHelper();
    private boolean typing;

    public InputComponent(InputSetting setting) {
        super(setting);
        this.input = setting;
        editor.setText(setting.getContent());
    }

    public static void clearFocus() {
        if (active != null) {
            active.syncToSetting();
            active.typing = false;
            active = null;
        }
    }

    public static boolean isTyping() {
        return active != null && active.typing;
    }

    public static void tickRepeatKeys() {
        if (active != null && active.typing) {
            active.editor.tickRepeatKeys();
            active.syncToSetting();
        }
    }

    private void syncToSetting() {
        input.setContent(editor.getText());
    }

    private void syncFromSetting() {
        editor.setText(input.getContent());
    }

    @Override
    public void render(int mouseX, int mouseY) {
        boolean hovered = isHovered(mouseX, mouseY);
        if (hovered) {
            RenderUtils.drawRect(x, y, x + width, y + height, alphaColor(GuiTheme.ROW_BG_HOVER));
        }

        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        boolean focused = typing && active == this;

        // Label on left
        drawSmartText(input.name, x + GuiTheme.PADDING_X, y + (height - 8) / 2, alphaColor(GuiTheme.SETTINGS_TEXT), 20);

        // Meteor style text box on right
        int boxW = Math.min(80,Math.max(24,width/2));
        int boxH = 12;
        int boxX = x + width - boxW - 4;
        int boxY = y + (height - boxH) / 2;

        int boxBg = focused ? 0xFF282828 : (hovered ? 0xFF202020 : 0xFF181818);
        RenderUtils.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, alphaColor(boxBg));
        RenderUtils.drawOutline(boxX, boxY, boxX + boxW, boxY + boxH, alphaColor(focused ? GuiTheme.ACCENT : GuiTheme.BORDER));

        if (focused) {
            String settingValue = input.getContent();
            if (settingValue == null) settingValue = "";
            if (!settingValue.equals(editor.getText())) editor.setText(settingValue);
            editor.drawWithin(fr, boxX + 4, boxY + 2, alphaColor(GuiTheme.TEXT), true, boxW-8);
        } else {
            String text = input.getContent();
            if (text == null || text.isEmpty()) text = "...";
            fr.drawStringWithShadow(fr.trimStringToWidth(text,boxW-8), boxX + 4, boxY + 2, alphaColor(GuiTheme.TEXT_MUTED));
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isHovered(mouseX, mouseY) || mouseButton != 0) {
            return;
        }

        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        int boxW = Math.min(80,Math.max(24,width/2));
        int boxX = x + width - boxW - 4;

        if (active != this) {
            clearFocus();
            active = this;
            typing = true;
            syncFromSetting();
        }

        if (mouseX >= boxX) {
            editor.beginMouseSelection(mouseX, boxX + 4, fr);
        } else {
            editor.selectAll();
            editor.endMouseSelection();
        }
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!typing || active != this || clickedMouseButton != 0) {
            return;
        }
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        int boxW = Math.min(80,Math.max(24,width/2));
        int boxX = x + width - boxW - 4;
        editor.updateMouseSelection(mouseX, boxX + 4, fr);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (typing && active == this) {
            editor.endMouseSelection();
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!typing || active != this) {
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_ESCAPE) {
            clearFocus();
            return;
        }
        if (editor.handleKeyTyped(typedChar, keyCode)) {
            syncToSetting();
        }
    }
}

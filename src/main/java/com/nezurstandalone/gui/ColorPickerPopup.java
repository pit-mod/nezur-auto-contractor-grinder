package com.nezurstandalone.gui;

import com.nezurstandalone.settings.ColorSetting;
import com.nezurstandalone.utils.RenderUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public class ColorPickerPopup {
    private static final int[] HUE_COLORS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    private final ColorSetting setting;
    private final TextInputHelper rInput = new TextInputHelper();
    private final TextInputHelper gInput = new TextInputHelper();
    private final TextInputHelper bInput = new TextInputHelper();
    private final TextInputHelper aInput = new TextInputHelper();
    private final TextInputHelper hexInput = new TextInputHelper();

    private float hue = 0f;
    private float saturation = 1f;
    private float brightness = 1f;
    private int alpha = 255;

    private boolean draggingQuad = false;
    private boolean draggingHue = false;
    private String activeInput = null;

    public ColorPickerPopup(ColorSetting setting) {
        this.setting = setting;
        updateFromSetting();
    }

    public void updateFromSetting() {
        Color c = setting.getColor();
        alpha = c.getAlpha();
        float[] hsv = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hue = hsv[0];
        saturation = hsv[1];
        brightness = hsv[2];
        syncInputs();
    }

    private void syncInputs() {
        Color c = getColor();
        rInput.setText(String.valueOf(c.getRed()));
        gInput.setText(String.valueOf(c.getGreen()));
        bInput.setText(String.valueOf(c.getBlue()));
        aInput.setText(String.valueOf(c.getAlpha()));
        hexInput.setText(String.format("#%02X%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()));
    }

    public Color getColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }

    public void draw(int mouseX, int mouseY, FontRenderer fr) {
        float scale=uiScale();
        ScaledResolution screen=com.nezurstandalone.utils.ScreenScale.get();
        float delta=Math.min(0.1f,Math.max(0,(System.nanoTime()-lastFrame)*1e-9f));
        lastFrame=System.nanoTime();
        hoverDelta=delta;
        open=GuiAnim.approach(open,1,16,delta);
        if (!com.nezurstandalone.module.impl.render.ClickGuiSettings.animationsOn()) open=1;
        GuiDraw.rect(0,0,screen.getScaledWidth(),screen.getScaledHeight(),GuiDraw.withAlpha(0x88000000,open));
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try {
            net.minecraft.client.renderer.GlStateManager.scale(scale,scale,1);
            drawContents((int)(mouseX/scale),(int)(mouseY/scale),fr);
        } finally { net.minecraft.client.renderer.GlStateManager.popMatrix(); }
    }

    private long lastFrame=System.nanoTime();
    private float open,hoverDelta;
    private final java.util.Map<String,Float> buttonHover=new java.util.HashMap<>();
    private static float uiScale() {
        ScaledResolution sr=com.nezurstandalone.utils.ScreenScale.get();
        return Math.max(0.1f,Math.min(1f,Math.min(sr.getScaledWidth()/216f,sr.getScaledHeight()/256f)));
    }

    private void drawContents(int mouseX,int mouseY,FontRenderer fr) {
        // Dim background


        int pw = 200;
        int ph = 240;
        ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();
        int px = ((int)(sr.getScaledWidth()/uiScale()) - pw) / 2;
        int py = ((int)(sr.getScaledHeight()/uiScale()) - ph) / 2;

        GuiDraw.shadow(px,py,px+pw,py+ph,6,6,110);
        GuiDraw.glass(px,py,px+pw,py+ph,6,GuiTheme.PANEL_BG,GuiTheme.GLASS_BORDER,0.5f,1);
        RenderUtils.drawOutline(px, py, px + pw, py + ph, GuiTheme.BORDER);
        RenderUtils.drawHLine(px, px + pw, py + 22, GuiTheme.BORDER);

        GuiDraw.textFitScaled("Colour · " + setting.name,px+8,py+7,1,pw-34,GuiTheme.TEXT,false);

        // Close button
        int closeX = px + pw - 18;
        int closeY = py + 4;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14;
        RenderUtils.drawRect(closeX, closeY, closeX + 14, closeY + 14, closeHov ? 0xFFFF4444 : GuiTheme.ROW_BG);
        fr.drawStringWithShadow("X", closeX + 4, closeY + 3, 0xFFFFFF);

        // Saturation / Value Quad (184x100)
        int quadX = px + 8;
        int quadY = py + 28;
        int quadW = 184;
        int quadH = 90;

        GuiDraw.roundedRectGradient(quadX,quadY,quadX+quadW,quadY+quadH,0,0xFFFFFFFF,Color.HSBtoRGB(hue,1f,1f));
        GuiDraw.roundedRectGradientV(quadX,quadY,quadX+quadW,quadY+quadH,0,0x00000000,0xFF000000);
        RenderUtils.drawOutline(quadX, quadY, quadX + quadW, quadY + quadH, GuiTheme.BORDER);

        // Handle on Quad
        int handleX = quadX + (int) (saturation * quadW);
        int handleY = quadY + (int) ((1f - brightness) * quadH);
        RenderUtils.drawRect(handleX - 2, handleY - 2, handleX + 2, handleY + 2, 0xFFFFFFFF);
        RenderUtils.drawOutline(handleX - 2, handleY - 2, handleX + 2, handleY + 2, 0xFF000000);

        // Hue Slider Bar
        int hueX = px + 8;
        int hueY = quadY + quadH + 8;
        int hueW = 184;
        int hueH = 12;

        float secW = (float) hueW / (HUE_COLORS.length - 1);
        for (int i = 0; i < HUE_COLORS.length - 1; i++) {
            GuiDraw.roundedRectGradient(hueX+i*secW,hueY,hueX+(i+1)*secW,hueY+hueH,0,HUE_COLORS[i],HUE_COLORS[i+1]);
        }
        RenderUtils.drawOutline(hueX, hueY, hueX + hueW, hueY + hueH, GuiTheme.BORDER);

        int hueHandleX = hueX + (int) (hue * hueW);
        RenderUtils.drawRect(hueHandleX - 1, hueY, hueHandleX + 2, hueY + hueH, 0xFFFFFFFF);

        // RGBA Input Rows
        int inputY = hueY + hueH + 8;
        drawInputBox("R:", rInput, px + 8, inputY, 40, mouseX, mouseY, fr, "r");
        drawInputBox("G:", gInput, px + 54, inputY, 40, mouseX, mouseY, fr, "g");
        drawInputBox("B:", bInput, px + 100, inputY, 40, mouseX, mouseY, fr, "b");
        drawInputBox("A:", aInput, px + 146, inputY, 40, mouseX, mouseY, fr, "a");

        // Hex Input Row
        int hexY = inputY + 22;
        drawInputBox("Hex:", hexInput, px + 8, hexY, 184, mouseX, mouseY, fr, "hex");

        // Bottom Action buttons (Copy, Paste, Reset)
        int btnY = hexY + 24;
        drawButton("Copy", px + 8, btnY, 55, 16, mouseX, mouseY, fr);
        drawButton("Paste", px + 72, btnY, 55, 16, mouseX, mouseY, fr);
        drawButton("Reset", px + 136, btnY, 56, 16, mouseX, mouseY, fr);
    }

    private void drawInputBox(String label, TextInputHelper helper, int x, int y, int w, int mouseX, int mouseY, FontRenderer fr, String key) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 16;
        boolean focused = key.equals(activeInput);
        int bg = focused ? GuiTheme.ROW_BG_HOVER : (hov ? 0x44303035 : GuiTheme.ROW_BG);

        RenderUtils.drawRect(x, y, x + w, y + 16, bg);
        RenderUtils.drawOutline(x, y, x + w, y + 16, focused ? GuiTheme.ACCENT : GuiTheme.BORDER);
        helper.drawWithin(fr, x + 4, y + 4, GuiTheme.TEXT, focused,w-8);
    }

    private void drawButton(String label, int x, int y, int w, int h, int mouseX, int mouseY, FontRenderer fr) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        float t=GuiAnim.approach(buttonHover.containsKey(label)?buttonHover.get(label):0,hov?1:0,18,hoverDelta);
        buttonHover.put(label,t);
        GuiDraw.roundedRect(x,y,x+w,y+h,3,GuiDraw.lerpColor(GuiTheme.CHIP_BG,GuiTheme.CHIP_BG_HOVER,t),GuiDraw.lerpColor(GuiTheme.BORDER,GuiTheme.ACCENT,t));
        RenderUtils.drawOutline(x, y, x + w, y + h, GuiTheme.BORDER);
        int strW = fr.getStringWidth(label);
        fr.drawStringWithShadow(label, x + (w - strW) / 2, y + 4, GuiTheme.TEXT);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button!=0) return true;
        mouseX=(int)(mouseX/uiScale()); mouseY=(int)(mouseY/uiScale());
        ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();
        int pw = 200;
        int ph = 240;
        int px = ((int)(sr.getScaledWidth()/uiScale()) - pw) / 2;
        int py = ((int)(sr.getScaledHeight()/uiScale()) - ph) / 2;

        // Close button
        int closeX = px + pw - 18;
        int closeY = py + 4;
        if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14) {
            return false; // Close popup
        }

        // Quad click
        int quadX = px + 8;
        int quadY = py + 28;
        int quadW = 184;
        int quadH = 90;
        if (mouseX >= quadX && mouseX <= quadX + quadW && mouseY >= quadY && mouseY <= quadY + quadH) {
            draggingQuad = true;
            updateQuad(mouseX, mouseY, quadX, quadY, quadW, quadH);
            return true;
        }

        // Hue slider click
        int hueX = px + 8;
        int hueY = quadY + quadH + 8;
        int hueW = 184;
        int hueH = 12;
        if (mouseX >= hueX && mouseX <= hueX + hueW && mouseY >= hueY && mouseY <= hueY + hueH) {
            draggingHue = true;
            updateHue(mouseX, hueX, hueW);
            return true;
        }

        // Input clicks
        int inputY = hueY + hueH + 8;
        if (mouseX >= px + 8 && mouseX <= px + 48 && mouseY >= inputY && mouseY <= inputY + 16) { activeInput = "r"; rInput.beginMouseSelection(mouseX, px + 12, net.minecraft.client.Minecraft.getMinecraft().fontRendererObj); return true; }
        if (mouseX >= px + 54 && mouseX <= px + 94 && mouseY >= inputY && mouseY <= inputY + 16) { activeInput = "g"; gInput.beginMouseSelection(mouseX, px + 58, net.minecraft.client.Minecraft.getMinecraft().fontRendererObj); return true; }
        if (mouseX >= px + 100 && mouseX <= px + 140 && mouseY >= inputY && mouseY <= inputY + 16) { activeInput = "b"; bInput.beginMouseSelection(mouseX, px + 104, net.minecraft.client.Minecraft.getMinecraft().fontRendererObj); return true; }
        if (mouseX >= px + 146 && mouseX <= px + 186 && mouseY >= inputY && mouseY <= inputY + 16) { activeInput = "a"; aInput.beginMouseSelection(mouseX, px + 150, net.minecraft.client.Minecraft.getMinecraft().fontRendererObj); return true; }

        int hexY = inputY + 22;
        if (mouseX >= px + 8 && mouseX <= px + 192 && mouseY >= hexY && mouseY <= hexY + 16) { activeInput = "hex"; hexInput.beginMouseSelection(mouseX, px + 12, net.minecraft.client.Minecraft.getMinecraft().fontRendererObj); return true; }

        activeInput = null;

        // Action Buttons
        int btnY = hexY + 24;
        if (mouseX >= px + 8 && mouseX <= px + 63 && mouseY >= btnY && mouseY <= btnY + 16) {
            copyToClipboard();
            return true;
        }
        if (mouseX >= px + 72 && mouseX <= px + 127 && mouseY >= btnY && mouseY <= btnY + 16) {
            pasteFromClipboard();
            return true;
        }
        if (mouseX >= px + 136 && mouseX <= px + 192 && mouseY >= btnY && mouseY <= btnY + 16) {
            setting.setColor(setting.getDefaultColor());
            updateFromSetting();
            return true;
        }

        return true;
    }

    public void mouseClickMove(int mouseX, int mouseY, int button) {
        if (button!=0) return;
        mouseX=(int)(mouseX/uiScale()); mouseY=(int)(mouseY/uiScale());
        ScaledResolution sr = com.nezurstandalone.utils.ScreenScale.get();
        int pw = 200;
        int ph = 240;
        int px = ((int)(sr.getScaledWidth()/uiScale()) - pw) / 2;
        int py = ((int)(sr.getScaledHeight()/uiScale()) - ph) / 2;

        int quadX = px + 8;
        int quadY = py + 28;
        int quadW = 184;
        int quadH = 90;

        if (draggingQuad) {
            updateQuad(mouseX, mouseY, quadX, quadY, quadW, quadH);
        } else if (draggingHue) {
            updateHue(mouseX, quadX, quadW);
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        rInput.endMouseSelection(); gInput.endMouseSelection(); bInput.endMouseSelection(); aInput.endMouseSelection(); hexInput.endMouseSelection();
        draggingQuad = false;
        draggingHue = false;
    }

    private void updateQuad(int mouseX, int mouseY, int x, int y, int w, int h) {
        saturation = Math.max(0f, Math.min(1f, (float) (mouseX - x) / w));
        brightness = Math.max(0f, Math.min(1f, 1f - (float) (mouseY - y) / h));
        setting.setColor(getColor());
        syncInputs();
    }

    private void updateHue(int mouseX, int x, int w) {
        hue = Math.max(0f, Math.min(1f, (float) (mouseX - x) / w));
        setting.setColor(getColor());
        syncInputs();
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (activeInput == null) return;
        TextInputHelper current = getActiveHelper();
        if (current == null) return;

        if (keyCode == Keyboard.KEY_RETURN) {
            applyInputs();
            syncInputs();
            activeInput = null;
            return;
        }

        current.handleKeyTyped(typedChar, keyCode);
        applyInputs();
    }

    private TextInputHelper getActiveHelper() {
        if ("r".equals(activeInput)) return rInput;
        if ("g".equals(activeInput)) return gInput;
        if ("b".equals(activeInput)) return bInput;
        if ("a".equals(activeInput)) return aInput;
        if ("hex".equals(activeInput)) return hexInput;
        return null;
    }

    private void applyInputs() {
        try {
            if ("hex".equals(activeInput)) {
                String val = hexInput.getText().replace("#", "").trim();
                if (val.length() == 6 || val.length() == 8) {
                    int r = Integer.parseInt(val.substring(0, 2), 16);
                    int g = Integer.parseInt(val.substring(2, 4), 16);
                    int b = Integer.parseInt(val.substring(4, 6), 16);
                    int a = val.length() == 8 ? Integer.parseInt(val.substring(6, 8), 16) : 255;
                    setting.setColor(new Color(r, g, b, a));
                    float[] hsv = Color.RGBtoHSB(r, g, b, null);
                    hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; alpha = a;
                }
            } else {
                int r = Math.max(0, Math.min(255, Integer.parseInt(rInput.getText())));
                int g = Math.max(0, Math.min(255, Integer.parseInt(gInput.getText())));
                int b = Math.max(0, Math.min(255, Integer.parseInt(bInput.getText())));
                int a = Math.max(0, Math.min(255, Integer.parseInt(aInput.getText())));
                setting.setColor(new Color(r, g, b, a));
                float[] hsv = Color.RGBtoHSB(r, g, b, null);
                hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; alpha = a;
            }
            if ("hex".equals(activeInput)) {
                Color c=getColor();
                rInput.setText(""+c.getRed());gInput.setText(""+c.getGreen());bInput.setText(""+c.getBlue());aInput.setText(""+c.getAlpha());
            } else {
                Color c=getColor();
                hexInput.setText(String.format("#%02X%02X%02X%02X",c.getRed(),c.getGreen(),c.getBlue(),c.getAlpha()));
            }
        } catch (NumberFormatException ignored) { /* Keep the last valid colour while editing. */ }
    }

    private void copyToClipboard() {
        Color c = getColor();
        String str = String.format("#%02X%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        GuiScreen.setClipboardString(str);
    }

    private void pasteFromClipboard() {
        String clip = GuiScreen.getClipboardString();
        if (clip != null && !clip.isEmpty()) {
            hexInput.setText(clip);
            activeInput = "hex";
            applyInputs();
            syncInputs();
            activeInput = null;
        }
    }
}

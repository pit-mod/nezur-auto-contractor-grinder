package com.nezurstandalone.gui;

import com.nezurstandalone.utils.RenderUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

public class TextInputHelper {
    private static final int REPEAT_INITIAL_MS = 400;
    private static final int REPEAT_INTERVAL_MS = 35;
    private static final int DOUBLE_CLICK_MS = 250;
    private static final int SELECTION_COLOR = 0xFF000080;

    private String text = "";
    private int cursor = 0;
    private int viewportStart;
    private int selectionAnchor = -1;

    private long lastClickMs = 0;
    private long lastDeleteMs = 0;
    private boolean repeating = false;
    private boolean dragging = false;

    public void setText(String value) {
        text = value == null ? "" : value;
        viewportStart=0;
        cursor = text.length();
        clearSelection();
        clampIndices();
    }

    public String getText() {
        return text;
    }

    public boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != cursor;
    }

    public int selectionStart() {
        clampIndices();
        return hasSelection() ? Math.min(selectionAnchor, cursor) : cursor;
    }

    public int selectionEnd() {
        clampIndices();
        return hasSelection() ? Math.max(selectionAnchor, cursor) : cursor;
    }

    public void clearSelection() {
        selectionAnchor = -1;
    }

    public void selectAll() {
        selectionAnchor = 0;
        cursor = text.length();
    }

    public void handleMouseClick(int mouseX, int textDrawX, FontRenderer fr) {
        beginMouseSelection(mouseX, textDrawX, fr);
    }

    public void beginMouseSelection(int mouseX, int textDrawX, FontRenderer fr) {
        int pos = getCursorAtXClamped(fr, mouseX - textDrawX + fr.getStringWidth(text.substring(0,Math.min(viewportStart,text.length()))), text);

        long now = GuiAnim.millis();
        boolean doubleClick = lastClickMs != 0 && (now - lastClickMs) < DOUBLE_CLICK_MS;
        lastClickMs = now;

        if (doubleClick) {
            selectWordAt(pos);
            dragging = false;
            return;
        }

        selectionAnchor = pos;
        cursor = pos;
        dragging = true;
    }

    public void updateMouseSelection(int mouseX, int textDrawX, FontRenderer fr) {
        if (!dragging) {
            return;
        }
        cursor = getCursorAtXClamped(fr, mouseX - textDrawX + fr.getStringWidth(text.substring(0,Math.min(viewportStart,text.length()))), text);
    }

    public void endMouseSelection() {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (selectionAnchor == cursor) {
            clearSelection();
        }
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean handleKeyTyped(char typedChar, int keyCode) {
        clampIndices();
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
            lastDeleteMs = GuiAnim.millis();
            repeating = false;
            return keyCode == Keyboard.KEY_BACK ? deleteBackward() : deleteForward();
        }

        if (ctrl && keyCode == Keyboard.KEY_A) {
            selectAll();
            return true;
        }

        if (ctrl && keyCode == Keyboard.KEY_C) {
            copyToClipboard();
            return true;
        }

        if (ctrl && keyCode == Keyboard.KEY_V) {
            pasteFromClipboard();
            return true;
        }

        if (ctrl && keyCode == Keyboard.KEY_X) {
            copyToClipboard();
            if (hasSelection()) {
                removeSelectionIfAny();
            } else if (!text.isEmpty()) {
                text = "";
                cursor = 0;
                clearSelection();
            }
            clampIndices();
            return true;
        }

        if (keyCode == Keyboard.KEY_LEFT) {
            if (hasSelection() && !ctrl) {
                cursor = selectionStart();
                clearSelection();
            } else if (ctrl) {
                cursor = nthWordFromPos(cursor, -1);
            } else {
                cursor = Math.max(0, cursor - 1);
            }
            return true;
        }

        if (keyCode == Keyboard.KEY_RIGHT) {
            if (hasSelection() && !ctrl) {
                cursor = selectionEnd();
                clearSelection();
            } else if (ctrl) {
                cursor = nthWordFromPos(cursor, 1);
            } else {
                cursor = Math.min(text.length(), cursor + 1);
            }
            return true;
        }

        if (keyCode == Keyboard.KEY_HOME) {
            cursor = 0;
            if (!ctrl) {
                clearSelection();
            }
            return true;
        }

        if (keyCode == Keyboard.KEY_END) {
            cursor = text.length();
            if (!ctrl) {
                clearSelection();
            }
            return true;
        }

        if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
            removeSelectionIfAny();
            text = text.substring(0, cursor) + typedChar + text.substring(cursor);
            cursor++;
            return true;
        }

        return false;
    }

    public void tickRepeatKeys() {
        boolean backDown = Keyboard.isKeyDown(Keyboard.KEY_BACK);
        boolean deleteDown = Keyboard.isKeyDown(Keyboard.KEY_DELETE);
        if (!backDown && !deleteDown) {
            lastDeleteMs = 0;
            repeating = false;
            return;
        }
        if (lastDeleteMs == 0) {
            return;
        }

        long now = GuiAnim.millis();
        int threshold = repeating ? REPEAT_INTERVAL_MS : REPEAT_INITIAL_MS;
        if (now - lastDeleteMs < threshold) {
            return;
        }

        boolean changed = backDown ? deleteBackward() : deleteForward();
        if (changed) {
            lastDeleteMs = now;
            repeating = true;
        }
    }

    public void draw(FontRenderer fr, int x, int y, int color, boolean focused) {
        viewportStart=0;
        clampIndices();

        if (hasSelection()) {
            int selStart = selectionStart();
            int selEnd = selectionEnd();
            int selX1 = x + fr.getStringWidth(text.substring(0, selStart));
            int selX2 = x + fr.getStringWidth(text.substring(0, selEnd));
            RenderUtils.drawRect(selX1, y - 1, selX2, y + fr.FONT_HEIGHT, SELECTION_COLOR);
        }

        if (text.isEmpty()) {
            if (focused && GuiAnim.millis() % 1000 < 500) {
                RenderUtils.drawRect(x, y - 1, x + 1, y + fr.FONT_HEIGHT, 0xFFD0D0D0);
            }
            return;
        }

        fr.drawStringWithShadow(text, x, y, color);

        if (focused && !hasSelection() && GuiAnim.millis() % 1000 < 500) {
            int caretX = x + fr.getStringWidth(text.substring(0, cursor));
            RenderUtils.drawRect(caretX, y - 1, caretX + 1, y + fr.FONT_HEIGHT, 0xFFD0D0D0);
        }
    }

    /** Horizontal caret tracking without allowing text or selection to spill into other controls. */
    public void drawWithin(FontRenderer fr,int x,int y,int color,boolean focused,int width) {
        clampIndices();
        if(width<=1) return;
        viewportStart=Math.min(viewportStart,text.length());
        if(!focused) viewportStart=0;
        else {
            viewportStart=Math.min(viewportStart,cursor);
            while(viewportStart<cursor && fr.getStringWidth(text.substring(viewportStart,cursor))>width-2) viewportStart++;
        }
        String visible=fr.trimStringToWidth(text.substring(viewportStart),width-1);
        int end=viewportStart+visible.length();
        if(focused && hasSelection()) {
            int a=Math.max(viewportStart,Math.min(end,selectionStart()));
            int b=Math.max(a,Math.min(end,selectionEnd()));
            if(b>a) RenderUtils.drawRect(x+fr.getStringWidth(text.substring(viewportStart,a)),y-1,
                    x+fr.getStringWidth(text.substring(viewportStart,b)),y+fr.FONT_HEIGHT,0x664A90E2);
        }
        fr.drawStringWithShadow(visible,x,y,color);
        if(focused && !hasSelection() && GuiAnim.millis()%1000<500 && cursor>=viewportStart && cursor<=end) {
            int caret=x+fr.getStringWidth(text.substring(viewportStart,cursor));
            RenderUtils.drawRect(caret,y-1,caret+1,y+fr.FONT_HEIGHT,color);
        }
    }

    private boolean deleteBackward() {
        if (hasSelection()) {
            removeSelectionIfAny();
            return true;
        }
        if (cursor <= 0) {
            return false;
        }
        text = text.substring(0, cursor - 1) + text.substring(cursor);
        cursor--;
        return true;
    }

    private boolean deleteForward() {
        if (hasSelection()) {
            removeSelectionIfAny();
            return true;
        }
        if (cursor >= text.length()) {
            return false;
        }
        text = text.substring(0, cursor) + text.substring(cursor + 1);
        return true;
    }

    private void removeSelectionIfAny() {
        if (!hasSelection()) {
            return;
        }
        int start = selectionStart();
        int end = selectionEnd();
        text = text.substring(0, start) + text.substring(end);
        cursor = start;
        clearSelection();
    }

    private void copyToClipboard() {
        String toCopy;
        if (hasSelection()) {
            toCopy = text.substring(selectionStart(), selectionEnd());
        } else {
            toCopy = text;
        }
        if (!toCopy.isEmpty()) {
            GuiScreen.setClipboardString(toCopy);
        }
    }

    private void pasteFromClipboard() {
        String clipboard = filterPasteText(GuiScreen.getClipboardString());
        if (clipboard.isEmpty()) {
            return;
        }
        removeSelectionIfAny();
        text = text.substring(0, cursor) + clipboard + text.substring(cursor);
        cursor += clipboard.length();
        clampIndices();
    }

    private static String filterPasteText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') {
                continue;
            }
            if (ChatAllowedCharacters.isAllowedCharacter(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void selectWordAt(int pos) {
        pos = clamp(pos);
        int start = nthWordFromPos(pos, -1);
        int end = nthWordFromPos(nthWordFromPos(pos, 1), -1);
        if (start == end && end < text.length()) {
            end = Math.min(text.length(), end + 1);
        }
        selectionAnchor = start;
        cursor = end;
    }

    private void clampIndices() {
        int len = text.length();
        if (cursor < 0) {
            cursor = 0;
        } else if (cursor > len) {
            cursor = len;
        }
        if (selectionAnchor < -1) {
            selectionAnchor = -1;
        } else if (selectionAnchor > len) {
            selectionAnchor = len;
        }
        if (selectionAnchor >= 0 && selectionAnchor == cursor) {
            selectionAnchor = -1;
        }
    }

    private int clamp(int pos) {
        if (pos < 0) {
            return 0;
        }
        if (pos > text.length()) {
            return text.length();
        }
        return pos;
    }

    private int nthWordFromPos(int pos, int direction) {
        pos = clamp(pos);
        boolean forward = direction >= 0;
        int steps = Math.abs(direction);

        for (int i = 0; i < steps; i++) {
            if (forward) {
                int len = text.length();
                int nextSpace = text.indexOf(' ', pos);
                if (nextSpace == -1) {
                    pos = len;
                } else {
                    pos = nextSpace;
                    while (pos < len && text.charAt(pos) == ' ') {
                        pos++;
                    }
                }
            } else {
                while (pos > 0 && text.charAt(pos - 1) == ' ') {
                    pos--;
                }
                while (pos > 0 && text.charAt(pos - 1) != ' ') {
                    pos--;
                }
            }
        }
        return pos;
    }

    private int getCursorAtXClamped(FontRenderer fr, int relativeX, String value) {
        if (value.isEmpty()) {
            return 0;
        }
        if (relativeX <= 0) {
            return 0;
        }
        int textWidth = fr.getStringWidth(value);
        if (relativeX >= textWidth) {
            return value.length();
        }
        return getCursorAtX(fr, relativeX, value);
    }

    private int getCursorAtX(FontRenderer fr, int relativeX, String value) {
        if (value.isEmpty()) {
            return 0;
        }

        int bestPos = 0;
        for (int i = 1; i <= value.length(); i++) {
            int width = fr.getStringWidth(value.substring(0, i));
            if (width > relativeX) {
                int prevWidth = fr.getStringWidth(value.substring(0, i - 1));
                return (relativeX - prevWidth) < (width - relativeX) ? i - 1 : i;
            }
            bestPos = i;
        }
        return bestPos;
    }
}

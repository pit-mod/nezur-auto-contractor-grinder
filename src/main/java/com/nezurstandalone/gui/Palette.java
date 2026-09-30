package com.nezurstandalone.gui;

import java.util.Collections;
import java.util.List;

/**
 * The options palette. Selecting a module row in a category panel retargets this panel to
 * that module's settings, keeping the category panels themselves free of nested drawers.
 */
public class Palette {

    private static final int BODY_PAD = 4;
    private static final int DESC_GAP = 3;

    public int x, y;
    public int width = GuiTheme.PALETTE_W;
    public boolean dragging;

    private ModuleButton target;
    /**
     * Set while the panel is animating shut. {@link #target} deliberately stays put for the
     * duration: the close used to null it immediately and the renderer then bailed out before
     * drawing anything, so the fade was computed every frame and never once shown.
     */
    private boolean closing;
    private int dragOffsetX, dragOffsetY;
    private float showAnim;
    private float contentAnim;
    private float headerHover;
    private float closeHover;
    /** Smoothed drag state, so the panel lifts and catches light the way a category panel does. */
    private float liftAnim;
    private float scroll, targetScroll;
    private boolean draggingScrollbar;

    private int bodyTop, bodyHeight, contentHeight, panelHeight;
    private List<String> descLines = Collections.emptyList();

    public Palette(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public ModuleButton getTarget() {
        // A panel on its way out is already gone as far as callers are concerned, so
        // right-clicking the same row again reopens it rather than closing it twice.
        return closing ? null : target;
    }

    public boolean isVisible() {
        return target != null || showAnim > 0.01f;
    }

    public void setTarget(ModuleButton button) {
        if (this.target == button && !closing) {
            return;
        }
        releaseInput();
        this.closing = false;
        this.target = button;
        this.scroll = 0f;
        this.targetScroll = 0f;
        this.contentAnim = 0f;
        rebuildDescription();
    }

    public void close() {
        releaseInput();
        if (target != null) {
            closing = true;
        }
    }

    private void releaseInput() {
        mouseReleased(-1,-1,0);
        InputComponent.clearFocus();
        KeybindComponent.clearBinding();
    }

    private void rebuildDescription() {
        if (target == null) {
            descLines = Collections.emptyList();
            return;
        }
        String desc = target.module.getDescription();
        if (desc == null || desc.trim().isEmpty()) {
            descLines = Collections.emptyList();
            return;
        }
        if (!com.nezurstandalone.module.impl.render.ClickGuiSettings.descriptionsOn()) {
            descLines = Collections.emptyList();
            return;
        }
        // Wrapped against the pre-scale width so 0.75x text fills the body evenly.
        int wrapWidth = (int) ((width - BODY_PAD * 2 - 4) / 0.75f);
        descLines = GuiDraw.font().listFormattedStringToWidth(desc, wrapWidth);
    }

    // ---------------------------------------------------------------- rendering

    public void render(int mouseX, int mouseY, float delta, int screenBottom) {
        render(mouseX, mouseY, delta, screenBottom, 1f);
    }

    /**
     * @param exitAmount 1 while the GUI is open; falls to 0 during a dismissal, sliding the
     *                   palette back out to the right the way it came in
     */
    public void render(int mouseX, int mouseY, float delta, int screenBottom, float exitAmount) {
        boolean wanted = target != null && !closing;
        // Closing needs to finish decisively. A slow tail made a deselected row seem frozen
        // after a right-click even though the palette had no live content left.
        showAnim = GuiDraw.approach(showAnim, wanted ? 1f : 0f, wanted ? 15f : 38f, delta);
        if (!wanted && showAnim < 0.025f) {
            showAnim = 0f;
            panelHeight = 0;
            target = null;
            closing = false;
            return;
        }
        if (target == null) {
            return;
        }
        target.refreshVisibility(); // live show/hide (e.g. perk boxes when Auto Perk toggles)

        List<Component> components = target.components;
        int descH = descLines.isEmpty() ? 0 : Math.round(descLines.size() * 8f * 0.75f + 2f) + DESC_GAP;
        int rowsH = 0;
        for (Component comp : components) {
            rowsH += comp.getPreferredHeight() + comp.getExtraHeight();
        }
        contentHeight = BODY_PAD + descH
                + (components.isEmpty() ? GuiTheme.ROW_H : rowsH)
                + BODY_PAD;

        float shown = GuiDraw.easeOutCubic(showAnim);

        int available = Math.max(0, screenBottom - (y + GuiTheme.HEADER_H));
        int fullBody = Math.min(contentHeight, available);
        // On the way out the body collapses into the header, and the existing body clip takes
        // the content with it. Fading alone put the whole dismissal at the mercy of every
        // child widget honouring its row alpha - a slider or a colour swatch that draws at its
        // own opacity stayed fully solid over a panel that had already gone, which is what
        // made the close look broken rather than animated. Clipping cannot be opted out of.
        bodyHeight = closing ? Math.round(fullBody * shown) : fullBody;
        bodyTop = y + GuiTheme.HEADER_H;
        panelHeight = GuiTheme.HEADER_H + bodyHeight;

        float maxScroll = Math.max(0f, contentHeight - fullBody);
        targetScroll = Math.max(0f, Math.min(maxScroll, targetScroll));
        scroll = GuiDraw.approach(scroll, targetScroll, 18f, delta);

        float exit = GuiAnim.clamp01(exitAmount);
        float appear = shown * exit;
        contentAnim = Math.min(1f, contentAnim + delta * 2.4f);
        headerHover = GuiDraw.approach(headerHover,
                !closing && (isHeaderHovered(mouseX, mouseY) || dragging) ? 1f : 0f, 16f, delta);
        liftAnim = GuiDraw.approach(liftAnim, dragging ? 1f : 0f, 12f, delta);

        // Keep rendered controls at their actual hit positions. The old translate and scale
        // made a just-opened slider look clickable somewhere other than where input landed.

        GuiChrome.card(x, y, width, panelHeight, appear, liftAnim);

        if (headerHover > 0.01f) {
            GuiDraw.roundedRect(x + 1, y + 1, x + width - 1, y + GuiTheme.HEADER_H,
                    GuiTheme.PANEL_RADIUS - 1, GuiDraw.withAlpha(GuiTheme.HEADER_BG, headerHover * appear));
        }

        // Header: dot in the owning category's hue, module name, close button
        int dotColor = GuiTheme.categoryColor(target.module.getCategory());
        float dotY = y + GuiTheme.HEADER_H / 2f;
        GuiDraw.circle(x + 9, dotY, 4.2f, GuiDraw.withAlpha(dotColor, 0.18f * appear));
        GuiDraw.circle(x + 9, dotY, 2.4f, GuiDraw.withAlpha(dotColor, appear));

        float closeCx = x + width - 9f;
        GuiDraw.textFitScaled(target.module.getName(), x + 16, y + (GuiTheme.HEADER_H - 8) / 2f + 0.5f,
                1f, closeCx - 8f - (x + 16), GuiDraw.withAlpha(GuiTheme.TEXT, appear), false);

        closeHover = GuiDraw.approach(closeHover, isCloseHovered(mouseX, mouseY) ? 1f : 0f, 16f, delta);
        int closeColor = GuiDraw.withAlpha(
                GuiDraw.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.DANGER, closeHover), appear);
        float r = 2.6f;
        GuiDraw.line(closeCx - r, dotY - r, closeCx + r, dotY + r, 1.3f, closeColor);
        GuiDraw.line(closeCx + r, dotY - r, closeCx - r, dotY + r, 1.3f, closeColor);

        if (bodyHeight <= 1) {
            return;
        }
        GuiDraw.rect(x + 1, bodyTop, x + width - 1, bodyTop + 1, GuiDraw.withAlpha(GuiTheme.SEPARATOR, appear));

        GuiDraw.beginClip(x + 1, bodyTop + 1, width - 2, bodyHeight - 1);
        float cursorY = bodyTop + BODY_PAD - scroll;

        for (String lineText : descLines) {
            GuiDraw.textScaled(lineText, x + BODY_PAD + 2, cursorY, 0.75f,
                    GuiDraw.withAlpha(GuiTheme.TEXT_MUTED, appear), false);
            cursorY += 8f * 0.75f + 1f;
        }
        if (!descLines.isEmpty()) {
            cursorY += DESC_GAP;
        }

        if (components.isEmpty()) {
            GuiDraw.textScaled("No options for this module.", x + BODY_PAD + 2, cursorY + 3f, 0.75f,
                    GuiDraw.withAlpha(GuiTheme.TEXT_FAINT, appear), false);
        } else {
            // Rows cascade in from the right as the palette settles.
            float reveal = GuiAnim.clamp01(contentAnim);
            for (int i = 0; i < components.size(); i++) {
                Component comp = components.get(i);
                int rowH = comp.getPreferredHeight() + comp.getExtraHeight();
                float t = GuiAnim.outCubic(GuiAnim.stagger(reveal, i, components.size(), 0.55f));
                comp.setRowAnim(appear * t, 0f);
                comp.updatePosition(x + 2, Math.round(cursorY), width - 4, rowH);
                comp.tickPress(delta);
                if (cursorY + rowH >= bodyTop && cursorY <= bodyTop + bodyHeight && t > 0.001f) {
                    float pl = comp.pressLevel();
                    if (pl > 0.01f) {
                        GuiDraw.roundedRect(x + 3, cursorY + 1, x + width - 3, cursorY + rowH - 1, 3f,
                                GuiDraw.withAlpha(GuiDraw.lerpColor(GuiTheme.ROW_BG,
                                        GuiTheme.ROW_BG_SELECTED, pl), appear * t));
                    }
                    comp.render(mouseX, mouseY);
                }
                cursorY += rowH;
            }
        }
        GuiDraw.endClip();

        drawScrollbar(appear);

    }

    private void drawScrollbar(float appear) {
        if (contentHeight <= bodyHeight || bodyHeight <= 4) {
            return;
        }
        float track = bodyHeight - 4;
        float thumbH = Math.max(12f, track * (bodyHeight / (float) contentHeight));
        float maxScroll = contentHeight - bodyHeight;
        float t = maxScroll <= 0f ? 0f : scroll / maxScroll;
        float thumbY = bodyTop + 2 + (track - thumbH) * t;
        GuiDraw.roundedRect(x + width - 4f, thumbY, x + width - 2f, thumbY + thumbH, 1f,
                GuiDraw.withAlpha(0xFFFFFFFF, 0.16f * appear));
    }

    private boolean hasScrollbar() {
        return contentHeight > bodyHeight && bodyHeight > 4;
    }

    private void scrollToMouse(int mouseY) {
        if (!hasScrollbar()) return;
        float track=bodyHeight-4f;
        float thumb=Math.max(12f,track*(bodyHeight/(float)contentHeight));
        float span=Math.max(1f,track-thumb);
        float normalized=GuiAnim.clamp01((mouseY-(bodyTop+2f)-thumb/2f)/span);
        targetScroll=normalized*(contentHeight-bodyHeight);
    }

    // ------------------------------------------------------------------- input

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // A closing panel is still on screen for its dismissal but is no longer a control:
        // leaving it clickable meant the fade swallowed clicks meant for the panel behind it.
        if (target == null || closing || !isPanelHovered(mouseX, mouseY)
                && !isDropdownOptionHovered(mouseX, mouseY)) {
            return false;
        }

        if (isCloseHovered(mouseX, mouseY) && mouseButton == 0) {
            close();
            return true;
        }

        if (isHeaderHovered(mouseX, mouseY)) {
            if (mouseButton == 0) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            }
            return true;
        }

        if (hasScrollbar() && !isDropdownOptionHovered(mouseX, mouseY)
                && mouseX >= x + width - 8 && mouseX <= x + width
                && mouseY >= bodyTop && mouseY < bodyTop + bodyHeight && mouseButton == 0) {
            draggingScrollbar=true;
            scrollToMouse(mouseY);
            return true;
        }

        if (isBodyHovered(mouseX, mouseY) || isDropdownOptionHovered(mouseX, mouseY)) {
            for (Component comp : target.components) {
                if (comp.isHovered(mouseX, mouseY) || comp instanceof PerkDropdownComponent
                        && ((PerkDropdownComponent) comp).isOptionHit(mouseX, mouseY)) {
                    comp.press();
                }
                comp.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }
        return true;
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            dragging = false;
            draggingScrollbar = false;
        }
        if (target != null) {
            for (Component comp : target.components) {
                comp.mouseReleased(mouseX, mouseY, state);
            }
        }
    }

    public void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (closing) {
            return;
        }
        if (target == null) {
            return;
        }
        if (draggingScrollbar && button == 0) {
            scrollToMouse(mouseY);
            return;
        }
        for (Component comp : target.components) {
            comp.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (target == null) {
            return;
        }
        for (Component comp : target.components) {
            comp.keyTyped(typedChar, keyCode);
        }
    }

    public void updateDrag(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!dragging) {
            return;
        }
        x = Math.max(2, Math.min(screenWidth - width - 2, mouseX - dragOffsetX));
        y = Math.max(2, Math.min(screenHeight - GuiTheme.HEADER_H - 2, mouseY - dragOffsetY));
    }

    public boolean scroll(int mouseX, int mouseY, float amount) {
        if (closing) {
            return false;
        }
        if (target == null || !isBodyHovered(mouseX, mouseY) || contentHeight <= bodyHeight) {
            return false;
        }
        targetScroll = Math.max(0f, Math.min(contentHeight - bodyHeight, targetScroll + amount));
        return true;
    }

    /** False while dismissing, so nothing outside treats a fading panel as live. */
    public boolean isClosing() {
        return closing;
    }

    public boolean isHeaderHovered(int mouseX, int mouseY) {
        return target != null && mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY < y + GuiTheme.HEADER_H;
    }

    public boolean isBodyHovered(int mouseX, int mouseY) {
        return target != null && mouseX >= x && mouseX <= x + width
                && mouseY >= bodyTop && mouseY < bodyTop + bodyHeight;
    }

    public boolean isPanelHovered(int mouseX, int mouseY) {
        return target != null && mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY < y + panelHeight;
    }

    private boolean isDropdownOptionHovered(int mouseX, int mouseY) {
        if (target == null || !isBodyHovered(mouseX, mouseY)
                || mouseX < x + 1 || mouseX >= x + width - 1
                || mouseY < bodyTop + 1 || mouseY >= bodyTop + bodyHeight) return false;
        for (Component comp : target.components) {
            if (comp instanceof PerkDropdownComponent
                    && ((PerkDropdownComponent) comp).isOptionHit(mouseX, mouseY)) return true;
        }
        return false;
    }

    private boolean isCloseHovered(int mouseX, int mouseY) {
        float cx = x + width - 9f;
        float cy = y + GuiTheme.HEADER_H / 2f;
        return Math.abs(mouseX + 0.5f - cx) <= 5f && Math.abs(mouseY + 0.5f - cy) <= 5f;
    }
}

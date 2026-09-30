package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * One category panel: a draggable header (dot, name, enabled counter) over a scrollable
 * list of module rows. The panel eases in as a unit and its rows cascade in behind it.
 */
public class Frame {

    /** Notified when a row is clicked so the owner can retarget the options palette. */
    public interface SelectionListener {
        void onModuleSelected(ModuleButton button);
    }

    private static final int ROW_PAD = 3;
    private static final int DOT_X = 9;

    public final Category category;
    public int x, y, width;
    public boolean open = true;
    public boolean dragging;

    public final List<ModuleButton> buttons = new ArrayList<>();

    private int dragOffsetX, dragOffsetY;

    private final PhysicsSpring openSpring = PhysicsSpring.iOSFluid(1f);
    private final PhysicsSpring headerHoverSpring = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring dotSpring = PhysicsSpring.iOSFluid(1f);
    private final PhysicsSpring scrollSpring = PhysicsSpring.iOSFluid(0f);

    /** 0..1 cascade of the row list; reset whenever the visible set changes. */
    private float rowCascade = 0f;
    private int lastVisibleCount = -1;
    private float scrollbarFade = 0f;
    private float targetScroll;
    private float liftAnim;

    // Geometry from the last render pass — input handling reuses it so hit tests can never
    // disagree with what is actually on screen.
    private int bodyTop, bodyHeight, contentHeight, panelHeight;

    public Frame(Category category, int x, int y, int width) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public List<ModuleButton> getVisibleButtons(String query) {
        List<ModuleButton> out = new ArrayList<>();
        for (ModuleButton b : buttons) {
            if (b.matchesSearch(query)) {
                out.add(b);
            }
        }
        return out;
    }

    public boolean hasVisibleButtons(String query) {
        for (ModuleButton b : buttons) {
            if (b.matchesSearch(query)) {
                return true;
            }
        }
        return false;
    }

    public int getPanelHeight() {
        return panelHeight;
    }

    /** Replays the row cascade — used when the GUI reopens. */
    public void resetIntro() {
        rowCascade = 0f;
        lastVisibleCount = -1;
    }

    // ---------------------------------------------------------------- rendering

    public void render(int mouseX, int mouseY, String query, float delta, int screenBottom,
                       Module selected, float reveal) {
        render(mouseX, mouseY, query, delta, screenBottom, selected, reveal, false);
    }

    /**
     * @param reveal  0..1 entrance progress, or — when {@code exiting} — 1..0 dismissal
     *                progress, which may briefly exceed 1 to give the collapse its
     *                anticipation pop
     * @param exiting true while the panel is being dismissed: it lifts toward its own header
     *                and shrinks, rather than replaying the entrance backwards
     */
    public void render(int mouseX, int mouseY, String query, float delta, int screenBottom,
                       Module selected, float reveal, boolean exiting) {
        List<ModuleButton> visible = getVisibleButtons(query);

        if (visible.size() != lastVisibleCount) {
            lastVisibleCount = visible.size();
            rowCascade = 0f;
        }
        rowCascade = Math.min(1f, rowCascade + delta * 2.2f);

        contentHeight = visible.size() * GuiTheme.ROW_H + ROW_PAD * 2;
        int available = Math.max(0, screenBottom - (y + GuiTheme.HEADER_H));
        // Measured from the content alone. This used to collapse to zero the instant the
        // panel was closed, which meant the open spring was multiplied by nothing on the very
        // first frame of the collapse: the body vanished in one frame and no amount of easing
        // downstream could have shown it, because there was never anything left to ease.
        int fullBody;
        if (contentHeight <= available) {
            fullBody = contentHeight;
        } else {
            // Clip on a row boundary so the list never ends on a half-drawn row.
            int rows = Math.max(1, (available - ROW_PAD * 2) / GuiTheme.ROW_H);
            fullBody = rows * GuiTheme.ROW_H + ROW_PAD * 2;
        }

        openSpring.setTarget(open ? 1f : 0f);
        openSpring.update(delta);
        float openT = GuiAnim.clamp01(openSpring.getCurrentValue());

        bodyHeight = Math.round(fullBody * openT);
        bodyTop = y + GuiTheme.HEADER_H;
        panelHeight = GuiTheme.HEADER_H + bodyHeight;

        float maxScroll = Math.max(0f, contentHeight - bodyHeight);
        targetScroll = Math.max(0f, Math.min(maxScroll, targetScroll));
        scrollSpring.setTarget(targetScroll);
        scrollSpring.update(delta);
        float scroll = scrollSpring.getCurrentValue();

        boolean headerHovered = isHeaderHovered(mouseX, mouseY);
        headerHoverSpring.setTarget(headerHovered || dragging ? 1f : 0f);
        headerHoverSpring.update(delta);
        float headerHover = GuiAnim.clamp01(headerHoverSpring.getCurrentValue());

        dotSpring.setTarget(headerHovered ? 1.12f : 1f);
        dotSpring.update(delta);

        // Dragging lifts the panel: bigger shadow, slight scale.
        liftAnim = GuiDraw.approach(liftAnim, dragging ? 1f : 0f, 12f, delta);

        boolean scrollable = contentHeight > bodyHeight;
        boolean scrollActive = scrollable && (Math.abs(scrollSpring.getCurrentValue() - targetScroll) > 0.2f
                || isBodyHovered(mouseX, mouseY));
        scrollbarFade = GuiDraw.approach(scrollbarFade, scrollActive ? 1f : 0.25f, 6f, delta);

        // --- Panel entrance / dismissal ---------------------------------------
        float appear;
        float driftY;
        float scale;
        if (exiting) {
            // Hold opacity a touch longer than the shrink so the panel reads as collapsing
            // into the top bar rather than as being switched off mid-flight.
            appear = (float) Math.pow(GuiAnim.clamp01(reveal), 0.75);
            driftY = -(1f - reveal) * 14f;
            scale = 0.86f + 0.16f * reveal;
        } else {
            appear = GuiAnim.outCubic(reveal);
            driftY = (1f - appear) * 18f;
            scale = (0.95f + 0.05f * appear) * (1f + 0.012f * liftAnim);
        }
        if (appear <= 0.001f) {
            return;
        }
        GuiDraw.pushTranslate(0f, driftY);
        GuiDraw.pushScale(x + width / 2f, y + 10f, scale, scale);

        // Panel surface. Routed through the shared card so a category panel is built from the
        // same parts as every other surface in the mod; drawing its own fill here is what kept
        // the panels flat while the top bar had already moved to glass.
        GuiChrome.card(x, y, width, panelHeight, appear, liftAnim);

        if (headerHover > 0.01f) {
            GuiDraw.roundedRect(x + 1, y + 1, x + width - 1, y + GuiTheme.HEADER_H,
                    GuiTheme.PANEL_RADIUS - 1,
                    GuiDraw.withAlpha(GuiTheme.HEADER_BG, headerHover * appear));
        }

        // Header: category dot + name + enabled counter
        int dotColor = GuiTheme.categoryColor(category);
        float dotY = y + GuiTheme.HEADER_H / 2f;
        float dotScale = dotSpring.getCurrentValue();
        GuiDraw.circle(x + DOT_X, dotY, 2.4f * dotScale, GuiDraw.withAlpha(dotColor, appear));

        String title = toTitleCase(category.name);
        GuiDraw.text(title, x + DOT_X + 7, y + (GuiTheme.HEADER_H - 8) / 2f,
                GuiDraw.withAlpha(GuiTheme.TEXT, appear));

        String counter = countEnabled() + "/" + buttons.size();
        float counterScale = 0.75f;
        float counterW = GuiDraw.textWidthScaled(counter, counterScale);
        GuiDraw.textScaled(counter, x + width - 8 - counterW,
                y + (GuiTheme.HEADER_H - 8f * counterScale) / 2f, counterScale,
                GuiDraw.withAlpha(open ? GuiTheme.TEXT_MUTED : dotColor, appear), false);

        if (bodyHeight <= 1) {
            GuiDraw.popMatrix();
            GuiDraw.popMatrix();
            return;
        }
        GuiDraw.rect(x + 1, bodyTop, x + width - 1, bodyTop + 1,
                GuiDraw.withAlpha(GuiTheme.SEPARATOR, appear));

        // --- Rows -------------------------------------------------------------
        GuiDraw.beginClip(x + 1, bodyTop + 1, width - 2, bodyHeight - 1);
        float rowY = bodyTop + ROW_PAD - scroll;
        for (int i = 0; i < visible.size(); i++) {
            ModuleButton button = visible.get(i);
            int ry = Math.round(rowY);
            button.updatePosition(x, ry, width, GuiTheme.ROW_H);
            rowY += GuiTheme.ROW_H;
            if (ry + GuiTheme.ROW_H < bodyTop || ry > bodyTop + bodyHeight) {
                continue; // off-screen inside the clip window
            }
            // Opening cascades the rows in from the top; collapsing plays the same stagger
            // backwards from the bottom, so the list peels away instead of simply being
            // clipped off as the body shrinks past it.
            int n = Math.max(1, visible.size());
            float rowT;
            if (open) {
                rowT = GuiAnim.outCubic(GuiAnim.stagger(rowCascade, i, n, 0.45f));
            } else {
                rowT = 1f - GuiAnim.inCubic(GuiAnim.stagger(1f - openT, n - 1 - i, n, 0.45f));
            }
            if (rowT <= 0.001f) {
                continue;
            }
            boolean hoverable = isBodyHovered(mouseX, mouseY);
            GuiDraw.pushTranslate(-(1f - rowT) * 12f, 0f);
            button.render(mouseX, mouseY, selected == button.module, hoverable, delta, appear * rowT);
            GuiDraw.popMatrix();
        }
        GuiDraw.endClip();

        drawScrollbar(scroll, appear);

        GuiDraw.popMatrix();
        GuiDraw.popMatrix();
    }

    private void drawScrollbar(float scroll, float appear) {
        if (contentHeight <= bodyHeight || bodyHeight <= 4) {
            return;
        }
        float track = bodyHeight - 4;
        float thumbH = Math.max(12f, track * (bodyHeight / (float) contentHeight));
        float maxScroll = contentHeight - bodyHeight;
        float t = maxScroll <= 0f ? 0f : scroll / maxScroll;
        float thumbY = bodyTop + 2 + (track - thumbH) * GuiAnim.clamp01(t);
        float w = 1.5f + 0.8f * scrollbarFade;
        GuiDraw.roundedRect(x + width - 3f - w, thumbY, x + width - 3f + w, thumbY + thumbH, w,
                GuiDraw.withAlpha(0xFFFFFFFF, (0.10f + 0.22f * scrollbarFade) * appear));
    }

    private int countEnabled() {
        int n = 0;
        for (ModuleButton b : buttons) {
            if (b.module.isToggled()) {
                n++;
            }
        }
        return n;
    }

    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    // ------------------------------------------------------------------- input

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton, String query, SelectionListener listener) {
        if (!isPanelHovered(mouseX, mouseY)) {
            return false;
        }

        if (isHeaderHovered(mouseX, mouseY)) {
            if (mouseButton == 0) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            } else if (mouseButton == 1) {
                open = !open;
                openSpring.setVelocity(open ? 4f : -4f);
                if (open) {
                    rowCascade = 0f;
                }
            }
            return true;
        }

        if (bodyHeight > 1 && isBodyHovered(mouseX, mouseY)) {
            for (ModuleButton button : getVisibleButtons(query)) {
                if (!button.isRowHovered(mouseX, mouseY)) {
                    continue;
                }
                button.press();
                if (button.module.isSettingsOnly()) {
                    // A settings-only row has nothing to toggle: either click opens its options.
                    if (listener != null) {
                        listener.onModuleSelected(button);
                    }
                } else if (mouseButton == 0) {
                    // Left-click anywhere on the row toggles the module, including the pill.
                    button.toggleModule();
                } else if (mouseButton == 1) {
                    // Right-click opens the module's options in the palette.
                    if (listener != null) {
                        listener.onModuleSelected(button);
                    }
                }
                return true;
            }
        }
        return true; // swallow clicks that land on the panel but miss a row
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            dragging = false;
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
        if (!isBodyHovered(mouseX, mouseY) || contentHeight <= bodyHeight) {
            return false;
        }
        targetScroll = Math.max(0f, Math.min(contentHeight - bodyHeight, targetScroll + amount));
        return true;
    }

    public boolean isHeaderHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + GuiTheme.HEADER_H;
    }

    public boolean isBodyHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= bodyTop && mouseY < bodyTop + bodyHeight;
    }

    public boolean isPanelHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + panelHeight;
    }

    public ModuleButton getHoveredModuleButton(int mouseX, int mouseY, String query) {
        if (bodyHeight <= 1 || !isBodyHovered(mouseX, mouseY)) {
            return null;
        }
        for (ModuleButton button : getVisibleButtons(query)) {
            if (button.isRowHovered(mouseX, mouseY)) {
                return button;
            }
        }
        return null;
    }
}

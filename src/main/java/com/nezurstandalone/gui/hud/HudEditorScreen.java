package com.nezurstandalone.gui.hud;

import com.nezurstandalone.Nezur;
import com.nezurstandalone.gui.ColorComponent;
import com.nezurstandalone.gui.ColorPickerPopup;
import com.nezurstandalone.gui.GuiAnim;
import com.nezurstandalone.gui.GuiChrome;
import com.nezurstandalone.gui.GuiDraw;
import com.nezurstandalone.gui.GuiExitAnimator;
import com.nezurstandalone.gui.GuiTheme;
import com.nezurstandalone.gui.ModuleButton;
import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.module.DraggableHud;
import com.nezurstandalone.module.Module;
import com.nezurstandalone.utils.HudPositionManager;
import com.nezurstandalone.utils.HudPositionSaveDebouncer;
import com.nezurstandalone.utils.HudStackManager;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The HUD editor. Every draggable HUD element is a card in the same idiom as a module row —
 * rounded surface, accent selection marker, spring-driven hover — laid out over a dimmed
 * world. Dragging snaps to screen edges, screen centre and the other elements, and the match
 * that caused a snap is drawn as an accent guide so the alignment is visible while it happens.
 */
public class HudEditorScreen extends GuiScreen implements GuiExitAnimator.ExitRenderer {

    private static final int NO_GUIDE = Integer.MIN_VALUE;
    private static final int SNAP_RANGE = 6;
    private static final int GRID_COLOR = 0x18FFFFFF;

    private static final String[] FOOTER_HINTS = {
            "drag an element to move it · it snaps to edges, centre and its neighbours",
            "arrow keys nudge · hold ctrl for ×10",
            "right-click an element for its options"
    };

    private final GuiChrome chrome = new GuiChrome(GuiChrome.Tab.HUD, "Search HUD elements");
    private final PhysicsSpring openSpring = PhysicsSpring.iOSFluid(0f);

    private final List<HudElementAdapter> elements = new ArrayList<>();
    private HudElementAdapter selectedElement = null;

    private boolean pressed;
    private boolean dragging;
    private boolean moved;
    private int dragOffsetX, dragOffsetY;
    private int snapGuideX = NO_GUIDE;
    private int snapGuideY = NO_GUIDE;

    private ModuleButton activeModuleButton = null;
    private float popupAnim = 0f;
    private float popupCloseHover = 0f;
    private int popupX, popupY, popupW, popupH;

    public HudEditorScreen() {
    }

    @Override
    public void initGui() {
        super.initGui();
        GuiExitAnimator.cancel();
        chrome.resetIntro();
        if (chrome.consumeCrossNav()) {
            openSpring.snapTo(1f);
            openSpring.setTarget(1f);
        } else {
            openSpring.snapTo(0f);
            openSpring.setTarget(1f);
            openSpring.setVelocity(6.5f);
        }
        chrome.resetClock();
        refreshElements();
    }

    private void refreshElements() {
        elements.clear();
        selectedElement = null;
        for (Module m : Nezur.moduleManager.getModules()) {
            if (m instanceof DraggableHud) {
                elements.add(new HudElementAdapter((DraggableHud) m, m));
            }
        }
    }

    // --------------------------------------------------------------- rendering

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float delta = chrome.delta();
        openSpring.update(delta);
        float appear = GuiAnim.clamp01(openSpring.getCurrentValue());

        GuiDraw.resetState();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GuiDraw.rect(0, 0, width, height, GuiDraw.alpha(0x0B0B10, (int) (185 * appear)));

        ColorPickerPopup picker = ColorComponent.getActivePicker();

        if (pressed && moved) {
            drawGrid(appear);
            drawSnapGuides(appear);
        }

        boolean interactive = activeModuleButton == null && picker == null;
        int enabled = 0;
        int shown = 0;
        com.nezurstandalone.gui.GuiIntro.Style intro = chrome.introStyle();
        float content = chrome.contentIntro();
        com.nezurstandalone.gui.GuiIntro.begin(intro, content, width / 2f, height / 2f);
        for (int i = 0; i < elements.size(); i++) {
            HudElementAdapter elem = elements.get(i);
            if (elem.module.isToggled()) {
                enabled++;
            }
            boolean matched = chrome.matches(elem.hud.getHudKey());
            if (matched) {
                shown++;
            }
            float reveal = appear * com.nezurstandalone.gui.GuiIntro.ease(intro,
                    GuiAnim.stagger(content, i, Math.max(1, elements.size()), 0.6f));
            elem.render(mouseX, mouseY, delta, interactive, matched, reveal);
        }
        com.nezurstandalone.gui.GuiIntro.end();

        chrome.drawTopBar(mouseX, mouseY, delta, appear);
        chrome.drawFooter(width, height, FOOTER_HINTS, null, footerStatus(enabled, shown), GuiTheme.ACCENT,
                mouseX, mouseY, delta, appear);

        drawSettingsPopup(mouseX, mouseY, delta, appear);

        if (picker != null) {
            picker.draw(mouseX, mouseY, fontRendererObj);
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private String footerStatus(int enabled, int matching) {
        return chrome.getQuery().isEmpty()
                ? enabled + " shown · " + elements.size() + " elements"
                : matching + " matching · " + elements.size() + " elements";
    }

    /** Rule-of-thirds grid, shown only while something is being dragged. */
    private void drawGrid(float appear) {
        int w3 = width / 3;
        int h3 = height / 3;
        int color = GuiDraw.withAlpha(GRID_COLOR, appear);
        GuiDraw.line(w3, 0, w3, height, 1f, color);
        GuiDraw.line(w3 * 2, 0, w3 * 2, height, 1f, color);
        GuiDraw.line(0, h3, width, h3, 1f, color);
        GuiDraw.line(0, h3 * 2, width, h3 * 2, 1f, color);
    }

    /** Accent lines marking the alignment the current drag snapped to. */
    private void drawSnapGuides(float appear) {
        int color = GuiDraw.withAlpha(GuiTheme.ACCENT, 0.75f * appear);
        if (snapGuideX != NO_GUIDE) {
            GuiDraw.line(snapGuideX, 0, snapGuideX, height, 1f, color);
        }
        if (snapGuideY != NO_GUIDE) {
            GuiDraw.line(0, snapGuideY, width, snapGuideY, 1f, color);
        }
    }

    private void drawSettingsPopup(int mouseX, int mouseY, float delta, float appear) {
        popupAnim = GuiDraw.approach(popupAnim, activeModuleButton != null ? 1f : 0f, 15f, delta);
        if (activeModuleButton == null || popupAnim < 0.01f) {
            return;
        }
        float t = GuiDraw.easeOutCubic(popupAnim) * appear;

        GuiDraw.rect(0, 0, width, height, GuiDraw.alpha(0x000000, (int) (120 * t)));
        layoutPopup();

        float scale = 0.92f + 0.08f * t;
        GuiDraw.pushScale(popupX + popupW / 2f, popupY + popupH / 2f, scale, scale);

        GuiChrome.card(popupX, popupY, popupW, popupH, t, 0.6f);
        int dotColor = GuiTheme.categoryColor(activeModuleButton.module.getCategory());
        GuiChrome.cardHeader(popupX, popupY, popupW, dotColor, activeModuleButton.module.getName(),
                null, t, 1f);

        float closeCx = popupX + popupW - 9f;
        float closeCy = popupY + GuiTheme.HEADER_H / 2f;
        popupCloseHover = GuiDraw.approach(popupCloseHover, isCloseHovered(mouseX, mouseY) ? 1f : 0f, 16f, delta);
        int closeColor = GuiDraw.withAlpha(
                GuiDraw.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.DANGER, popupCloseHover), t);
        float r = 2.6f;
        GuiDraw.line(closeCx - r, closeCy - r, closeCx + r, closeCy + r, 1.3f, closeColor);
        GuiDraw.line(closeCx + r, closeCy - r, closeCx - r, closeCy + r, 1.3f, closeColor);

        int bodyTop = popupY + GuiTheme.HEADER_H + 4;
        GuiDraw.beginClip(popupX + 1, bodyTop, popupW - 2, popupH - (bodyTop - popupY) - 4);
        activeModuleButton.layoutSettings(popupX + 3, bodyTop, popupW - 6);
        if (activeModuleButton.components.isEmpty()) {
            GuiChrome.emptyState(popupX, bodyTop + 6, popupW, "No options for this element", t);
        } else {
            activeModuleButton.renderSettings(mouseX, mouseY);
        }
        GuiDraw.endClip();

        GuiDraw.popMatrix();
    }

    private void layoutPopup() {
        popupW = 210;
        int content = activeModuleButton == null ? 0 : activeModuleButton.getSettingsHeight();
        popupH = Math.min(height - 60, Math.max(60, GuiTheme.HEADER_H + 8 + Math.max(content, GuiTheme.ROW_H)));
        popupX = (width - popupW) / 2;
        popupY = (height - popupH) / 2;
    }

    private boolean isCloseHovered(int mouseX, int mouseY) {
        float cx = popupX + popupW - 9f;
        float cy = popupY + GuiTheme.HEADER_H / 2f;
        return Math.abs(mouseX + 0.5f - cx) <= 5f && Math.abs(mouseY + 0.5f - cy) <= 5f;
    }

    // ------------------------------------------------------------------- input

    /**
     * Closes immediately so the player can move, and lets {@link GuiExitAnimator} finish the
     * dismissal over the live game.
     */
    private void requestClose() {
        activeModuleButton = null;
        GuiExitAnimator.play(this);
        mc.displayGuiScreen(null);
    }

    /** The dismissal: dim clears first, then the element cards drop away last-to-first. */
    @Override
    public void renderExit(float progress, float delta) {
        GuiDraw.resetState();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        float dim = 1f - GuiAnim.outCubic(GuiAnim.clamp01(progress / 0.45f));
        GuiDraw.rect(0, 0, width, height, GuiDraw.alpha(0x0B0B10, (int) (185 * dim)));

        for (int i = 0; i < elements.size(); i++) {
            int index = elements.size() - 1 - i;
            float staged = GuiAnim.stagger(progress, index, Math.max(1, elements.size()), 0.65f);
            elements.get(i).renderExit(1f - GuiAnim.inBack(staged), delta);
        }

        int enabled = 0;
        for (HudElementAdapter elem : elements) {
            if (elem.module.isToggled()) {
                enabled++;
            }
        }
        float chromeOut = 1f - GuiAnim.inCubic(GuiAnim.clamp01(progress / 0.75f));
        chrome.drawTopBar(-1, -1, delta, chromeOut);
        chrome.drawFooter(width, height, FOOTER_HINTS, null, footerStatus(enabled, enabled), GuiTheme.ACCENT,
                -1, -1, delta, chromeOut);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        ColorPickerPopup picker = ColorComponent.getActivePicker();
        if (picker != null) {
            if (!picker.mouseClicked(mouseX, mouseY, mouseButton)) {
                ColorComponent.closePicker();
            }
            return;
        }

        if (activeModuleButton != null) {
            layoutPopup();
            if (isCloseHovered(mouseX, mouseY)) {
                activeModuleButton = null;
                return;
            }
            if (GuiChrome.inside(mouseX, mouseY, popupX, popupY, popupW, popupH)) {
                activeModuleButton.settingsMouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
            activeModuleButton = null;
            return;
        }

        if (chrome.mouseClicked(mouseX, mouseY, mouseButton, mc, fontRendererObj)) {
            return;
        }

        if (mouseButton == 0) {
            pressed = true;
            moved = false;

            HudElementAdapter hovered = getHovered(mouseX, mouseY);
            dragging = hovered != null;
            if (dragging) {
                selectedElement = hovered;
                dragOffsetX = mouseX - hovered.getDrawX();
                dragOffsetY = mouseY - hovered.getDrawY();
                // Bring the grabbed element to the top of the draw and hit order.
                elements.remove(hovered);
                elements.add(hovered);
            } else {
                selectedElement = null;
            }
        } else if (mouseButton == 1) {
            HudElementAdapter hovered = getHovered(mouseX, mouseY);
            if (hovered != null) {
                selectedElement = hovered;
                activeModuleButton = new ModuleButton(hovered.module, "hud_editor", 0, 0, 180, GuiTheme.ROW_H);
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        ColorPickerPopup picker = ColorComponent.getActivePicker();
        if (picker != null) {
            picker.mouseClickMove(mouseX, mouseY, clickedMouseButton);
            return;
        }

        if (activeModuleButton != null) {
            activeModuleButton.settingsMouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            return;
        }
        chrome.mouseClickMove(mouseX, clickedMouseButton, fontRendererObj);

        if (clickedMouseButton == 0 && dragging && selectedElement != null) {
            moved = true;

            int targetDrawX = mouseX - dragOffsetX;
            int targetDrawY = mouseY - dragOffsetY;

            snapGuideX = NO_GUIDE;
            snapGuideY = NO_GUIDE;
            int snappedX = applySnappingX(selectedElement, targetDrawX);
            int snappedY = applySnappingY(selectedElement, targetDrawY);

            selectedElement.setPosFromDraw(snappingRange(targetDrawX, snappedX), snappingRange(targetDrawY, snappedY));

            HudPositionSaveDebouncer.markDirty();
            HudStackManager.markDirty();
        } else if (pressed) {
            moved = true;
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    /** Snaps {@code drawX} to the nearest alignment and records the guide line for it. */
    private int applySnappingX(HudElementAdapter elem, int drawX) {
        int elemW = elem.getWidth();
        int elemRight = drawX + elemW;
        int elemCenterX = drawX + elemW / 2;
        int screenCenterX = width / 2;

        if (Math.abs(drawX) <= SNAP_RANGE) return guideX(0, 0);
        if (Math.abs(width - elemRight) <= SNAP_RANGE) return guideX(width - elemW, width);
        if (Math.abs(screenCenterX - elemCenterX) <= SNAP_RANGE) return guideX(screenCenterX - elemW / 2, screenCenterX);

        for (HudElementAdapter other : elements) {
            if (other == elem) continue;
            int oX = other.getDrawX();
            int oW = other.getWidth();
            int oRight = oX + oW;
            int oCenterX = oX + oW / 2;

            if (Math.abs(oX - drawX) <= SNAP_RANGE) return guideX(oX, oX);
            if (Math.abs(oRight - elemRight) <= SNAP_RANGE) return guideX(oRight - elemW, oRight);
            if (Math.abs(oRight - drawX) <= SNAP_RANGE) return guideX(oRight, oRight);
            if (Math.abs(oX - elemRight) <= SNAP_RANGE) return guideX(oX - elemW, oX);
            if (Math.abs(oCenterX - elemCenterX) <= SNAP_RANGE) return guideX(oCenterX - elemW / 2, oCenterX);
        }
        return drawX;
    }

    private int applySnappingY(HudElementAdapter elem, int drawY) {
        int elemH = elem.getHeight();
        int elemBottom = drawY + elemH;
        int elemCenterY = drawY + elemH / 2;
        int screenCenterY = height / 2;

        if (Math.abs(drawY) <= SNAP_RANGE) return guideY(0, 0);
        if (Math.abs(height - elemBottom) <= SNAP_RANGE) return guideY(height - elemH, height);
        if (Math.abs(screenCenterY - elemCenterY) <= SNAP_RANGE) return guideY(screenCenterY - elemH / 2, screenCenterY);

        for (HudElementAdapter other : elements) {
            if (other == elem) continue;
            int oY = other.getDrawY();
            int oH = other.getHeight();
            int oBottom = oY + oH;
            int oCenterY = oY + oH / 2;

            if (Math.abs(oY - drawY) <= SNAP_RANGE) return guideY(oY, oY);
            if (Math.abs(oBottom - elemBottom) <= SNAP_RANGE) return guideY(oBottom - elemH, oBottom);
            if (Math.abs(oBottom - drawY) <= SNAP_RANGE) return guideY(oBottom, oBottom);
            if (Math.abs(oY - elemBottom) <= SNAP_RANGE) return guideY(oY - elemH, oY);
            if (Math.abs(oCenterY - elemCenterY) <= SNAP_RANGE) return guideY(oCenterY - elemH / 2, oCenterY);
        }
        return drawY;
    }

    private int guideX(int snapped, int guide) {
        snapGuideX = guide;
        return snapped;
    }

    private int guideY(int snapped, int guide) {
        snapGuideY = guide;
        return snapped;
    }

    private int snappingRange(int target, int snapped) {
        return Math.abs(target - snapped) <= SNAP_RANGE ? snapped : target;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        ColorPickerPopup picker = ColorComponent.getActivePicker();
        if (picker != null) {
            picker.mouseReleased(mouseX, mouseY, state);
            return;
        }
        chrome.mouseReleased();

        if (activeModuleButton != null) {
            activeModuleButton.settingsMouseReleased(mouseX, mouseY, state);
        }

        if (state == 0) {
            pressed = false;
            if (dragging) {
                HudPositionSaveDebouncer.markDirty();
                HudStackManager.markDirty();
            }
            dragging = false;
            moved = false;
            snapGuideX = NO_GUIDE;
            snapGuideY = NO_GUIDE;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        ColorPickerPopup picker = ColorComponent.getActivePicker();
        if (picker != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                ColorComponent.closePicker();
                return;
            }
            picker.keyTyped(typedChar, keyCode);
            return;
        }

        if (activeModuleButton != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                activeModuleButton = null;
                return;
            }
            activeModuleButton.settingsKeyTyped(typedChar, keyCode);
            return;
        }

        if (chrome.keyTyped(typedChar, keyCode)) {
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            requestClose();
            return;
        }

        if (selectedElement != null) {
            int step = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) ? 10 : 1;
            int dx = 0, dy = 0;
            if (keyCode == Keyboard.KEY_UP) dy = -step;
            if (keyCode == Keyboard.KEY_DOWN) dy = step;
            if (keyCode == Keyboard.KEY_LEFT) dx = -step;
            if (keyCode == Keyboard.KEY_RIGHT) dx = step;

            if (dx != 0 || dy != 0) {
                selectedElement.setPosFromDraw(selectedElement.getDrawX() + dx, selectedElement.getDrawY() + dy);
                HudPositionSaveDebouncer.markDirty();
                HudStackManager.markDirty();
                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        chrome.updateScreen();
    }

    @Override
    public void onGuiClosed() {
        ColorComponent.closePicker();
        mouseReleased(-1,-1,0);
        com.nezurstandalone.gui.InputComponent.clearFocus();
        com.nezurstandalone.gui.KeybindComponent.clearBinding();
        ColorComponent.closePicker();
        chrome.stopTyping();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Topmost element under the cursor that the search filter still shows. */
    private HudElementAdapter getHovered(int mouseX, int mouseY) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElementAdapter elem = elements.get(i);
            if (chrome.matches(elem.hud.getHudKey()) && elem.isMouseOver(mouseX, mouseY)) {
                return elem;
            }
        }
        return null;
    }

    /** One draggable HUD element, drawn as a card with its own hover and selection springs. */
    public class HudElementAdapter {
        public final DraggableHud hud;
        public final Module module;

        private final PhysicsSpring hoverSpring = PhysicsSpring.iOSFluid(0f);
        private final PhysicsSpring selectSpring = PhysicsSpring.iOSFluid(0f);

        public HudElementAdapter(DraggableHud hud, Module module) {
            this.hud = hud;
            this.module = module;
        }

        public int getWidth() {
            return 110;
        }

        public int getHeight() {
            return 16;
        }

        public int getDrawX() {
            // GuiScreen.width IS the scaled width (setWorldAndResolution is handed it, and
            // re-runs on resize), so there is no reason to build a ScaledResolution here.
            // This is called for every element every frame, and once per candidate inside
            // both snapping loops while dragging, so the allocation was the hottest garbage
            // source on this screen.
            if (hud.isHudCenterAnchored()) {
                return (width / 2) + hud.getRenderX();
            }
            return hud.getRenderX();
        }

        public int getDrawY() {
            return hud.getRenderY();
        }

        void render(int mouseX, int mouseY, float delta, boolean interactive, boolean matched, float reveal) {
            boolean selected = this == selectedElement;
            boolean hovered = interactive && matched && !pressed && isMouseOver(mouseX, mouseY);
            hoverSpring.setTarget(hovered ? 1f : 0f);
            selectSpring.setTarget(selected ? 1f : 0f);
            hoverSpring.update(delta);
            selectSpring.update(delta);

            float hover = GuiAnim.clamp01(hoverSpring.getCurrentValue());
            float select = GuiAnim.clamp01(selectSpring.getCurrentValue());
            // Filtered-out elements stay visible but recede, so the layout still reads whole.
            float alpha = reveal * (matched ? 1f : 0.28f);

            // Grabbed and hovered cards lift a little off the backdrop.
            float lift = Math.max(hover * 0.5f, select) + (dragging && selected ? 0.5f : 0f);
            draw(alpha, hover, select, lift, 1f + 0.03f * Math.min(1f, lift), 0f);
        }

        /**
         * One frame of the dismissal: the card shrinks and drops away, with its hover and
         * selection states released so nothing stays lit on the way out.
         *
         * @param a 1 at the start of the dismissal, 0 at the end
         */
        void renderExit(float a, float delta) {
            hoverSpring.setTarget(0f);
            selectSpring.setTarget(0f);
            hoverSpring.update(delta);
            selectSpring.update(delta);

            float alpha = (float) Math.pow(GuiAnim.clamp01(a), 0.75);
            draw(alpha, 0f, 0f, 0f, 0.85f + 0.15f * a, (1f - a) * 10f);
        }

        private void draw(float alpha, float hover, float select, float lift, float scale, float offsetY) {
            if (alpha <= 0.01f) {
                return;
            }
            int x = getDrawX();
            int y = getDrawY();
            int w = getWidth();
            int h = getHeight();
            boolean on = module.isToggled();

            int fill = on ? GuiTheme.ROW_BG_HOVER : GuiChrome.tinted(0x33141418, GuiTheme.DANGER, 0.45f);
            int border = on ? GuiTheme.PANEL_BORDER : GuiDraw.alpha(GuiTheme.DANGER, 0x77);
            int accent = select > 0.01f ? GuiTheme.ACCENT : GuiTheme.categoryColor(module.getCategory());
            fill = GuiChrome.tinted(fill, accent, Math.max(hover * 0.5f, select * 0.65f));
            border = GuiDraw.lerpColor(border, GuiDraw.alpha(accent, 0xCC), Math.max(hover, select));

            GuiDraw.pushTranslate(0f, offsetY);
            GuiDraw.pushScale(x + w / 2f, y + h / 2f, scale, scale);

            GuiDraw.shadow(x, y, x + w, y + h, 3f, (int) (2 + 3 * lift), (int) ((70 + 60 * lift) * alpha));
            GuiDraw.roundedRect(x, y, x + w, y + h, 3f,
                    GuiDraw.withAlpha(fill, alpha), GuiDraw.withAlpha(border, alpha));

            if (select > 0.01f) {
                float barH = (h - 6f) * select;
                GuiDraw.roundedRect(x + 3f, y + (h - barH) / 2f, x + 5f, y + (h + barH) / 2f, 1f,
                        GuiDraw.withAlpha(GuiTheme.ACCENT, alpha));
            }

            String label = hud.getHudKey();
            int textColor = on
                    ? GuiDraw.lerpColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, Math.max(hover, select))
                    : GuiTheme.TEXT_MUTED;
            float labelMax = w - (on ? 14f : 30f);
            float labelW = Math.min(GuiDraw.textWidth(label), labelMax);
            GuiDraw.textFitScaled(label, x + (w - labelW) / 2f - (on ? 0f : 6f), y + (h - 8) / 2f + 0.5f,
                    1f, labelMax, GuiDraw.withAlpha(textColor, alpha), false);

            if (!on) {
                // Disabled elements still show where they sit, flagged so it is obvious why
                // they never appear in game.
                String off = "off";
                float offW = GuiDraw.textWidthScaled(off, 0.7f);
                GuiDraw.roundedRect(x + w - offW - 9f, y + 3f, x + w - 4f, y + h - 3f, 2f,
                        GuiDraw.withAlpha(GuiDraw.alpha(GuiTheme.DANGER, 0x44), alpha));
                GuiDraw.textScaled(off, x + w - offW - 6.5f, y + (h - 8f * 0.7f) / 2f, 0.7f,
                        GuiDraw.withAlpha(GuiTheme.DANGER, alpha), false);
            }

            GuiDraw.popMatrix();
            GuiDraw.popMatrix();
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            int x = getDrawX();
            int y = getDrawY();
            return mouseX >= x && mouseX <= x + getWidth() && mouseY >= y && mouseY <= y + getHeight();
        }

        public void setPosFromDraw(int drawX, int drawY) {
            if (hud.isHudCenterAnchored()) {
                int centerRelX = drawX - (width / 2);
                HudPositionManager.setBounded(hud.getHudKey(), centerRelX, drawY, getWidth(), getHeight(), true);
            } else {
                HudPositionManager.setBounded(hud.getHudKey(), drawX, drawY, getWidth(), getHeight(), false);
            }
        }
    }
}

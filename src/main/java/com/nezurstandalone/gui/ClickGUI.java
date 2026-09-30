package com.nezurstandalone.gui;

import com.nezurstandalone.Nezur;
import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.module.Category;
import com.nezurstandalone.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The module browser. One draggable panel per category, an options palette on the right,
 * a search field in the top bar and a status footer. Bound to RSHIFT.
 */
public class ClickGUI extends GuiScreen implements Frame.SelectionListener, GuiExitAnimator.ExitRenderer {

    // Layout is static so panel positions and the current selection survive reopening the
    // screen — including when another GUI constructs a fresh ClickGUI to return to.
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static Palette palette;
    private static boolean initialized = false;
    /** Screen size the current layout was measured for; a change invalidates panel widths. */
    private static int builtForWidth = -1;
    private static int builtForHeight = -1;

    private static final long REOPEN_GRACE_MS = 250L;

    private static final String[] FOOTER_HINTS = {
            "drag panel & palette headers to arrange",
            "left-click a module to toggle · right-click for options",
            "right-click a header to collapse"
    };

    /** Top bar, search field and footer are shared with the HUD/config screens. */
    private final GuiChrome chrome = new GuiChrome(GuiChrome.Tab.MODULES, "Type to search modules");

    private final PhysicsSpring openSpring = PhysicsSpring.iOSFluid(0f);
    private long openedAtMs = 0L;

    private Module hoveredModule = null;
    private long hoveredSinceMs = 0L;

    public ClickGUI() {
    }

    // ------------------------------------------------------------------ layout

    private void buildLayout() {
        // Every button below is a fresh object, so a palette still pointing at one from the
        // previous layout would be editing a control that is no longer on screen.
        if (palette != null) {
            palette.close();
        }
        FRAMES.clear();
        builtForWidth = width;
        builtForHeight = height;

        List<Category> populated = new ArrayList<>();
        for (Category category : Category.values()) {
            if (!Nezur.moduleManager.getModulesByCategory(category).isEmpty()) {
                populated.add(category);
            }
        }
        if (populated.isEmpty()) {
            initialized = true;
            return;
        }

        // Panels share the row evenly, shrinking below their preferred width on narrow
        // screens so every category stays reachable without horizontal scrolling.
        int usable = width - GuiTheme.PANEL_LEFT * 2 - (populated.size() - 1) * GuiTheme.PANEL_GAP;
        int panelW = Math.max(84, Math.min(GuiTheme.PANEL_W, usable / populated.size()));

        int px = GuiTheme.PANEL_LEFT;
        for (Category category : populated) {
            Frame frame = new Frame(category, px, GuiTheme.PANEL_TOP, panelW);
            for (Module m : Nezur.moduleManager.getModulesByCategory(category)) {
                frame.buttons.add(new ModuleButton(m, category.name, px, 0, panelW, GuiTheme.ROW_H));
            }
            FRAMES.add(frame);
            px += panelW + GuiTheme.PANEL_GAP;
        }

        int paletteW = Math.min(GuiTheme.PALETTE_W, Math.max(80, width - 8));
        int paletteX = px + paletteW > width - 4 ? width - paletteW - 4 : px;
        if (palette == null) {
            palette = new Palette(paletteX, GuiTheme.PANEL_TOP);
        } else {
            palette.x = paletteX;
            palette.y = GuiTheme.PANEL_TOP;
        }
        palette.width = paletteW;

        clampLayout();
        initialized = true;
    }

    /** Keeps every panel reachable after a resolution or GUI-scale change. */
    private void clampLayout() {
        for (Frame frame : FRAMES) {
            frame.x = Math.max(2, Math.min(width - frame.width - 2, frame.x));
            frame.y = Math.max(2, Math.min(height - GuiTheme.HEADER_H - 2, frame.y));
        }
        if (palette != null) {
            palette.x = Math.max(2, Math.min(width - palette.width - 2, palette.x));
            palette.y = Math.max(2, Math.min(height - GuiTheme.HEADER_H - 2, palette.y));
        }
    }

    private void resetLayout() {
        chrome.setQuery("");
        chrome.stopTyping();
        if (palette != null) {
            palette.close();
        }
        buildLayout();
        for (Frame frame : FRAMES) {
            frame.open = true;
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        GuiExitAnimator.cancel();
        this.chrome.resetIntro();
        if (this.chrome.consumeCrossNav()) {
            this.openSpring.snapTo(1f);
            this.openSpring.setTarget(1f);
        } else {
            this.openSpring.snapTo(0f);
            this.openSpring.setTarget(1f);
            this.openSpring.setVelocity(6.5f);
        }
        this.chrome.resetClock();
        this.openedAtMs = GuiAnim.millis();
        for (Frame frame : FRAMES) {
            frame.resetIntro();
        }

        // Panel widths are shared out across the screen at build time, so a resolution or
        // GUI-scale change while the menu was closed leaves them measured for the old size.
        // clampLayout() only moves panels back into view; it cannot resize them.
        if (!initialized || FRAMES.isEmpty() || builtForWidth != width || builtForHeight != height) {
            buildLayout();
        } else {
            clampLayout();
        }
    }

    private int panelBottomLimit() {
        return height - GuiTheme.FOOTER_H - 4;
    }

    // --------------------------------------------------------------- rendering

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float delta = chrome.delta();
        String searchQuery = chrome.getQuery();

        com.nezurstandalone.module.impl.render.ClickGuiSettings.tickTheme(); // rainbow accent, if enabled
        // Animation toggle and speed come from the ClickGUI module: off snaps straight to fully
        // open, otherwise the spring is stepped faster or slower than real time.
        if (!com.nezurstandalone.module.impl.render.ClickGuiSettings.animationsOn()) {
            openSpring.snapTo(1f);
        } else {
            openSpring.update(delta * com.nezurstandalone.module.impl.render.ClickGuiSettings.animationSpeed());
        }
        float appear = GuiAnim.clamp01(openSpring.getCurrentValue());
        float content = chrome.contentIntro();

        GuiDraw.resetState();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        GuiDraw.rect(0, 0, width, height, GuiDraw.alpha(0x000000,
                (int) (com.nezurstandalone.module.impl.render.ClickGuiSettings.backgroundAlpha() * appear)));

        ColorPickerPopup picker = ColorComponent.getActivePicker();

        for (Frame frame : FRAMES) {
            frame.updateDrag(mouseX, mouseY, width, height);
        }
        if (palette != null) {
            palette.updateDrag(mouseX, mouseY, width, height);
        }

        Module selected = palette != null && palette.getTarget() != null ? palette.getTarget().module : null;
        int shown = 0;
        for (Frame frame : FRAMES) {
            if (frame.hasVisibleButtons(searchQuery)) {
                shown++;
            }
        }
        int slot = 0;
        for (Frame frame : FRAMES) {
            if (!frame.hasVisibleButtons(searchQuery)) {
                continue;
            }
            // Panels cascade in left to right, on the content clock so a tab switch still
            // plays the cascade over an already-solid backdrop.
            float reveal = appear * GuiAnim.stagger(content, slot++, Math.max(1, shown), 0.6f);
            frame.render(mouseX, mouseY, searchQuery, delta, panelBottomLimit(), selected, reveal);
        }
        if (palette != null) {
            palette.render(mouseX, mouseY, delta, panelBottomLimit());
        }

        chrome.drawTopBar(mouseX, mouseY, delta, appear);
        drawFooter(mouseX, mouseY, delta, appear);

        updateHoveredModule(mouseX, mouseY);
        drawModuleTooltip(mouseX, mouseY, appear);

        if (picker != null) {
            picker.draw(mouseX, mouseY, fontRendererObj);
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /** Hints and the module counter, plus the inline reset-layout button. */
    private void drawFooter(int mouseX, int mouseY, float delta, float appear) {
        int total = 0;
        int enabled = 0;
        for (Frame frame : FRAMES) {
            total += frame.buttons.size();
            for (ModuleButton b : frame.buttons) {
                if (b.module.isToggled()) {
                    enabled++;
                }
            }
        }
        chrome.drawFooter(width, height, FOOTER_HINTS, "reset layout",
                enabled + " enabled · " + total + " modules", GuiTheme.ACCENT,
                mouseX, mouseY, delta, appear);
    }



    private void updateHoveredModule(int mouseX, int mouseY) {
        Module current = null;
        for (int i = FRAMES.size() - 1; i >= 0; i--) {
            ModuleButton hovered = FRAMES.get(i).getHoveredModuleButton(mouseX, mouseY, chrome.getQuery());
            if (hovered != null) {
                current = hovered.module;
                break;
            }
        }
        if (current != hoveredModule) {
            hoveredModule = current;
            hoveredSinceMs = GuiAnim.millis();
        }
    }

    private void drawModuleTooltip(int mouseX, int mouseY, float appear) {
        if (hoveredModule == null || GuiAnim.millis() - hoveredSinceMs < 700L) {
            return;
        }
        String desc = hoveredModule.getDescription();
        if (desc == null || desc.trim().isEmpty()) {
            return;
        }

        float scale = 0.75f;
        List<String> lines = fontRendererObj.listFormattedStringToWidth(desc, 200);
        float textW = 0f;
        for (String line : lines) {
            textW = Math.max(textW, GuiDraw.textWidthScaled(line, scale));
        }
        float lineH = 8f * scale + 1.5f;
        float w = textW + 12f;
        float h = lines.size() * lineH + 8f;

        float x = mouseX + 11;
        float y = mouseY + 12;
        if (x + w > width - 4) x = width - w - 4;
        if (y + h > height - 4) y = mouseY - h - 6;
        if (x < 4) x = 4;
        if (y < 4) y = 4;

        GuiDraw.shadow(x, y, x + w, y + h, 3f, 4, (int) (110 * appear));
        GuiDraw.glass(x, y, x + w, y + h, 3f, 0xF0141419, GuiTheme.GLASS_BORDER, 0.7f, appear);
        GuiDraw.roundedRect(x + 1, y + 4, x + 2.5f, y + h - 4, 0.75f,
                GuiDraw.withAlpha(GuiTheme.categoryColor(hoveredModule.getCategory()), appear));

        float ty = y + 4f;
        for (String line : lines) {
            GuiDraw.textScaled(line, x + 7f, ty, scale, GuiDraw.withAlpha(GuiTheme.TEXT_DIM, appear), false);
            ty += lineH;
        }
    }

    // ------------------------------------------------------------------- input

    /**
     * Dismisses the screen at once — control returns to the player on this very frame — and
     * hands the closing animation to {@link GuiExitAnimator}, which finishes it as an overlay
     * over the live game.
     */
    @Override
    public void onGuiClosed() {
        chrome.mouseReleased();
        chrome.stopTyping();
        for (Frame frame : FRAMES) frame.mouseReleased(-1,-1,0);
        if (palette != null) palette.mouseReleased(-1,-1,0);
        InputComponent.clearFocus();
        KeybindComponent.clearBinding();
        ColorComponent.closePicker();
        super.onGuiClosed();
    }

    private void requestClose() {
        GuiExitAnimator.play(this);
        mc.displayGuiScreen(null);
    }

    /**
     * The dismissal: the dim clears first so the world is legible immediately, the panels
     * collapse up into the top bar one after another from the right, and the palette slides
     * back out the way it arrived.
     */
    @Override
    public void renderExit(float progress, float delta) {
        GuiDraw.resetState();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        float dim = 1f - GuiAnim.outCubic(GuiAnim.clamp01(progress / 0.45f));
        GuiDraw.rect(0, 0, width, height, GuiDraw.alpha(0x000000,
                (int) (com.nezurstandalone.module.impl.render.ClickGuiSettings.backgroundAlpha() * dim)));

        String searchQuery = chrome.getQuery();
        Module selected = palette != null && palette.getTarget() != null ? palette.getTarget().module : null;

        List<Frame> leaving = new ArrayList<>();
        for (Frame frame : FRAMES) {
            if (frame.hasVisibleButtons(searchQuery)) {
                leaving.add(frame);
            }
        }
        for (Frame frame : leaving) {
            // Reversed against the entrance, and ordered by where the panel actually sits
            // rather than by draw order: the rightmost panel leaves first.
            int rank = 0;
            for (Frame other : leaving) {
                if (other.x > frame.x) {
                    rank++;
                }
            }
            float staged = GuiAnim.stagger(progress, rank, Math.max(1, leaving.size()), 0.6f);
            frame.render(-1, -1, searchQuery, delta, panelBottomLimit(), selected,
                    1f - GuiAnim.inBack(staged), true);
        }
        if (palette != null) {
            palette.render(-1, -1, delta, panelBottomLimit(), 1f - GuiAnim.inCubic(progress));
        }

        float chromeOut = 1f - GuiAnim.inCubic(GuiAnim.clamp01(progress / 0.75f));
        chrome.drawTopBar(-1, -1, delta, chromeOut);
        drawFooter(-1, -1, delta, chromeOut);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    @Override
    public void onModuleSelected(ModuleButton button) {
        if (palette == null) {
            return;
        }
        if (palette.getTarget() == button) {
            palette.close();
        } else {
            palette.setTarget(button);
        }
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

        if (chrome.mouseClicked(mouseX, mouseY, mouseButton, mc, fontRendererObj)) {
            return;
        }
        if (mouseButton == 0 && chrome.isFooterActionHovered(mouseX, mouseY)) {
            resetLayout();
            return;
        }

        chrome.stopTyping();
        String searchQuery = chrome.getQuery();

        if (palette != null && palette.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }

        for (int i = FRAMES.size() - 1; i >= 0; i--) {
            Frame frame = FRAMES.get(i);
            if (!frame.hasVisibleButtons(searchQuery)) {
                continue;
            }
            if (frame.mouseClicked(mouseX, mouseY, mouseButton, searchQuery, this)) {
                // Bring the interacted panel to the front of the draw/hit order.
                FRAMES.remove(i);
                FRAMES.add(frame);
                return;
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
        chrome.mouseClickMove(mouseX, clickedMouseButton, fontRendererObj);
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (palette != null) {
            palette.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        ColorPickerPopup picker = ColorComponent.getActivePicker();
        if (picker != null) {
            picker.mouseReleased(mouseX, mouseY, state);
            return;
        }
        chrome.mouseReleased();
        super.mouseReleased(mouseX, mouseY, state);
        for (Frame frame : FRAMES) {
            frame.mouseReleased(mouseX, mouseY, state);
        }
        if (palette != null) {
            palette.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        ColorPickerPopup picker = ColorComponent.getActivePicker();
        if (picker != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                ColorComponent.closePicker();
            } else {
                picker.keyTyped(typedChar, keyCode);
            }
            return;
        }

        if (chrome.keyTyped(typedChar, keyCode)) {
            return;
        }

        // A setting is capturing input (rebinding a key, typing in a text field).
        if (KeybindComponent.currentlyBinding != null || InputComponent.isTyping()) {
            if (palette != null) {
                palette.keyTyped(typedChar, keyCode);
            }
            return;
        }

        boolean closeKey = keyCode == Keyboard.KEY_ESCAPE
                || (keyCode == Keyboard.KEY_RSHIFT && GuiAnim.millis() - openedAtMs > REOPEN_GRACE_MS);
        if (closeKey) {
            requestClose();
            return;
        }

        if (palette != null) {
            palette.keyTyped(typedChar, keyCode);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (ColorComponent.getActivePicker() != null) return;
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        float amount = wheel > 0 ? -GuiTheme.ROW_H * 2f : GuiTheme.ROW_H * 2f;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        if (palette != null && palette.scroll(mouseX, mouseY, amount)) {
            return;
        }
        for (int i = FRAMES.size() - 1; i >= 0; i--) {
            if (FRAMES.get(i).scroll(mouseX, mouseY, amount)) {
                return;
            }
        }
    }

    @Override
    public void updateScreen() {
        chrome.updateScreen();
        InputComponent.tickRepeatKeys();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

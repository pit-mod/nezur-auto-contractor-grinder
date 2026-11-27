package com.nezurstandalone.gui;

import com.nezurstandalone.gui.hud.HudEditorScreen;
import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.module.impl.render.ClickGuiSettings;
import com.nezurstandalone.utils.ScreenScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

/**
 * The chrome every nezur screen shares: the top bar (wordmark, version, search field and
 * tab chips) and the status footer, plus the card and list primitives the secondary screens
 * build their content out of.
 *
 * <p>Each screen owns one instance, tells it which tab is active, and delegates its top-bar
 * drawing, hit testing and search input to it. Because the geometry is computed in one
 * place, the bar sits identically on every screen — switching tabs moves the accent, not
 * the layout.
 */
public final class GuiChrome {

    /** One screen each; the chip row is drawn in declaration order. */
    public enum Tab {
        MODULES("Modules"),
        HUD("HUD Editor"),
        CONFIGS("Configs");

        public final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    public static final int TOPBAR_Y = 8;
    public static final int TOPBAR_H = 34;
    private static final int TOPBAR_W = 228;
    private static final int SEARCH_H = 18;
    private static final int SIDE_SEARCH_MIN_WIDTH = 460;
    public static final int SEARCH_W = 150;

    private static final Tab[] TABS = Tab.values();

    private final Tab active;
    private final String placeholder;

    private final float[] chipHover = new float[TABS.length];
    private final int[] chipX = new int[TABS.length];
    private final int[] chipW = new int[TABS.length];

    /**
     * How long a clicked chip is held before its screen actually opens.
     *
     * <p>Navigation used to be instantaneous, which meant the press was never seen: the chip
     * was replaced by a whole new screen on the same frame it was clicked. Holding it briefly
     * costs nothing perceptible and turns a hard cut into a transition the eye can follow.
     */
    private static final long NAV_DELAY_MS = 85L;

    /** Chip awaiting navigation, or null when nothing is in flight. */
    private Tab pendingTab;
    private long pendingSinceMs;

    /**
     * Where the underline was when the previous screen handed over, so the incoming screen can
     * finish the same travel instead of having the accent appear already parked. Static
     * because each screen is a separate instance - this is the only thing that crosses.
     */
    private static Tab navFrom;
    private final PhysicsSpring lens;
    private final PhysicsSpring press = new PhysicsSpring(0, 500, 28);

    private final TextInputHelper searchEditor = new TextInputHelper();
    private String query = "";
    /** {@link #query} folded once, so per-row filtering does not rebuild it every frame. */
    private String queryLower = "";
    private boolean typing;
    private float searchHover;
    private float searchFocus;
    private int searchFieldX, searchFieldY, searchWidth;

    private float actionHover;
    private int actionX, actionY, actionW, actionH;
    private boolean actionShown;

    private long lastFrameMs = GuiAnim.millis();

    /**
     * 0..1 progress of the per-tab content entrance, always replayed on open. Kept separate
     * from the screen's own {@code appear} so the backdrop can already be solid on a tab switch
     * (no flash) while the panels still animate in.
     */
    private float contentIntro;
    private static final float INTRO_SPEED = 3.2f;

    /**
     * Set the instant one nezur screen navigates to another, consumed by the incoming screen.
     * A cross-navigation must not fade its dim up from nothing - the screen it replaced was
     * already fully drawn, so a frame of transparent dim shows the live world straight through,
     * which is the flash. Static because it has to survive the screen swap.
     */
    private static boolean crossNav;

    public GuiChrome(Tab active, String placeholder) {
        this.active = active;
        this.placeholder = placeholder;
        // Claimed once and cleared, so only the screen the handover was meant for plays the
        // arrival. Left set, a menu opened fresh hours later would slide its accent in from
        // whatever tab happened to be visited last.
        Tab arriveFrom = navFrom;
        navFrom = null;
        lens = new PhysicsSpring(arriveFrom == null ? active.ordinal() : arriveFrom.ordinal(), 650, 32);
    }

    // -------------------------------------------------------------------- state

    /** Seconds since the previous frame, clamped so an alt-tab cannot fling animations. */
    public float delta() {
        long now = GuiAnim.millis();
        float delta = Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;
        if (com.nezurstandalone.module.impl.render.ClickGuiSettings.animationsOn()) {
            contentIntro = Math.min(1f, contentIntro + delta * INTRO_SPEED
                    * com.nezurstandalone.module.impl.render.ClickGuiSettings.animationSpeed());
        } else contentIntro = 1f;
        return delta;
    }

    /** Per-tab content entrance progress, 0..1. Drive panels and cards from this. */
    public float contentIntro() {
        return contentIntro;
    }

    /** Replays the content entrance. Call from {@code initGui}. */
    public void resetIntro() {
        contentIntro = 0f;
    }

    /**
     * True once if this screen was reached by clicking a tab rather than opened fresh. The
     * incoming screen uses it to skip its backdrop fade-in and avoid the switch flash.
     */
    public boolean consumeCrossNav() {
        boolean was = crossNav;
        crossNav = false;
        return was;
    }

    /** Call from {@code initGui} so the first frame after opening has no backlog. */
    public void resetClock() {
        lastFrameMs = GuiAnim.millis();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String value) {
        applyQuery(value);
        this.searchEditor.setText(this.query);
    }

    private void applyQuery(String value) {
        String next = value == null ? "" : value;
        if (next.equals(query)) {
            return;
        }
        query = next;
        queryLower = next.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isTyping() {
        return typing;
    }

    public void stopTyping() {
        typing = false;
    }

    /**
     * True when {@code text} survives the active search filter. Called once per row per
     * frame, so the empty-query case does no work at all.
     *
     * <p>Case folding is pinned to {@link Locale#ROOT}: under a Turkish locale the default
     * would map {@code I} to a dotless {@code i} and quietly break every search containing
     * that letter.
     */
    public boolean matches(String text) {
        if (queryLower.isEmpty()) {
            return true;
        }
        return text != null && text.toLowerCase(Locale.ROOT).contains(queryLower);
    }

    // ---------------------------------------------------------------- rendering

    /** The entrance this screen plays; also drives the bar's own arrival direction. */
    public GuiIntro.Style introStyle() {
        return GuiIntro.forTab(active);
    }

    public static int contentTop(int screenWidth) {
        int navigationBottom = TOPBAR_Y + TOPBAR_H;
        if (screenWidth < SIDE_SEARCH_MIN_WIDTH) navigationBottom += 6 + SEARCH_H;
        return Math.max(GuiTheme.PANEL_TOP, navigationBottom + 8);
    }

    public void drawTopBar(int mouseX, int mouseY, float delta, float appear) {
        int width = ScreenScale.get().getScaledWidth();
        int navWidth = Math.max(TABS.length, Math.min(TOPBAR_W, width - 16));
        int navX = (width - navWidth) / 2;
        float navY = TOPBAR_Y;
        float segment = navWidth / (float) TABS.length;

        boolean sideSearch = width >= SIDE_SEARCH_MIN_WIDTH;
        if (sideSearch) {
            searchFieldX = navX + navWidth + 8;
            searchWidth = Math.min(SEARCH_W, width - searchFieldX - 8);
            searchFieldY = TOPBAR_Y + (TOPBAR_H - SEARCH_H) / 2;
        } else {
            searchWidth = Math.min(SEARCH_W, Math.max(1, width - 16));
            searchFieldX = (width - searchWidth) / 2;
            searchFieldY = TOPBAR_Y + TOPBAR_H + 6;
        }

        Tab selected = pendingTab == null ? active : pendingTab;
        lens.setTarget(selected.ordinal());
        press.setTarget(pendingTab == null ? 0 : 1);
        if (ClickGuiSettings.animationsOn()) {
            float dt = delta * ClickGuiSettings.animationSpeed();
            lens.update(dt);
            press.update(dt);
        } else {
            lens.snapTo(selected.ordinal());
            press.snapTo(0);
        }

        float captureX = navX - segment;
        float captureY = Math.max(0, navY - TOPBAR_H);
        float captureWidth = navWidth + segment * 2;
        NavigationLens.capture(captureX, captureY, captureWidth, TOPBAR_H * 3);
        GuiDraw.shadow(navX, navY, navX + navWidth, navY + TOPBAR_H, TOPBAR_H / 2f, 5, (int) (55 * appear));
        if (!NavigationLens.drawBackdrop(navX, navY, navWidth, TOPBAR_H, appear, GuiTheme.GLASS_BODY)) {
            GuiDraw.glass(navX, navY, navX + navWidth, navY + TOPBAR_H, TOPBAR_H / 2f,
                    GuiTheme.GLASS_BODY, GuiTheme.GLASS_BORDER, .7f, appear);
        }

        float held = GuiAnim.clamp01(press.getCurrentValue());
        float lensWidth = Math.max(1, segment - 5 + held * 5);
        float lensHeight = TOPBAR_H - 4 + held * 4;
        float center = navX + (lens.getCurrentValue() + .5f) * segment;
        float lensX = Math.max(navX - 3, Math.min(navX + navWidth + 3 - lensWidth, center - lensWidth / 2));
        float lensY = navY + (TOPBAR_H - lensHeight) / 2;

        for (int i = 0; i < TABS.length; i++) {
            chipX[i] = Math.round(navX + i * segment);
            chipW[i] = Math.round(navX + (i + 1) * segment) - chipX[i];
            boolean hovered = inside(mouseX, mouseY, chipX[i], TOPBAR_Y, chipW[i], TOPBAR_H);
            chipHover[i] = GuiDraw.approach(chipHover[i], hovered ? 1f : 0f, 16f, delta);
        }

        drawNavLabels(navX, navY, segment, selected, appear);
        NavigationLens.capture(captureX, captureY, captureWidth, TOPBAR_H * 3);
        GuiDraw.shadow(lensX, lensY, lensX + lensWidth, lensY + lensHeight, lensHeight / 2, 3,
                (int) ((20 + held * 30) * appear));
        if (!NavigationLens.draw(lensX, lensY, lensWidth, lensHeight, 1.015f + held * .045f, held, appear)) {
            GuiDraw.roundedRect(lensX, lensY, lensX + lensWidth, lensY + lensHeight, lensHeight / 2,
                    GuiDraw.withAlpha(GuiTheme.ROW_BG_SELECTED, appear));
            drawNavLabels(navX, navY, segment, selected, appear);
        }

        if (sideSearch || searchFieldX > 58) {
            GuiDraw.textScaled("nezur", 10, searchFieldY + 4, 1.15f, GuiDraw.withAlpha(GuiTheme.TEXT, appear), false);
        }
        drawSearchField(searchFieldX, mouseX, mouseY, delta, appear);
    }

    private void drawNavLabels(float x, float y, float segment, Tab selected, float appear) {
        for (int i = 0; i < TABS.length; i++) {
            float center = x + (i + .5f) * segment;
            int color = TABS[i] == selected ? GuiTheme.ACCENT
                    : GuiDraw.lerpColor(GuiTheme.TEXT_DIM, GuiTheme.TEXT, chipHover[i]);
            int textColor = GuiDraw.withAlpha(color, appear);
            NavigationIcons.draw(i, center, y + 11, 13, textColor);
            String label = TABS[i].label;
            float scale = Math.min(.85f, (segment - 5) / Math.max(1, GuiDraw.textWidth(label)));
            GuiDraw.textScaled(label, center - GuiDraw.textWidthScaled(label, scale) / 2,
                    y + TOPBAR_H - 11, scale, textColor, false);
        }
    }

    private void drawSearchField(int x, int mouseX, int mouseY, float delta, float appear) {
        searchFieldX = x;
        boolean hovered = inside(mouseX, mouseY, x, searchFieldY, searchWidth, SEARCH_H);
        searchHover = GuiDraw.approach(searchHover, hovered ? 1f : 0f, 16f, delta);
        searchFocus = GuiDraw.approach(searchFocus, typing ? 1f : 0f, 16f, delta);

        inputField(x, searchFieldY, searchWidth, SEARCH_H, searchHover, searchFocus, appear);

        float iconCx = x + 9f;
        float iconCy = searchFieldY + SEARCH_H / 2f - 0.5f;
        int iconColor = GuiDraw.lerpColor(GuiTheme.TEXT_FAINT, GuiTheme.TEXT_DIM, Math.max(searchHover, searchFocus));
        GuiDraw.searchIcon(iconCx, iconCy, 3.1f, GuiDraw.withAlpha(iconColor, appear));

        int textX = x + 17;
        int textY = searchFieldY + (SEARCH_H - 8) / 2 + 1;
        boolean hasQuery = query != null && !query.isEmpty();

        if (typing) {
            searchEditor.drawWithin(GuiDraw.font(), textX, textY, GuiDraw.withAlpha(GuiTheme.TEXT, appear), true, searchWidth-30);
        } else if (hasQuery) {
            GuiDraw.textFitScaled(query, textX, textY, 1f, searchWidth - 30,
                    GuiDraw.withAlpha(GuiTheme.TEXT, appear), false);
        } else {
            GuiDraw.textFitScaled(placeholder, textX, textY, 1f, searchWidth - 30,
                    GuiDraw.withAlpha(GuiTheme.TEXT_FAINT, appear), false);
        }

        if (hasQuery) {
            float cx = x + searchWidth - 8f;
            boolean clearHov = Math.abs(mouseX + 0.5f - cx) <= 5f
                    && Math.abs(mouseY + 0.5f - (searchFieldY + SEARCH_H / 2f)) <= 6f;
            int col = GuiDraw.withAlpha(clearHov ? GuiTheme.DANGER : GuiTheme.TEXT_FAINT, appear);
            float r = 2.4f;
            float cy = searchFieldY + SEARCH_H / 2f;
            GuiDraw.line(cx - r, cy - r, cx + r, cy + r, 1.2f, col);
            GuiDraw.line(cx + r, cy - r, cx - r, cy + r, 1.2f, col);
        }
    }

    /**
     * Bottom strip: hints on the left, an optional inline action button, and a status
     * counter pinned right. Hints are dropped rather than overlapped on narrow screens.
     */
    public void drawFooter(int screenW, int screenH, String[] hints, String actionLabel,
                           String status, int statusColor, int mouseX, int mouseY,
                           float delta, float appear) {
        GuiDraw.pushTranslate(0f, (1f - appear) * 16f);
        float scale = 0.7f;
        float y = screenH - 13f;
        float x = GuiTheme.PANEL_LEFT;

        float statusW = status == null ? 0f : GuiDraw.textWidthScaled(status, scale);
        float statusX = screenW - GuiTheme.PANEL_LEFT - statusW;

        float actionWidth = actionLabel == null ? 0f : GuiDraw.textWidthScaled(actionLabel, scale) + 12f;
        if (hints != null) {
            for (String hint : hints) {
                float hintW = GuiDraw.textWidthScaled(hint, scale);
                if (x + hintW + 14f + actionWidth > statusX - 10f) {
                    break;
                }
                GuiDraw.textScaled(hint, x, y, scale, GuiDraw.withAlpha(GuiTheme.TEXT_FAINT, appear), false);
                x += hintW + 14f;
            }
        }

        actionShown = actionLabel != null;
        if (actionShown) {
            actionW = (int) actionWidth;
            actionH = 12;
            actionX = (int) x;
            actionY = (int) (y - 2f);
            boolean hovered = inside(mouseX, mouseY, actionX, actionY, actionW, actionH);
            actionHover = GuiDraw.approach(actionHover, hovered ? 1f : 0f, 16f, delta);
            GuiDraw.roundedRect(actionX, actionY, actionX + actionW, actionY + actionH, 3f,
                    GuiDraw.withAlpha(GuiDraw.lerpColor(GuiTheme.CHIP_BG, GuiTheme.CHIP_BG_HOVER, actionHover), appear),
                    GuiDraw.withAlpha(GuiDraw.lerpColor(GuiTheme.PANEL_BORDER, GuiTheme.ACCENT_MUTED, actionHover), appear));
            GuiDraw.textScaled(actionLabel, actionX + 6f, actionY + 3f, scale,
                    GuiDraw.withAlpha(GuiDraw.lerpColor(GuiTheme.TEXT_MUTED, GuiTheme.TEXT, actionHover), appear), false);
        }

        if (status != null) {
            GuiDraw.textScaled(status, statusX, y, scale, GuiDraw.withAlpha(statusColor, appear), false);
        }
        GuiDraw.popMatrix();
    }

    public boolean isFooterActionHovered(int mouseX, int mouseY) {
        return actionShown && inside(mouseX, mouseY, actionX, actionY, actionW, actionH);
    }

    // ------------------------------------------------------------------- input

    /**
     * Handles chip navigation and search focus.
     *
     * @return true when the click landed on the bar and the screen should stop processing it
     */
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton, Minecraft mc, FontRenderer fr) {
        if (mouseButton != 0) {
            return false;
        }

        for (int i = 0; i < TABS.length; i++) {
            if (!inside(mouseX, mouseY, chipX[i], TOPBAR_Y, chipW[i], TOPBAR_H)) {
                continue;
            }
            if (TABS[i] != active && pendingTab == null) {
                beginNavigation(TABS[i]);
            }
            return true;
        }

        if (inside(mouseX, mouseY, searchFieldX, searchFieldY, searchWidth, SEARCH_H)) {
            boolean hasQuery = query != null && !query.isEmpty();
            if (hasQuery && mouseX >= searchFieldX + searchWidth - 14) {
                applyQuery("");
                searchEditor.setText("");
                typing = false;
                return true;
            }
            typing = true;
            searchEditor.beginMouseSelection(mouseX, searchFieldX + 17, fr);
            return true;
        }
        return false;
    }

    public void mouseClickMove(int mouseX, int button, FontRenderer fr) {
        if (typing && button == 0) {
            searchEditor.updateMouseSelection(mouseX, searchFieldX + 17, fr);
        }
    }

    public void mouseReleased() {
        if (typing) {
            searchEditor.endMouseSelection();
        }
    }

    /** @return true when the search field consumed the keystroke */
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!typing) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
            typing = false;
            return true;
        }
        searchEditor.handleKeyTyped(typedChar, keyCode);
        applyQuery(searchEditor.getText());
        return true;
    }

    public void updateScreen() {
        if (typing) {
            searchEditor.tickRepeatKeys();
            applyQuery(searchEditor.getText());
        }
        // Every screen already calls this each tick, so the held navigation lands here rather
        // than needing four separate hooks. Switching screens from inside drawScreen would
        // swap the screen out from under the render pass that is still running.
        if (pendingTab != null && GuiAnim.millis() - pendingSinceMs >= NAV_DELAY_MS) {
            Tab target = pendingTab;
            pendingTab = null;
            open(target, Minecraft.getMinecraft());
        }
    }

    /**
     * Starts the press, the ripple and the underline's departure, and arms the screen change.
     * The screen itself does not swap until {@link #updateScreen()} sees the hold elapse.
     */
    private void beginNavigation(Tab target) {
        pendingTab = target;
        pendingSinceMs = GuiAnim.millis();
        navFrom = active;
        stopTyping();
    }

    public static void open(Tab tab, Minecraft mc) {
        crossNav = true;
        switch (tab) {
            case HUD:
                mc.displayGuiScreen(new HudEditorScreen());
                break;
            case CONFIGS:
                mc.displayGuiScreen(new ConfigGUI(null));
                break;
            default:
                mc.displayGuiScreen(new ClickGUI());
                break;
        }
    }

    // ---------------------------------------------------------------- surfaces
    // Shared primitives, so a card here is built from the same parts as a category panel.

    /** Panel surface: soft shadow, rounded fill, 1px border. */
    public static void card(float x, float y, float w, float h, float appear, float lift) {
        GuiDraw.shadow(x, y, x + w, y + h, GuiTheme.PANEL_RADIUS,
                (int) (4 + 4 * lift), (int) ((90 + 60 * lift) * appear));
        GuiDraw.glass(x, y, x + w, y + h, GuiTheme.PANEL_RADIUS,
                GuiTheme.GLASS_BODY, GuiTheme.GLASS_BORDER, 1f, appear);
    }

    /** Header band of a card: colour dot, title, right-aligned counter, separator. */
    public static void cardHeader(float x, float y, float w, int dotColor, String title,
                                  String rightLabel, float appear, float dotScale) {
        float dotY = y + GuiTheme.HEADER_H / 2f;
        GuiDraw.circle(x + 9f, dotY, 2.4f * dotScale, GuiDraw.withAlpha(dotColor, appear));

        float rightW = rightLabel == null ? 0f : GuiDraw.textWidthScaled(rightLabel, 0.75f);
        GuiDraw.textFitScaled(title, x + 16f, y + (GuiTheme.HEADER_H - 8) / 2f + 0.5f, 1f,
                w - 26f - rightW, GuiDraw.withAlpha(GuiTheme.TEXT, appear), false);
        if (rightLabel != null) {
            GuiDraw.textScaled(rightLabel, x + w - 8f - rightW,
                    y + (GuiTheme.HEADER_H - 8f * 0.75f) / 2f, 0.75f,
                    GuiDraw.withAlpha(GuiTheme.TEXT_MUTED, appear), false);
        }
        GuiDraw.rect(x + 1f, y + GuiTheme.HEADER_H, x + w - 1f, y + GuiTheme.HEADER_H + 1f,
                GuiDraw.withAlpha(GuiTheme.SEPARATOR, appear));
    }

    /** Small pill button. {@code tint} colours the hover fill, border and label. */
    public static void button(String label, float x, float y, float w, float h, float hover,
                              int tint, float appear) {
        int fill = tinted(GuiDraw.lerpColor(GuiTheme.GLASS_CHIP, GuiTheme.CHIP_BG_HOVER, hover), tint, hover * 0.35f);
        int border = GuiDraw.lerpColor(GuiTheme.GLASS_BORDER, GuiDraw.alpha(tint, 0xB0), hover);
        GuiDraw.glass(x, y, x + w, y + h, 3f, fill, border, 0.5f + 0.4f * hover, appear);
        int text = GuiDraw.lerpColor(GuiTheme.TEXT_MUTED, tint, hover);
        float scale = h < 13f ? 0.75f : 1f;
        float labelW = GuiDraw.textWidthScaled(label, scale);
        GuiDraw.textScaled(label, x + (w - labelW) / 2f, y + (h - 8f * scale) / 2f + 0.5f, scale,
                GuiDraw.withAlpha(text, appear), false);
    }

    /** Width a {@link #button} needs for {@code label} at the given height. */
    public static float buttonWidth(String label, float h) {
        return GuiDraw.textWidthScaled(label, h < 13f ? 0.75f : 1f) + 12f;
    }

    /** Rounded text field with an accent focus ring. */
    public static void inputField(float x, float y, float w, float h, float hover, float focus, float appear) {
        int bg = GuiDraw.lerpColor(GuiTheme.GLASS_CHIP, GuiTheme.CHIP_BG_HOVER,
                Math.max(hover * 0.5f, focus * 0.7f));
        int border = GuiDraw.lerpColor(GuiTheme.GLASS_BORDER, GuiTheme.ACCENT, focus);
        GuiDraw.glass(x, y, x + w, y + h, 3f, bg, border, 0.6f + 0.3f * focus, appear);
    }

    /** Row highlight used by every list in the GUI. */
    public static void rowHighlight(float x, float y, float w, float h, float strength, float appear) {
        if (strength <= 0.01f) {
            return;
        }
        int bg = GuiDraw.lerpColor(GuiTheme.ROW_BG, GuiTheme.ROW_BG_SELECTED, strength);
        GuiDraw.roundedRect(x, y, x + w, y + h, 3f, GuiDraw.withAlpha(bg, appear));
    }

    /** Accent bar that slides out on the left edge of a hovered or selected row. */
    public static void rowMarker(float x, float centerY, float height, int color, float strength, float appear) {
        if (strength <= 0.01f) {
            return;
        }
        float barH = height * GuiAnim.clamp01(strength);
        GuiDraw.roundedRect(x, centerY - barH / 2f, x + 2f, centerY + barH / 2f, 1f,
                GuiDraw.withAlpha(color, appear));
    }

    /** Matching scrollbar thumb for a clipped list body. */
    public static void scrollbar(float rightEdge, float bodyTop, float bodyHeight,
                                 float contentHeight, float scroll, float fade, float appear) {
        if (contentHeight <= bodyHeight || bodyHeight <= 4f) {
            return;
        }
        float track = bodyHeight - 4f;
        float thumbH = Math.max(12f, track * (bodyHeight / contentHeight));
        float maxScroll = contentHeight - bodyHeight;
        float t = maxScroll <= 0f ? 0f : GuiAnim.clamp01(scroll / maxScroll);
        float thumbY = bodyTop + 2f + (track - thumbH) * t;
        float w = 1.5f + 0.8f * fade;
        GuiDraw.roundedRect(rightEdge - 3f - w, thumbY, rightEdge - 3f + w, thumbY + thumbH, w,
                GuiDraw.withAlpha(0xFFFFFFFF, (0.10f + 0.22f * fade) * appear));
    }

    /** Empty-list placeholder, centred across the body. */
    public static void emptyState(float x, float y, float w, String line, float appear) {
        float scale = 0.85f;
        float lineW = GuiDraw.textWidthScaled(line, scale);
        GuiDraw.textScaled(line, x + (w - lineW) / 2f, y, scale,
                GuiDraw.withAlpha(GuiTheme.TEXT_FAINT, appear), false);
    }

    /** Blends {@code tint}'s hue into {@code base} while keeping base's alpha. */
    public static int tinted(int base, int tint, float amount) {
        return GuiDraw.lerpColor(base, GuiDraw.alpha(tint, base >>> 24), GuiAnim.clamp01(amount));
    }

    public static boolean inside(int mouseX, int mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}

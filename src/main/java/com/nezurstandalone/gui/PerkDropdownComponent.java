package com.nezurstandalone.gui;

import com.nezurstandalone.gui.physics.PhysicsSpring;
import com.nezurstandalone.settings.PerkSetting;

import java.util.List;

/**
 * Expandable perk / killstreak dropdown.
 *
 * <p>Closed it is a labelled pill carrying the current choice; opening springs the list out below
 * it, pushing the settings under it down, and each option cascades in behind the growing panel.
 * The motion is spring-driven rather than a linear fade so it settles the way the rest of the
 * ClickGUI does: the height and the pill both carry momentum, a reversal mid-flight is smooth
 * instead of a snap, and every row has its own eased hover wash and selection glow.
 */
public class PerkDropdownComponent extends Component {

    private static final float TEXT_SCALE = 0.85f;
    private static final int ITEM_H = 13;
    private static final int LIST_PAD = 4;

    private final PerkSetting perk;

    /** Height of the list, 0..1 of full - a fluid spring so the push-down never snaps. */
    private final PhysicsSpring openSpring = PhysicsSpring.iOSFluid(0f);
    /** Pill emphasis while open, and a small pop when the value changes. */
    private final PhysicsSpring pillSpring = PhysicsSpring.iOSFluid(0f);
    private final PhysicsSpring popSpring = PhysicsSpring.iOSFluid(1f);
    private final PhysicsSpring hoverSpring = PhysicsSpring.iOSFluid(0f);

    /** Per-row hover wash, eased so the highlight glides between options. */
    private float[] rowHover = new float[0];

    private boolean open;
    private float appear;
    private String shownMode;
    private long lastMs = GuiAnim.millis();

    public PerkDropdownComponent(PerkSetting setting) {
        super(setting);
        this.perk = setting;
        this.shownMode = setting.getMode();
    }

    // ------------------------------------------------------------------ layout
    @Override
    public int getExtraHeight() {
        int full = fullListHeight();
        // Expand the palette hit/layout bounds as soon as the user opens the control. The
        // spring still animates the visible list, but its first frame can no longer be clipped
        // by a panel that was measured before render advanced the spring.
        return open ? full : Math.round(full * clamp01(openSpring.getCurrentValue()));
    }

    private int fullListHeight() {
        return Math.max(1, perk.shown().size()) * ITEM_H + LIST_PAD * 2;
    }

    private float visibleListHeight() {
        return fullListHeight() * clamp01(openSpring.getCurrentValue());
    }

    private float rowReveal(int index, int count, float progress) {
        return GuiAnim.outCubic(GuiAnim.stagger(progress, index, count, 0.5f));
    }

    private boolean isOptionVisible(int index, int count, float progress, float rowY) {
        float listBottom = y + GuiTheme.ROW_H + fullListHeight() * progress;
        return rowReveal(index, count, progress) > 0.002f && rowY + ITEM_H <= listBottom + 0.5f;
    }

    private boolean isOptionHovered(int mouseX, int mouseY, float rowY) {
        return mouseX >= x + 4 && mouseX <= x + width - 4
                && mouseY >= rowY && mouseY < rowY + ITEM_H;
    }

    private void setOpen(boolean value) {
        open = value;
        float target = value ? 1f : 0f;
        openSpring.setTarget(target);
        pillSpring.setTarget(target);
    }

    private void cycleMode() {
        List<String> options = perk.shown();
        if (options.isEmpty()) return;
        int current = options.indexOf(perk.getMode());
        perk.setMode(options.get((current + 1 + options.size()) % options.size()));
        shownMode = perk.getMode();
        popSpring.setVelocity(-5f);
        press();
    }

    private void selectOption(String option) {
        perk.setMode(option);
        setOpen(false);
        press();
    }

    /** True when the point lies on a fully revealed option row, for parent-panel hit testing. */
    public boolean isOptionHit(int mouseX, int mouseY) {
        if (!open) return false;
        float progress = clamp01(openSpring.getCurrentValue());
        List<String> opts = perk.shown();
        int count = opts.size();
        float rowY = y + GuiTheme.ROW_H + LIST_PAD;
        for (int i = 0; i < count; i++, rowY += ITEM_H) {
            if (isOptionVisible(i, count, progress, rowY) && isOptionHovered(mouseX, mouseY, rowY)) return true;
        }
        return false;
    }

    private boolean clickOption(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return false;
        float progress = clamp01(openSpring.getCurrentValue());
        List<String> opts = perk.shown();
        if (opts.isEmpty()) return false;

        int count = opts.size();
        float rowY = y + GuiTheme.ROW_H + LIST_PAD;
        for (int i = 0; i < count; i++, rowY += ITEM_H) {
            if (isOptionVisible(i, count, progress, rowY) && isOptionHovered(mouseX, mouseY, rowY)) {
                selectOption(opts.get(i));
                return true;
            }
        }
        return false;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(int mouseX, int mouseY) {
        long now = GuiAnim.millis();
        float dt = Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;

        // Reconcile with the setting (config loads and other code can change it under us).
        String current = perk.getMode();
        if (!current.equals(shownMode)) {
            shownMode = current;
            popSpring.setVelocity(0f);
        }

        openSpring.setTarget(open ? 1f : 0f);
        pillSpring.setTarget(open ? 1f : 0f);
        popSpring.setTarget(1f);
        openSpring.update(dt);
        pillSpring.update(dt);
        popSpring.update(dt);

        appear = GuiAnim.approach(appear, 1f, 9f, dt);
        float a = clamp01(appear);
        float baseH = GuiTheme.ROW_H;
        float openT = clamp01(openSpring.getCurrentValue());
        float pillT = clamp01(pillSpring.getCurrentValue());

        boolean rowHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + baseH;
        hoverSpring.setTarget(rowHovered ? 1f : 0f);
        hoverSpring.update(dt);
        float hoverT = clamp01(hoverSpring.getCurrentValue());

        // --- base row -------------------------------------------------------
        float wash = Math.max(hoverT * 0.65f, pillT * 0.9f);
        if (wash > 0.01f) {
            GuiDraw.roundedRect(x + 1, y + 1, x + width - 1, y + baseH - 1, 3f,
                    GuiDraw.withAlpha(GuiTheme.ROW_BG_HOVER, wash * a));
        }

        boolean none = "No Perk".equals(shownMode);
        int accent = none ? GuiTheme.TEXT_MUTED : GuiTheme.ACCENT;

        float naturalLabelW = GuiDraw.textWidthScaled(perk.name, 1f);
        float maxPillW = width - 6f - GuiTheme.PADDING_X - Math.min(naturalLabelW, width * 0.45f) - 10f;
        float idealPillW = GuiDraw.textWidthScaled(shownMode, TEXT_SCALE) + 20f;
        float pillW = Math.max(30f, Math.min(idealPillW, maxPillW));
        float pillH = 11f;
        float pillX = x + width - 6f - pillW;
        float pillY = y + (baseH - pillH) / 2f;

        GuiDraw.textFitScaled(perk.name, x + GuiTheme.PADDING_X, y + (baseH - 8) / 2f, 1f,
                Math.max(10f, pillX - 5f - (x + GuiTheme.PADDING_X)), GuiDraw.withAlpha(GuiTheme.SETTINGS_TEXT, a), false);

        float pop = Math.max(0.1f, popSpring.getCurrentValue());
        GuiDraw.pushScale(pillX + pillW / 2f, pillY + pillH / 2f, pop, pop);

        float emphasis = Math.max(hoverT * 0.55f, pillT);
        int bg = GuiDraw.lerpColor(0xFF1A1A21, 0xFF233A5E, emphasis);
        int border = GuiDraw.lerpColor(GuiTheme.PANEL_BORDER, accent, emphasis);
        GuiDraw.roundedRect(pillX, pillY, pillX + pillW, pillY + pillH, pillH / 2f,
                GuiDraw.withAlpha(bg, a), GuiDraw.withAlpha(border, a));
        GuiDraw.chevron(pillX + 8f, pillY + pillH / 2f, 2.7f, openT,
                GuiDraw.withAlpha(GuiDraw.lerpColor(GuiTheme.TEXT_DIM, accent, emphasis), a));
        float sw = GuiDraw.textWidthScaled(shownMode, TEXT_SCALE);
        float innerW = Math.max(1f, pillW - 18f);
        float actualSw = sw > innerW ? innerW : sw;
        GuiDraw.textFitScaled(shownMode, pillX + 13f + (innerW - actualSw) / 2f, pillY + 2f, TEXT_SCALE, innerW,
                GuiDraw.withAlpha(GuiDraw.lerpColor(GuiTheme.TEXT_DIM, 0xFFFFFFFF, emphasis), a), false);
        GuiDraw.popMatrix();

        // --- option list ----------------------------------------------------
        if (openT <= 0.004f) {
            return;
        }
        List<String> opts = perk.shown();
        if (rowHover.length != opts.size()) {
            rowHover = new float[opts.size()];
        }

        float listTop = y + baseH;
        float listH = visibleListHeight();
        float listBottom = listTop + listH;

        // Panel: fades and lifts with the spring, so it reads as unfolding rather than appearing.
        GuiDraw.shadow(x + 2, listTop, x + width - 2, listBottom - 1, 3f, 3, (int) (70 * openT));
        GuiDraw.roundedRect(x + 2, listTop, x + width - 2, listBottom - 1, 3f,
                GuiDraw.withAlpha(0xF01A1A20, openT), GuiDraw.withAlpha(GuiTheme.GLASS_BORDER, openT));
        // Accent hairline down the left of the open list.
        GuiDraw.roundedRect(x + 3f, listTop + 2f, x + 4f, listBottom - 3f, 0.5f,
                GuiDraw.withAlpha(accent, 0.55f * openT));

        float oy = listTop + LIST_PAD;
        int n = opts.size();
        for (int i = 0; i < n; i++) {
            String opt = opts.get(i);
            // Use the same full-row visibility test as input. The palette clips the widget to
            // its body, while this prevents a revealed row from drawing past the dropdown panel.
            boolean visibleRow = isOptionVisible(i, n, openT, oy);
            boolean hov = visibleRow && openT > 0.004f && isOptionHovered(mouseX, mouseY, oy);
            rowHover[i] = GuiAnim.approach(rowHover[i], hov ? 1f : 0f, 14f, dt);
            if (!visibleRow) { oy += ITEM_H; continue; }

            float rowT = rowReveal(i, n, openT);

            boolean sel = opt.equals(shownMode);
            float rowA = openT * rowT;
            float slide = (1f - rowT) * 7f;

            if (rowHover[i] > 0.01f || sel) {
                int fill = sel ? GuiDraw.alpha(accent, 0x30) : GuiTheme.ROW_BG_HOVER;
                float strength = sel ? 1f : rowHover[i];
                GuiDraw.roundedRect(x + 4, oy + 0.5f, x + width - 4, oy + ITEM_H - 0.5f, 2f,
                        GuiDraw.withAlpha(fill, strength * rowA));
            }
            if (sel) {
                GuiDraw.roundedRect(x + 4.5f, oy + 2f, x + 6f, oy + ITEM_H - 2f, 0.75f,
                        GuiDraw.withAlpha(accent, rowA));
            }

            int col = sel ? accent
                    : "No Perk".equals(opt) ? GuiTheme.TEXT_MUTED
                    : GuiDraw.lerpColor(GuiTheme.TEXT_DIM, 0xFFFFFFFF, rowHover[i]);
            GuiDraw.textFitScaled(opt, x + 10 + slide, oy + (ITEM_H - 8) / 2f + 0.5f, TEXT_SCALE,
                    width - 16f, GuiDraw.withAlpha(col, rowA), false);
            oy += ITEM_H;
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float baseH = GuiTheme.ROW_H;
        boolean inBase = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + baseH;
        if (inBase) {
            if (mouseButton == 0 && perk.cycleOnLeftClick) {
                cycleMode();
            } else if (mouseButton == 0 || mouseButton == 1) {
                setOpen(!open);
                press();
            }
            return;
        }
        clickOption(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }

}

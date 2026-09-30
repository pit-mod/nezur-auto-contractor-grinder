package com.nezurstandalone.gui;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small glass information panel for module debug overlays, drawn in the ClickGUI's language.
 *
 * <p>Modules that want a live readout build one of these per frame — a title, a status badge and
 * a stack of rows and meters — and hand it a screen corner to draw into. The panel itself owns
 * the presentation: the glass surface, the accent spine, a pulsing status dot, a slide-and-fade
 * entrance, a per-row cascade, and smoothly interpolated numbers so a distance counter glides
 * rather than flickers. The module supplies facts; the HUD makes them look like the rest of the
 * client.
 *
 * <h2>Usage</h2>
 * <pre>
 *   hud.begin("Auto Sewer", accent);
 *   hud.badge("ACTIVE", green);
 *   hud.row("Engine", "PATHFINDING", stateColor);
 *   hud.meter("AFK", frac, "12:04", green);
 *   hud.render(screenW - MARGIN, MARGIN);
 * </pre>
 *
 * <p>One instance per module, kept as a field, so its animation state (entrance progress, the
 * interpolation table) persists across frames.
 */
public final class DebugHud {

    private static final float WIDTH = 168f;
    private static final float PAD = 7f;
    private static final float HEADER_H = 18f;
    private static final float ROW_H = 12f;
    private static final float METER_H = 14f;

    private enum Kind { ROW, METER, SEP }

    private static final class Entry {
        Kind kind;
        String label;
        String value;
        int valueColor;
        float frac;
        int meterColor;
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private final Map<String, Float> lerp = new HashMap<String, Float>();

    private String title = "";
    private int accent = GuiTheme.ACCENT;
    private String badgeText;
    private int badgeColor;

    private float appear;
    private long lastNanos;

    // ------------------------------------------------------------------ build

    /** Starts a fresh frame. Clears last frame's rows; keeps the entrance and lerp state. */
    public DebugHud begin(String title, int accent) {
        entries.clear();
        this.title = title == null ? "" : title;
        this.accent = accent;
        this.badgeText = null;
        return this;
    }

    /** The pill on the right of the header — a one-word status. */
    public DebugHud badge(String text, int color) {
        this.badgeText = text;
        this.badgeColor = color;
        return this;
    }

    public DebugHud row(String label, String value, int valueColor) {
        Entry e = new Entry();
        e.kind = Kind.ROW;
        e.label = label;
        e.value = value;
        e.valueColor = valueColor;
        entries.add(e);
        return this;
    }

    /** A labelled progress track with a right-aligned caption (usually a timer). */
    public DebugHud meter(String label, float frac, String caption, int color) {
        Entry e = new Entry();
        e.kind = Kind.METER;
        e.label = label;
        e.value = caption;
        e.frac = GuiAnim.clamp01(frac);
        e.meterColor = color;
        entries.add(e);
        return this;
    }

    public DebugHud separator() {
        Entry e = new Entry();
        e.kind = Kind.SEP;
        entries.add(e);
        return this;
    }

    /**
     * Interpolates a named value toward {@code target} at {@code speed}, returning the smoothed
     * current value. Use it for anything numeric that would otherwise jump frame to frame — a
     * distance, a rate — so the readout glides.
     */
    public float smooth(String key, float target, float speed) {
        float dt = Math.min(0.1f, (System.nanoTime() - lastNanos) / 1_000_000_000f);
        Float cur = lerp.get(key);
        float next = cur == null ? target : cur + (target - cur) * Math.min(1f, speed * dt);
        lerp.put(key, next);
        return next;
    }

    // ------------------------------------------------------------------ render

    /**
     * Draws the panel with its top-right corner at {@code (rightX, topY)} — the readout grows
     * downward and hugs the right edge, out of the way of the crosshair and hotbar.
     */
    public float getWidth() { return WIDTH; }
    public float getHeight() {
        float rows=0;
        for(Entry e:entries)rows+=e.kind==Kind.METER?METER_H:(e.kind==Kind.SEP?5f:ROW_H);
        return HEADER_H+PAD+rows+PAD-2f;
    }

    public void render(float rightX, float topY) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;

        appear = Math.min(1f, appear + dt * 6.5f);
        float ease = GuiAnim.outCubic(appear);

        float bodyRows = 0f;
        for (Entry e : entries) {
            bodyRows += e.kind == Kind.METER ? METER_H : (e.kind == Kind.SEP ? 5f : ROW_H);
        }
        float h = HEADER_H + PAD + bodyRows + PAD - 2f;
        float x = rightX - WIDTH;
        // Slide in from the right and fade as it settles.
        float slideX = x + (1f - ease) * 16f;
        float y = topY;

        GuiDraw.resetState();
        Minecraft mc = Minecraft.getMinecraft();

        GuiDraw.shadow(slideX, y, slideX + WIDTH, y + h, GuiTheme.PANEL_RADIUS, 5, (int) (120 * ease));
        GuiDraw.glass(slideX, y, slideX + WIDTH, y + h, GuiTheme.PANEL_RADIUS,
                GuiTheme.GLASS_BODY, GuiTheme.GLASS_BORDER, 1f, ease);

        // Accent spine down the left edge.
        GuiDraw.roundedRect(slideX + 1.5f, y + 4f, slideX + 3f, y + h - 4f, 0.75f,
                GuiDraw.withAlpha(accent, ease));

        // --- Header: pulsing dot, title, status pill ---
        float dotPulse = 0.75f + 0.25f * GuiAnim.pulse(1.4f);
        float dotY = y + HEADER_H / 2f;
        GuiDraw.glow(slideX + 11f, dotY, 6.5f * dotPulse, GuiDraw.withAlpha(accent, 0.4f * ease), 4);
        GuiDraw.circle(slideX + 11f, dotY, 2.5f, GuiDraw.withAlpha(accent, ease));

        GuiDraw.textScaled(title, slideX + 17f, y + (HEADER_H - 8f) / 2f + 0.5f, 0.9f,
                GuiDraw.withAlpha(GuiTheme.TEXT, ease), false);

        if (badgeText != null) {
            float bw = GuiDraw.textWidthScaled(badgeText, 0.7f) + 8f;
            float bx = slideX + WIDTH - bw - 6f;
            float by = y + (HEADER_H - 9f) / 2f;
            GuiDraw.roundedRect(bx, by, bx + bw, by + 9f, 2.5f,
                    GuiDraw.withAlpha(GuiChrome.tinted(GuiTheme.CHIP_BG, badgeColor, 0.4f), ease),
                    GuiDraw.withAlpha(GuiDraw.alpha(badgeColor, 0xB0), ease));
            GuiDraw.textScaled(badgeText, bx + 4f, by + 1.5f, 0.7f, GuiDraw.withAlpha(badgeColor, ease), false);
        }

        GuiDraw.rect(slideX + 5f, y + HEADER_H, slideX + WIDTH - 5f, y + HEADER_H + 0.75f,
                GuiDraw.withAlpha(GuiTheme.SEPARATOR, ease));

        // --- Body: rows and meters, each cascading in ---
        float ry = y + HEADER_H + PAD - 2f;
        int idx = 0;
        int count = Math.max(1, entries.size());
        for (Entry e : entries) {
            float rowT = GuiAnim.outCubic(GuiAnim.stagger(ease, idx++, count, 0.55f));
            float rowA = ease * rowT;
            float slide = (1f - rowT) * 8f;

            if (e.kind == Kind.SEP) {
                GuiDraw.rect(slideX + PAD + slide, ry + 2f, slideX + WIDTH - PAD, ry + 2.75f,
                        GuiDraw.withAlpha(GuiTheme.SEPARATOR, rowA));
                ry += 5f;
                continue;
            }

            if (e.kind == Kind.METER) {
                GuiDraw.textScaled(e.label, slideX + PAD + slide, ry, 0.75f,
                        GuiDraw.withAlpha(GuiTheme.TEXT_DIM, rowA), false);
                if (e.value != null) {
                    float cw = GuiDraw.textWidthScaled(e.value, 0.75f);
                    GuiDraw.textScaled(e.value, slideX + WIDTH - PAD - cw, ry, 0.75f,
                            GuiDraw.withAlpha(GuiTheme.TEXT, rowA), false);
                }
                float trackY = ry + 8f;
                float trackX1 = slideX + PAD + slide;
                float trackX2 = slideX + WIDTH - PAD;
                GuiDraw.roundedRect(trackX1, trackY, trackX2, trackY + 2.5f, 1.25f,
                        GuiDraw.withAlpha(0x33FFFFFF, rowA));
                float fillW = (trackX2 - trackX1) * e.frac;
                if (fillW > 1f) {
                    GuiDraw.roundedRect(trackX1, trackY, trackX1 + fillW, trackY + 2.5f, 1.25f,
                            GuiDraw.withAlpha(e.meterColor, rowA));
                }
                ry += METER_H;
                continue;
            }

            // ROW
            GuiDraw.textScaled(e.label, slideX + PAD + slide, ry, 0.85f,
                    GuiDraw.withAlpha(GuiTheme.TEXT_MUTED, rowA), false);
            if (e.value != null) {
                float vw = GuiDraw.textWidthScaled(e.value, 0.85f);
                float vx = slideX + WIDTH - PAD - vw;
                float labelEnd = slideX + PAD + slide + GuiDraw.textWidthScaled(e.label, 0.85f) + 4f;
                GuiDraw.textFitScaled(e.value, Math.max(vx, labelEnd), ry, 0.85f,
                        slideX + WIDTH - PAD - Math.max(vx, labelEnd),
                        GuiDraw.withAlpha(e.valueColor, rowA), false);
            }
            ry += ROW_H;
        }
    }

    /** Resets the entrance so the panel plays its intro again next time it is shown. */
    public void resetEntrance() {
        appear = 0f;
    }

    // ------------------------------------------------------------------ format

    /** {@code mm:ss} for a millisecond duration, clamped at zero. */
    public static String mmss(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        return String.format("%02d:%02d", total / 60, total % 60);
    }

    /** {@code Hh MMm} for a millisecond duration, clamped at zero. */
    public static String hhmm(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 60000;
        return String.format("%dh %02dm", total / 60, total % 60);
    }
}

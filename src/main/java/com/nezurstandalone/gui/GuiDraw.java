package com.nezurstandalone.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Float-precision primitives for the ClickGUI: rounded surfaces, circles, pills, lines and
 * sub-pixel scaled text. Every helper restores the GL state it touched so callers can mix
 * these freely with vanilla font/gui rendering.
 */
public final class GuiDraw {

    private GuiDraw() {
    }

    // ------------------------------------------------------------------ shapes

    /**
     * Normalises the GL state the in-game HUD renderers leave behind before the GUI pass,
     * resyncing GlStateManager's cache with the driver.
     */
    public static void resetState() {
        GlStateManager.enableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.disableLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.enableAlpha();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        SCISSOR_STACK.clear();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static void rect(float x1, float y1, float x2, float y2, int color) {
        if (isTransparent(color) || x2 <= x1 || y2 <= y1) {
            return;
        }
        begin(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
        end();
    }

    public static void roundedRect(float x1, float y1, float x2, float y2, float radius, int color) {
        if (isTransparent(color) || x2 <= x1 || y2 <= y1) {
            return;
        }
        float r = Math.min(radius, Math.min((x2 - x1) / 2f, (y2 - y1) / 2f));
        if (r <= 0.05f) {
            rect(x1, y1, x2, y2, color);
            return;
        }

        begin(color);
        GL11.glBegin(GL11.GL_POLYGON);
        arc(x1 + r, y1 + r, r, 180f, 270f);
        arc(x2 - r, y1 + r, r, 270f, 360f);
        arc(x2 - r, y2 - r, r, 0f, 90f);
        arc(x1 + r, y2 - r, r, 90f, 180f);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        arc(x1 + r, y1 + r, r, 180f, 270f);
        arc(x2 - r, y1 + r, r, 270f, 360f);
        arc(x2 - r, y2 - r, r, 0f, 90f);
        arc(x1 + r, y2 - r, r, 90f, 180f);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        end();
    }

    /** Rounded surface with a crisp 1px border drawn underneath the fill. */
    public static void roundedRect(float x1, float y1, float x2, float y2, float radius, int fill, int border) {
        roundedRect(x1, y1, x2, y2, radius, border);
        roundedRect(x1 + 1f, y1 + 1f, x2 - 1f, y2 - 1f, Math.max(0f, radius - 1f), fill);
    }

    /** Fully rounded capsule — radius is locked to half the height. */
    public static void pill(float x, float y, float width, float height, int color) {
        roundedRect(x, y, x + width, y + height, height / 2f, color);
    }

    public static void circle(float cx, float cy, float radius, int color) {
        if (isTransparent(color) || radius <= 0f) {
            return;
        }
        begin(color);
        GL11.glBegin(GL11.GL_POLYGON);
        arc(cx, cy, radius, 0f, 360f);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        arc(cx, cy, radius, 0f, 360f);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        end();
    }

    public static void ring(float cx, float cy, float radius, float thickness, int color) {
        if (isTransparent(color) || radius <= 0f) {
            return;
        }
        float inner = Math.max(0f, radius - thickness);
        begin(color);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        int steps = Math.max(12, (int) (radius * 6f));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(360.0 * i / steps);
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            GL11.glVertex2d(cx + cos * inner, cy + sin * inner);
            GL11.glVertex2d(cx + cos * radius, cy + sin * radius);
        }
        GL11.glEnd();
        end();
    }

    public static void roundedRectGradient(float x1, float y1, float x2, float y2, float radius,
                                           int leftColor, int rightColor) {
        if (x2 <= x1 || y2 <= y1 || (isTransparent(leftColor) && isTransparent(rightColor))) {
            return;
        }
        float r = Math.min(radius, Math.min((x2 - x1) / 2f, (y2 - y1) / 2f));
        float span = x2 - x1;

        beginNoColor();
        GL11.glBegin(GL11.GL_POLYGON);
        arcGradient(x1 + r, y1 + r, r, 180f, 270f, x1, span, leftColor, rightColor);
        arcGradient(x2 - r, y1 + r, r, 270f, 360f, x1, span, leftColor, rightColor);
        arcGradient(x2 - r, y2 - r, r, 0f, 90f, x1, span, leftColor, rightColor);
        arcGradient(x1 + r, y2 - r, r, 90f, 180f, x1, span, leftColor, rightColor);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        arcGradient(x1 + r, y1 + r, r, 180f, 270f, x1, span, leftColor, rightColor);
        arcGradient(x2 - r, y1 + r, r, 270f, 360f, x1, span, leftColor, rightColor);
        arcGradient(x2 - r, y2 - r, r, 0f, 90f, x1, span, leftColor, rightColor);
        arcGradient(x1 + r, y2 - r, r, 90f, 180f, x1, span, leftColor, rightColor);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        end();
    }

    /** Rounded rect filled with a top-to-bottom gradient. */
    public static void roundedRectGradientV(float x1, float y1, float x2, float y2, float radius,
                                            int topColor, int bottomColor) {
        if (x2 <= x1 || y2 <= y1 || (isTransparent(topColor) && isTransparent(bottomColor))) {
            return;
        }
        float r = Math.min(radius, Math.min((x2 - x1) / 2f, (y2 - y1) / 2f));
        float span = y2 - y1;

        beginNoColor();
        GL11.glBegin(GL11.GL_POLYGON);
        arcGradientV(x1 + r, y1 + r, r, 180f, 270f, y1, span, topColor, bottomColor);
        arcGradientV(x2 - r, y1 + r, r, 270f, 360f, y1, span, topColor, bottomColor);
        arcGradientV(x2 - r, y2 - r, r, 0f, 90f, y1, span, topColor, bottomColor);
        arcGradientV(x1 + r, y2 - r, r, 90f, 180f, y1, span, topColor, bottomColor);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        arcGradientV(x1 + r, y1 + r, r, 180f, 270f, y1, span, topColor, bottomColor);
        arcGradientV(x2 - r, y1 + r, r, 270f, 360f, y1, span, topColor, bottomColor);
        arcGradientV(x2 - r, y2 - r, r, 0f, 90f, y1, span, topColor, bottomColor);
        arcGradientV(x1 + r, y2 - r, r, 90f, 180f, y1, span, topColor, bottomColor);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        end();
    }

    // ------------------------------------------------------------------- glass

    /**
     * A frosted-glass surface.
     *
     * <p>Built as a stack rather than a single fill, because the thing that makes a pane read
     * as glass is not its transparency - it is the light behaviour at its edges. The body is
     * translucent, a sheen falls through the upper half, a bright specular catch runs along
     * the top edge and fades out at the corners, and a darker band grounds the bottom. A flat
     * translucent rectangle with a border reads as a hole cut in the screen; this reads as
     * something lying on top of it.
     *
     * <p>No framebuffer blur is involved. Capturing and blurring the backdrop every frame is
     * the usual way to do this and it is far too expensive to spend on a menu, so the
     * impression is assembled out of ordinary geometry instead.
     *
     * @param body     surface colour and opacity of the pane
     * @param strength 0 flattens the effect back to a plain fill, 1 is full glass
     */
    public static void glass(float x1, float y1, float x2, float y2, float radius,
                             int body, int border, float strength, float appear) {
        if (x2 <= x1 || y2 <= y1 || appear <= 0.004f) {
            return;
        }
        float inner = Math.max(0f, radius - 1f);
        // Flat, even, translucent grey. The panes used to carry a sheen down their top half and
        // a bright specular line across their top edge; rendered, that line read as a white bar
        // cutting every surface in two. A window on a menu is a tint you see through, not a lit
        // 3D solid - so the material is now a single uniform fill inside a soft one-pixel border.
        roundedRect(x1, y1, x2, y2, radius, withAlpha(border, appear));
        roundedRect(x1 + 1f, y1 + 1f, x2 - 1f, y2 - 1f, inner, withAlpha(body, appear));
    }

    /**
     * The bright line along the top of a pane, brightest in the middle and gone at the ends.
     * Two mirrored gradients, because a constant-brightness line reads as a drawn border
     * rather than as light catching on an edge.
     */
    public static void specularEdge(float x1, float x2, float y, float thickness, int color) {
        if (x2 <= x1 || isTransparent(color)) {
            return;
        }
        float mid = (x1 + x2) / 2f;
        int clear = alpha(color, 0);
        roundedRectGradient(x1, y, mid, y + thickness, 0f, clear, color);
        roundedRectGradient(mid, y, x2, y + thickness, 0f, color, clear);
    }

    /**
     * A slow highlight travelling across a pane. {@code phase} runs 0..1 and wraps; the band
     * is soft-edged so it never resolves into a hard shape.
     */
    public static void glassSweep(float x1, float y1, float x2, float y2, float radius,
                                  float phase, int color, float appear) {
        if (x2 <= x1 || y2 <= y1 || isTransparent(color)) {
            return;
        }
        float w = x2 - x1;
        float bandW = Math.max(18f, w * 0.28f);
        // Travel a full band-width past each edge so the entry and exit are off-surface.
        float cx = x1 - bandW + (w + bandW * 2f) * clamp01(phase);
        float left = Math.max(x1 + 1f, cx - bandW / 2f);
        float right = Math.min(x2 - 1f, cx + bandW / 2f);
        if (right <= left) {
            return;
        }

        // Bounded by geometry rather than by a scissor clip on purpose. endClip() disables
        // scissor outright instead of restoring an enclosing one, so clipping here would
        // silently destroy the clip of any list this surface is drawn inside. The band is
        // already confined to the pane, and at this alpha the few pixels it can spill past a
        // rounded corner are below the threshold of visibility.
        float clampedCx = Math.max(left, Math.min(right, cx));
        int clear = alpha(color, 0);
        int peak = withAlpha(color, appear);
        roundedRectGradient(left, y1 + 1f, clampedCx, y2 - 1f, 0f, clear, peak);
        roundedRectGradient(clampedCx, y1 + 1f, right, y2 - 1f, 0f, peak, clear);
    }

    /** Soft radial glow built from concentric fading circles. */
    public static void glow(float cx, float cy, float radius, int color, int layers) {
        int n = Math.max(2, layers);
        int baseAlpha = (color >>> 24);
        if (baseAlpha == 0 || radius <= 0f) {
            return;
        }
        for (int i = n; i >= 1; i--) {
            float t = i / (float) n;
            int a = (int) (baseAlpha * (1f - t) * (1f - t) * 0.9f);
            if (a <= 0) {
                continue;
            }
            circle(cx, cy, radius * t, alpha(color, a));
        }
    }

    /** Expanding ring used for click ripples; fades as it grows. */
    public static void ripple(float cx, float cy, float maxRadius, float progress, int color) {
        float t = clamp01(progress);
        if (t >= 1f) {
            return;
        }
        float radius = maxRadius * easeOutCubic(t);
        float fade = 1f - t;
        ring(cx, cy, radius, Math.max(1f, radius * 0.35f), withAlpha(color, fade * fade));
    }

    // ------------------------------------------------------------- transforms

    /** Scales subsequent drawing about a pivot. Pair with {@link #popMatrix()}. */
    public static void pushScale(float pivotX, float pivotY, float scaleX, float scaleY) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(pivotX, pivotY, 0f);
        GlStateManager.scale(scaleX, scaleY, 1f);
        GlStateManager.translate(-pivotX, -pivotY, 0f);
    }

    public static void pushTranslate(float dx, float dy) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(dx, dy, 0f);
    }

    public static void popMatrix() {
        GlStateManager.popMatrix();
    }

    public static void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        if (isTransparent(color)) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001f) {
            return;
        }
        float nx = -dy / len * thickness / 2f;
        float ny = dx / len * thickness / 2f;

        begin(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1 + nx, y1 + ny);
        GL11.glVertex2f(x2 + nx, y2 + ny);
        GL11.glVertex2f(x2 - nx, y2 - ny);
        GL11.glVertex2f(x1 - nx, y1 - ny);
        GL11.glEnd();
        end();
    }

    /** Soft drop shadow rendered as concentric rounded rings behind a panel. */
    public static void shadow(float x1, float y1, float x2, float y2, float radius, int layers, int baseAlpha) {
        if (!com.nezurstandalone.module.impl.render.ClickGuiSettings.shadowsOn()) {
            return; // panel shadows switched off in the ClickGUI settings
        }
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;
            int a = (int) (baseAlpha * (1f - t) * (1f - t));
            if (a <= 0) {
                continue;
            }
            roundedRect(x1 - i, y1 - i, x2 + i, y2 + i, radius + i, a << 24);
        }
    }

    /** Magnifying-glass glyph — the vanilla font has no icon for this. */
    public static void searchIcon(float cx, float cy, float radius, int color) {
        ring(cx, cy, radius, 1f, color);
        float k = radius * 0.72f;
        line(cx + k, cy + k, cx + k + radius * 0.9f, cy + k + radius * 0.9f, 1.4f, color);
    }

    /** Chevron pointing down, rotated by {@code openness} (0 = collapsed/right, 1 = open/down). */
    public static void chevron(float cx, float cy, float size, float openness, int color) {
        double angle = Math.toRadians(-90.0 + 90.0 * clamp01(openness));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        float half = size / 2f;
        // Local space: an inverted "V" whose tip points down.
        float[][] pts = {{-half, -half * 0.55f}, {0f, half * 0.55f}, {half, -half * 0.55f}};
        float[][] world = new float[3][2];
        for (int i = 0; i < 3; i++) {
            world[i][0] = (float) (cx + pts[i][0] * cos - pts[i][1] * sin);
            world[i][1] = (float) (cy + pts[i][0] * sin + pts[i][1] * cos);
        }
        line(world[0][0], world[0][1], world[1][0], world[1][1], 1.4f, color);
        line(world[1][0], world[1][1], world[2][0], world[2][1], 1.4f, color);
    }

    // -------------------------------------------------------------------- text

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    /**
     * Vanilla's font renderer treats a nearly-transparent colour as fully opaque.
     *
     * <p>{@code FontRenderer.renderString} opens with
     * <pre>if ((color & 0xFC000000) == 0) color |= 0xFF000000;</pre>
     * which is meant to let callers pass a bare RGB value and get opaque text. The side effect
     * is that any alpha below 4 - the tail end of every fade-out in this GUI - is silently
     * promoted to 255. So text does not fade to nothing, it fades down and then snaps back to
     * full brightness on the last frame or two.
     *
     * <p>That is what made the top-bar logo flash: {@code drawTopBar} fades it with
     * {@code withAlpha(TEXT, appear)} while sliding it left by {@code (1 - appear) * 24}px, so
     * the frame where alpha crosses below 4 is also the frame where the logo is furthest left.
     * The result was a bright "nezur" jumping sideways across the screen as the GUI opened or
     * closed, over a world that no longer had a panel on it.
     *
     * <p>Anything this faint is invisible by intent, so it is simply not drawn.
     */
    private static boolean tooFaintToDraw(int color) {
        return (color & 0xFC000000) == 0 && (color & 0xFF000000) != 0xFF000000;
    }

    public static void text(String s, float x, float y, int color) {
        if (tooFaintToDraw(color)) {
            return;
        }
        font().drawStringWithShadow(s, x, y, color);
    }

    public static void textScaled(String s, float x, float y, float scale, int color, boolean shadow) {
        shadow = shadow || com.nezurstandalone.module.impl.render.ClickGuiSettings.textShadowOn();
        if (tooFaintToDraw(color)) {
            return;
        }
        FontRenderer fr = font();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(scale, scale, 1f);
        fr.drawString(s, 0f, 0f, color, shadow);
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    public static int textWidth(String s) {
        return font().getStringWidth(s);
    }

    public static float textWidthScaled(String s, float scale) {
        return font().getStringWidth(s) * scale;
    }

    /** Draws {@code s} at {@code scale}, shrinking further if it would exceed {@code maxWidth}. */
    public static void textFitScaled(String s, float x, float y, float scale, float maxWidth, int color, boolean shadow) {
        float w = textWidthScaled(s, scale);
        if (w > maxWidth && maxWidth > 0f) {
            scale *= maxWidth / w;
        }
        textScaled(s, x, y, scale, color, shadow);
    }

    // ----------------------------------------------------------------- scissor

    // Scissor is a stack, not a flag: ModeComponent pills and ButtonComponent ripples clip
    // while already inside the palette body's clip, and the old endClip() disabled the test
    // outright - every row after the first pill rendered unclipped. Intersecting with the
    // enclosing box (GL11.glScissor does not intersect, so track it manually) keeps nesting
    // correct for any depth.
    private static final java.util.ArrayDeque<int[]> SCISSOR_STACK = new java.util.ArrayDeque<>();

    /** Clips to a GUI-space rectangle. Coordinates are in scaled screen pixels. */
    public static void beginClip(float x, float y, float width, float height) {
        if (width <= 0f || height <= 0f) {
            SCISSOR_STACK.push(new int[]{0,0,0,0});
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0,0,0,0);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = resolution(mc);
        int scale = sr.getScaleFactor();
        int screenHeight = mc.currentScreen instanceof GuiScreen ? mc.currentScreen.height : sr.getScaledHeight();

        float top = Math.max(0f, y);
        float bottom = Math.min(screenHeight, y + height);
        if (bottom <= top) {
            SCISSOR_STACK.push(new int[]{0, 0, 0, 0});
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, 0, 0);
            return;
        }

        int nx = Math.round(Math.max(0f, x) * scale);
        int ny = Math.round(mc.displayHeight - bottom * scale);
        int nw = Math.round(Math.max(0f, width) * scale);
        int nh = Math.round((bottom - top) * scale);

        // Intersect with the enclosing clip so nested clips shrink, never widen.
        int[] outer = SCISSOR_STACK.peek();
        if (outer != null) {
            int ix = Math.max(nx, outer[0]);
            int iy = Math.max(ny, outer[1]);
            int ix2 = Math.min(nx + nw, outer[0] + outer[2]);
            int iy2 = Math.min(ny + nh, outer[1] + outer[3]);
            nx = ix; ny = iy;
            nw = Math.max(0, ix2 - ix);
            nh = Math.max(0, iy2 - iy);
        }

        SCISSOR_STACK.push(new int[]{nx, ny, nw, nh});
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(nx, ny, nw, nh);
    }

    public static void endClip() {
        if (SCISSOR_STACK.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        SCISSOR_STACK.pop();
        int[] outer = SCISSOR_STACK.peek();
        if (outer != null) {
            GL11.glScissor(outer[0], outer[1], outer[2], outer[3]);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /**
     * A {@link ScaledResolution} is pure arithmetic over the display size, the GUI scale and
     * the unicode-font flag, so it only has to be rebuilt when one of those changes. Every
     * clipped panel calls {@link #beginClip} once or twice a frame, which made allocating a
     * fresh one per call the steadiest source of garbage in the GUI. Render thread only.
     */
    private static ScaledResolution cachedResolution;
    private static int cachedDisplayWidth;
    private static int cachedDisplayHeight;
    private static int cachedGuiScale;
    private static boolean cachedUnicode;

    /** Kept as a shim: this cache is now shared with every other caller in the mod. */
    private static ScaledResolution resolution(Minecraft mc) {
        return com.nezurstandalone.utils.ScreenScale.get();
    }

    // ------------------------------------------------------------------ colors

    public static int lerpColor(int from, int to, float t) {
        t = clamp01(t);
        int a = lerpChannel(from >>> 24, to >>> 24, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int color, float multiplier) {
        int a = (int) ((color >>> 24) * clamp01(multiplier));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int alpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------- maths

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Frame-rate independent approach: pulls {@code current} toward {@code target}. */
    public static float approach(float current, float target, float speed, float delta) {
        return GuiAnim.approach(current,target,speed,delta);
    }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    // ----------------------------------------------------------------- private

    private static int lerpChannel(int from, int to, float t) {
        return Math.max(0, Math.min(255, (int) (from + (to - from) * t + 0.5f)));
    }

    private static boolean isTransparent(int color) {
        return (color >>> 24) == 0;
    }

    private static void vertexGradient(float vx, float vy, float x1, float span, int left, int right) {
        float t = span <= 0f ? 0f : clamp01((vx - x1) / span);
        int c = lerpColor(left, right, t);
        GL11.glColor4f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f,
                ((c >>> 24) & 0xFF) / 255f);
        GL11.glVertex2f(vx, vy);
    }

    private static void vertexGradientV(float vx, float vy, float y1, float span, int top, int bottom) {
        float t = span <= 0f ? 0f : clamp01((vy - y1) / span);
        int c = lerpColor(top, bottom, t);
        GL11.glColor4f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f,
                ((c >>> 24) & 0xFF) / 255f);
        GL11.glVertex2f(vx, vy);
    }

    private static void arcGradientV(float cx, float cy, float r, float startDeg, float endDeg,
                                     float y1, float span, int top, int bottom) {
        int steps = Math.max(16, (int) (Math.abs(endDeg - startDeg) / 4f + r * 3f));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(startDeg + (endDeg - startDeg) * (i / (double) steps));
            vertexGradientV((float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r),
                    y1, span, top, bottom);
        }
    }

    private static void arcGradient(float cx, float cy, float r, float startDeg, float endDeg,
                                    float x1, float span, int left, int right) {
        int steps = Math.max(16, (int) (Math.abs(endDeg - startDeg) / 4f + r * 3f));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(startDeg + (endDeg - startDeg) * (i / (double) steps));
            vertexGradient((float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r),
                    x1, span, left, right);
        }
    }

    private static void arc(float cx, float cy, float r, float startDeg, float endDeg) {
        int steps = Math.max(16, (int) (Math.abs(endDeg - startDeg) / 4f + r * 3f));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(startDeg + (endDeg - startDeg) * (i / (double) steps));
            GL11.glVertex2d(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        }
    }

    // Two GL hazards this GUI has to defend against, both caused by the in-game HUD/ESP
    // renderers running immediately before the GUI pass:
    //
    //  1. Face culling is left enabled. These primitives wind their vertices the opposite
    //     way from RenderUtils.drawRect (which swaps its coordinates to compensate), so
    //     every shape would be discarded as back-facing. 2D UI has no meaningful facing,
    //     so culling is simply turned off while drawing.
    //  2. GlStateManager caches GL enables, and those renderers poke GL11 directly, which
    //     desyncs the cache and can turn disableTexture2D() into a no-op. Driving both the
    //     manager and raw GL keeps the cache and the driver in agreement.

    /** Same state setup as {@link #begin(int)} but leaves colour to per-vertex calls. */
    private static void beginNoColor() {
        begin(0xFFFFFFFF);
        gradientShade=GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static int gradientShade=-1;

    private static void begin(int color) {
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager.disableTexture2D();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GlStateManager.disableAlpha();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GlStateManager.disableLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glColor4f(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f);
    }

    private static void end() {
        if (gradientShade!=-1) {
            GlStateManager.shadeModel(gradientShade);
            gradientShade=-1;
        }
        GlStateManager.enableAlpha();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GlStateManager.enableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.enableCull();
        GL11.glEnable(GL11.GL_CULL_FACE);
        GlStateManager.disableBlend();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}

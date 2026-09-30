package com.nezurstandalone.gui;

/**
 * The entrance each screen plays when it opens.
 *
 * <p>Every screen used to animate in identically — the bar slid in from the left and the
 * content cascaded left to right — so all four tabs felt like the same screen redrawing rather
 * than like four places.
 *
 * <p>Two rules keep these from looking cheap, both learned the hard way. Transforms are
 * anchored to the <em>content's</em> own centre, never to a screen edge: scaling a whole
 * screen about its right-hand side drags everything sideways by an amount that depends on how
 * far from that edge it happens to sit, which reads as a skew rather than as an arrival. And
 * each style commits to one thing — a direction or a scale, not both — because two transforms
 * fighting over the same frames is what makes motion look unresolved.
 */
public final class GuiIntro {

    private GuiIntro() {
    }

    public enum Style {
        /** Panels fan outward from the left; the screen itself does not move. */
        CASCADE,
        /** Content rises from below and settles. */
        RISE,
        /** Content slides in from the right. */
        SWEEP,
        /** Content scales open about its own centre. */
        BLOOM
    }

    public static Style forTab(GuiChrome.Tab tab) {
        if (tab == null) {
            return Style.CASCADE;
        }
        switch (tab) {
            case HUD:
                return Style.RISE;
            case CONFIGS:
                return Style.SWEEP;
            default:
                return Style.CASCADE;
        }
    }

    /**
     * The curve this style reveals on. Exposed separately so a screen that staggers its own
     * children drives them from the same easing the wrapper uses, and the two stay in step.
     */
    public static float ease(Style style, float appear) {
        float t = GuiAnim.clamp01(appear);
        switch (style) {
            case RISE:
                return GuiAnim.outQuint(t);
            case SWEEP:
                return GuiAnim.outExpo(t);
            case BLOOM:
                return GuiAnim.outBack(t);
            default:
                return GuiAnim.outCubic(t);
        }
    }

    /**
     * Pushes this style's entrance transform about the content's own centre. Always pushes
     * exactly two matrices so {@link #end()} can pop blindly whatever the style.
     *
     * @param pivotX centre of the content being revealed, not of the screen
     */
    public static void begin(Style style, float appear, float pivotX, float pivotY) {
        float e = ease(style, appear);
        float out = 1f - e;
        switch (style) {
            case RISE:
                GuiDraw.pushTranslate(0f, out * 22f);
                GuiDraw.pushScale(pivotX, pivotY, 1f, 1f);
                break;
            case SWEEP:
                GuiDraw.pushTranslate(out * 34f, 0f);
                GuiDraw.pushScale(pivotX, pivotY, 1f, 1f);
                break;
            case BLOOM: {
                float sc = 0.94f + 0.06f * e;
                GuiDraw.pushTranslate(0f, 0f);
                GuiDraw.pushScale(pivotX, pivotY, sc, sc);
                break;
            }
            default:
                // Modules keeps its per-panel cascade, which carries more information than any
                // whole-screen transform could; the wrapper only has to stay balanced.
                GuiDraw.pushTranslate(0f, 0f);
                GuiDraw.pushScale(pivotX, pivotY, 1f, 1f);
                break;
        }
    }

    public static void end() {
        GuiDraw.popMatrix();
        GuiDraw.popMatrix();
    }

    /**
     * The top bar's own entrance offset, as {x, y} pixels.
     *
     * <p>Small on purpose. The bar is the one element common to all four screens, so it should
     * agree with the direction the content arrives from without restaging itself every time —
     * a bar that travels as far as the content reads as a second, competing animation.
     */
    public static float[] barOffset(Style style, float appear) {
        float out = 1f - ease(style, appear);
        switch (style) {
            case RISE:
                return new float[]{0f, -out * 10f};
            case SWEEP:
                return new float[]{out * 14f, 0f};
            case BLOOM:
                return new float[]{0f, -out * 7f};
            default:
                return new float[]{-out * 14f, 0f};
        }
    }
}

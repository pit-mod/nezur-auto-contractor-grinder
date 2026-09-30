package com.nezurstandalone.gui;

/**
 * Easing curves, staggered-reveal timing and click ripples for the ClickGUI.
 *
 * <p>Springs ({@link com.nezurstandalone.gui.physics.PhysicsSpring}) drive anything that can be
 * interrupted mid-flight — panel open/close, toggle knobs. These curves drive one-shot
 * reveals where the timeline is known up front.
 */
public final class GuiAnim {
    private static final long START_NANOS = System.nanoTime();

    public static long millis() { return (System.nanoTime()-START_NANOS)/1_000_000L; }

    public static float approach(float current,float target,float speed,float delta) {
        if (!Float.isFinite(delta) || delta<=0 || speed<=0) return current;
        return current+(target-current)*(float)-Math.expm1(-speed*delta);
    }

    private GuiAnim() {
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    // ------------------------------------------------------------------ easing

    public static float outSine(float t) {
        return (float) Math.sin(clamp01(t) * Math.PI / 2.0);
    }

    public static float outCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    public static float outQuint(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u * u * u;
    }

    public static float outExpo(float t) {
        t = clamp01(t);
        return t >= 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2.0 * t + 2.0, 3.0) / 2f;
    }

    /** Accelerates away from 0 — the natural partner of {@link #outCubic} for dismissals. */
    public static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    /**
     * Anticipation: dips backwards past 0 before accelerating away. Used for exits, where
     * the small pop outwards before the collapse is what makes the motion read as deliberate
     * rather than as the element simply being switched off.
     */
    public static float inBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        t = clamp01(t);
        return c3 * t * t * t - c1 * t * t;
    }

    /** Overshoots past 1 then settles — good for pills and knobs snapping into place. */
    public static float outBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = clamp01(t) - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Springy overshoot with a couple of decaying bounces. */
    public static float outElastic(float t) {
        t = clamp01(t);
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c4 = (float) (2.0 * Math.PI / 3.0);
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0);
    }

    // ----------------------------------------------------------------- stagger

    /**
     * Progress for one item in a staggered reveal.
     *
     * @param progress     overall 0..1 progress of the reveal
     * @param index        item index
     * @param count        total items
     * @param itemDuration fraction of the timeline a single item takes (0..1); smaller
     *                     values spread items further apart
     */
    public static float stagger(float progress, int index, int count, float itemDuration) {
        if (count <= 1) {
            return clamp01(progress);
        }
        float duration = Math.max(0.05f, Math.min(1f, itemDuration));
        float step = (1f - duration) / (count - 1);
        float start = index * step;
        return clamp01((progress - start) / duration);
    }

    // ------------------------------------------------------------------ ripple

    /** Expanding circular ripple from a click point. */
    public static final class Ripple {
        private float progress = 1f;
        private float originX, originY;

        public void trigger(float x, float y) {
            this.originX = x;
            this.originY = y;
            this.progress = 0f;
        }

        public void update(float delta, float speed) {
            if (progress < 1f) {
                progress = Math.min(1f, progress + delta * speed);
            }
        }

        public boolean isActive() {
            return progress < 1f;
        }

        public float getProgress() {
            return progress;
        }

        public float getX() {
            return originX;
        }

        public float getY() {
            return originY;
        }
    }

    /** Monotonic seconds, for ambient loops like pulses and shimmers. */
    public static float time() {
        return (System.nanoTime()-START_NANOS)/1_000_000_000f;
    }

    /**
     * 0..1 sawtooth with the given period in seconds. Wraps rather than reversing, so a
     * highlight driven by it always travels the same way instead of sliding back and forth.
     */
    public static float wrap(float periodSeconds) {
        if (!Float.isFinite(periodSeconds) || periodSeconds<=0) return 0;
        return (float)(((System.nanoTime()-START_NANOS)/1_000_000_000.0 % periodSeconds)/periodSeconds);
    }

    /** 0..1 triangle wave with the given period in seconds. */
    public static float pulse(float periodSeconds) {
        float t = wrap(periodSeconds);
        return t < 0.5f ? t * 2f : (1f - t) * 2f;
    }
}

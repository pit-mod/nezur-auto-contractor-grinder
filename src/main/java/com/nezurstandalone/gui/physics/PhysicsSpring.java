package com.nezurstandalone.gui.physics;

/**
 * Velocity-based spring integrator (iOS-style). Target changes mid-flight preserve momentum —
 * closing while still opening smoothly reverses without snapping.
 *
 * <pre>{@code
 * // Inside GuiScreen.drawScreen:
 * float dt = DeltaTimer.tickSeconds();
 * panelOpenSpring.update(dt);
 * float t = panelOpenSpring.getCurrentValue(); // 0 = closed, 1 = open
 * GlStateManager.scale(t, t, 1f);
 *
 * // Flip target at any time — spring adapts:
 * panelOpenSpring.setTarget(open ? 1f : 0f);
 * }</pre>
 */
public final class PhysicsSpring {

    /** High tension, low friction — elastic pop with slight overshoot. */
    public static final float IOS_BOUNCY_TENSION = 320f;
    public static final float IOS_BOUNCY_FRICTION = 11f;

    /** Medium tension, near-critical damping — smooth, expensive panel motion. */
    public static final float IOS_FLUID_TENSION = 175f;
    public static final float IOS_FLUID_FRICTION = 27f;

    /** Fast snap — close / dismiss overlays. */
    public static final float IOS_SNAPPY_TENSION = 420f;
    public static final float IOS_SNAPPY_FRICTION = 32f;

    private static final float MAX_STEP_SECONDS = 1f / 120f;
    private static final float MAX_FRAME_SECONDS = 0.05f;
    private static final float SETTLE_EPSILON = 0.0005f;

    private float targetValue;
    private float currentValue;
    private float velocity;
    private float tension;
    private float friction;

    public PhysicsSpring(float initialValue, float tension, float friction) {
        this.currentValue = initialValue;
        this.targetValue = initialValue;
        this.tension = tension;
        this.friction = friction;
    }

    public static PhysicsSpring iOSBouncy(float initialValue) {
        return new PhysicsSpring(initialValue, IOS_BOUNCY_TENSION, IOS_BOUNCY_FRICTION);
    }

    public static PhysicsSpring iOSFluid(float initialValue) {
        return new PhysicsSpring(initialValue, IOS_FLUID_TENSION, IOS_FLUID_FRICTION);
    }

    public static PhysicsSpring withPreset(Preset preset, float initialValue) {
        switch (preset) {
            case IOS_BOUNCY:
                return iOSBouncy(initialValue);
            case IOS_SNAPPY:
                return new PhysicsSpring(initialValue, IOS_SNAPPY_TENSION, IOS_SNAPPY_FRICTION);
            case IOS_FLUID:
            default:
                return iOSFluid(initialValue);
        }
    }

    public void setVelocity(float velocity) {
        this.velocity = velocity;
    }

    public void setTarget(float target) {
        this.targetValue = target;
    }

    public void setCurrent(float value) {
        this.currentValue = value;
    }

    public void snapTo(float value) {
        this.currentValue = value;
        this.targetValue = value;
        this.velocity = 0f;
    }

    public void setTension(float tension) {
        this.tension = tension;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }

    public void applyPreset(Preset preset) {
        switch (preset) {
            case IOS_BOUNCY:
                tension = IOS_BOUNCY_TENSION;
                friction = IOS_BOUNCY_FRICTION;
                break;
            case IOS_FLUID:
                tension = IOS_FLUID_TENSION;
                friction = IOS_FLUID_FRICTION;
                break;
            case IOS_SNAPPY:
                tension = IOS_SNAPPY_TENSION;
                friction = IOS_SNAPPY_FRICTION;
                break;
            default:
                break;
        }
    }

    public float getTargetValue() {
        return targetValue;
    }

    public float getCurrentValue() {
        return currentValue;
    }

    public float getVelocity() {
        return velocity;
    }

    public boolean isSettled() {
        float displacement = currentValue - targetValue;
        return Math.abs(displacement) <= SETTLE_EPSILON && Math.abs(velocity) <= SETTLE_EPSILON;
    }

    /**
     * Advances the spring using frame delta time in seconds. Large frames are clamped and
     * sub-stepped so animation speed stays consistent at 30, 60, 144, or variable FPS.
     */
    public void update(float deltaTimeSeconds) {
        if (!Float.isFinite(deltaTimeSeconds) || deltaTimeSeconds <= 0f) {
            return;
        }

        integrate(Math.min(deltaTimeSeconds, 0.25f));

        if (isSettled()) {
            currentValue = targetValue;
            velocity = 0f;
        }
    }

    private void integrate(float dt) {
        // Exact damped-spring solution: the same elapsed time produces the same
        // motion at different frame rates, including when the target reverses.
        double x=currentValue-targetValue, v=velocity;
        double half=Math.max(0,friction)*0.5, k=Math.max(0,tension);
        double discriminant=half*half-k, decay=Math.exp(-half*dt), next, speed;
        if (Math.abs(discriminant)<1e-6) {
            double b=v+half*x;
            next=decay*(x+b*dt);speed=decay*(v-half*b*dt);
        } else if (discriminant<0) {
            double w=Math.sqrt(-discriminant), sin=Math.sin(w*dt), cos=Math.cos(w*dt);
            next=decay*(x*cos+(v+half*x)*sin/w);
            speed=decay*(v*cos-(half*v+k*x)*sin/w);
        } else {
            double root=Math.sqrt(discriminant), r1=-half+root, r2=-half-root;
            double a=(v-r2*x)/(r1-r2), b=x-a;
            double e1=Math.exp(r1*dt), e2=Math.exp(r2*dt);
            next=a*e1+b*e2;speed=r1*a*e1+r2*b*e2;
        }
        currentValue=(float)(targetValue+next);velocity=(float)speed;
    }

    public enum Preset {
        IOS_BOUNCY,
        IOS_FLUID,
        IOS_SNAPPY
    }

    /**
     * Tracks real wall-clock delta between frames for spring updates.
     * Call {@link #tickSeconds()} once per drawScreen / render tick.
     */
    public static final class DeltaTimer {

        private long lastNanos;
        private boolean primed;

        private DeltaTimer() {
        }

        public static DeltaTimer create() {
            return new DeltaTimer();
        }

        public float tickSeconds() {
            long now = System.nanoTime();
            if (!primed) {
                primed = true;
                lastNanos = now;
                return 1f / 60f;
            }
            float dt = (now - lastNanos) / 1_000_000_000f;
            lastNanos = now;
            return dt;
        }

        public void reset() {
            primed = false;
        }
    }
}

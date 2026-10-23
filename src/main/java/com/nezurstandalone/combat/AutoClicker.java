package com.nezurstandalone.combat;

import net.minecraft.client.Minecraft;

import java.util.Random;

/**
 * Shared combat/autogrinder click scheduler. Drivers advance timing separately from deciding
 * whether aim, reach and input ownership permit an intent. Emission uses the configured owner
 * queue or ClickSimulator; the native input gate retains responsibility for dispatch and rate limits.
 *
 * <p>The configured rate can drift within its band, with multiplicative interval jitter and
 * optional short/long intervals. These are scheduling policies, not evidence of human input or
 * anticheat acceptance. Server combat opts into deadline phase correction for the 20 Hz input
 * grid; other callers retain dispatch-relative scheduling. Missed deadlines never queue a backlog.
 */
public final class AutoClicker {
    private com.nezurstandalone.control.Clock clock = com.nezurstandalone.control.Clock.SYSTEM;
    public AutoClicker clock(com.nezurstandalone.control.Clock value) { clock = java.util.Objects.requireNonNull(value); return this; }

    /** Which mouse button this clicker drives. */
    public enum Button {
        LEFT,
        RIGHT
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    // ------------------------------------------------------------------ config

    private Button button = Button.LEFT;
    private Runnable emitter;

    /** Optional owner-specific intent queue; never changes vanilla packet encoding. */
    public AutoClicker emitter(Runnable emitter) {
        this.emitter = emitter;
        return this;
    }

    /** Target CPS band. The live rate wanders inside this via the OU process below. */
    private double minCps = 8.0;
    private double maxCps = 12.0;

    /**
     * Spread of per-click log-normal interval jitter, in natural-log units.
     */
    private double jitterSigma = 0.18;

    /** Configured floor on the sampled interval in milliseconds. */
    private long minIntervalMs = 46;

    /** Per-click chance the next gap collapses into a double-tap burst. */
    private double doubleClickChance = 0.04;
    private long burstIntervalMinMs = 46;
    private long burstIntervalMaxMs = 78;

    /** Per-click chance of an attention-lapse pause, and how long it runs. */
    private double pauseChance = 0.015;
    private long pauseMinMs = 320;
    private long pauseMaxMs = 850;

    /** Reaction time before the first click after the gate opens. Log-normal about the median. */
    private double reactionMedianMs = 190.0;
    private double reactionSigma = 0.30;
    private long reactionMinMs = 110;
    private long reactionMaxMs = 380;
    private boolean reactionDelay = true;
    private boolean nativeTickCadence;

    /** Opt-in for a held combat trigger: aim/reach validation remains the emitter's responsibility. */
    public AutoClicker reactionDelay(boolean enabled) { reactionDelay = enabled; return this; }
    /** Preserve deadline phase across the native 20 Hz input grid without accumulating missed clicks. */
    public AutoClicker nativeTickCadence(boolean enabled) { nativeTickCadence = enabled; return this; }

    /**
     * At most one queued attack per client tick. Server ticks and packet arrival times can
     * differ; the native budget separately controls bursts using actual elapsed time.
     */
    private boolean oneClickPerTick = true;

    // ------------------------------------------------------------------- state

    /** OU rate state, in CPS. NaN until the first active frame seeds it from the band mean. */
    private double liveCps = Double.NaN;
    private static final double OU_THETA = 0.9;   // reversion strength, per second
    private static final double OU_SIGMA = 1.6;   // drive, CPS per sqrt(second)

    private boolean gateOpen;
    private long nextClickAtMs;
    private long lastFrameMs;
    private int lastEmitTick = Integer.MIN_VALUE;

    // ------------------------------------------------------------------ config API

    public AutoClicker button(Button b) {
        this.button = b;
        return this;
    }

    /** Maximum supported configured rate, shared with the native action budget. */
    public static final double MAX_CPS = 14.0;

    /** Sets the CPS band the live rate wanders inside. Order-insensitive; both must be &gt; 0.
     *  Clamped to {@link #MAX_CPS}. */
    public AutoClicker cps(double min, double max) {
        double oldMin = this.minCps, oldMax = this.maxCps;
        this.minCps = Math.max(0.1, Math.min(min, max));
        this.maxCps = Math.min(MAX_CPS, Math.max(0.1, Math.max(min, max)));
        if (this.minCps > this.maxCps) this.minCps = this.maxCps;
        // A band change invalidates the current wander position; reseed on the next frame.
        if (oldMin != this.minCps || oldMax != this.maxCps) this.liveCps = Double.NaN;
        return this;
    }

    public AutoClicker jitterSigma(double sigma) {
        this.jitterSigma = Math.max(0.0, sigma);
        return this;
    }

    public AutoClicker minInterval(long ms) {
        this.minIntervalMs = Math.max(1, ms);
        return this;
    }

    public AutoClicker doubleClickChance(double chance) {
        this.doubleClickChance = clamp01(chance);
        return this;
    }

    public AutoClicker pauseChance(double chance) {
        this.pauseChance = clamp01(chance);
        return this;
    }

    public AutoClicker reaction(double medianMs, double sigma) {
        this.reactionMedianMs = Math.max(1.0, medianMs);
        this.reactionSigma = Math.max(0.0, sigma);
        return this;
    }

    public AutoClicker oneClickPerTick(boolean on) {
        this.oneClickPerTick = on;
        return this;
    }

    // ------------------------------------------------------------------ driving

    /**
     * Pump the clicker for one render frame.
     *
     * <p>{@code allowed} combines the driver's engagement and emission gates. Drivers that
     * track outside reach use the two-argument overload to advance timing without emitting.
     * Actual server combat dispatch remains in the native input phase.
     */
    public void onRenderFrame(boolean allowed) {
        onRenderFrame(allowed, allowed);
    }

    /** Keep a tracking trigger armed through brief aim/range gaps without consuming a click. */
    public void onRenderFrame(boolean armed, boolean mayEmit) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }

        long now = clock.nanos() / 1_000_000L;
        long elapsed = lastFrameMs == 0 ? 0 : now - lastFrameMs;
        double dt = Math.min(0.1, elapsed / 1000.0);
        lastFrameMs = now;

        if (!armed) {
            // Releasing the trigger clears the schedule; the next engagement starts fresh.
            gateOpen = false;
            return;
        }

        if (!gateOpen) {
            // Gate just opened: seed the reaction delay before the first click.
            gateOpen = true;
            nextClickAtMs = now + (reactionDelay ? sampleLogNormalMs(reactionMedianMs, reactionSigma,
                    reactionMinMs, reactionMaxMs) : 0);
        }

        advanceRate(dt);
        if (nativeTickCadence && elapsed > 250 && nextClickAtMs < now) nextClickAtMs = now;

        if (!mayEmit || now < nextClickAtMs) {
            return;
        }

        if (oneClickPerTick) {
            int tick = mc.thePlayer.ticksExisted;
            if (tick == lastEmitTick) {
                // A gap came due twice inside one client tick. Hold the second until the tick
                // rolls, so this scheduler queues at most one intent per client tick.
                return;
            }
            lastEmitTick = tick;
        }

        long scheduled = nextClickAtMs;
        emitClick();
        long gap = Math.max((long) Math.ceil(1000.0 / maxCps), nextGapMs());
        // Rounding every interval from its dispatch time repeatedly discards the tick-grid remainder.
        // Carry less than one native tick of phase, while always putting the next deadline in the future.
        // Clamp excess lateness rather than dropping the whole remainder after a pacing/raycast gap.
        long basis = nativeTickCadence ? Math.max(scheduled, now - Math.min(49, gap - 1)) : now;
        nextClickAtMs = basis + gap;
    }

    /** Convenience: gate on and pump in one call. */
    public void onRenderFrame() {
        onRenderFrame(true);
    }

    /** Drops all timing state. The next active frame starts clean, reaction delay and all. */
    public void reset() {
        gateOpen = false;
        liveCps = Double.NaN;
        nextClickAtMs = 0;
        lastFrameMs = 0;
        lastEmitTick = Integer.MIN_VALUE;
    }

    public boolean isGateOpen() {
        return gateOpen;
    }

    /** The rate the clicker is currently wandering at, CPS, or the band mean before it seeds. */
    public double currentCps() {
        return Double.isNaN(liveCps) ? (minCps + maxCps) / 2.0 : liveCps;
    }

    // ------------------------------------------------------------------ internals

    /**
     * Advances the Ornstein–Uhlenbeck rate one frame and keeps it inside the band by reflection.
     * Reflection keeps ordinary samples moving through the configured band instead of
     * accumulating them at its boundaries.
     */
    private void advanceRate(double dt) {
        double mean = (minCps + maxCps) / 2.0;
        if (Double.isNaN(liveCps)) {
            liveCps = mean;
            return;
        }
        if (dt <= 0.0) {
            return;
        }
        double next = liveCps
                + OU_THETA * (mean - liveCps) * dt
                + OU_SIGMA * random.nextGaussian() * Math.sqrt(dt);

        if (next < minCps) {
            next = minCps + (minCps - next);
        } else if (next > maxCps) {
            next = maxCps - (next - maxCps);
        }
        // A violent sample could reflect back out the far side; clamp as a backstop only.
        liveCps = Math.max(minCps, Math.min(maxCps, next));
    }

    /** The gap to wait after a click before the next one is allowed, ms. */
    private long nextGapMs() {
        // Attention lapse: a rare long pause, sampled independently of the rate.
        if (random.nextDouble() < pauseChance) {
            return randomBetween(pauseMinMs, pauseMaxMs);
        }
        // Double-tap: the next click rides in close behind this one.
        if (random.nextDouble() < doubleClickChance) {
            return randomBetween(burstIntervalMinMs, burstIntervalMaxMs);
        }

        double cps = Double.isNaN(liveCps) ? (minCps + maxCps) / 2.0 : liveCps;
        double baseMs = 1000.0 / cps;
        // Log-normal jitter: multiplicative, right-skewed, never negative.
        double gap = baseMs * Math.exp(random.nextGaussian() * jitterSigma);
        return Math.max(minIntervalMs, Math.round(gap));
    }

    /**
     * Queues one intent for native dispatch. This scheduler does not send packets directly.
     */
    private void emitClick() {
        if (emitter != null) {
            emitter.run();
            return;
        }
        if (mc.gameSettings == null) {
            return;
        }
        com.nezurstandalone.input.ClickSimulator.pulse(this,button==Button.LEFT);
    }

    private long randomBetween(long minMs, long maxMs) {
        if (maxMs <= minMs) {
            return minMs;
        }
        return minMs + (long) (random.nextDouble() * (maxMs - minMs));
    }

    private long sampleLogNormalMs(double medianMs, double sigma, long min, long max) {
        double value = medianMs * Math.exp(random.nextGaussian() * sigma);
        return (long) Math.max(min, Math.min(max, value));
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}

package com.nezurstandalone.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import java.util.Random;

/**
 * A human-shaped click source for the combat / autogrinder system.
 *
 * <p>Not a module and not on any event bus: it holds no state the game owns and registers
 * nothing. A driver (the future combat module) constructs one, configures it, and pumps it
 * once per render frame with a gate that says whether clicking is warranted this instant. The
 * clicker owns only the <em>timing</em> — when a click may leave — and emits it through the
 * exact vanilla input path a real click takes.
 *
 * <h2>Why the emission path matters</h2>
 * A click is delivered by {@link KeyBinding#onTick(int)} on the bound attack (or use) key.
 * That is the same call the game makes when it polls the keyboard/mouse: it queues one press
 * that vanilla drains in {@code runTick} as an ordinary click, producing an ordinary swing and
 * an ordinary attack packet. There is no synthetic packet, no reflection into the network
 * handler, nothing that looks different on the wire from a mouse the player pressed. Everything
 * clever here is about <em>when</em> that call happens, never about faking what it produces.
 *
 * <h2>What "human-shaped" means, concretely</h2>
 * A naive autoclicker is caught not because a click looks wrong but because the <em>sequence</em>
 * of inter-click gaps does. Four properties separate a person from a loop, and this models all
 * four rather than only the first:
 *
 * <ol>
 *   <li><b>The rate drifts.</b> A person speeds up and slows down over seconds; they do not hold
 *       a fixed CPS. The base rate is an Ornstein–Uhlenbeck process — a mean-reverting random
 *       walk — so it wanders smoothly and is autocorrelated in time, the way a hand is, instead
 *       of being redrawn independently every click (which is white noise and separable from a
 *       hand by its flat spectrum).</li>
 *   <li><b>The gaps are log-normal, not Gaussian.</b> Human motor intervals are right-skewed:
 *       many gaps near the mode, a tail of longer ones, and a hard floor no one beats. A
 *       symmetric Gaussian has the wrong shape and can even propose negative gaps. Each interval
 *       is the base period times {@code exp(N(0, sigma))}.</li>
 *   <li><b>Doubles happen.</b> Real clicking occasionally fires two taps almost together — a
 *       physical double-tap. A small per-click chance collapses the next gap to a burst
 *       interval, which is what puts the short-tail mass a person's histogram has and a metronome
 *       never does.</li>
 *   <li><b>Attention lapses.</b> Every so often a gap is much longer than the rate would predict
 *       — a glance away, a readjustment. A small chance of a long pause supplies the long tail.</li>
 * </ol>
 *
 * <p>On top of that: engaging a fresh target costs a reaction delay before the first click (a
 * person does not click the frame a target enters reach), releasing the gate clears the schedule
 * so clicking actually stops, and a hard per-server-tick clamp guarantees at most one attack is
 * queued per game tick — two hits in one tick is not something a hand can do and is a clean flag.
 *
 * <h2>Honest limits</h2>
 * This makes the click stream statistically hard to separate from a human's by the usual
 * interval heuristics. It is not, and cannot be, "provably undetected": a server that correlates
 * clicks with server-authoritative aim, or simply rate-limits, is a different problem that timing
 * shape does not address. Treat this as one well-built layer, not a guarantee.
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
     * Spread of the per-click log-normal jitter, in natural-log units. 0.18 gives a coefficient
     * of variation on the gaps of roughly 18% — squarely in the range measured off real players,
     * loose enough not to read as a metronome, tight enough not to read as random noise.
     */
    private double jitterSigma = 0.18;

    /** Hard floor on the gap between two clicks, ms. Nothing a hand does beats this. */
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

    /**
     * At most one queued attack per server tick. Left on for combat: two attacks in a single
     * 50 ms tick is physically impossible for a mouse and a textbook flag. A pure block/place
     * clicker with no combat meaning can turn it off.
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

    /** Sustained human ceiling. Sliders can be dragged past it; the emitted rate cannot. */
    public static final double MAX_CPS = 14.0;

    /** Sets the CPS band the live rate wanders inside. Order-insensitive; both must be &gt; 0.
     *  Clamped to {@link #MAX_CPS} so no slider can configure a superhuman sustained rate. */
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
     * <p>Called every frame, sub-tick, so the emission time can land anywhere inside a game tick
     * rather than being quantised to the 20 Hz client tick — a click train pinned to tick
     * boundaries is itself a signature. {@code allowed} is the driver's gate: true only while a
     * click is genuinely warranted (a target is in reach, a weapon is held, the screen is closed,
     * whatever the combat module decides). The clicker never decides <em>whether</em> to fight,
     * only the timing of the clicks once told it may.
     */
    public void onRenderFrame(boolean allowed) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }

        long now = clock.nanos() / 1_000_000L;
        double dt = lastFrameMs == 0 ? 0.0 : Math.min(0.1, (now - lastFrameMs) / 1000.0);
        lastFrameMs = now;

        if (!allowed) {
            // Releasing the gate has to actually stop the hand. Clearing the schedule means the
            // next engagement pays a fresh reaction delay instead of firing instantly on a gap
            // that was already due — which is both correct behaviour and a thing a person does.
            gateOpen = false;
            return;
        }

        if (!gateOpen) {
            // Gate just opened: seed the reaction delay before the first click.
            gateOpen = true;
            nextClickAtMs = now + sampleLogNormalMs(reactionMedianMs, reactionSigma,
                    reactionMinMs, reactionMaxMs);
        }

        advanceRate(dt);

        if (now < nextClickAtMs) {
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

        emitClick();
        nextClickAtMs = now + Math.max((long) Math.ceil(1000.0 / maxCps), nextGapMs());
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
     * Reflecting rather than clamping matters: a hard clamp would let the rate sit pinned to a
     * bound for a stretch, which shows up as a flat run in the CPS trace; reflection turns it
     * around and keeps the walk moving the way a real one does.
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
     * Queues exactly one press on the bound button through the vanilla input path. Nothing here
     * touches the network directly — the queued press becomes a normal click, swing and attack
     * packet inside {@code runTick}, identical to a hardware press.
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

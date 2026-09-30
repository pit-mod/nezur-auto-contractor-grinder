package com.nezurstandalone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The single writer of player rotation.
 *
 * <p>Every angle this class produces leaves through {@code EntityPlayerSP.setAngles} as an
 * <em>integer</em> mouse delta scaled by vanilla's sensitivity curve, so the emitted rotation
 * is always a whole multiple of the client's mouselook GCD. Nothing else in the mod may
 * assign {@code rotationYaw} / {@code rotationPitch} directly; doing so produces deltas that
 * no physical mouse could have generated.
 *
 * <h2>Motion model</h2>
 * Aim is <em>ballistic</em>, not exponential. Each re-aim plans one minimum-jerk submovement
 *
 * <pre>s(tau) = 10*tau^3 - 15*tau^4 + 6*tau^5,   tau = t / T</pre>
 *
 * whose duration {@code T} comes from a Fitts model of the angular distance. Velocity is
 * therefore zero at both ends and peaks mid-flight, which is the opposite of the previous
 * {@code diff * dt * speed} decay: that had maximum velocity at onset, infinite jerk at
 * t = 0, and never overshot. Yaw and pitch run on one clock but <em>different durations</em>,
 * so the axes desynchronise and the path traced in (yaw, pitch) space is curved instead of a
 * perfectly straight line. The primary submovement deliberately misses by 5-15%; a shorter
 * corrective submovement then closes the residual, reproducing the two-phase structure of
 * human aim.
 *
 * <h2>Plans are immutable once started</h2>
 * A plan is never retargeted mid-flight. Mutating the endpoint of an in-flight minimum-jerk
 * curve injects a velocity step proportional to how far the target moved, which is exactly
 * the snap this model exists to remove. Small target drift is absorbed by the corrective
 * submovement that follows; only a jump larger than {@link #REPLAN_JUMP_DEG} aborts the plan
 * and starts a fresh one, which is what a person does when a target moves far enough to be
 * worth re-aiming at.
 *
 * <h2>Requests</h2>
 * Callers publish a <em>request</em> rather than latching a target. Requests carry a priority
 * and expire {@link #REQUEST_TTL_MS} ms after they were last refreshed, so a module that
 * simply stops asking releases the camera automatically. The old latch had no such release: a
 * consumer that returned early without calling {@code clearTarget()} left the camera easing
 * toward a dead target indefinitely, drifting and jittering while the player stood still.
 */
public class RotationManager {

    // ------------------------------------------------------------------ owners

    /** Shared slot for callers using the legacy three-argument entry point. */
    public static final String OWNER_LEGACY = "legacy";
    public static final String OWNER_COMBAT = "camera.combat";
    public static final String OWNER_CHEST = "camera.chest";
    public static final String OWNER_BOW = "camera.bow";
    public static final String OWNER_MACRO = "camera.macro";

    public static final int PRIORITY_LEGACY = 0;
    public static final int PRIORITY_COMBAT = 25;
    public static final int PRIORITY_CHEST = 30;
    public static final int PRIORITY_BOW = 45;
    public static final int PRIORITY_MACRO = 50;

    // ------------------------------------------------------------------ tuning

    /** A request is dropped this long after its last refresh. Two client ticks plus slack. */
    private static final long REQUEST_TTL_MS = 150L;

    /** Below this the aim counts as on-target and the idle model takes over. */
    private static final float DEAD_ZONE_DEG = 0.5f;

    /** Hard ceiling on combined angular speed. Human flicks peak well under this. */
    private static final double MAX_ANGULAR_VELOCITY = 1200.0;

    /** Fitts model: T = a + b * log2(D / W + 1), seconds. */
    private static final double FITTS_A = 0.09;
    private static final double FITTS_B = 0.075;
    private static final double FITTS_W = 1.5;
    private static final double MIN_MOVEMENT_TIME = 0.055;
    private static final double MAX_MOVEMENT_TIME = 0.90;

    /** Request speed that maps to the unscaled Fitts duration. */
    private static final float BASELINE_SPEED = 10.0f;

    /** Target drift larger than this aborts the plan in flight instead of waiting it out. */
    private static final double REPLAN_JUMP_DEG = 8.0;
    /** Replan threshold for a responsive (combat) request: follow a moving target aggressively. */
    private static final double RESPONSIVE_REPLAN_DEG = 1.25;

    /** Pitch settles at 0.7-0.9x the yaw duration, which is what curves the 2D aim path. */
    private static final double PITCH_DURATION_MIN = 0.70;
    private static final double PITCH_DURATION_MAX = 0.90;

    private static final double OVERSHOOT_MIN = 0.05;
    private static final double OVERSHOOT_MAX = 0.15;
    /** Fraction of primary submovements that overshoot rather than undershoot. */
    private static final double OVERSHOOT_BIAS = 0.6;

    private static final double CORRECTIVE_DURATION_SCALE = 0.30;

    /** Physiological tremor: ~10 Hz correlation time, 0.15 degree standard deviation. */
    private static final double TREMOR_SIGMA = 0.15;
    private static final double TREMOR_TAU = 0.10;

    private static final long IDLE_QUIET_MIN_MS = 300L;
    private static final long IDLE_QUIET_MAX_MS = 3000L;
    private static final long IDLE_TREMOR_MIN_MS = 400L;
    private static final long IDLE_TREMOR_MAX_MS = 2000L;

    /** Post-GUI re-orientation delay, log-normal about 210 ms. */
    private static final double GUI_RESUME_MEDIAN_MS = 210.0;
    private static final double GUI_RESUME_SIGMA = 0.35;
    private static final long GUI_RESUME_MIN_MS = 140L;
    private static final long GUI_RESUME_MAX_MS = 400L;

    // ------------------------------------------------------------------ state

    private Object rotationWorld, rotationPlayer;
    private static final RotationManager INSTANCE = new RotationManager();

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    /** One entry per owner; tiny and reused, so the render frame allocates nothing. */
    private final Map<String, Request> requests = new HashMap<String, Request>();

    private long requestOrder;
    private long lastRenderTime = 0;

    // Fractional mouse pixels carried between frames. Without this a high frame rate rounds
    // every delta to zero and the camera never moves at all.
    private float yawAccumulator = 0;
    private float pitchAccumulator = 0;

    // Active submovement.
    private boolean planActive = false;
    private boolean planIsCorrective = false;
    private double planElapsed = 0;
    private double planDurationYaw = 0;
    private double planDurationPitch = 0;
    private double planTotalYaw = 0;
    private double planTotalPitch = 0;
    private double planEmittedYaw = 0;
    private double planEmittedPitch = 0;
    private float planAimYaw = 0;
    private float planAimPitch = 0;

    // Tremor and idle model.
    private double tremorYaw = 0;
    private double tremorPitch = 0;
    private double tremorEmittedYaw = 0;
    private double tremorEmittedPitch = 0;
    private boolean idleQuiet = true;
    private long idlePhaseEndsAt = 0;

    /** Set while a screen is open; the camera stays still until this passes. */
    private long resumeAtMs = 0;

    private static final class Request {
        String owner;
        long generation, order;
        int priority;
        float yaw;
        float pitch;
        float speed;
        long expiresAt;
        boolean responsive;
    }

    public RotationManager() {
    }

    public static RotationManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new com.nezurstandalone.control.Lifecycle());
    }

    // ------------------------------------------------------------------ requests

    /** Legacy entry point: shared slot, lowest priority, last writer within a tick wins. */
    public void setTargetRotation(float yaw, float pitch, float speedMultiplier) {
        setTargetRotation(OWNER_LEGACY, PRIORITY_LEGACY, yaw, pitch, speedMultiplier);
    }

    /**
     * Publishes an aim request. Refresh it every tick (or frame) for as long as it applies; it
     * lapses on its own {@value #REQUEST_TTL_MS} ms after the last refresh.
     *
     * @param speedMultiplier aggression scalar; {@code 10} is unscaled human pace and larger
     *                        is faster. Clamped to a physically plausible band.
     */
    public void setTargetRotation(String owner, int priority, float yaw, float pitch, float speedMultiplier) {
        setTargetRotation(owner, priority, yaw, pitch, speedMultiplier, false);
    }

    /**
     * As above, but {@code responsive} makes the aim track a moving target hard: it replans on the
     * slightest drift and lands exactly on target rather than deliberately missing and correcting.
     * Combat uses it - a person tracking an opponent in melee is fast and direct, not leisurely.
     */
    public void setTargetRotation(String owner, int priority, float yaw, float pitch,
                                  float speedMultiplier, boolean responsive) {
        if (owner == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || !Float.isFinite(speedMultiplier)) {
            if (owner != null) clearTarget(owner);
            return;
        }
        Request request = requests.get(owner);
        if (request == null) {
            request = new Request();
            requests.put(owner, request);
        }
        request.owner = owner;
        request.generation++;
        request.order = ++requestOrder;
        request.priority = priority;
        request.yaw = yaw;
        request.pitch = MathHelper.clamp_float(pitch, -89.9f, 89.9f);
        request.speed = MathHelper.clamp_float(speedMultiplier, 4.0f, 25.0f);
        request.expiresAt = com.nezurstandalone.control.Clock.millis() + REQUEST_TTL_MS;
        request.responsive = responsive;
    }

    public void setTargetEntity(Entity entity, float speedMultiplier) {
        float[] rots = RotationUtils.getRotations(entity);
        setTargetRotation(rots[0], rots[1], speedMultiplier);
    }

    /** Releases the legacy slot. Other owners are untouched; use {@link #clearTarget(String)}. */
    public void clearTarget() {
        clearTarget(OWNER_LEGACY);
    }

    public void clearTarget(String owner) {
        Request request = requests.get(owner);
        if (request != null) {
            request.expiresAt = 0;
        }
    }

    public void clearAll() {
        for (Request request : requests.values()) {
            request.expiresAt = 0;
        }
        abortPlan();
    }

    // ------------------------------------------------------------------ driver

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (rotationWorld != mc.theWorld || rotationPlayer != mc.thePlayer) {
            clearAll();
            lastRenderTime = 0;
            rotationWorld = mc.theWorld;
            rotationPlayer = mc.thePlayer;
        }

        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead || mc.currentScreen != null) {
            // A screen is up: the cursor belongs to the GUI, so the camera must be still.
            // Coming back out is not instantaneous either - sample a re-orientation delay
            // rather than resuming on the very next frame.
            lastRenderTime = 0;
            if (mc.currentScreen != null) {
                resumeAtMs = com.nezurstandalone.control.Clock.millis()
                        + sampleLogNormalMs(GUI_RESUME_MEDIAN_MS, GUI_RESUME_SIGMA,
                        GUI_RESUME_MIN_MS, GUI_RESUME_MAX_MS);
            }
            abortPlan();
            return;
        }

        long currentTime = com.nezurstandalone.control.Clock.millis();
        if (lastRenderTime == 0) {
            lastRenderTime = currentTime;
            return;
        }
        double dt = (currentTime - lastRenderTime) / 1000.0;
        lastRenderTime = currentTime;
        if (dt > 0.1) dt = 0.1;
        if (dt <= 0) return;

        if (currentTime < resumeAtMs) {
            return;
        }

        Request active = resolveRequest(currentTime);
        if (active == null) {
            abortPlan();
            return;
        }

        double deltaYaw = 0;
        double deltaPitch = 0;

        float errorYaw = MathHelper.wrapAngleTo180_float(active.yaw - mc.thePlayer.rotationYaw);
        float errorPitch = active.pitch - mc.thePlayer.rotationPitch;

        if (active.responsive) {
            // Sustained proportional (exponential) follow, used for combat tracking. The min-jerk
            // plan below restarts from zero velocity on every replan, so against a target whose
            // relative angle moves fast - which is exactly what happens when the *player* strafes,
            // not only when the target does - it keeps starting from rest and lags behind. This
            // path instead closes a fixed fraction of the remaining error each frame, so it carries
            // velocity frame to frame and stays glued to the target whether it or the player moves.
            planActive = false; // drop any ballistic plan; accumulators are left intact for emit()

            double gain = clamp01(active.speed / 25.0) * 0.5 + 0.10;   // 0.10 .. 0.60 per 60fps frame
            double f = 1.0 - Math.pow(1.0 - gain, dt * 60.0);          // frame-rate normalised
            double stepYaw = errorYaw * f;
            double stepPitch = errorPitch * f;

            double step = Math.sqrt(stepYaw * stepYaw + stepPitch * stepPitch);
            double maxStep = MAX_ANGULAR_VELOCITY * dt;
            if (step > maxStep && step > 0) {
                double scale = maxStep / step;
                stepYaw *= scale;
                stepPitch *= scale;
            }

            double outYaw = stepYaw;
            double outPitch = stepPitch;
            if (!isIdleQuiet(currentTime)) {
                advanceTremor(dt);
                outYaw += tremorYaw - tremorEmittedYaw;
                outPitch += tremorPitch - tremorEmittedPitch;
                tremorEmittedYaw = tremorYaw;
                tremorEmittedPitch = tremorPitch;
            }
            emit(outYaw, outPitch);
            return;
        }

        if (planActive) {
            // A large target move is a genuine re-aim, not drift worth waiting out.
            double driftYaw = MathHelper.wrapAngleTo180_float(active.yaw - planAimYaw);
            double driftPitch = active.pitch - planAimPitch;
            double replanThresh = active.responsive ? RESPONSIVE_REPLAN_DEG : REPLAN_JUMP_DEG;
            if (Math.sqrt(driftYaw * driftYaw + driftPitch * driftPitch) > replanThresh) {
                beginPlan(errorYaw, errorPitch, active, false);
            }
        } else if (Math.abs(errorYaw) >= DEAD_ZONE_DEG || Math.abs(errorPitch) >= DEAD_ZONE_DEG) {
            beginPlan(errorYaw, errorPitch, active, false);
        }

        if (planActive) {
            planElapsed += dt;

            double tauYaw = planDurationYaw <= 0 ? 1.0 : clamp01(planElapsed / planDurationYaw);
            double tauPitch = planDurationPitch <= 0 ? 1.0 : clamp01(planElapsed / planDurationPitch);

            double wantYaw = planTotalYaw * minimumJerk(tauYaw) - planEmittedYaw;
            double wantPitch = planTotalPitch * minimumJerk(tauPitch) - planEmittedPitch;

            // Velocity ceiling. Clamping the emitted step rather than the plan keeps the
            // landing point exact: whatever is withheld here is still owed at tau = 1.
            double step = Math.sqrt(wantYaw * wantYaw + wantPitch * wantPitch);
            double maxStep = MAX_ANGULAR_VELOCITY * dt;
            if (step > maxStep && step > 0) {
                double scale = maxStep / step;
                wantYaw *= scale;
                wantPitch *= scale;
            }

            planEmittedYaw += wantYaw;
            planEmittedPitch += wantPitch;
            deltaYaw += wantYaw;
            deltaPitch += wantPitch;

            boolean timeUp = tauYaw >= 1.0 && tauPitch >= 1.0;
            boolean landed = Math.abs(planTotalYaw - planEmittedYaw) < 0.01
                    && Math.abs(planTotalPitch - planEmittedPitch) < 0.01;
            if (timeUp && landed) {
                finishPlan(active);
            }
        }

        // Tremor rides on top of whatever the plan is doing, and is the only thing moving the
        // camera once the aim has settled. Frozen outright during an idle quiet phase.
        if (!isIdleQuiet(currentTime)) {
            advanceTremor(dt);
            deltaYaw += tremorYaw - tremorEmittedYaw;
            deltaPitch += tremorPitch - tremorEmittedPitch;
            tremorEmittedYaw = tremorYaw;
            tremorEmittedPitch = tremorPitch;
        }

        emit(deltaYaw, deltaPitch);
    }

    private Request resolveRequest(long now) {
        Request best = null;
        for (Request request : requests.values()) {
            if (request.expiresAt <= now) continue;
            if (best == null || com.nezurstandalone.control.RotationOrder.before(request.priority, request.owner, best.priority, best.owner)) {
                best = request;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ planning

    /** {@code s(tau) = 10tau^3 - 15tau^4 + 6tau^5} - zero velocity and acceleration at both ends. */
    private static double minimumJerk(double tau) {
        if (tau <= 0) return 0;
        if (tau >= 1) return 1;
        double t3 = tau * tau * tau;
        return t3 * (10.0 + tau * (-15.0 + 6.0 * tau));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private void beginPlan(double errorYaw, double errorPitch, Request request, boolean corrective) {
        double distance = Math.sqrt(errorYaw * errorYaw + errorPitch * errorPitch);
        if (distance < 1.0e-4) {
            abortPlan();
            return;
        }

        double duration;
        if (corrective) {
            duration = Math.max(MIN_MOVEMENT_TIME, planDurationYaw * CORRECTIVE_DURATION_SCALE);
            planTotalYaw = errorYaw;
            planTotalPitch = errorPitch;
        } else {
            duration = fittsDuration(distance, request.speed);
            if (request.responsive) {
                // Land exactly on target: tracking wants directness, not the human miss-and-fix.
                planTotalYaw = errorYaw;
                planTotalPitch = errorPitch;
            } else {
                // Deliberate endpoint error, closed afterwards by the corrective submovement.
                double miss = OVERSHOOT_MIN + random.nextDouble() * (OVERSHOOT_MAX - OVERSHOOT_MIN);
                double factor = 1.0 + (random.nextDouble() < OVERSHOOT_BIAS ? miss : -miss);
                planTotalYaw = errorYaw * factor;
                planTotalPitch = errorPitch * factor;
            }
        }

        // Honour the velocity ceiling by stretching the plan rather than clipping it: peak
        // minimum-jerk velocity is 1.875 * D / T.
        double planned = Math.sqrt(planTotalYaw * planTotalYaw + planTotalPitch * planTotalPitch);
        duration = Math.max(duration, 1.875 * planned / MAX_ANGULAR_VELOCITY);
        duration = Math.max(MIN_MOVEMENT_TIME, Math.min(MAX_MOVEMENT_TIME, duration));

        planActive = true;
        planIsCorrective = corrective;
        planElapsed = 0;
        planEmittedYaw = 0;
        planEmittedPitch = 0;
        planDurationYaw = duration;
        planDurationPitch = duration
                * (PITCH_DURATION_MIN + random.nextDouble() * (PITCH_DURATION_MAX - PITCH_DURATION_MIN));
        planAimYaw = request.yaw;
        planAimPitch = request.pitch;
    }

    private double fittsDuration(double distanceDeg, float speed) {
        double bits = Math.log(distanceDeg / FITTS_W + 1.0) / Math.log(2.0);
        return (FITTS_A + FITTS_B * bits) * (BASELINE_SPEED / speed);
    }

    /**
     * A primary submovement that lands outside the dead zone earns exactly one corrective
     * submovement; anything still outstanding after that is treated as a fresh re-aim.
     */
    private void finishPlan(Request request) {
        float residualYaw = MathHelper.wrapAngleTo180_float(request.yaw - mc.thePlayer.rotationYaw);
        float residualPitch = request.pitch - mc.thePlayer.rotationPitch;
        boolean offTarget = Math.abs(residualYaw) >= DEAD_ZONE_DEG
                || Math.abs(residualPitch) >= DEAD_ZONE_DEG;

        if (offTarget && !planIsCorrective) {
            beginPlan(residualYaw, residualPitch, request, true);
            return;
        }

        planActive = false;
        planIsCorrective = false;
        if (!offTarget) {
            // Settled: hand over to the idle model rather than continuing to nudge.
            armIdlePhase(com.nezurstandalone.control.Clock.millis(), true);
        }
    }

    private void abortPlan() {
        planActive = false;
        planIsCorrective = false;
        planElapsed = 0;
        planEmittedYaw = 0;
        planEmittedPitch = 0;
        yawAccumulator = 0;
        pitchAccumulator = 0;
    }

    // ------------------------------------------------------------------ tremor / idle

    /**
     * Ornstein-Uhlenbeck tremor. The old model drew an independent Gaussian every frame, which
     * is white noise: flat spectrum, zero autocorrelation, and separable from a human hand by
     * inspection in the frequency domain. This is a low-pass process with a
     * {@value #TREMOR_TAU}s correlation time and a stationary standard deviation of
     * {@value #TREMOR_SIGMA} degrees, comfortably inside the dead zone so it can never keep
     * the aim from settling the way the old 0.5 degree jitter did.
     */
    private void advanceTremor(double dt) {
        double alpha = Math.exp(-dt / TREMOR_TAU);
        double drive = TREMOR_SIGMA * Math.sqrt(1.0 - alpha * alpha);
        tremorYaw = tremorYaw * alpha + random.nextGaussian() * drive;
        tremorPitch = tremorPitch * alpha + random.nextGaussian() * drive;
    }

    /**
     * Alternates dead-still holds with bursts of tremor. A hand resting on a mouse emits
     * genuinely zero counts for seconds at a time; continuous micro-movement around a fixed
     * point, which is what the old dead zone plus jitter produced, is the artefact this
     * replaces.
     */
    private boolean isIdleQuiet(long now) {
        if (planActive) {
            return false;
        }
        if (now >= idlePhaseEndsAt) {
            armIdlePhase(now, !idleQuiet);
        }
        return idleQuiet;
    }

    private void armIdlePhase(long now, boolean quiet) {
        idleQuiet = quiet;
        long span = quiet
                ? IDLE_QUIET_MIN_MS + (long) (random.nextDouble() * (IDLE_QUIET_MAX_MS - IDLE_QUIET_MIN_MS))
                : IDLE_TREMOR_MIN_MS + (long) (random.nextDouble() * (IDLE_TREMOR_MAX_MS - IDLE_TREMOR_MIN_MS));
        idlePhaseEndsAt = now + span;
        if (quiet) {
            // Freeze the tremor where it stands so resuming does not emit a catch-up jump.
            tremorEmittedYaw = tremorYaw;
            tremorEmittedPitch = tremorPitch;
        }
    }

    private long sampleLogNormalMs(double medianMs, double sigma, long min, long max) {
        double value = medianMs * Math.exp(random.nextGaussian() * sigma);
        return (long) Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------ output

    /**
     * Converts an angular step into whole mouse pixels and applies it through the exact
     * vanilla path. Fractions are carried across frames so a high frame rate cannot round
     * every step to zero.
     */
    private void emit(double angleYaw, double anglePitch) {
        if (angleYaw == 0 && anglePitch == 0) return;

        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float f1 = f * f * f * 8.0F;
        if (f1 <= 0) return;

        // Pitch is subtracted inside setAngles, hence the negative divisor.
        yawAccumulator += (float) (angleYaw / (f1 * 0.15f));
        pitchAccumulator += (float) (anglePitch / (f1 * -0.15f));

        int deltaX = (int) yawAccumulator;
        int deltaY = (int) pitchAccumulator;
        yawAccumulator -= deltaX;
        pitchAccumulator -= deltaY;

        if (deltaX != 0 || deltaY != 0) {
            mc.thePlayer.setAngles((float) deltaX * f1, (float) deltaY * f1);
        }
    }
}

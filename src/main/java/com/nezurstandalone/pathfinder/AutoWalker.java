package com.nezurstandalone.pathfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.settings.KeyBinding;
import com.nezurstandalone.utils.NotificationManager;

import java.util.List;

public final class AutoWalker {
    private AutoWalker(){}
    public static final AutoWalker INSTANCE = new AutoWalker();
    private boolean active = false;
    private static final String PATH_ROTATION_OWNER = "walker.path";
    private static final String CHASE_ROTATION_OWNER = "walker.combat";
    private boolean registered = false;
    private Minecraft mc = Minecraft.getMinecraft();
    private int stuckTicks = 0;
    private Vec3 lastPos = null;

    // Persistent state for realistic variance
    private java.util.Random random = new java.util.Random();
    private float currentTurnSpeed = 15.0f;
    private float targetTurnSpeed = 15.0f;
    private int jumpHoldTicks = 0;
    private int activeNodeIndex = -1;

    // --- Spline steering ---------------------------------------------------
    /** Arc length along the smoothed curve, in blocks. The driver steers off this, not nodes. */
    private double splineProgress = 0;
    /** False until the cursor has been anchored against the current route. */
    private boolean splineProgressKnown = false;
    /** How far either side of the last progress the projection is allowed to look. */
    private static final double PROGRESS_SEARCH_WINDOW = 4.0;
    // Raised from 1.5/2.5. The aim point sits this far along the curve, so any wobble in
    // the target converts to yaw error as atan(wobble / lookAhead) - a short look-ahead
    // magnifies every bit of noise into a visible camera shake, and 1.5 blocks was short
    // enough that the lateral drift alone swung the view by about ten degrees.
    private static final double LOOKAHEAD_MIN = 2.2;
    private static final double LOOKAHEAD_MAX = 3.6;
    /** Bend across the look-ahead window, in degrees, at which it is fully shortened. */
    private static final double LOOKAHEAD_BEND_LIMIT = 40.0;
    private static final double PITCH_LOOKAHEAD = 4.0;

    /**
      * Lateral drift across the corridor: mean-reverting, so it never jumps.
      *
      * <p>Retuned to be a slow lean rather than a wobble. It used to hold a stationary sigma of
      * ~0.18 blocks (clamped at 0.30) with a correlation time near 0.8s; against a 1.5 block
      * look-ahead that is five to eleven degrees of yaw swinging back and forth roughly once a
      * second, which reads as the camera shaking rather than as a person not walking a perfect
      * line. Halving the reversion rate stretches the period to about 1.6s and the smaller sigma
      * keeps the resulting yaw error near a degree - still not a straight line, but no longer
      * something you can see.
      */
    private double lateralOffset = 0;
    private static final double DRIFT_THETA = 0.03;
    private static final double DRIFT_LIMIT = 0.12;
    /** Step scale giving a stationary sigma of ~0.06 blocks at the theta above. */
    private static final double DRIFT_STEP_SIGMA = 0.0146;

    // --- Input shaping -----------------------------------------------------
    /** Hysteresis on W: opens at 40 degrees of yaw error, closes at 55. */
    private boolean forwardGateOpen = false;
    private static final double FORWARD_GATE_OPEN_DEG = 65.0;
    private static final double FORWARD_GATE_CLOSE_DEG = 80.0;
    /**
      * Above this yaw error the turn is helped along with a strafe instead of a dead stop.
      *
      * <p>Lowered from 30: engaging the strafe earlier means the walker starts cutting into the
      * corner while it is still turning, rather than running past the apex and correcting after.
      */
    private static final double STRAFE_ASSIST_DEG = 18.0;

    private boolean wasOnGround = false;
    /** Ticks still to wait on the ground before the next hop is allowed. */
    private int bhopDelayTicks = 0;
    /** Whether this landing drops the beat instead of hopping again. */
    private boolean bhopSkip = false;
    private static final double BHOP_SKIP_CHANCE = 0.20;

    /** Wall-clock time before which the walker stays still after a screen closes. */
    private boolean screenPaused;
    private int suicideAttempts;
    private long guiResumeAtMs = 0;
    private static final double GUI_RESUME_MEDIAN_MS = 210.0;
    private static final double GUI_RESUME_SIGMA = 0.35;
    private static final double GUI_CLOSE_MEDIAN_MS = 280.0;
    private static final double GUI_CLOSE_SIGMA = 0.40;
    private long guiDetectedTime = 0;
    private long guiCloseDelay = 0;

    // Hysteresis state
    private boolean isWhipping = false;

    // Internal input state to avoid desyncs with hardware
    private boolean forwardIntent = false;

    // Path version tracking to prevent state bleeding on silent recalculations
    private int currentPathVersion = -1;

    private int tickCounter = 0;

    /**
     * Blocks A* must avoid because the driver physically snagged on them.
     *
     * <p>Bounded on purpose: this used to be an unbounded set cleared only by {@link #start()},
     * so over a long session it kept subtracting geometry from the search until the goal became
     * unreachable and the recovery ladder could never climb back out. Oldest entries are
     * evicted, which also means a snag caused by a transient obstacle (a player standing in a
     * doorway) stops poisoning the route once it is gone.
     */
    private static final int MAX_BLACKLIST = 64;
    private static final java.util.LinkedHashSet<net.minecraft.util.BlockPos> blacklistedBlocks =
            new java.util.LinkedHashSet<net.minecraft.util.BlockPos>();

    /** Read-only view for callers that want to inspect the avoid set. */
    public static java.util.Set<net.minecraft.util.BlockPos> getBlacklistedBlocks() {
        return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(blacklistedBlocks));
    }

    private static java.util.function.Predicate<net.minecraft.util.BlockPos> avoidSnapshot() {
        final java.util.Set<net.minecraft.util.BlockPos> snapshot = getBlacklistedBlocks();
        return pos -> !snapshot.contains(pos);
    }

    private static void blacklist(net.minecraft.util.BlockPos pos) {
        if (blacklistedBlocks.add(pos) && blacklistedBlocks.size() > MAX_BLACKLIST) {
            java.util.Iterator<net.minecraft.util.BlockPos> it = blacklistedBlocks.iterator();
            it.next();
            it.remove();
        }
    }

    /** Scratch position for per-tick world lookups, so the tick allocates no BlockPos. */
    private final net.minecraft.util.BlockPos.MutableBlockPos scratchPos =
            new net.minecraft.util.BlockPos.MutableBlockPos();
    private int recoveryTicks = 0;
    private int recoveryAttempts = 0;

    /**
     * Which rung of the escalation ladder the current recovery is on: 0 sidesteps, 1 backs
     * up, 2 backs up while hopping. Replaying one identical routine on every attempt was the
     * repetitive signature; a person escalates instead of looping.
     */
    private int recoveryStage = 0;
    private int recoveryStrafeDir = 0;
    /** Tick at which the next look-around sweep is due, sampled rather than every 5 ticks. */
    private int recoveryNextScanTick = 0;
    private boolean suicideMode = false;
    private int suicideTimer = 0;

    // Liquid state
    private int ticksSinceLiquid = 100;
    /** True while any part of the player is inside a cobweb this tick. */
    private boolean inCobweb = false;

    // Evasion state
    private Vec3 stuckCheckLastPos = null;
    private int stuckCheckTimer = 0;
    /** Coarse progress watchdog: repath if less than 3 blocks are covered over ~3 seconds. */
    private Vec3 progressLastPos = null;
    private int progressTimer = 0;
    private int randomStrafeDir = 0;
    private int randomStrafeTicks = 0;

    private net.minecraft.entity.player.EntityPlayer combatTarget = null;

    /**
     * Whether combat movement adds the random side-to-side strafe. On by default (duels want the
     * unpredictability); a grinder working a stacked mid turns it off so the bot just pressures
     * forward instead of dancing sideways into the pile.
     */
    boolean combatStrafe = true;
    /** Ticks remaining before the combat strafe direction may re-roll. */
    private int combatStrafeHold = 0;

    /**
     * Whether combat movement adds jumps: a sprint-jump approach to close the gap when the target
     * is more than a few blocks away, and an occasional crit hop once in range so attacks land on
     * the way down. Off by default (self-defense keeps a flat approach); the grinder turns it on.
     */
    boolean combatJumps = false;
    private long nextCombatCritJump = 0L;

    void setCombatTarget(net.minecraft.entity.player.EntityPlayer target) {
        if (target == null && this.combatTarget != null) {
            resetKeys();
            com.nezurstandalone.utils.RotationManager.getInstance().clearTarget(CHASE_ROTATION_OWNER);
        }
        this.combatTarget = target;
        if (target != null) {
            // Must register too, not just flip active: onTick only runs while this is on the event
            // bus, and registration otherwise happens only when a route is published. A combat
            // chase that never pathfinds would therefore never tick, so the bot stood still and
            // only clicked whoever walked into range.
            ensureRegistered();
            // A chase request is an explicit demand for movement: clear any latch (suicideMode,
            // recovery, gui resume) that would make onTick return before the combat block runs.
            if (suicideMode) {
                suicideMode = false;
                suicideTimer = 0;
                recoveryTicks = 0;
                stuckTicks = 0;
            }
            this.active = true;
        }
    }

    private void ensureRegistered() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }
    }

    /**
     * A route landed for a navigation the driver is <em>already</em> walking.
     *
     * <p>This is deliberately not {@link #start()}. {@code PathfinderManager.publish} used to
     * call {@code start()} for every published route, including the ones {@link #repath}
     * requests mid-recovery — so the recovery repath destroyed the very blacklist it had just
     * handed to A*, reset {@link #recoveryAttempts} to zero, and got back the identical route
     * it had just failed to walk. The result was an unbounded stuck/back-up/repath limit
     * cycle with a near-constant period, and a {@code /oof} escape hatch at twelve attempts
     * that could never be reached because the counter never survived long enough to climb.
     *
     * <p>Everything route-local (the node cursor, the path version) is re-synced by the
     * version check in {@link #onTick}; everything session-local (blacklist, attempt counter,
     * recovery stage) survives on purpose.
     */
    void onPathPublished() {
        ensureRegistered();
        active = true;
    }

    void start() {
        ensureRegistered();

        // Clean Restart: Wipe all previous session variables
        active = true;
        stuckTicks = 0;
        lastPos = null;
        currentTurnSpeed = 15.0f;
        targetTurnSpeed = 15.0f;
        jumpHoldTicks = 0;
        activeNodeIndex = -1;
        guiCloseDelay = 0;
        forwardIntent = false;
        isWhipping = false;
        currentPathVersion = -1;
        if (mc.thePlayer != null) {
            // Camera vars removed
        }
        tickCounter = 0;
        blacklistedBlocks.clear();
        recoveryTicks = 0;
        recoveryAttempts = 0;
        recoveryStage = 0;
        recoveryStrafeDir = 0;
        recoveryNextScanTick = 0;
        suicideMode = false;
        suicideTimer = 0;
        stuckCheckLastPos = null;
        stuckCheckTimer = 0;
        progressLastPos = null;
        progressTimer = 0;
        randomStrafeTicks = 0;
        ticksSinceLiquid = 100;
        inCobweb = false;
        splineProgress = 0;
        splineProgressKnown = false;
        lateralOffset = 0;
        forwardGateOpen = false;
        wasOnGround = false;
        bhopDelayTicks = 0;
        bhopSkip = false;
        guiResumeAtMs = 0;
        screenPaused = false;
        suicideAttempts = 0;
    }

    void stop() {
        active = false;
        combatTarget = null; // a stale chase must not survive into the next enable
        com.nezurstandalone.utils.RotationManager.getInstance().clearTarget(PATH_ROTATION_OWNER);
        com.nezurstandalone.utils.RotationManager.getInstance().clearTarget(CHASE_ROTATION_OWNER);
        resetKeys();
    }

    /**
     * Full state wipe for the start of a fresh driving session.
     *
     * <p>{@link #stop()} only drops the route and the keys. Every latch this class holds -
     * {@code suicideMode} above all - survives it, and suicideMode is exactly the trap: once
     * tripped, onTick returns before the combat block ever runs, releasing keys every tick,
     * so a module that disables and re-enables after a trip inherits a walker that aims and
     * clicks but never moves. Any consumer starting a new drive session calls this instead of
     * reasoning about which internal flags might be latched.
     */
    void resetForNewSession() {
        stop();
        start();
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Whether the player's bounding box overlaps a cobweb.
     *
     * <p>{@code Entity.isInWeb} cannot be read from here: {@code moveEntity} clears it in the
     * same tick it applies the slowdown, so by the time an event handler looks at it the flag
     * is already false. Checking the blocks the hitbox covers is the reliable way to ask.
     */
    private boolean detectCobweb() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        net.minecraft.util.AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        int minX = net.minecraft.util.MathHelper.floor_double(box.minX);
        int maxX = net.minecraft.util.MathHelper.floor_double(box.maxX);
        int minY = net.minecraft.util.MathHelper.floor_double(box.minY);
        int maxY = net.minecraft.util.MathHelper.floor_double(box.maxY);
        int minZ = net.minecraft.util.MathHelper.floor_double(box.minZ);
        int maxZ = net.minecraft.util.MathHelper.floor_double(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    scratchPos.set(x, y, z);
                    if (mc.theWorld.getBlockState(scratchPos).getBlock() == net.minecraft.init.Blocks.web) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void resetKeys() {
        if (mc.gameSettings != null) {
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindForward.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindBack.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindJump.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindLeft.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindRight.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
        jumpHoldTicks = 0;
        forwardIntent = false;
    }

    /**
     * One tick of the combat chase: aim at the target, press forward + sprint, jump logic.
     *
     * @return true while the target is alive and the chase owns this tick; false when the
     *         target died or expired (caller falls through to route walking).
     */
    private final com.nezurstandalone.control.CombatMovement combatMovement = new com.nezurstandalone.control.CombatMovement();
    double combatReach = 3.0;

    private int nextCombatHopTick;
    private boolean driveCombatChase() {
        if (combatTarget.worldObj != mc.theWorld || combatTarget.isDead || combatTarget.getHealth() <= 0 || mc.thePlayer.getDistanceToEntity(combatTarget) > 40) {
            setCombatTarget(null);
            return false;
        }

        float[] targetRots = com.nezurstandalone.utils.RotationUtils.getRotations(combatTarget);
        // CombatAura is the sole combat camera owner.
        double dx = combatTarget.posX - mc.thePlayer.posX;
        double dz = combatTarget.posZ - mc.thePlayer.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double closing = horizontal < 0.001 ? 0 :
                (mc.thePlayer.motionX * dx + mc.thePlayer.motionZ * dz) / horizontal;
        com.nezurstandalone.control.CombatMovement policy = combatMovement;
        policy.update(net.minecraft.util.MathHelper.wrapAngleTo180_float(targetRots[0] - mc.thePlayer.rotationYaw),
                mc.thePlayer.getDistanceToEntity(combatTarget), combatReach, closing,
                mc.thePlayer.isCollidedHorizontally || !mc.thePlayer.canEntityBeSeen(combatTarget),
                (mc.thePlayer.hurtTime > 0 && !mc.thePlayer.onGround)
                || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()
                || mc.theWorld.getBlockState(new net.minecraft.util.BlockPos(mc.thePlayer)).getBlock() == net.minecraft.init.Blocks.web);
        // Grinder mid pressure has no stop-distance/backpedal band. Hazards and obstacles still stop it.
        if (com.nezurstandalone.engine.GrinderEngine.isMidPressureActive()
                && policy.state != com.nezurstandalone.control.CombatMovement.State.RECOVER
                && policy.state != com.nezurstandalone.control.CombatMovement.State.ALIGN) {
            policy.forward = true;
            policy.back = false;
            // Keep the movement policy sprint decision; do not force sprint at melee distance.
        }
        forwardIntent = policy.forward;
        com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindForward.getKeyCode(), policy.forward);
        com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindBack.getKeyCode(), policy.back);
        com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindSprint.getKeyCode(), policy.sprint);
        com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindLeft.getKeyCode(), false);
        com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindRight.getKeyCode(), false);
        // Shared grinder/contractor vanilla jump input; never hop in menus or while sneaking.
        boolean hop=combatJumps && mc.currentScreen==null && mc.thePlayer.onGround
                && !mc.thePlayer.isSneaking() && !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()
                && !mc.thePlayer.isCollidedHorizontally && mc.thePlayer.canEntityBeSeen(combatTarget)
                && "Pit".equals(com.nezurstandalone.utils.PitMapManager.getZone(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ))
                && ((policy.forward && mc.thePlayer.getDistanceToEntity(combatTarget)>combatReach+1
                        && Math.hypot(mc.thePlayer.motionX,mc.thePlayer.motionZ)>.15)
                    || (mc.thePlayer.getDistanceToEntity(combatTarget)<=combatReach
                        && combatTarget.hurtResistantTime>0 && combatTarget.hurtResistantTime<=10))
                && mc.thePlayer.ticksExisted>=nextCombatHopTick;
        if(hop)nextCombatHopTick=mc.thePlayer.ticksExisted+24;
        com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindJump.getKeyCode(), hop);
        // Vanilla living update alone processes the jump key and its cooldown.

        return true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!active || event.phase != TickEvent.Phase.START) return;
        if(!PathfinderManager.hasValidHandle()){stop();return;}
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead
                || mc.thePlayer.getHealth() <= 0) { stop(); return; }
        if (mc.currentScreen != null || (mc.thePlayer.isUsingItem()
                && !com.nezurstandalone.engine.GrinderEngine.allowsHealingMovement())
                || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            if (mc.currentScreen != null) screenPaused = true;
            resetKeys();
            com.nezurstandalone.utils.RotationManager.getInstance().clearTarget(PATH_ROTATION_OWNER);
            com.nezurstandalone.utils.RotationManager.getInstance().clearTarget(CHASE_ROTATION_OWNER);
            return;
        }

        if (screenPaused) {
            screenPaused = false;
            guiResumeAtMs = System.currentTimeMillis()
                    + sampleReactionMs(PathfinderConfig.guiResumeMs.value, GUI_RESUME_SIGMA, 140L, 400L);
        }
        if (System.currentTimeMillis() < guiResumeAtMs) { resetKeys(); return; }

        // Combat may own movement only after common input/state guards. The fight block used to sit below the
        // suicide latch, the ESC-menu bail, screen handling and the resume delay - any one of
        // them latching across a module disable/enable left a live chase pressing nothing.
        // A target in reach is an unconditional demand for movement; nothing else in this
        // tick outranks it. (The gates still apply to route walking below.)
        if (combatTarget != null) {
            if (driveCombatChase()) return;
        }

        if (suicideMode && com.nezurstandalone.engine.GrinderEngine.isAutoRaffleActive()) {
            PathfinderManager.fail();return;
        }
        if (suicideMode) {
            resetKeys();
            if (com.nezurstandalone.utils.PitMapManager.isInSpawn(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)) {
                NotificationManager.show("§a[AutoWalker] Respawned safely in spawn area.", 3000);
                suicideMode = false;
                PathfinderManager.complete();
            } else {
                suicideTimer++;
                if (suicideTimer >= 100) {
                    if (suicideAttempts >= 3) {
                        PathfinderManager.complete();
                        NotificationManager.show("Recovery received no response; navigation stopped.", 4000);
                        return;
                    }
                    suicideAttempts++;
                    if (!com.nezurstandalone.control.CommandCoordinator.send(this, "/oof")) return;
                    suicideTimer = 0;
                }
            }
            return;
        }

        // Combat chase was handled at the top of the tick; combatTarget is null here or the
        // chase block already returned. Route walking continues below.

        // One atomic read: nodes, destination and version can no longer disagree with each
        // other mid-tick, which is what previously let the walker measure arrival against a
        // destination belonging to a route it was no longer walking.
        final PathSnapshot snapshot = PathfinderManager.getSnapshot();
        final List<Vec3> path = snapshot.getNodes();
        final net.minecraft.util.BlockPos destination = snapshot.getDestination();
        if (path.isEmpty()) {
            resetKeys(); // Wait for path to calculate
            return;
        }

        tickCounter++;
        if (tickCounter % 20 == 0 && destination != null && recoveryTicks == 0) {
            double minDistToPath = Double.MAX_VALUE;
            int searchStart = Math.max(0, activeNodeIndex - 3);
            int searchEnd = Math.min(path.size() - 1, activeNodeIndex + 5);
            if (searchStart >= 0 && searchEnd < path.size()) {
                for (int i = searchStart; i <= searchEnd; i++) {
                    double d = getHorizontalDistance(mc.thePlayer.getPositionVector(), path.get(i));
                    if (d < minDistToPath) minDistToPath = d;
                }
            }

            // Only trigger a background path recalculation if we are in a critical situation
            if (minDistToPath > 2.5 || stuckTicks > 10) {
                repath(destination);
            }
        }

        // Reset local node state if the PathRenderer generated a fresh route underneath us
        if (snapshot.getVersion() != currentPathVersion) {
            currentPathVersion = snapshot.getVersion();
            activeNodeIndex = -1;
            splineProgressKnown = false;
        }

        Vec3 playerPos = mc.thePlayer.getPositionVector();

        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) {
            ticksSinceLiquid = 0;
        } else {
            ticksSinceLiquid++;
        }

        inCobweb = detectCobweb();

        // [Stuck detection logic was moved to the bottom of the tick phase to rely on actual movement attempts]

        double minDist = Double.MAX_VALUE;

        // Sequential Progression: Only do global search on first tick or if we lost the path
        if (activeNodeIndex == -1 || activeNodeIndex >= path.size()) {
            for (int i = 0; i < path.size(); i++) {
                Vec3 node = path.get(i);
                double distSq = playerPos.squareDistanceTo(node);
                if (distSq < minDist) {
                    minDist = distSq;
                    activeNodeIndex = i;
                }
            }
        }

        if (activeNodeIndex == -1 || activeNodeIndex >= path.size()) {
            resetKeys();
            return;
        }

        // Lookahead Recovery & Node Progression
        int closestIdx = activeNodeIndex;
        double closestNodeDist = getHorizontalDistance(playerPos, path.get(activeNodeIndex));

        // Scan up to 5 nodes ahead and up to 3 nodes behind to recover from lag/knockback
        int windowStart = Math.max(0, activeNodeIndex - 3);
        int windowEnd = Math.min(path.size() - 1, activeNodeIndex + 5);

        for (int i = windowStart; i <= windowEnd; i++) {
            double d = getHorizontalDistance(playerPos, path.get(i));
            if (d < closestNodeDist - 0.5) { // Must be significantly closer to steal focus
                closestNodeDist = d;
                closestIdx = i;
            }
        }

        if (closestIdx != activeNodeIndex) {
            activeNodeIndex = closestIdx;
        }

        // Aggressive Node Skipping: If the next node is closer than our current node, we skipped it! (Prevents backing up after jumps)
        while (activeNodeIndex + 1 < path.size()) {
            double distCurrent = getHorizontalDistance(playerPos, path.get(activeNodeIndex));
            double distNext = getHorizontalDistance(playerPos, path.get(activeNodeIndex + 1));
            if (distNext < distCurrent + 0.3) {
                activeNodeIndex++;
                } else {
                break;
            }
        }

        // Standard advance when close enough
        if (getHorizontalDistance(playerPos, path.get(activeNodeIndex)) < 0.45 && activeNodeIndex + 1 < path.size()) {
            activeNodeIndex++;
        }

        Vec3 movementTarget = path.get(activeNodeIndex);
        // Destination check against the true node: the lateral drift is a steering
        // offset only, and must not move where the route is considered finished.
        if (activeNodeIndex == path.size() - 1 && getHorizontalDistance(playerPos, movementTarget) < 1.0 && Math.abs(movementTarget.yCoord - mc.thePlayer.posY) < 1.5) {
            double distToActualEnd = 0.0;
            if (destination != null) {
                distToActualEnd = getHorizontalDistance(playerPos,
                        new Vec3(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5));
            }

            if (destination == null || distToActualEnd <= 1.5) {
                if (!com.nezurstandalone.engine.GrinderEngine.isNpcNavigationActive())
                    NotificationManager.show("§a[AutoWalker] Reached destination!", 3000);
                com.nezurstandalone.utils.RotationManager.getInstance().clearTarget(PATH_ROTATION_OWNER);
                PathfinderManager.complete();
                return;
            } else {
                // We reached the end of a partial path! Trigger rapid recalculation
                stuckTicks += 50;
            }
        }

        // --- Steering ---
        // Aim at a point on the smoothed curve a fixed *distance* ahead rather than at the
        // next grid node. Steering node-to-node produced late, sharp turns and pinned every
        // heading to a multiple of 45 degrees, because that is all a 26-way block grid can
        // express. A distance-based look-ahead on the spline rounds corners in advance, the
        // way a person walks them.
        if (splineProgressKnown) {
            splineProgress = snapshot.project(playerPos, splineProgress, PROGRESS_SEARCH_WINDOW);
        } else {
            // First tick on this route: no prior cursor to anchor to, so scan the lot.
            splineProgress = snapshot.project(playerPos, snapshot.getLength() * 0.5,
                    snapshot.getLength());
            splineProgressKnown = true;
        }
        advanceLateralDrift();

        double lookAhead = lookAheadFor(snapshot, splineProgress);
        Vec3 rawLookTarget = snapshot.pointAt(splineProgress + lookAhead);
        if (rawLookTarget == null) {
            rawLookTarget = path.get(Math.min(path.size() - 1, activeNodeIndex + 1));
        }
        // Restore the pre-audit steering behavior. A diagonal swept body towards a
        // lower aim point intersects the stair support even though walking off it is valid.
        // Route generation retains its collision validation; steering is not a flight path.
        Vec3 offsetLookTarget = applyLateralDrift(rawLookTarget,
                snapshot.tangentAt(splineProgress + lookAhead));

        double dx = offsetLookTarget.xCoord - mc.thePlayer.posX;
        double dy = offsetLookTarget.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.5);
        double dz = offsetLookTarget.zCoord - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = mc.thePlayer.rotationYaw;
        if (dist > 0.1) {
            targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }

        // Pitch tracks the elevation of the curve ahead. Reading it off the spline rather than
        // off node indices makes it continuous: the old version stepped in whole 15 degree
        // jumps every time the node cursor advanced, so a session's pitch histogram was a
        // handful of spikes at 5, -10, 20 with nothing in between.
        Vec3 pitchAhead = snapshot.pointAt(splineProgress + PITCH_LOOKAHEAD);
        Vec3 pitchHere = snapshot.pointAt(splineProgress);
        float targetPitch = 5.0f; // Base pitch (looking slightly forward/down)
        if (pitchAhead != null && pitchHere != null) {
            double elevationChange = pitchAhead.yCoord - pitchHere.yCoord;
            targetPitch -= (float) (elevationChange * 15.0);
            targetPitch = Math.max(-60.0f, Math.min(70.0f, targetPitch)); // Natural neck limits
        }

        float currentYaw = mc.thePlayer.rotationYaw;
        float currentPitch = mc.thePlayer.rotationPitch;

        float yawDiff = net.minecraft.util.MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);

        // YIELD ROTATION TO CHEST AURA
        boolean yieldRotation = false;

        if (!yieldRotation) {
            // A big correction is turned faster than a small one, which is what a hand does and
            // also what stops the walker dawdling sideways through a re-aim. RotationManager
            // still shapes it as one ballistic minimum-jerk move, so this only shortens the
            // flight time - it does not add a snap.
            // Corner speed. These scale the Fitts duration in RotationManager (duration is
            // multiplied by BASELINE_SPEED / speed), so a bigger number only shortens the
            // flight - the movement is still one minimum-jerk submovement with a deliberate
            // overshoot and a corrective pass, and still leaves as whole-pixel setAngles
            // deltas. A 90 degree turn at the top tier peaks near 890 deg/s, which is an
            // ordinary flick; the manager's 1200 deg/s ceiling is still above it.
            float tierSpeed = Math.abs(yawDiff) > 45f ? 26.0f
                    : Math.abs(yawDiff) > 20f ? 18.0f
                    : 13.0f;
            float turnSpeed = (float) (tierSpeed * com.nezurstandalone.pathfinder.PathfinderConfig.turnSpeedMult.value);
            com.nezurstandalone.utils.RotationManager.getInstance().setTargetRotation(
                    PATH_ROTATION_OWNER, com.nezurstandalone.utils.RotationManager.PRIORITY_LEGACY,
                    targetYaw, targetPitch, turnSpeed);
        }

        // --- Safe Movement Simulation (Realistic Key Holding & Strafing) ---
        forwardIntent = true;

        net.minecraft.util.BlockPos playerBlock = new net.minecraft.util.BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        net.minecraft.block.Block currentBlock = mc.theWorld.getBlockState(playerBlock).getBlock();
        boolean isClimbing = currentBlock instanceof net.minecraft.block.BlockLadder || currentBlock instanceof net.minecraft.block.BlockVine;

        if (isClimbing) {
            // When climbing, face straight at it and hold W
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindForward.getKeyCode(), true);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindLeft.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindRight.getKeyCode(), false);
        } else if (isWhipping) {
            // Catch whip phases first before processing strafes using Hysteresis
            forwardIntent = false;
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindForward.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindLeft.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindRight.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindBack.getKeyCode(), false);
        } else if (recoveryTicks > 0) {
            // --- PROGRESSIVE RECOVERY ---
            // Stage 0 sidesteps, stage 1 backs off, stage 2 backs off while hopping. Trying
            // the cheap thing first and escalating only when it fails is both what a person
            // does and what actually clears the common snags (a corner, a doorway lintel)
            // without giving up ground.
            recoveryTicks--;
            forwardIntent = false;

            boolean stepBack = recoveryStage >= 1;
            boolean hop = recoveryStage >= 2;

            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindForward.getKeyCode(), false);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindBack.getKeyCode(), stepBack);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindLeft.getKeyCode(), recoveryStrafeDir < 0);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindRight.getKeyCode(), recoveryStrafeDir > 0);
            if (hop) {
                jumpHoldTicks = 2; // Keep hopping to get out of holes
            }

            // Look around for a way out, but only once backing off has already failed, and on
            // a sampled interval — a sweep every 5 ticks exactly is a 250 ms metronome.
            if (recoveryStage >= 1 && recoveryTicks <= recoveryNextScanTick && !yieldRotation) {
                recoveryNextScanTick = recoveryTicks - (6 + random.nextInt(9));
                targetYaw += (random.nextFloat() * 90 - 45); // Randomly look around
                com.nezurstandalone.utils.RotationManager.getInstance().setTargetRotation(
                        PATH_ROTATION_OWNER, com.nezurstandalone.utils.RotationManager.PRIORITY_LEGACY,
                        targetYaw, targetPitch, 8.0f);
            }

            if (recoveryTicks == 1) {
                if (destination != null) {
                    NotificationManager.show("§c[AutoWalker] Rerouting around obstacle...", 3000);
                    repath(destination);
                }
            }
        } else {
            boolean pressForward = false;
            boolean pressLeft = false;
            boolean pressRight = false;
            boolean pressBack = false;

            if (yieldRotation) {
                // Dynamic 8-way strafing when yielding camera to ChestAura!
                // Allows the bot to run straight while snapping its neck sideways to loot a chest.
                if (yawDiff >= -22.5 && yawDiff < 22.5) pressForward = true;
                else if (yawDiff >= 22.5 && yawDiff < 67.5) { pressForward = true; pressRight = true; }
                else if (yawDiff >= 67.5 && yawDiff < 112.5) pressRight = true;
                else if (yawDiff >= 112.5 && yawDiff < 157.5) { pressBack = true; pressRight = true; }
                else if (yawDiff >= 157.5 || yawDiff < -157.5) pressBack = true;
                else if (yawDiff >= -157.5 && yawDiff < -112.5) { pressBack = true; pressLeft = true; }
                else if (yawDiff >= -112.5 && yawDiff < -67.5) pressLeft = true;
                else if (yawDiff >= -67.5 && yawDiff < -22.5) { pressForward = true; pressLeft = true; }
            } else {
                // Forward-only steering. Point the camera at the path and press W only once
                // roughly aligned; the rotation turns the body into the line and the server
                // derives velocity from where we face. This is the model that actually walks
                // corners cleanly - proactively strafing at every few degrees off-heading (the
                // version this replaced) sidesteps the player diagonally into the wall on the
                // inside of a curve, which is the wall-grinding it used to do. A sharp corner
                // costs a brief turn-in-place instead, which is fine and what a person does.
                pressForward = Math.abs(yawDiff) < com.nezurstandalone.pathfinder.PathfinderConfig.forwardAngle.value;

                // Commit to drops. If the path leads down off a ledge just ahead, walk off it at
                // once instead of dithering at the edge - the sewer-chest race is won by taking
                // the fall immediately. Gravity does the rest once we step past the lip.
                if (movementTarget.yCoord < mc.thePlayer.posY - 0.4 && Math.abs(yawDiff) < 60.0) {
                    pressForward = true;
                }

                // --- Wall handling ---
                // When actually scraping a wall: if we are not yet aligned with the path, stop
                // pushing into it and turn in place first (the camera is already rotating onto the
                // line). Only once roughly aligned do we sidestep to slide around the corner. This
                // is what stops the "hold W into the corner and wiggle" grind - the walker turns
                // before it commits, so in a one-wide corridor it rounds the corner cleanly.
                if (mc.thePlayer.isCollidedHorizontally) {
                    if (Math.abs(yawDiff) > 22.0) {
                        pressForward = false; // turn in place rather than grind the wall
                    }

                    float yawRad = (float) Math.toRadians(mc.thePlayer.rotationYaw);
                    double facingX = -Math.sin(yawRad);
                    double facingZ = Math.cos(yawRad);
                    double toTargetX = offsetLookTarget.xCoord - mc.thePlayer.posX;
                    double toTargetZ = offsetLookTarget.zCoord - mc.thePlayer.posZ;
                    double cross = facingX * toTargetZ - facingZ * toTargetX;
                    // Set both keys explicitly so they can never cancel to no strafe at all.
                    if (cross > 0.1) {
                        pressRight = true;
                        pressLeft = false;
                    } else if (cross < -0.1) {
                        pressLeft = true;
                        pressRight = false;
                    }
                }
            }

            if (stuckTicks > 10 && randomStrafeTicks > 0) {
                randomStrafeTicks--;
                if (randomStrafeDir == -1) {
                    pressLeft = true;
                } else if (randomStrafeDir == 1) {
                    pressRight = true;
                }
            }

            // Wall repulsion, but ONLY while turning. A straight corridor - even one walled on
            // both sides - is walked dead straight with no strafe at all; the repulsion exists
            // solely to peel the body off a wall on the inside of a bend, which is the only place
            // it would otherwise scrape. So it fires when the path actually bends here (a real
            // yaw error) and a wall is close on exactly one side, and it strafes away from that
            // wall - left when the wall is on the right, right when on the left. Walls on both
            // sides (a one-wide passage) or no near wall: hold centre, go straight.
            if (Math.abs(yawDiff) > 12.0f) {
                float wr = (float) Math.toRadians(mc.thePlayer.rotationYaw);
                double rightX = Math.cos(wr);   // unit "right" vector for this facing
                double rightZ = Math.sin(wr);
                boolean wallOnRight = solidColumnAt(mc.thePlayer.posX + rightX * 0.58, mc.thePlayer.posZ + rightZ * 0.58);
                boolean wallOnLeft = solidColumnAt(mc.thePlayer.posX - rightX * 0.58, mc.thePlayer.posZ - rightZ * 0.58);
                if (wallOnRight && !wallOnLeft) {
                    pressLeft = true;
                    pressRight = false;
                } else if (wallOnLeft && !wallOnRight) {
                    pressRight = true;
                    pressLeft = false;
                }
            }

            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindForward.getKeyCode(), pressForward);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindBack.getKeyCode(), pressBack);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindLeft.getKeyCode(), pressLeft);
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindRight.getKeyCode(), pressRight);

            // Sprint only when the walker owns it (a dedicated sprint mod is the common setup,
            // hence off by default): forward, on-heading, not scraping a wall, hunger permitting.
            boolean sprintNow = com.nezurstandalone.pathfinder.PathfinderConfig.sprint.enabled
                    && pressForward && !pressBack
                    && Math.abs(yawDiff) < com.nezurstandalone.pathfinder.PathfinderConfig.sprintMaxYaw.value
                    && !mc.thePlayer.isCollidedHorizontally
                    && !mc.thePlayer.isSneaking()
                    && mc.thePlayer.getFoodStats().getFoodLevel() > 6;
            if (com.nezurstandalone.pathfinder.PathfinderConfig.sprint.enabled) {
                com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindSprint.getKeyCode(), sprintNow);
            }
        }

        // Landing bookkeeping for the hop rhythm. Re-arming on every touchdown made the jump
        // train exactly periodic at the physics airtime; a sampled delay plus an occasional
        // dropped beat gives it the variance a person has.
        if (mc.thePlayer.onGround) {
            if (!wasOnGround) {
                bhopDelayTicks = random.nextInt(3);
                bhopSkip = random.nextDouble() < com.nezurstandalone.pathfinder.PathfinderConfig.bHopSkipChance.value;
            } else if (bhopDelayTicks > 0) {
                bhopDelayTicks--;
            }
        }
        wasOnGround = mc.thePlayer.onGround;

        // Unified Jump & Evasion Logic
        boolean needsToJump = false;
        if (movementTarget.yCoord > mc.thePlayer.posY + 1.5) {
            needsToJump = true;
        } else if (activeNodeIndex + 1 < path.size()) {
            Vec3 nextTarget = path.get(activeNodeIndex + 1);
            if (nextTarget.yCoord > mc.thePlayer.posY + 1.5) {
                // If the next block is elevated, jump early (~1.6 blocks away) to cleanly clear the corner without rubbing against it!
                if (getHorizontalDistance(playerPos, nextTarget) < 1.6) {
                    needsToJump = true;
                }
            }
        }
        // Proactive step-up: whenever a one-block step sits directly ahead, hop it without
        // waiting to bump into it first. The checks above only fire for a 1.5+ block rise, and
        // collision-evade only fires *after* the body is already grinding the block face - so a
        // plain one-block step onto a ledge used to be hit-or-miss. This makes it always jump the
        // instant a climbable step is in front, which is what "always jump when it has to" means.
        if (stepUpAhead()) {
            needsToJump = true;
        }

        needsToJump = needsToJump && mc.thePlayer.onGround && Math.abs(yawDiff) < 30.0;

        // A jump clears at most a one-block rise. If the obstacle straight ahead is a full
        // two-block face - the side wall of a staircase, a whole block - hopping at it just bonks
        // forever, which is the "spam jumping hoping for a big jump" thrash beside a step. Detect
        // that tall face and refuse to jump into it; the wall-slide strafe below then carries the
        // body sideways to where the step is a single climbable rise, and it goes up there. A real
        // one-block step (solid at the feet, clear at the head) is left alone so ordinary steps and
        // staircases climbed head-on still jump normally.
        boolean tallFace = tallFaceAhead();
        if (tallFace) {
            needsToJump = false;
        }
        boolean collisionEvasion = (mc.thePlayer.isCollidedHorizontally && mc.thePlayer.onGround && !tallFace);

        // --- Low-Clearance Doorway Detection ---
        // Scan up to 3 nodes ahead to detect 2-block-high passages (doorways, tunnels).
        // If found, suppress ALL jumping to prevent bonking the lintel above the door.
        boolean lowClearanceAhead = false;
        int lookaheadNodes = Math.min(path.size() - 1, activeNodeIndex + 3);
        for (int i = activeNodeIndex; i <= lookaheadNodes; i++) {
            Vec3 node = path.get(i);
            // Check if 2 blocks above the floor is solid (= only 2 blocks of air = doorway)
            scratchPos.set(net.minecraft.util.MathHelper.floor_double(node.xCoord),
                    net.minecraft.util.MathHelper.floor_double(node.yCoord) + 2,
                    net.minecraft.util.MathHelper.floor_double(node.zCoord));
            net.minecraft.block.Block blockAtHead = mc.theWorld.getBlockState(scratchPos).getBlock();
            if (blockAtHead.getMaterial().isSolid()) {
                lowClearanceAhead = true;
                break;
            }
        }

        if (lowClearanceAhead) {
            needsToJump = false;
            collisionEvasion = false; // Don't jump into the doorway lintel, let wall-slide handle it
        }

        // B-Hop Logic (Sprint-Jumping on straight paths with clear headspace)
        boolean straightLine = true;
        float baseLineYaw = 0;
        int nodesToCheck = Math.min(path.size() - activeNodeIndex, 4);
        if (nodesToCheck < 3) {
            straightLine = false; // Don't bhop right before the destination
        } else {
            // Check if the nodes themselves form a straight line by comparing the direction of each segment
            Vec3 firstNode = path.get(activeNodeIndex);
            Vec3 secondNode = path.get(activeNodeIndex + 1);

            if (Math.abs(secondNode.yCoord - firstNode.yCoord) > 0.1) {
                straightLine = false; // Prevent B-Hopping if the very first segment is elevated
            }

            baseLineYaw = (float) Math.toDegrees(Math.atan2(-(secondNode.xCoord - firstNode.xCoord), (secondNode.zCoord - firstNode.zCoord)));

            for (int i = activeNodeIndex + 1; i < activeNodeIndex + nodesToCheck - 1; i++) {
                Vec3 n1 = path.get(i);
                Vec3 n2 = path.get(i + 1);

                // Never B-Hop if a staircase, ledge, or drop is approaching! We need to land to execute the early-jump.
                if (Math.abs(n2.yCoord - n1.yCoord) > 0.1) {
                    straightLine = false;
                    break;
                }

                float segmentYaw = (float) Math.toDegrees(Math.atan2(-(n2.xCoord - n1.xCoord), (n2.zCoord - n1.zCoord)));
                if (Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(segmentYaw - baseLineYaw)) > 5.0) {
                    straightLine = false;
                    break;
                }
            }
        }
        boolean clearHeadspace = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0, 1.0, 0)).isEmpty();

        // Wait until: 1) We are looking exactly where we want to go (yawDiff < 8)
        // 2) Our target look direction is perfectly aligned with the path's true direction (< 10 deg)
        // This ensures we ONLY jump when we are centered on the line and parallel to it, preventing diagonal jumps!
        if (com.nezurstandalone.pathfinder.PathfinderConfig.bHop.enabled && straightLine && clearHeadspace && !isClimbing && mc.thePlayer.onGround && !lowClearanceAhead && !inCobweb) {
            if (Math.abs(yawDiff) < 8.0 && Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(targetYaw - baseLineYaw)) < 10.0
                    && bhopDelayTicks <= 0) {
                if (bhopSkip) {
                    // The dropped beat is consumed here, not on the next landing. It used to be
                    // re-rolled only inside the touchdown branch above - but skipping a hop means
                    // the walker never leaves the ground, so there is no touchdown, so the flag was
                    // never re-rolled. One skip therefore latched b-hopping off for good, and
                    // because onPathPublished deliberately does not call start(), it survived
                    // repaths too. Clearing it here keeps the occasional missed beat without
                    // letting it become permanent.
                    bhopSkip = false;
                    bhopDelayTicks = 2 + random.nextInt(4);
                } else {
                    needsToJump = true;
                }
            }
        }

        boolean inLiquidEvasion = ticksSinceLiquid <= 2;

        if (inLiquidEvasion) {
            jumpHoldTicks = 2; // Force jump while in or smoothly exiting liquid (0.1 sec)
        } else if ((needsToJump || collisionEvasion) && jumpHoldTicks <= 0) {
            jumpHoldTicks = 2 + random.nextInt(4); // Organic hop length
        }

        if (jumpHoldTicks > 0) {
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindJump.getKeyCode(), true);
            jumpHoldTicks--;
        } else {
            com.nezurstandalone.control.MovementKeys.set(com.nezurstandalone.control.MovementKeys.walkerOwner(), mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

        // Sophisticated Progress-Based Anti-Stuck.
        //
        // A cobweb is exempt. Web physics multiplies horizontal motion by 0.25 every tick, so
        // crossing one legitimately covers far less than the half a block per second this
        // treats as stuck - the walker would decide it was snagged, start throwing random
        // strafes and random jumps to break free, and eventually /oof. That thrash is also the
        // worst thing to do to a movement-prediction anticheat: a jump inside a web resolves to
        // motionY 0.42 * 0.05 = 0.021, and flipping the strafe direction every few ticks on top
        // of it is not a movement a person produces. Walking straight out is both correct and
        // what a human does.
        // Coarse progress watchdog: if the walker has a destination and has not covered 3 blocks
        // in the last 3 seconds, the current route is not working - recompute it from here.
        if (destination != null && recoveryTicks == 0) {
            progressTimer++;
            if (progressTimer >= 60) { // ~3 seconds at 20 tps
                if (progressLastPos != null && playerPos.distanceTo(progressLastPos) < 3.0) {
                    repath(destination);
                }
                progressLastPos = playerPos;
                progressTimer = 0;
            }
        } else {
            progressTimer = 0;
            progressLastPos = playerPos;
        }

        if (forwardIntent && recoveryTicks == 0 && !inCobweb) {
            stuckCheckTimer++;
            if (stuckCheckTimer >= 20) { // Check every 1 second
                if (stuckCheckLastPos != null && playerPos.distanceTo(stuckCheckLastPos) < 0.5) {
                    // Moved less than 0.5 blocks total in the last second
                    stuckTicks += 20;
                } else {
                    stuckTicks = 0;
                    recoveryAttempts = 0; // Making solid progress again
                }
                stuckCheckLastPos = playerPos;
                stuckCheckTimer = 0;
            }
        } else if (recoveryTicks == 0) {
            stuckCheckTimer = 0;
            stuckTicks = 0;
            stuckCheckLastPos = null;
        }

        // Randomize evasive strafing
        if (stuckTicks > 10 && randomStrafeTicks <= 0 && !inCobweb) {
            randomStrafeDir = random.nextInt(3) - 1; // -1, 0, or 1
            randomStrafeTicks = 5 + random.nextInt(10);
            if (random.nextBoolean() && jumpHoldTicks <= 0) {
                jumpHoldTicks = 2 + random.nextInt(4); // Add random organic jumps to help break free
            }
        }

        // Trigger ultimate recovery or suicide
        if (stuckTicks > 40) {
            recoveryAttempts++;
            if (recoveryAttempts >= (int) com.nezurstandalone.pathfinder.PathfinderConfig.stuckGiveUp.value) { // failed escalations -> last resort
                if (com.nezurstandalone.engine.GrinderEngine.isAutoRaffleActive()) {
                    PathfinderManager.fail();return;
                }
                suicideMode = true;
                suicideTimer = 100;
                suicideAttempts = 0;
                NotificationManager.show("§c[AutoWalker] Recovery limit reached. Trying /oof up to three times.", 4000);
                return;
            } else {
                net.minecraft.util.BlockPos stuckTarget = new net.minecraft.util.BlockPos(movementTarget.xCoord, movementTarget.yCoord, movementTarget.zCoord);
                blacklist(stuckTarget);

                // Climb the ladder, then cycle back through it with fresh randomisation
                // rather than replaying one fixed routine forever.
                recoveryStage = (recoveryAttempts - 1) % 3;
                recoveryStrafeDir = recoveryStage == 0 ? (random.nextBoolean() ? -1 : 1) : 0;
                switch (recoveryStage) {
                    case 0:  recoveryTicks = 12 + random.nextInt(8);  break;
                    case 1:  recoveryTicks = 18 + random.nextInt(10); break;
                    default: recoveryTicks = 24 + random.nextInt(12); break;
                }
                recoveryNextScanTick = recoveryTicks - (6 + random.nextInt(9));
                stuckTicks = 0;
            }
        }

        lastPos = playerPos;
    }

    /**
     * Re-runs the search toward {@code destination} while avoiding everything the driver has
     * snagged on. Walking is explicit now rather than inherited from a global flag.
     */
    private void repath(net.minecraft.util.BlockPos destination) {
        PathfinderManager.repathTo(destination.getX(), destination.getY(), destination.getZ(),
                getBlacklistedBlocks().isEmpty() ? null : avoidSnapshot());
    }

    /**
     * How far ahead on the curve to steer, shortened going into a bend.
     *
     * <p>A long look-ahead rounds corners early and keeps the heading continuous; too long and
     * the bot cuts across the inside of a turn and clips the geometry the route was avoiding.
     * Comparing the tangent at the near and far ends of the window detects the bend and pulls
     * the target back in for the duration of it.
     */
    private double lookAheadFor(PathSnapshot snapshot, double progress) {
        double lookMin = com.nezurstandalone.pathfinder.PathfinderConfig.lookAheadMin.value;
        double lookMax = com.nezurstandalone.pathfinder.PathfinderConfig.lookAheadMax.value;
        Vec3 near = snapshot.tangentAt(progress + lookMin);
        Vec3 far = snapshot.tangentAt(progress + lookMax);
        if (near == null || far == null) {
            return lookMin;
        }
        double dot = near.xCoord * far.xCoord + near.zCoord * far.zCoord;
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double bend = Math.toDegrees(Math.acos(dot));
        double straightness = 1.0 - Math.min(1.0, bend / com.nezurstandalone.pathfinder.PathfinderConfig.bendLimit.value);
        return lookMin + (lookMax - lookMin) * straightness;
    }

    /**
     * Ornstein-Uhlenbeck drift across the corridor.
     *
     * <p>The previous per-node random offset was disabled outright because redrawing it at
     * every node made the target jump sideways and snagged the player on thin blocks. An OU
     * process has no jumps: it is a mean-reverting random walk, so the offset wanders smoothly
     * over roughly a second and always pulls back toward the centre of the path. Without it the
     * bot tracks the exact block centreline, and the residual of its position against the block
     * lattice is identically zero.
     */
    private void advanceLateralDrift() {
        if (!com.nezurstandalone.pathfinder.PathfinderConfig.lateralDrift.enabled || recoveryTicks > 0) {
            lateralOffset *= 1.0 - DRIFT_THETA; // Decay to centre while recovering or when disabled.
            return;
        }
        double limit = com.nezurstandalone.pathfinder.PathfinderConfig.driftAmount.value;
        lateralOffset = lateralOffset * (1.0 - DRIFT_THETA) + random.nextGaussian() * DRIFT_STEP_SIGMA;
        if (lateralOffset > limit) lateralOffset = limit;
        if (lateralOffset < -limit) lateralOffset = -limit;
    }

    /**
     * Offsets {@code point} perpendicular to the path, but only as far as there is room. The
     * clearance test is what makes the drift safe to re-enable: a doorway or a run of iron bars
     * simply squeezes it back to zero instead of walking the player into the frame.
     */
    private Vec3 applyLateralDrift(Vec3 point, Vec3 tangent) {
        if (tangent == null || Math.abs(lateralOffset) < 1.0e-3) {
            return point;
        }
        // Left-hand normal of the tangent in the XZ plane.
        double nx = -tangent.zCoord;
        double nz = tangent.xCoord;

        double offset = lateralOffset;
        for (int attempt = 0; attempt < 3 && Math.abs(offset) > 1.0e-3; attempt++) {
            double x = point.xCoord + nx * offset;
            double z = point.zCoord + nz * offset;
            if (hasClearance(x, point.yCoord, z)) {
                return new Vec3(x, point.yCoord, z);
            }
            offset *= 0.5;
        }
        return point;
    }

    /** Body-height clearance test at a candidate position. */
    private boolean hasClearance(double x, double y, double z) {
        int bx = net.minecraft.util.MathHelper.floor_double(x);
        int bz = net.minecraft.util.MathHelper.floor_double(z);
        int by = net.minecraft.util.MathHelper.floor_double(y);
        for (int dy = 0; dy <= 1; dy++) {
            scratchPos.set(bx, by + dy, bz);
            if (mc.theWorld.getBlockState(scratchPos).getBlock().getMaterial().isSolid()) {
                return false;
            }
        }
        return true;
    }

    /** Log-normal reaction time. Uniform delays have the wrong shape and a hard floor. */
    private long sampleReactionMs(double medianMs, double sigma, long min, long max) {
        double value = medianMs * Math.exp(random.nextGaussian() * sigma);
        return (long) Math.max(min, Math.min(max, value));
    }

    /** A solid, non-pass-through block at foot or head height at this world point. */
    private boolean solidColumnAt(double x, double z) {
        if (mc.theWorld == null) {
            return false;
        }
        int bx = net.minecraft.util.MathHelper.floor_double(x);
        int bz = net.minecraft.util.MathHelper.floor_double(z);
        int by = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posY + 0.1);
        for (int dy = 0; dy <= 1; dy++) {
            scratchPos.set(bx, by + dy, bz);
            net.minecraft.block.Block b = mc.theWorld.getBlockState(scratchPos).getBlock();
            if (!b.getMaterial().isSolid()) {
                continue;
            }
            if (b instanceof net.minecraft.block.BlockBasePressurePlate
                    || b instanceof net.minecraft.block.BlockCarpet
                    || b instanceof net.minecraft.block.BlockSign
                    || b instanceof net.minecraft.block.BlockDoor
                    || b instanceof net.minecraft.block.BlockVine
                    || b instanceof net.minecraft.block.BlockLadder
                    || b instanceof net.minecraft.block.BlockSnow) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * True when a full two-block-tall solid face sits directly ahead in the facing direction -
     * solid at both foot and head height 0.6 blocks out. That is a wall a jump cannot clear (the
     * side of a step, a full block), as opposed to a one-block step, which is solid at the feet but
     * open at the head. Used to suppress jumping so the walker strafes around to the low side of a
     * step instead of pogo-ing against its tall face.
     */
    private boolean tallFaceAhead() {
        if (mc.theWorld == null) {
            return false;
        }
        double rad = Math.toRadians(mc.thePlayer.rotationYaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        int bx = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX + fx * 0.6);
        int bz = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ + fz * 0.6);
        int fy = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posY + 0.1);
        return solidBlockAt(bx, fy, bz) && solidBlockAt(bx, fy + 1, bz);
    }

    /**
     * True when a one-block step sits directly ahead in the facing direction: solid at foot
     * height, clear at head height, and clear again above that, 0.75 blocks out. That is a ledge
     * a single jump lands on top of. Probed a little further than the tall-face check so the hop
     * arms just before the body reaches the step, giving the sprint-jump room to carry up onto it.
     */
    private boolean stepUpAhead() {
        if (mc.theWorld == null) {
            return false;
        }
        double rad = Math.toRadians(mc.thePlayer.rotationYaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        int bx = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX + fx * 0.75);
        int bz = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ + fz * 0.75);
        int fy = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posY + 0.1);
        return solidBlockAt(bx, fy, bz)
                && !solidBlockAt(bx, fy + 1, bz)
                && !solidBlockAt(bx, fy + 2, bz);
    }

    /** A solid, non-pass-through block at exactly (x,y,z). */
    private boolean solidBlockAt(int x, int y, int z) {
        scratchPos.set(x, y, z);
        net.minecraft.block.Block b = mc.theWorld.getBlockState(scratchPos).getBlock();
        if (!b.getMaterial().isSolid()) {
            return false;
        }
        return !(b instanceof net.minecraft.block.BlockBasePressurePlate
                || b instanceof net.minecraft.block.BlockCarpet
                || b instanceof net.minecraft.block.BlockSign
                || b instanceof net.minecraft.block.BlockDoor
                || b instanceof net.minecraft.block.BlockVine
                || b instanceof net.minecraft.block.BlockLadder
                || b instanceof net.minecraft.block.BlockSnow);
    }

    private double getHorizontalDistance(Vec3 p1, Vec3 p2) {
        double dx = p1.xCoord - p2.xCoord;
        double dz = p1.zCoord - p2.zCoord;
        return Math.sqrt(dx * dx + dz * dz);
    }
}

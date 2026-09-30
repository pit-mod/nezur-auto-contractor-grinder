package com.nezurstandalone.pathfinder;

import com.nezurstandalone.pathfinder.PathfinderConfig;
import com.nezurstandalone.utils.NotificationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * The only way in or out of the pathfinder.
 *
 * <p>Previously {@code currentEndPos}, {@code autoMove}, {@code PathRenderer.rawPath} and
 * {@code PathRenderer.pathVersion} were public mutable statics, and five modules wrote to
 * them directly. Two concrete failures came out of that:
 *
 * <ul>
 *   <li>{@code autoMove} is global, so whether SewerHelper/PizzaHelper/BlockheadHelper
 *       actually <em>walked</em> a route depended on whichever module last set the flag —
 *       they never set it themselves. Walking is now a property of the request.</li>
 *   <li>A consumer assigning {@code currentEndPos} directly could disagree with the search
 *       the executor was actually running, and a reader taking path and destination in two
 *       separate reads could see one from each of two navigations.</li>
 * </ul>
 *
 * <p>State is now private. A navigation is an immutable {@link Navigation} record swapped
 * atomically, and the computed route is an immutable {@link PathSnapshot} swapped atomically,
 * so every reader sees a consistent pair.
 */
public final class PathfinderManager {
    public enum State { IDLE, SEARCHING, ACTIVE, COMPLETED, FAILED, CANCELLED }
    public enum Outcome { SUCCESS, PARTIAL, NO_PATH, CANCELLED, ERROR }
    private static volatile State state = State.IDLE;
    private static volatile Outcome outcome;
    private static Object owner;
    private static NavigationHandle handle;
    private static long handleGeneration;
    public static final class NavigationHandle {
        private final Object owner; private final long session,generation;
        private NavigationHandle(Object owner){this.owner=owner;session=com.nezurstandalone.control.ClientSession.current();generation=++handleGeneration;}
        public boolean valid(){return this==handle && owner==PathfinderManager.owner && session==com.nezurstandalone.control.ClientSession.current();}
    }
    public static NavigationHandle claim(Object requester){return acquire(requester)?handle:null;}
    public static boolean stop(NavigationHandle h){if(h==null||!h.valid())return false;AutoWalker.INSTANCE.stop();return true;}
    public static boolean reset(NavigationHandle h){if(h==null||!h.valid())return false;AutoWalker.INSTANCE.resetForNewSession();return true;}
    public static boolean combatTarget(NavigationHandle h,net.minecraft.entity.player.EntityPlayer target){if(h==null||!h.valid())return false;AutoWalker.INSTANCE.setCombatTarget(target);return true;}

    // ---- no-owner pokes ------------------------------------------------------------------
    // Clearing a combat target, stopping an idle walker or writing combat tuning are
    // maintenance gestures, not navigations. Performing them through claim() made every
    // caller silently TAKE ownership whenever the manager happened to be free — and the
    // owners that called them as post-combat cleanup never navigate again, so the lease
    // looked busy to every other module forever. That is AutoSewer's "Pathing: no" while a
    // toggled-off AutoGrinder still holds it. These variants apply the global effect and
    // never acquire.

    /** Clears the walker's chase target. Never acquires or changes ownership. */
    public static void clearCombatTarget() { AutoWalker.INSTANCE.setCombatTarget(null); }

    /** Points the walker's chase at a player for an active combat flow. Never acquires. */
    public static void setCombatTarget(net.minecraft.entity.player.EntityPlayer target) { AutoWalker.INSTANCE.setCombatTarget(target); }

    /** Writes walker combat tuning. Global configuration; never acquires. */
    public static void combatSettings(boolean strafe, boolean jumps, double reach) {
        AutoWalker.INSTANCE.combatStrafe=strafe; AutoWalker.INSTANCE.combatJumps=jumps; AutoWalker.INSTANCE.combatReach=reach;
    }

    /** Stops the walker only when the manager is free or already owned by the requester. */
    public static boolean stopIfAvailable(Object requester) { if (!available(requester)) return false; AutoWalker.INSTANCE.stop(); return true; }

    /** Full walker session wipe only when the manager is free or already owned by the requester. */
    public static boolean resetIfAvailable(Object requester) { if (!available(requester)) return false; AutoWalker.INSTANCE.resetForNewSession(); return true; }
    public static boolean combatSettings(NavigationHandle h,boolean strafe,boolean jumps,double reach){if(h==null||!h.valid())return false;AutoWalker.INSTANCE.combatStrafe=strafe;AutoWalker.INSTANCE.combatJumps=jumps;AutoWalker.INSTANCE.combatReach=reach;return true;}
    public static State walkTo(NavigationHandle h,double x,double y,double z,boolean silent){if(h==null||!h.valid())return State.CANCELLED;navigate(x,y,z,silent,true,false,null);return state;}
    public static boolean clear(NavigationHandle h,boolean silent){if(h==null||!h.valid())return false;clear(h.owner,silent);return true;}

    private static final Object LEGACY_OWNER = new Object();
    private static AStarPathfinder preparing;
    private static Runnable launchPrepared;
    static boolean hasValidHandle(){return handle!=null&&handle.valid();}
    public static State getState() { return state; }
    public static Outcome getOutcome() { return outcome; }
    public static boolean acquire(Object requester) {
        if(requester==null)return false;
        if(handle!=null&&!handle.valid()){cancelInternal(true);owner=null;handle=null;}
        if (owner != null && owner != requester) return false;
        owner = requester; if(handle==null||!handle.valid())handle=new NavigationHandle(requester); return true;
    }
    /** acquire() that refuses quietly from the caller's point of view but never from the log's. */
    private static long lastRefusalLog;
    private static boolean acquireOrLog(Object requester) {
        if (acquire(requester)) return true;
        long now = System.currentTimeMillis();
        if (now - lastRefusalLog > 5000L) {
            lastRefusalLog = now;
            Object o = owner;
            String holder = o == null ? "idle" : (o == LEGACY_OWNER ? "command" : o.getClass().getSimpleName());
            System.out.println("[Nezur] Pathfinder busy (held by " + holder + "); request from "
                    + (requester == null ? "null" : requester.getClass().getSimpleName()) + " refused");
        }
        return false;
    }

    public static State walkTo(Object requester,double x,double y,double z,boolean silent) {
        if (!acquireOrLog(requester)) return State.CANCELLED;
        navigate(x,y,z,silent,true,false,null); return state;
    }
    public static void clear(Object requester, boolean silent) {
        if (owner == null || owner == requester) { cancelInternal(silent); owner = null; }
    }

    /** Releases the lease only when the requester currently owns it; never disturbs anyone else. */
    public static boolean release(Object requester) {
        if (owner == null || owner != requester) return false;
        cancelInternal(true); owner = null; return true;
    }
    public static boolean available(Object requester) { return owner == null || owner == requester; }
    public static State walkTo(Object requester,double x,double y,double z,boolean silent,Predicate<BlockPos> bounds) {
        if(!acquireOrLog(requester))return State.CANCELLED; navigate(x,y,z,silent,true,false,bounds); return state;
    }
    public static void gotoXYZ(Object requester,double x,double y,double z,boolean silent,Predicate<BlockPos> bounds) {
        if(acquireOrLog(requester))navigate(x,y,z,silent,false,false,bounds);
    }
    private static void prepareStep() {
        if (preparing == null) return;
        try {
            // 16384 cells per tick: the capture is the bottleneck on long searches (the search
            // itself is off-thread), and at the old 4096 a spawn-to-camp box needed seven or
            // more seconds of preparation before compute() could even start — usually past the
            // caller's repath/timeout. Air cells now cost a map insert instead of a full
            // collision sweep, so this budget is comfortably inside the frame.
            if (preparing.prepare(16384)) {
                Runnable launch = launchPrepared; preparing = null; launchPrepared = null; launch.run();
            }
        } catch (RuntimeException error) {
            // A block update or chunk packet overlapping the search box invalidates the
            // capture mid-preparation — routine while chunks stream in, and the box is
            // prepared over several ticks. The search never ran, so this is retryable,
            // not a failure: drop the poisoned attempt and leave the navigation
            // published. SessionWatcher re-issues the request on its next tick
            // (reissueIfDropped) and the fresh search begins a new capture. Real search
            // failures throw inside the worker's own try/catch, not here.
            preparing = null; launchPrepared = null;
            state = State.CANCELLED; outcome = Outcome.CANCELLED;
            System.out.println("[Nezur] Path capture invalidated mid-preparation; re-issuing");
        }
    }


    /** An in-flight or completed navigation request. Immutable. */
    private static final class Navigation {
        static final Navigation NONE = new Navigation(0, null, false);

        final long epoch = com.nezurstandalone.control.ClientSession.current();
        final int session;
        final BlockPos destination;
        final boolean walk;
        final net.minecraft.world.World world;
        final Object player;
        final Predicate<BlockPos> bounds;



        Navigation(int session, BlockPos destination, boolean walk) {
            this(session, destination, walk, null);
        }

        Navigation(int session, BlockPos destination, boolean walk, Predicate<BlockPos> bounds) {
            this.session = session;
            this.destination = destination;
            this.walk = walk;
            Minecraft mc = Minecraft.getMinecraft();
            this.world = mc.theWorld;
            this.player = mc.thePlayer;
            this.bounds = bounds;
        }
    }


    private static final PathRenderer renderer = new PathRenderer();
    private static boolean rendererRegistered = false;

    /**
     * Daemon so a search in flight can never hold the JVM open on quit, and named so it is
     * identifiable in a profiler or a crash report.
     */
    private static final java.util.concurrent.ThreadPoolExecutor EXECUTOR = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "Nezur-Pathfinder");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }
    });

    private static final class SessionWatcher {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void onTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
            if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START) return;
            Navigation navigation = NAVIGATION.get();
            if (navigation.destination == null) return;
            if (!sameClient(navigation)) { cancelInternal(true); owner = null; return; }
            prepareStep();
            reissueIfDropped(navigation);
            if (navigation.walk) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (atVisualDestination(navigation.destination)) { clear(true); return; }
            // Only a sustained detour or substantial backtracking warrants a search.
            if (visualTracker != null && (currentTask == null || currentTask.isDone())
                    && visualTracker.shouldRefresh(mc.thePlayer.posX, mc.thePlayer.posY,
                            mc.thePlayer.posZ, System.nanoTime())) {
                BlockPos end = navigation.destination;
                navigate(end.getX(), end.getY(), end.getZ(), true, false, true, navigation.bounds);
            }
        }
    }

    /**
     * Re-issues a pending navigation whose search died before it could publish anything —
     * the capture was invalidated mid-preparation, or while the worker was computing.
     * The destination stays published and a walker already driving a previous route stays
     * active, so the engine's {@code isPathing()} never observes a gap; without this the
     * state machine waits forever on a navigation nobody is searching for. Resuming when
     * the walker is driving preserves its session state (blacklist, recovery stage,
     * attempt counter), exactly like a walker-issued repath.
     */
    private static void reissueIfDropped(Navigation navigation) {
        if (state != State.CANCELLED || preparing != null) return;
        Future<?> task = currentTask;
        if (task != null && !task.isDone()) return;
        BlockPos end = navigation.destination;
        boolean walk = navigation.walk;
        navigate(end.getX(), end.getY(), end.getZ(), true, walk,
                walk && AutoWalker.INSTANCE.isActive(), navigation.bounds);
    }

    private static boolean sameClient(Navigation navigation) {
        Minecraft mc = Minecraft.getMinecraft();
        return navigation.epoch == com.nezurstandalone.control.ClientSession.current() && navigation.world != null && navigation.world == mc.theWorld
                && navigation.player != null && navigation.player == mc.thePlayer;
    }

    private static final AtomicInteger SESSIONS = new AtomicInteger(0);
    private static final AtomicReference<Navigation> NAVIGATION = new AtomicReference<Navigation>(Navigation.NONE);
    private static final AtomicReference<PathSnapshot> SNAPSHOT = new AtomicReference<PathSnapshot>(PathSnapshot.EMPTY);

    private static volatile Future<?> currentTask = null;
    private static VisualRepathTracker visualTracker;

    private PathfinderManager() {
    }

    // ------------------------------------------------------------------ query

    /** The destination of the current navigation, set the moment it is requested. */
    public static BlockPos getDestination() {
        return NAVIGATION.get().destination;
    }

    public static boolean hasDestination() {
        return NAVIGATION.get().destination != null;
    }

    public static boolean isWalking() { return NAVIGATION.get().walk; }

    private static boolean atVisualDestination(BlockPos destination) {
        Minecraft mc = Minecraft.getMinecraft();
        double dx=mc.thePlayer.posX-(destination.getX()+0.5);
        double dz=mc.thePlayer.posZ-(destination.getZ()+0.5);
        return dx*dx+dz*dz <= 1.5625 && Math.abs(mc.thePlayer.posY-destination.getY()) <= 1.5;
    }

    /** Replaces the old {@code currentEndPos == null || !currentEndPos.equals(pos)} idiom. */
    public static boolean isNavigatingTo(BlockPos pos) {
        BlockPos destination = NAVIGATION.get().destination;
        return destination != null && destination.equals(pos);
    }

    /** The current route. Never null; consistent with its own destination and version. */
    public static PathSnapshot getSnapshot() {
        return SNAPSHOT.get();
    }

    public static int getPathLength() {
        return SNAPSHOT.get().size();
    }

    /** True while a destination is set or the walker is still driving. */
    public static boolean isPathing() {
        return hasDestination() || AutoWalker.INSTANCE.isActive();
    }

    // -------------------------------------------------------------- commands

    /** Computes and draws a route without walking it. */
    public static void gotoXYZ(double x, double y, double z) {
        if (!acquire(LEGACY_OWNER)) return;
        navigate(x, y, z, false, false, false, null);
    }

    public static void gotoXYZ(double x, double y, double z, boolean silent) {
        if (!acquire(LEGACY_OWNER)) return;
        navigate(x, y, z, silent, false, false, null);
    }

    public static void gotoXYZ(double x, double y, double z, boolean silent, Predicate<BlockPos> bounds) {
        if (!acquire(LEGACY_OWNER)) return;
        navigate(x, y, z, silent, false, false, bounds);
    }

    /** Computes a route and drives the player along it, from a clean walker state. */
    public static void walkTo(double x, double y, double z, boolean silent) {
        if (!acquire(LEGACY_OWNER)) return;
        navigate(x, y, z, silent, true, false, null);
    }

    public static void walkTo(double x, double y, double z, boolean silent, Predicate<BlockPos> bounds) {
        if (!acquire(LEGACY_OWNER)) return;
        navigate(x, y, z, silent, true, false, bounds);
    }

    /**
     * Recomputes the route for a navigation the walker is <em>already</em> driving.
     *
     * <p>Distinct from {@link #walkTo} because the two need opposite things from the driver.
     * A new navigation wants a clean slate; a repath issued from inside a recovery sequence
     * must keep the blacklist it just passed in as {@code bounds} and the attempt counter
     * that decides when to give up. Routing both through {@code AutoWalker.start()} meant the
     * recovery repath wiped both, so the search was handed an empty avoid-set, returned the
     * route that had just failed, and the walker looped on the same obstacle indefinitely.
     */
    static void repathTo(double x, double y, double z, Predicate<BlockPos> bounds) {
        navigate(x, y, z, true, true, true, bounds);
    }

    /**
     * @param silent suppresses the progress toasts
     * @param walk   whether to hand the finished route to {@link AutoWalker}
     * @param resume true when the walker is already driving this navigation, so its session
     *               state (blacklist, recovery stage, attempt counter) must be preserved
     * @param bounds per-node filter (zone locks, blacklists); may be null
     */
    private static void navigate(double x, double y, double z, boolean silent, boolean walk,
                                 boolean requestedResume, Predicate<BlockPos> bounds) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            cancelInternal(true);
            return;
        }
        if (!rendererRegistered) {
            MinecraftForge.EVENT_BUS.register(renderer);
            MinecraftForge.EVENT_BUS.register(new SessionWatcher());
            rendererRegistered = true;
        }

        final net.minecraft.world.World requestWorld = mc.theWorld;
        final int iterationLimit = (int) PathfinderConfig.maxIterations.value;
        final BlockPos startPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        final BlockPos endPos = new BlockPos(x, y, z);
        if (!walk && atVisualDestination(endPos)) {
            cancelInternal(true);
            return;
        }
        Navigation existing = NAVIGATION.get();
        if (!requestedResume && sameClient(existing) && endPos.equals(existing.destination)
                && existing.walk == walk && (state == State.SEARCHING || state == State.ACTIVE)) {
            if(state==State.ACTIVE && walk && !AutoWalker.INSTANCE.isActive()) AutoWalker.INSTANCE.onPathPublished();
            return;
        }
        final boolean resume = requestedResume || (sameClient(existing) && existing.walk == walk && AutoWalker.INSTANCE.isActive());
        final int session = SESSIONS.incrementAndGet();
        state = State.SEARCHING; outcome = null;
        visualTracker = null;

        if (!resume) {
            SNAPSHOT.set(PathSnapshot.EMPTY);
            PathRenderer.setPath(null);
            AutoWalker.INSTANCE.stop();
        }

        // Publish the destination before the search starts: callers dedupe on it immediately
        // after requesting, and the walker needs it to measure true arrival.
        NAVIGATION.set(new Navigation(session, endPos, walk, bounds));

        Future<?> previous = currentTask;
        if (previous != null && !previous.isDone()) {
            previous.cancel(true);
        }

        EXECUTOR.purge(); // Remove cancelled searches before accepting the latest request.
        final AStarPathfinder pathfinder;
        try { pathfinder = new AStarPathfinder(requestWorld, startPos, endPos, iterationLimit, true, bounds); }
        catch (RuntimeException failure) {
            cancelInternal(true); state=State.FAILED; outcome=Outcome.ERROR;
            System.out.println("[Nezur] Path search setup failed for " + endPos.getX() + ", " + endPos.getY()
                    + ", " + endPos.getZ() + ": " + failure.getMessage());
            // Silent navigations re-issue from their engine every tick; a toast here used to
            // mean one toast per tick for as long as the target stayed out of reach.
            if (!silent) {
                NotificationManager.show("§c[Pathfinder] Path blocked or too far!", 3000);
            }
            return;
        }
        preparing = pathfinder;
        launchPrepared = () -> { currentTask = EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                if (isSuperseded(session)) {
                    return;
                }
                if (!silent) {
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (!isSuperseded(session)) {
                                NotificationManager.show("§e[Pathfinder] Calculating route...", 3000);
                            }
                        }
                    });
                }

                final List<Vec3> nodes;
                final boolean isPartial;
                try {
                    nodes = pathfinder.compute();
                    isPartial = pathfinder.isPartial();
                } catch (RuntimeException failure) {
                    // compute() opens with snapshot.prepare(0), which throws the same two
                    // capture-invalidation errors the main thread sees. The world changed
                    // under the search while it ran, so nothing was published and the
                    // result was unusable anyway - retryable, exactly like a
                    // mid-preparation invalidation. Anything else is a real search
                    // failure and still tears the navigation down.
                    final boolean invalidated = isCaptureInvalidation(failure);
                    Minecraft.getMinecraft().addScheduledTask(() -> {
                        if (isSuperseded(session)) return;
                        if (invalidated) {
                            state=State.CANCELLED; outcome=Outcome.CANCELLED;
                            System.out.println("[Nezur] Path capture invalidated during compute; re-issuing");
                        } else {
                            cancelInternal(true); state=State.FAILED; outcome=Outcome.ERROR;
                            NotificationManager.show("Path search failed; navigation stopped.", 4000);
                        }
                    });
                    System.err.println("[Nezur] Path search failed: " + failure);
                    return;
                }

                // Second check before paying for the main-thread hop at all.
                if (isSuperseded(session) || Thread.currentThread().isInterrupted()) {
                    return;
                }

                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        // The world changed under the search while it computed, so the
                        // route was never published. Retryable exactly like a
                        // mid-preparation invalidation: drop the attempt but keep the
                        // navigation, and the session watcher re-issues it. cancelInternal
                        // here would strand the caller with no destination and no
                        // re-issued search.
                        if(!pathfinder.captureValid()){if(!isSuperseded(session)){state=State.CANCELLED;outcome=Outcome.CANCELLED;System.out.println("[Nezur] Path capture invalidated during compute; re-issuing");}return;}
                        publish(session, endPos, nodes, isPartial, silent, walk, resume);
                    }
                });
            }
        }); };
        prepareStep();
    }

    private static boolean isSuperseded(int session) {
        return NAVIGATION.get().session != session;
    }

    /**
     * The two errors {@link SearchSnapshot#prepare} throws when the collision capture was
     * dropped by a world revision. Only these mean "the search never produced a usable
     * route"; anything else reaching a catch block is a genuine failure. Matched by
     * message so the classification needs no cross-thread world reads on the worker.
     */
    private static boolean isCaptureInvalidation(RuntimeException failure) {
        String message = failure.getMessage();
        return message != null
                && (message.contains("Collision capture invalidated")
                    || message.contains("Collision capture identity changed"));
    }

    /**
     * Main thread only. The session check here is the one that actually matters: it closes
     * the window where a search finishes just as a newer one is requested, which would
     * otherwise overwrite the fresh route with a stale one.
     */
    private static void publish(int session, BlockPos destination, List<Vec3> nodes,
                                boolean isPartial, boolean silent, boolean walk, boolean resume) {
        if (isSuperseded(session)) {
            return;
        }

        if (!sameClient(NAVIGATION.get())) {
            cancelInternal(true);
            return;
        }
        if (nodes == null || nodes.isEmpty() || (nodes.size() == 1 && isPartial)) {
            cancelInternal(true); state=State.FAILED; outcome=Outcome.NO_PATH;
            // Engine navigations are silent, so a route-less search used to vanish without a
            // trace; the console line is the only footprint a NO_PATH leaves.
            System.out.println("[Nezur] Path search found no route to "
                    + destination.getX() + ", " + destination.getY() + ", " + destination.getZ());
            if (!silent) {
                NotificationManager.show("§c[Pathfinder] Path blocked or too far!", 3000);
            }
            return;
        }

        if (!walk && nodes.size() >= 2) {
            double[] x=new double[nodes.size()], y=new double[nodes.size()], z=new double[nodes.size()];
            for (int i=0;i<nodes.size();i++) {
                Vec3 point=nodes.get(i);x[i]=point.xCoord;y[i]=point.yCoord;z[i]=point.zCoord;
            }
            visualTracker=new VisualRepathTracker(new RouteCurve(x,y,z),System.nanoTime());
        }
        PathSnapshot snapshot = new PathSnapshot(session, destination, nodes, isPartial);
        state=State.ACTIVE; outcome=isPartial ? Outcome.PARTIAL : Outcome.SUCCESS;
        SNAPSHOT.set(snapshot);
        PathRenderer.setPath(snapshot);

        if (!silent) {
            NotificationManager.show("§a[Pathfinder] Path found!", 3000);
        }
        if (walk) {
            if (resume) {
                AutoWalker.INSTANCE.onPathPublished();
            } else {
                AutoWalker.INSTANCE.start();
            }
        }
    }

    public static void clear() {
        clear(false);
    }

    /** Cancels any search, drops the route, and stops the walker. */
    public static void clear(boolean silent) {
        if (owner != null && owner != LEGACY_OWNER) return;
        cancelInternal(silent); owner = null;
    }
    /** Walker completion/recovery is part of the currently leased navigation. */
    static void complete() { cancelInternal(true); owner=null; state=State.COMPLETED; }
    private static void cancelInternal(boolean silent) {
        WorldCapture.cancel();
        preparing=null; launchPrepared=null;
        state=State.CANCELLED; outcome=Outcome.CANCELLED;
        // Bump the session first so a search landing during this call is already superseded.
        SESSIONS.incrementAndGet();
        NAVIGATION.set(Navigation.NONE);
        visualTracker = null;

        Future<?> task = currentTask;
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        currentTask = null;

        SNAPSHOT.set(PathSnapshot.EMPTY);
        AutoWalker.INSTANCE.stop();
        PathRenderer.setPath(null);

        if (!silent) {
            NotificationManager.show("§a[Pathfinder] Cleared path.", 3000);
        }
    }
}

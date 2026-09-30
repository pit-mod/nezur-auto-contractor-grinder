package com.nezurstandalone.pathfinder;

import com.nezurstandalone.settings.BooleanSetting;
import com.nezurstandalone.settings.NumberSetting;
import com.nezurstandalone.settings.Setting;

/**
 * Every tunable the route search and the walker used to hard-code, in one place, as live
 * setting objects.
 *
 * <p>These are the same objects the {@code Pathfinder} tuning module hands to the ClickGUI, so
 * editing a slider in game changes the value the search reads on its very next node. The search
 * runs on a worker thread and reads the plain {@code value} fields directly: a double read is
 * atomic on every JVM this runs on, and a half-applied tuning value is harmless — it only ever
 * shifts a cost, never corrupts the search.
 *
 * <p>Defaults are the numbers the pathfinder shipped with, so a fresh install and a config with
 * no pathfinder block both behave exactly as before. Everything here is static: there is one
 * pathfinder for the whole client, and the config is read from a background thread with no
 * module instance in scope, so a static home is both correct and the simplest thing that works.
 */
public final class PathfinderConfig {

    private PathfinderConfig() {
    }

    // ── Search: budget & shape ────────────────────────────────────────────────

    /** How many nodes the search may expand before it returns the closest reachable one. */
    public static final NumberSetting maxIterations =
            new NumberSetting("Search Budget", 15000, 2000, 40000, 0);

    /**
     * Heuristic weight. 1.0 is the classic admissible A* that always returns the shortest
     * route; above 1 the search grows greedy — faster and cheaper, but it can settle for a
     * longer path. The old code baked in 0.99.
     */
    public static final NumberSetting heuristicWeight =
            new NumberSetting("Heuristic Weight", 0.99, 0.50, 2.00, 2);

    // ── Search: movement cost model ───────────────────────────────────────────

    /** Extra cost for changing horizontal direction, so routes prefer straight runs. */
    public static final NumberSetting turnPenalty =
            new NumberSetting("Turn Penalty", 0.50, 0.00, 3.00, 2);

    /** Cost multiplier on climbing a ladder or vine, per block. Low = strongly preferred. */
    public static final NumberSetting climbCost =
            new NumberSetting("Climb Cost", 0.50, 0.05, 4.00, 2);

    /** Cost multiplier on falling, per block. Near zero makes drops almost free. */
    public static final NumberSetting fallCost =
            new NumberSetting("Fall Cost", 0.00, 0.00, 1.00, 2);

    /** Cost multiplier on jumping up a block. High makes the route avoid needless climbs. */
    public static final NumberSetting jumpCost =
            new NumberSetting("Jump-Up Cost", 1.50, 0.10, 5.00, 2);

    /** Flat penalty added to any node that drops the player into a hole. */
    public static final NumberSetting holePenalty =
            new NumberSetting("Hole Penalty", 1000, 0, 5000, 0);

    /** Cost added per solid block beside a cell, biasing routes toward open centres off walls. */
    public static final NumberSetting wallClearance =
            new NumberSetting("Wall Clearance", 0.55, 0.00, 3.00, 2);

    /** Whether the search penalises holes at all. Off lets it path straight across pits. */
    public static final BooleanSetting avoidHoles =
            new BooleanSetting("Avoid Holes", true);

    // ── Search: anti-bridge field ─────────────────────────────────────────────

    /**
     * Strength of the outward push applied when the goal is below the player and close in the
     * horizontal plane, so the search looks for an edge to drop off instead of bridging down.
     */
    public static final NumberSetting antiBridgeStrength =
            new NumberSetting("Anti-Bridge Strength", 2.50, 0.00, 8.00, 2);

    /** Horizontal radius, in blocks, over which the anti-bridge push applies. */
    public static final NumberSetting antiBridgeRange =
            new NumberSetting("Anti-Bridge Range", 12.0, 0.0, 32.0, 1);

    // ── Search: geometry limits ───────────────────────────────────────────────

    /** Tallest step the player is allowed to jump up onto, in blocks. */
    public static final NumberSetting maxStepUp =
            new NumberSetting("Max Step Up", 1.25, 0.50, 2.00, 2);

    /** Rise the search treats as a free step, needing no jump. */
    public static final NumberSetting stepAssist =
            new NumberSetting("Step Assist", 0.60, 0.10, 1.25, 2);

    /** Deepest fall, in blocks, the search will accept as a valid landing. */
    public static final NumberSetting maxFall =
            new NumberSetting("Max Fall Blocks", 40, 3, 80, 0);

    // ── Walker: steering ──────────────────────────────────────────────────────

    /** Nearest distance ahead on the smoothed curve the walker steers toward, in blocks. */
    public static final NumberSetting lookAheadMin =
            new NumberSetting("Look-Ahead Min", 1.50, 0.50, 6.00, 2);

    /** Farthest look-ahead, used on straights and pulled back into bends. */
    public static final NumberSetting lookAheadMax =
            new NumberSetting("Look-Ahead Max", 2.60, 0.50, 8.00, 2);

    /** Bend across the look-ahead window, in degrees, at which it is fully shortened. */
    public static final NumberSetting bendLimit =
            new NumberSetting("Bend Limit", 40.0, 5.0, 90.0, 0);

    /**
     * Scales the walker's turn speed. The walker already picks a speed from how far off its
     * heading is (a hard flick for a big correction, a gentle nudge for a small one); this
     * multiplies all three tiers at once, so 1.0 is the shipped feel and higher snaps harder.
     */
    public static final NumberSetting turnSpeedMult =
            new NumberSetting("Turn Speed Mult", 1.00, 0.40, 2.50, 2);

    // ── Walker: humanisation ──────────────────────────────────────────────────

    /** Whether the walker drifts side to side across the corridor instead of tracking dead centre. */
    public static final BooleanSetting lateralDrift =
            new BooleanSetting("Lateral Drift", true);

    /** Maximum sideways wander off the centreline, in blocks. */
    public static final NumberSetting driftAmount =
            new NumberSetting("Drift Amount", 0.05, 0.00, 1.00, 2);

    /**
     * Yaw error, in degrees, under which the walker presses W. Below it, walk forward and let
     * the rotation turn us onto the line; above it, turn in place first. Wider drifts the body
     * off the path into walls on curves; tighter makes sharp corners a beat slower.
     */
    public static final NumberSetting forwardAngle =
            new NumberSetting("Forward Angle", 45.0, 20.0, 90.0, 0);

    // ── Walker: speed ─────────────────────────────────────────────────────────

    /**
     * Hold sprint while driving forward on-heading. Off by default because most setups run a
     * dedicated always-sprint mod; turn it on to let the walker manage sprint itself.
     */
    public static final BooleanSetting sprint =
            new BooleanSetting("Sprint", false);

    /** Yaw error, in degrees, under which sprinting is allowed. Above it, align before charging. */
    public static final NumberSetting sprintMaxYaw =
            new NumberSetting("Sprint Max Yaw", 45.0, 10.0, 90.0, 0);

    // ── Walker: hopping ───────────────────────────────────────────────────────

    /** Whether to sprint-jump across clear straights. */
    public static final BooleanSetting bHop =
            new BooleanSetting("B-Hop", true);

    /** Chance a given landing drops the hop beat instead of jumping again, for variance. */
    public static final NumberSetting bHopSkipChance =
            new NumberSetting("B-Hop Skip Chance", 0.20, 0.00, 1.00, 2);

    // ── Walker: recovery ──────────────────────────────────────────────────────

    /** Failed escalations before the walker gives up and /oof-respawns to reset. */
    public static final NumberSetting stuckGiveUp =
            new NumberSetting("Stuck Give-Up", 12, 3, 40, 0);

    /** Median re-orientation delay after a screen closes, in ms, before walking resumes. */
    public static final NumberSetting guiResumeMs =
            new NumberSetting("GUI Resume Delay", 210, 0, 800, 0);

    /**
     * Every setting, in display order, for the tuning module to register. Grouped loosely:
     * search first, then the walker, so the palette reads top to bottom from "what route" to
     * "how it walks it".
     */
    public static Setting[] all() {
        return new Setting[]{
                maxIterations, heuristicWeight,
                turnPenalty, climbCost, fallCost, jumpCost, holePenalty, avoidHoles, wallClearance,
                antiBridgeStrength, antiBridgeRange,
                maxStepUp, stepAssist, maxFall,
                lookAheadMin, lookAheadMax, bendLimit, turnSpeedMult,
                lateralDrift, driftAmount, forwardAngle,
                sprint, sprintMaxYaw,
                bHop, bHopSkipChance,
                stuckGiveUp, guiResumeMs
        };
    }
}

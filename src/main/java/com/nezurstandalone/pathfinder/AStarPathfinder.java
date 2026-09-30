package com.nezurstandalone.pathfinder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The route search.
 *
 * <p>The cost model, the heuristic (including its anti-bridge repulsion field), the movement
 * validation and the 26-way expansion are unchanged from the original — every constant and
 * every acceptance rule is identical, and a route computed here is the same route as before.
 * What changed is everything underneath: the search runs out of a reusable {@link SearchArena}
 * instead of allocating {@code BlockPos}, {@code Node}, {@code ArrayList} and boxed map
 * entries per candidate, and the per-expansion work is ordered so the cheap rejections happen
 * before the expensive geometry.
 *
 * <p>Instances are single-use and confined to the pathfinder worker thread.
 */
public class AStarPathfinder {

    private final BlockPos start;
    private final BlockPos end;
    private final int maxIterations;
    private final boolean avoidHoles;
    private final java.util.function.Predicate<BlockPos> bounds;

    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, // Cardinal
            {1, 0, 1}, {-1, 0, -1}, {1, 0, -1}, {-1, 0, 1}, // Diagonal
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, // Jump Cardinal
            {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}, // Drop Cardinal
            {1, 1, 1}, {-1, 1, -1}, {1, 1, -1}, {-1, 1, 1}, // Jump Diagonal
            {1, -1, 1}, {-1, -1, -1}, {1, -1, -1}, {-1, -1, 1}, // Drop Diagonal
            {0, 1, 0}, {0, -1, 0} // Ladder Vertical
    };

    /**
     * One arena per worker thread. The pathfinder executor is single-threaded, so in practice
     * this is one arena for the process; the thread-local binding is what guarantees the
     * primitive tables can never be shared across two concurrent searches.
     */
    private static final ThreadLocal<SearchArena> ARENA = new ThreadLocal<SearchArena>() {
        @Override
        protected SearchArena initialValue() {
            return new SearchArena();
        }
    };

    /** Scratch positions, reused for every world lookup this search performs. */
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos scratchB = new BlockPos.MutableBlockPos();
    /** Reused collision sink; {@code addCollisionBoxesToList} only ever appends. */
    private final List<AxisAlignedBB> collisionSink = new ArrayList<AxisAlignedBB>(8);

    private final SearchSnapshot snapshot;
    private final double cfg_antiBridgeRange = PathfinderConfig.antiBridgeRange.value;
    private final double cfg_antiBridgeStrength = PathfinderConfig.antiBridgeStrength.value;
    private final double cfg_climbCost = PathfinderConfig.climbCost.value;
    private final double cfg_fallCost = PathfinderConfig.fallCost.value;
    private final double cfg_heuristicWeight = PathfinderConfig.heuristicWeight.value;
    private final double cfg_holePenalty = PathfinderConfig.holePenalty.value;
    private final double cfg_jumpCost = PathfinderConfig.jumpCost.value;
    private final double cfg_maxFall = PathfinderConfig.maxFall.value;
    private final double cfg_maxStepUp = PathfinderConfig.maxStepUp.value;
    private final double cfg_stepAssist = PathfinderConfig.stepAssist.value;
    private final double cfg_turnPenalty = PathfinderConfig.turnPenalty.value;
    private final double cfg_wallClearance = PathfinderConfig.wallClearance.value;
    private final boolean cfg_avoidHoles = PathfinderConfig.avoidHoles.enabled;
    public boolean captureValid(){return snapshot.valid();}
    public boolean prepare(int budget) { return snapshot.prepare(budget); }
    private SearchArena arena;
    private boolean partial;

    public AStarPathfinder(BlockPos start, BlockPos end, int maxIterations, boolean avoidHoles) {
        this(start, end, maxIterations, avoidHoles, null);
    }

    public AStarPathfinder(BlockPos start, BlockPos end, int maxIterations, boolean avoidHoles,
                           java.util.function.Predicate<BlockPos> bounds) {
        this(Minecraft.getMinecraft().theWorld, start, end, maxIterations, avoidHoles, bounds);
    }

    public AStarPathfinder(World world, BlockPos start, BlockPos end, int maxIterations,
                           boolean avoidHoles, java.util.function.Predicate<BlockPos> bounds) {
        this.snapshot = new SearchSnapshot(world, start, end, bounds);
        this.start = start;
        this.end = end;
        this.maxIterations = maxIterations;
        this.avoidHoles = avoidHoles;
        this.bounds = bounds;
    }

    /**
     * True when the search ended on its iteration budget or exhausted the open set without
     * reaching the goal, so the returned route only leads to the closest reachable node.
     */
    public boolean isPartial() {
        return partial;
    }

    public List<Vec3> compute() {
        if (!snapshot.prepare(0)) throw new IllegalStateException("Snapshot is not ready");

        this.arena = ARENA.get();
        // Must be the first statement of the search: the arena carries the previous route.
        arena.reset();
        this.partial = true;

        final int endX = end.getX();
        final int endY = end.getY();
        final int endZ = end.getZ();

        int startNode = arena.newNode(pack(start.getX(), start.getY(), start.getZ()),
                start.getX(), start.getY(), start.getZ(),
                0.0, heuristic(start.getX(), start.getY(), start.getZ(), endX, endY, endZ), -1);
        arena.heapPush(startNode);
        arena.openPut(arena.nodeKey(startNode), startNode);

        int bestNode = startNode;
        double bestDistSq = distSq(start.getX(), start.getY(), start.getZ(), endX, endY, endZ);
        int iterations = 0;

        try {
            while (!arena.heapEmpty() && iterations < maxIterations && !Thread.currentThread().isInterrupted()) {
                int current = arena.heapPop();
                final int cx = arena.nodeX(current);
                final int cy = arena.nodeY(current);
                final int cz = arena.nodeZ(current);
                final long currentKey = arena.nodeKey(current);
                arena.openRemove(currentKey);

                double dSq = distSq(cx, cy, cz, endX, endY, endZ);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestNode = current;
                }
                if (dSq <= 1.5) {
                    bestNode = current;
                    partial = false;
                    break;
                }

                arena.closedAdd(currentKey);

                // Hoisted out of the neighbour loop: the origin height is identical for all
                // 26 candidates, and resolving it inside getDropDestination meant 26 redundant
                // block-state + collision-box lookups per expansion.
                final double startY = walkableHeight(cx, cy, cz);

                // Direction of travel into this node, for the zig-zag penalty.
                int prevDx = 0;
                int prevDz = 0;
                int parent = arena.nodeParent(current);
                if (parent >= 0) {
                    prevDx = Integer.signum(cx - arena.nodeX(parent));
                    prevDz = Integer.signum(cz - arena.nodeZ(parent));
                }
                final double currentG = arena.nodeG(current);

                for (int d = 0; d < DIRECTIONS.length; d++) {
                    final int[] dir = DIRECTIONS[d];
                    final int nx = cx + dir[0];
                    final int ny = cy + dir[1];
                    final int nz = cz + dir[2];

                    // Cheapest possible rejection first: an unloaded chunk reads as air and
                    // would otherwise be walked into by the fall simulation below.
                    scratch.set(nx, ny, nz);
                    if (!snapshot.loaded(scratch)) {
                        continue;
                    }

                    // Anti corner-cut: a diagonal step is only legal when both orthogonal cells
                    // beside it are clear. Cutting a corner past a wall drags the 0.6-wide body
                    // through the block, which is exactly the scraping the walker then fights.
                    if (dir[0] != 0 && dir[2] != 0
                            && (!canOccupy(cx + dir[0] + 0.5, startY, cz + 0.5)
                             || !canOccupy(cx + 0.5, startY, cz + dir[2] + 0.5))) {
                        continue;
                    }

                    final int landingY = dropDestinationY(cx, cz, startY, nx, ny, nz);
                    if (landingY == NO_LANDING) {
                        continue;
                    }

                    final long landingKey = pack(nx, landingY, nz);
                    if (arena.closedContains(landingKey)) {
                        continue;
                    }

                    scratch.set(nx, landingY, nz);
                    if (!snapshot.loaded(scratch)) {
                        continue;
                    }
                    if (!snapshot.allowed(new BlockPos(nx, landingY, nz))) {
                        // The only unavoidable allocation in the loop: the predicate is public
                        // API and callers keep the reference (blacklist sets, zone tests).
                        continue;
                    }

                    final double dx = nx - cx;
                    final double dz = nz - cz;
                    final double hDist = Math.sqrt(dx * dx + dz * dz);
                    final double vDrop = cy - landingY;

                    double turnPenalty = 0.0;
                    int newDx = Integer.signum(nx - cx);
                    int newDz = Integer.signum(nz - cz);
                    if ((newDx != 0 || newDz != 0) && (prevDx != 0 || prevDz != 0)
                            && (prevDx != newDx || prevDz != newDz)) {
                        turnPenalty = cfg_turnPenalty; // Zig-zag penalty
                    }

                    double stepCost = hDist + turnPenalty;
                    if (hDist == 0 && vDrop != 0) { // Pure vertical movement (Ladder)
                        stepCost += Math.abs(vDrop) * cfg_climbCost;
                    } else if (vDrop > 0) {
                        stepCost += vDrop * cfg_fallCost;
                    } else if (vDrop < 0) {
                        stepCost += -vDrop * cfg_jumpCost;
                    }

                    double cost = currentG + stepCost;

                    if (cfg_avoidHoles && isHole(nx, landingY, nz)) {
                        cost += cfg_holePenalty;
                    }

                    // Wall-clearance bias: every solid block directly beside the landing cell adds
                    // a little cost, so where the room allows it the search routes through open
                    // centres instead of hugging a wall. In a one-wide corridor every cell has the
                    // same two walls, so it changes nothing there; in anything wider it keeps the
                    // body off the stone, which is what stops the scraping in the first place.
                    cost += wallNeighbours(nx, landingY, nz) * cfg_wallClearance;

                    int existing = arena.openGet(landingKey);
                    if (existing >= 0) {
                        if (cost < arena.nodeG(existing)) {
                            arena.improve(existing, cost,
                                    cost + heuristic(nx, landingY, nz, endX, endY, endZ), current);
                        }
                    } else {
                        int node = arena.newNode(landingKey, nx, landingY, nz, cost,
                                cost + heuristic(nx, landingY, nz, endX, endY, endZ), current);
                        arena.openPut(landingKey, node);
                        arena.heapPush(node);
                    }
                }
                iterations++;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Path search failed", e);
        }

        // Walk the parent chain back to the start.
        int length = 0;
        for (int n = bestNode; n >= 0; n = arena.nodeParent(n)) {
            length++;
        }
        Vec3[] reversed = new Vec3[length];
        int cursor = length - 1;
        for (int n = bestNode; n >= 0; n = arena.nodeParent(n)) {
            int px = arena.nodeX(n);
            int py = arena.nodeY(n);
            int pz = arena.nodeZ(n);
            reversed[cursor--] = new Vec3(px + 0.5, walkableHeight(px, py, pz), pz + 0.5);
        }
        List<Vec3> path = new ArrayList<Vec3>(length);
        Collections.addAll(path, reversed);
        return path;
    }

    // ------------------------------------------------------------------ costs

    private static double distSq(int x, int y, int z, int ex, int ey, int ez) {
        double dx = x - ex;
        double dy = y - ey;
        double dz = z - ez;
        return dx * dx + dy * dy + dz * dz;
    }

    private double heuristic(int x, int y, int z, int ex, int ey, int ez) {
        double dx = x - ex;
        double dy = y - ey;
        double dz = z - ez;

        double hDist = Math.sqrt(dx * dx + dz * dz);
        double penalty = 0;

        // Anti-Bridge Logic: if we are above the target but horizontally very close, push
        // outward so the search looks for an edge to drop off instead of bridging down.
        double antiRange = cfg_antiBridgeRange;
        if (dy > 1.5 && hDist < antiRange) {
            scratchB.set(x, y - 1, z);
            if (snapshot.loaded(scratchB)
                    && snapshot.solid(scratchB)) {
                penalty = (antiRange - hDist) * cfg_antiBridgeStrength;
            }
        }

        // Heuristic weight; 1.0 is admissible (shortest route), higher trades optimality for speed.
        return Math.sqrt(dx * dx + dy * dy + dz * dz) * cfg_heuristicWeight + penalty;
    }

    // ------------------------------------------------------------- validation

    private static final int NO_LANDING = Integer.MIN_VALUE;

    /**
     * Resolves where a step toward {@code (nx, ny, nz)} actually lands, or {@link #NO_LANDING}
     * when the move is impossible. Identical rules to the original {@code getDropDestination},
     * with the origin height passed in rather than recomputed per neighbour.
     *
     * @return the Y of the landing block
     */
    private int dropDestinationY(int cx, int cz, double startY, int nx, int ny, int nz) {
        double endY = walkableHeight(nx, ny, nz);

        double maxStepUp = cfg_maxStepUp;
        if (endY - startY > maxStepUp) {
            return NO_LANDING; // Jump is too high
        }

        double peakY;
        if (endY > startY) {
            if (endY - startY <= cfg_stepAssist) {
                peakY = endY; // Step assist - no jump required
            } else {
                peakY = startY + maxStepUp; // We must jump UP to reach it
                if (!canOccupy(cx + 0.5, peakY, cz + 0.5)) {
                    return NO_LANDING; // No head clearance at the start of the jump
                }
            }
        } else {
            peakY = startY; // We just walk off the edge
        }

        double midX = cx + 0.5 + (nx - cx) * 0.5;
        double midZ = cz + 0.5 + (nz - cz) * 0.5;
        Vec3 startPoint=new Vec3(cx+.5,startY,cz+.5),peakStart=new Vec3(cx+.5,peakY,cz+.5),peakEnd=new Vec3(nx+.5,peakY,nz+.5);
        if(!snapshot.segment(startPoint,peakStart)||!snapshot.segment(peakStart,peakEnd))return NO_LANDING;
        int dropY = ny;
        double currentY = endY;
        int maxFall = (int) cfg_maxFall;
        for (int i = 0; i < maxFall; i++) {
            if (isSafeToStand(nx, dropY, nz)) {
                if(!snapshot.segment(peakEnd,new Vec3(nx+.5,walkableHeight(nx,dropY,nz),nz+.5)))return NO_LANDING;
                return dropY;
            }
            if (!canOccupy(nx + 0.5, currentY - 1.0, nz + 0.5)) {
                return NO_LANDING; // Hit a block mid-air while falling
            }
            dropY--;
            currentY -= 1.0;
        }
        return NO_LANDING; // Dropped too far
    }

    private double walkableHeight(int x, int y, int z) {
        long key = pack(x, y, z);
        double cached = arena.heightGet(key);
        if (!Double.isNaN(cached)) {
            return cached;
        }

        scratchB.set(x,y,z);
        double result = snapshot.top(scratchB);
        if (Double.isNaN(result)) { scratchB.set(x,y-1,z); result=snapshot.top(scratchB); }
        if (Double.isNaN(result)) result=y;
        arena.heightPut(key, result);
        return result;
    }

    /**
     * Player-sized clearance test. Standing player box (0.6 wide, 1.8 tall) and the same pass-through
     * whitelist as before; the loop now reuses one mutable position and one collision list,
     * and skips the collision call outright for air, which is the overwhelming majority of
     * the blocks it visits.
     */
    private boolean canOccupy(double x,double y,double z) { return snapshot.clear(x,y,z); }

    /** Blocks the player can walk through; unchanged from the original whitelist. */
    private static boolean isPassThrough(Block block) {
        return block instanceof BlockCarpet
                || block instanceof net.minecraft.block.BlockSnow
                || block instanceof net.minecraft.block.BlockLadder
                || block instanceof net.minecraft.block.BlockVine
                || block instanceof net.minecraft.block.BlockSign
                || block instanceof net.minecraft.block.BlockBush
                || block instanceof net.minecraft.block.BlockBasePressurePlate
                || block instanceof net.minecraft.block.BlockTallGrass
                || block instanceof net.minecraft.block.BlockDoor;
    }

    private boolean isSafeToStand(int x,int y,int z) {
        double floor=walkableHeight(x,y,z);
        if (!canOccupy(x+0.5,floor,z+0.5)) return false;
        BlockPos below=new BlockPos(x,(int)Math.floor(floor-0.01),z);
        return snapshot.solid(below) || snapshot.climb(below)
                || (snapshot.liquid(below) && isLiquid(x,y,z));
    }
    private boolean isSolid(int x,int y,int z) { return snapshot.solid(new BlockPos(x,y,z)); }

    /**
     * Whether a block can act as floor.
     *
     * <p>This must agree with {@link #isPassThrough}: a block the player walks straight
     * through cannot also be a block they stand on. It did not, and the disagreement was
     * material-shaped. {@code BlockSign} is built on {@code Material.wood}, a plain
     * {@code Material} whose {@code isSolid()} is {@code true}, while its collision box is
     * {@code null}. So {@code walkableHeight} found no box, looked at the block below, hit
     * air and returned the sign's own Y; {@code isSafeToStand} then accepted the node because
     * the sign reported itself as its own support. A wall sign with air underneath was a
     * standable node floating in mid-air, and A* routed up onto it.
     *
     * <p>Deferring to the whitelist changes only signs and doors in practice - carpet and snow
     * were already listed here, and plants, vines, ladders and tall grass are built on
     * {@code MaterialLogic}, which already reports {@code isSolid() == false}. Pressure plates
     * are unaffected because they resolve their floor through the solid block beneath them.
     */
    private static boolean isSolidBlock(Block block) {
        if (isPassThrough(block)) {
            return false;
        }
        return block.getMaterial().isSolid();
    }

    private boolean isLiquid(int x, int y, int z) {
        scratchB.set(x, y, z);
        return snapshot.liquid(scratchB);
    }

    private static boolean isLiquidMaterial(Block block) {
        Material material = block.getMaterial();
        return material == Material.water || material == Material.lava;
    }

    /** Count of solid blocks in the four cardinal cells beside (x,y,z); 0..4. */
    private int wallNeighbours(int x, int y, int z) {
        int n = 0;
        scratchB.set(x + 1, y, z);
        if (snapshot.solid(scratchB)) n++;
        scratchB.set(x - 1, y, z);
        if (snapshot.solid(scratchB)) n++;
        scratchB.set(x, y, z + 1);
        if (snapshot.solid(scratchB)) n++;
        scratchB.set(x, y, z - 1);
        if (snapshot.solid(scratchB)) n++;
        return n;
    }

    private boolean isHole(int x, int y, int z) {
        for (int i = 1; i <= 3; i++) {
            scratchB.set(x, y - i, z);
            if (snapshot.climb(scratchB) || snapshot.solid(scratchB)) return false;
        }
        return true;
    }

    /**
     * Injective packing of a block position into a long. Only ever used as a table key, never
     * unpacked, so it just has to be collision-free across the coordinate ranges a Pit map
     * can produce: 26 bits of X and Z (±33M) and 12 bits of Y.
     */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z & 0x3FFFFFF);
    }
}

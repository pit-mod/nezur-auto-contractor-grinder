package com.nezurstandalone.pathfinder;

import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable, self-consistent view of one navigation: the computed nodes, the destination
 * they lead to, and the version that identifies them.
 *
 * <p>Before this existed, a consumer read {@code PathRenderer.rawPath}, {@code pathVersion}
 * and {@code PathfinderManager.currentEndPos} as three independent statics. A {@code clear()}
 * or a newly landed search between any two of those reads produced a torn view — a path from
 * one navigation paired with the destination of another, which is exactly how AutoWalker
 * could decide it had "arrived" at a destination belonging to a route it was no longer
 * walking. Publishing all three together behind a single reference makes that impossible:
 * a reader either sees the whole old navigation or the whole new one.
 */
public final class PathSnapshot {

    public static final PathSnapshot EMPTY = new PathSnapshot(0, null, Collections.<Vec3>emptyList(), false);

    /** Spacing of the smoothed polyline, in blocks. Fine enough to steer along directly. */
    private static final double WALK_SUBDIVISION_STEP = 0.25;

    private final int version;
    private final BlockPos destination;
    private final List<Vec3> nodes;
    private final boolean partial;

    /**
     * Catmull-Rom smoothing of {@link #nodes}, with a cumulative arc length per point.
     *
     * <p>The raw nodes are block centres on a 26-way grid, so following them directly pins the
     * player to the exact centreline of the lattice and quantises every heading to a multiple
     * of 45 degrees. The renderer has always drawn this smoothed curve instead - the driver
     * simply never consumed it, so the smooth path on screen was not the path being walked.
     * Arc length is precomputed so the driver can ask for "the point 2 blocks further along"
     * without rescanning the polyline.
     */
    private final List<Vec3> smoothed;
    private final double[] cumulative;

    PathSnapshot(int version, BlockPos destination, List<Vec3> nodes, boolean partial) {
        this.version = version;
        this.destination = destination;
        this.nodes = nodes == null ? Collections.<Vec3>emptyList() : Collections.unmodifiableList(new ArrayList<Vec3>(nodes));
        this.partial = partial;
        this.smoothed = this.nodes; // Safe fallback: never walk an unvalidated spline.
        this.cumulative = arcLengths(this.smoothed);
    }

    // ------------------------------------------------------------------ spline

    /**
     * Catmull-Rom subdivision of a polyline. Endpoints are duplicated as their own control
     * points so the curve starts and ends exactly on the original path.
     */
    public static List<Vec3> subdivide(List<Vec3> positions, double step) {
        if (positions == null || positions.size() < 2) {
            return positions == null ? Collections.<Vec3>emptyList() : new ArrayList<Vec3>(positions);
        }
        List<Vec3> result = new ArrayList<Vec3>(positions.size() * 4);
        result.add(positions.get(0));

        for (int i = 0; i < positions.size() - 1; i++) {
            Vec3 p0 = (i - 1 >= 0) ? positions.get(i - 1) : positions.get(i);
            Vec3 p1 = positions.get(i);
            Vec3 p2 = positions.get(i + 1);
            Vec3 p3 = (i + 2 < positions.size()) ? positions.get(i + 2) : positions.get(i + 1);

            int steps = Math.max(1, (int) (p1.distanceTo(p2) / step));
            for (int s = 1; s <= steps; s++) {
                result.add(catmullRom(p0, p1, p2, p3, (double) s / steps));
            }
        }
        return result;
    }

    public static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double x = 0.5 * (2 * p1.xCoord + (p2.xCoord - p0.xCoord) * t
                + (2 * p0.xCoord - 5 * p1.xCoord + 4 * p2.xCoord - p3.xCoord) * t2
                + (3 * p1.xCoord - p0.xCoord - 3 * p2.xCoord + p3.xCoord) * t3);
        double y = 0.5 * (2 * p1.yCoord + (p2.yCoord - p0.yCoord) * t
                + (2 * p0.yCoord - 5 * p1.yCoord + 4 * p2.yCoord - p3.yCoord) * t2
                + (3 * p1.yCoord - p0.yCoord - 3 * p2.yCoord + p3.yCoord) * t3);
        double z = 0.5 * (2 * p1.zCoord + (p2.zCoord - p0.zCoord) * t
                + (2 * p0.zCoord - 5 * p1.zCoord + 4 * p2.zCoord - p3.zCoord) * t2
                + (3 * p1.zCoord - p0.zCoord - 3 * p2.zCoord + p3.zCoord) * t3);
        return new Vec3(x, y, z);
    }

    private static double[] arcLengths(List<Vec3> points) {
        double[] lengths = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            lengths[i] = lengths[i - 1] + points.get(i - 1).distanceTo(points.get(i));
        }
        return lengths;
    }

    /** The smoothed curve the driver steers along. Never null; empty for an empty route. */
    public List<Vec3> getSmoothed() {
        return smoothed;
    }

    /** Total arc length of the smoothed curve, in blocks. */
    public double getLength() {
        return cumulative.length == 0 ? 0.0 : cumulative[cumulative.length - 1];
    }

    /** The point that far along the smoothed curve, clamped to both ends. */
    public Vec3 pointAt(double distance) {
        if (smoothed.isEmpty()) return null;
        if (smoothed.size() == 1 || distance <= 0) return smoothed.get(0);
        double total = getLength();
        if (distance >= total) return smoothed.get(smoothed.size() - 1);

        int index = indexFor(distance);
        double segment = cumulative[index + 1] - cumulative[index];
        double t = segment <= 0 ? 0 : (distance - cumulative[index]) / segment;
        Vec3 a = smoothed.get(index);
        Vec3 b = smoothed.get(index + 1);
        return new Vec3(a.xCoord + (b.xCoord - a.xCoord) * t,
                a.yCoord + (b.yCoord - a.yCoord) * t,
                a.zCoord + (b.zCoord - a.zCoord) * t);
    }

    /** Unit tangent in the XZ plane at that arc length, or null when undefined. */
    public Vec3 tangentAt(double distance) {
        if (smoothed.size() < 2) return null;
        int index = indexFor(Math.max(0, Math.min(distance, getLength())));
        Vec3 a = smoothed.get(index);
        Vec3 b = smoothed.get(index + 1);
        double dx = b.xCoord - a.xCoord;
        double dz = b.zCoord - a.zCoord;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) return null;
        return new Vec3(dx / len, 0, dz / len);
    }

    /**
     * Arc length of the point on the curve closest to {@code pos}, searched only within
     * {@code window} blocks either side of {@code hint}. Bounding the search is what keeps the
     * driver from snapping onto a later leg of a route that doubles back on itself.
     */
    public double project(Vec3 pos, double hint, double window) {
        if (smoothed.size() < 2) return 0.0;
        double total = getLength();
        double from = Math.max(0, hint - window);
        double to = Math.min(total, hint + window);

        int start = indexFor(from);
        int end = indexFor(to);
        double best = from;
        double bestDistSq = Double.MAX_VALUE;

        for (int i = start; i <= end && i < smoothed.size() - 1; i++) {
            Vec3 a = smoothed.get(i);
            Vec3 b = smoothed.get(i + 1);
            double dx = b.xCoord - a.xCoord;
            double dy = b.yCoord - a.yCoord;
            double dz = b.zCoord - a.zCoord;
            double lenSq = dx * dx + dy * dy + dz * dz;
            double t = 0;
            if (lenSq > 0) {
                t = ((pos.xCoord - a.xCoord) * dx + (pos.yCoord - a.yCoord) * dy
                        + (pos.zCoord - a.zCoord) * dz) / lenSq;
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
            }
            double px = a.xCoord + dx * t - pos.xCoord;
            double py = a.yCoord + dy * t - pos.yCoord;
            double pz = a.zCoord + dz * t - pos.zCoord;
            double distSq = px * px + py * py + pz * pz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t;
            }
        }
        return best;
    }

    /** Index of the segment containing {@code distance}; binary search over the arc table. */
    private int indexFor(double distance) {
        int low = 0;
        int high = cumulative.length - 1;
        while (low < high - 1) {
            int mid = (low + high) >>> 1;
            if (cumulative[mid] <= distance) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return Math.min(low, Math.max(0, smoothed.size() - 2));
    }

    /** Monotonic id of the navigation that produced these nodes. */
    public int getVersion() {
        return version;
    }

    /** Where this path was trying to reach, or null when there is no navigation. */
    public BlockPos getDestination() {
        return destination;
    }

    /** Never null; unmodifiable. */
    public List<Vec3> getNodes() {
        return nodes;
    }

    public int size() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public Vec3 get(int index) {
        return nodes.get(index);
    }

    /**
     * True when the search hit its iteration budget and returned the closest reachable node
     * instead of the destination. The driver uses this to force an early recalculation rather
     * than treating the truncated end as an arrival.
     */
    public boolean isPartial() {
        return partial;
    }
}

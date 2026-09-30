package com.nezurstandalone.pathfinder;

/** Tracks meaningful departures from a visual route, independently of rendering. */
public final class VisualRepathTracker {
    private final RouteCurve curve;
    private final long publishedAt;
    private final double[] nearest = new double[3];
    private double progress = Double.NaN, furthest;
    private long outsideSince = -1;

    public VisualRepathTracker(RouteCurve curve, long now) {
        this.curve = curve;
        this.publishedAt = now;
    }

    public boolean shouldRefresh(double x, double y, double z, long now) {
        progress = curve.project(x, y, z, progress, 12);
        curve.sample(progress, nearest);
        double dx=x-nearest[0], dy=y-nearest[1], dz=z-nearest[2];
        boolean offRoute=dx*dx+dy*dy+dz*dz > 16;
        // A large forward jump can leave the local projection window. Check the
        // whole curve before mistaking a player still on the route for a detour.
        if (offRoute) {
            progress=curve.project(x,y,z,Double.NaN,0);
            curve.sample(progress,nearest);
            dx=x-nearest[0];dy=y-nearest[1];dz=z-nearest[2];
            offRoute=dx*dx+dy*dy+dz*dz > 16;
        }
        furthest=Math.max(furthest,progress);
        if (!offRoute && furthest-progress < 5) {
            outsideSince=-1;
            return false;
        }
        if (outsideSince<0) outsideSince=now;
        return now-publishedAt >= 2_000_000_000L && now-outsideSince >= 600_000_000L;
    }
}

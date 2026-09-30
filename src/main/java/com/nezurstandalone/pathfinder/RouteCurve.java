package com.nezurstandalone.pathfinder;

/** Immutable arc-length curve used only by the path visualization. */
public final class RouteCurve {
    private final double[] x, y, z, arc;

    public RouteCurve(double[] x, double[] y, double[] z) {
        if (x.length < 2 || x.length != y.length || x.length != z.length)
            throw new IllegalArgumentException("A curve needs matching coordinate arrays");
        this.x = x.clone(); this.y = y.clone(); this.z = z.clone();
        arc = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            if (!Double.isFinite(x[i]) || !Double.isFinite(y[i]) || !Double.isFinite(z[i]))
                throw new IllegalArgumentException("Non-finite route point");
            if (i > 0) arc[i] = arc[i - 1] + distance(x[i]-x[i-1], y[i]-y[i-1], z[i]-z[i-1]);
        }
    }

    public double length() { return arc[arc.length - 1]; }

    private int segment(double distance) {
        int low = 0, high = arc.length - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (arc[mid] <= distance) low = mid; else high = mid;
        }
        return low;
    }

    /** Samples into caller-owned storage; no per-vertex allocation. */
    public void sample(double distance, double[] out) {
        distance = Math.max(0, Math.min(length(), distance));
        int i = segment(distance);
        double span = arc[i + 1] - arc[i];
        double t = span > 1e-9 ? (distance - arc[i]) / span : 0;
        out[0] = x[i] + (x[i+1] - x[i]) * t;
        out[1] = y[i] + (y[i+1] - y[i]) * t;
        out[2] = z[i] + (z[i+1] - z[i]) * t;
    }

    /** Local arc window avoids jumping to a later branch at route crossings. */
    public double project(double px, double py, double pz, double around, double radius) {
        double min = Double.isNaN(around) ? 0 : Math.max(0, around - radius);
        double max = Double.isNaN(around) ? length() : Math.min(length(), around + radius);
        int first = segment(min), last = segment(max);
        double best = min, bestError = Double.POSITIVE_INFINITY;
        for (int i = first; i <= last; i++) {
            double dx = x[i+1]-x[i], dy = y[i+1]-y[i], dz = z[i+1]-z[i];
            double squared = dx*dx + dy*dy + dz*dz;
            if (squared < 1e-12) continue;
            double t = Math.max(0, Math.min(1, ((px-x[i])*dx+(py-y[i])*dy+(pz-z[i])*dz)/squared));
            double span = arc[i+1]-arc[i];
            double at = Math.max(min, Math.min(max, arc[i] + t*span));
            t = (at-arc[i])/span;
            double error = square(px-x[i]-t*dx)+square(py-y[i]-t*dy)+square(pz-z[i]-t*dz);
            if (error < bestError) { bestError = error; best = at; }
        }
        return best;
    }

    public static double damp(double current, double target, double rate, double seconds) {
        return current + (target-current) * -Math.expm1(-rate*Math.max(0, seconds));
    }
    public static double ease(double t) { t=Math.max(0,Math.min(1,t)); return t*t*(3-2*t); }
    private static double square(double v) { return v*v; }
    private static double distance(double x, double y, double z) { return Math.sqrt(x*x+y*y+z*z); }
}

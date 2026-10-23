package com.nezurstandalone.control;

/** Closest vanilla keyboard direction in the reported yaw's movement basis. */
public final class MoveFix {
    private MoveFix() { }
    public static float[] correct(float cameraYaw, float serverYaw, float forward, float strafe) {
        if (forward == 0 && strafe == 0) return new float[]{0, 0};
        double radians = Math.toRadians(cameraYaw - serverYaw);
        float f = (float) (forward * Math.cos(radians) + strafe * Math.sin(radians));
        float s = (float) (strafe * Math.cos(radians) - forward * Math.sin(radians));
        double best = Double.POSITIVE_INFINITY, magnitude = Math.sqrt(forward * forward + strafe * strafe);
        float bestF = 0, bestS = 0, scale = Math.max(Math.abs(forward), Math.abs(strafe));
        for (int df = -1; df <= 1; df++) for (int ds = -1; ds <= 1; ds++) {
            if (df == 0 && ds == 0) continue;
            double length = Math.sqrt(df * df + ds * ds);
            double error = Math.pow(ds / length - s / magnitude, 2) + Math.pow(df / length - f / magnitude, 2);
            if (error < best) { best = error; bestF = df * scale; bestS = ds * scale; }
        }
        return new float[]{bestF, bestS};
    }
}

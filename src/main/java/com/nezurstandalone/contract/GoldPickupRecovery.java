package com.nezurstandalone.contract;

/** Bounded local pickup recovery. No inputs, packets, progress counters or world mutation here. */
public final class GoldPickupRecovery {
    public enum Action { NONE, SIDESTEP_LEFT, SIDESTEP_RIGHT, REPATH }
    public static final double DEFAULT_SCAN_RADIUS = 64;
    public static final double MAX_SCAN_RADIUS = 128;
    private int target = Integer.MIN_VALUE, attempts;
    private long since = -1, recoveryAt = -1;
    private double x, z, distance;

    public static boolean ignoresPopulation(ContractOffer.Type objective) {
        return objective == ContractOffer.Type.COLLECT_GOLD_INGOTS;
    }
    public void reset() { target = Integer.MIN_VALUE; attempts = 0; since = recoveryAt = -1; }
    public long recoveryStartedAt() { return recoveryAt; }
    public Action update(long now, int id, boolean eligible, boolean grounded,
                         double px, double pz, double remaining) {
        if (!eligible) { reset(); return Action.NONE; }
        if (id != target) { reset(); target = id; }
        if (recoveryAt >= 0) {
            if (now - recoveryAt < 400) return attempts == 1 ? Action.SIDESTEP_LEFT : Action.SIDESTEP_RIGHT;
            recoveryAt = -1; since = now; x = px; z = pz; distance = remaining;
            return Action.NONE;
        }
        double dx = px - x, dz = pz - z;
        if (since < 0 || !grounded || now < since || dx * dx + dz * dz >= .04 || distance - remaining >= .15) {
            if (since >= 0 && distance - remaining >= .15) attempts = 0;
            since = now; x = px; z = pz; distance = remaining;
            return Action.NONE;
        }
        if (now - since < 1200) return Action.NONE;
        if (attempts >= 2) { reset(); return Action.REPATH; }
        ++attempts; recoveryAt = now;
        return attempts == 1 ? Action.SIDESTEP_LEFT : Action.SIDESTEP_RIGHT;
    }
}

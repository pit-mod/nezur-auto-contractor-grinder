package com.nezurstandalone.control;

/** Per-correction settling delay. Position corrections never request a module disable. */
public final class CorrectionWindow {
    private int correctedTick;
    private long correctedAt;
    private Object world, player;
    private boolean interrupted;

    public void context(Object world, Object player) {
        if (this.world == world && this.player == player) return;
        this.world = world; this.player = player;
        interrupted = false;
    }

    public void corrected(long now, int tick) {
        interrupted = true; correctedAt = now; correctedTick = tick;
    }

    public boolean allowed(long now, int tick) {
        return !interrupted || now - correctedAt >= 250_000_000L && tick - correctedTick >= 3;
    }
}

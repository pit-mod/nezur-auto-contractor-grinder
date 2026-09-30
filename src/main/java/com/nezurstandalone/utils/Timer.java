package com.nezurstandalone.utils;

public class Timer {
    private long lastMS = System.nanoTime() / 1_000_000L;

    public boolean hasTimeElapsed(long time, boolean reset) {
        if (System.nanoTime() / 1_000_000L - this.lastMS > time) {
            if (reset) {
                reset();
            }
            return true;
        }
        return false;
    }

    public long getPassed() {
        return System.nanoTime() / 1_000_000L - this.lastMS;
    }

    public void reset() {
        this.lastMS = System.nanoTime() / 1_000_000L;
    }
}


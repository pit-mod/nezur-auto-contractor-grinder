package com.nezurstandalone.control;
/** Elapsed durations only. Never use this clock for calendar timestamps. */
public interface Clock {
    long nanos();
    Clock SYSTEM = System::nanoTime;
    static long millis() { return System.nanoTime() / 1_000_000L; }
}

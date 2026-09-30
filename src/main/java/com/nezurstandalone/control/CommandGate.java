package com.nezurstandalone.control;
/** A refused command is not queued: its caller may retry after observing its state. */
public final class CommandGate {
    private final Clock clock;
    private long next, backoff, pendingUntil, session = Long.MIN_VALUE;
    private String pending;
    private Object owner;
    public CommandGate(Clock clock) { this.clock = clock; }
    public void throttle(long duration) { backoff = Math.max(backoff, clock.nanos() + duration); }
    public boolean acquire(Object who, String command, long token) {
        long now = clock.nanos();
        if (session != token) { session = token; pending = null; owner = null; }
        if (now < backoff || now < next || (pending != null && now < pendingUntil)) return false;
        boolean travel=command.equals("/l")||command.equals("/spawn")||command.equals("/oof")
                ||command.startsWith("/play ")||command.equals("<reconnect>");
        owner = who; pending = travel ? command : null;
        pendingUntil = now + 10_000_000_000L; next = now + (travel?3_000_000_000L:1_000_000_000L);
        return true;
    }
    public void cancel(Object who) { if (owner == who) { owner = null; pending = null; } }
}

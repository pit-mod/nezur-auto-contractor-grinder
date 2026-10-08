package com.nezurstandalone.control;

/** Physical look owns the camera until the mouse has been still briefly. */
public final class ManualCameraPriority {
    private static final long QUIET_MS=200;
    private long until;
    public boolean sample(long now,int dx,int dy) {
        if(dx!=0 || dy!=0)until=now+QUIET_MS;
        return blocked(now);
    }
    public boolean blocked(long now){return now<until;}
    public void reset(){until=0;}
}

package com.nezurstandalone.engine;

/** Bounds a ticket attempt even when movement oscillates or pickup never succeeds. */
public final class RaffleTicketProgress {
    private long startedAt, progressAt, nearAt;
    private boolean near;
    private double bestDistance;

    public void reset() { begin(0,Double.MAX_VALUE); }
    public void begin(long now,double distance) {
        startedAt=progressAt=now;bestDistance=distance;near=distance<=2.8;nearAt=now;
    }
    public boolean expired(long now,double distance) {
        if(distance<bestDistance-.12) {bestDistance=distance;progressAt=now;}
        // Once in pickup range, leaving and re-entering must not reset the deadline.
        if(!near && distance<=2.8) {near=true;nearAt=now;}
        return now-progressAt>=5000 || now-startedAt>=30000 || (near && now-nearAt>=3000);
    }
    public static boolean withinPickup(double x,double y,double z,double tx,double ty,double tz) {
        double dx=x-tx,dy=y-ty,dz=z-tz;
        return dx*dx+dy*dy+dz*dz<=1.0;
    }
}

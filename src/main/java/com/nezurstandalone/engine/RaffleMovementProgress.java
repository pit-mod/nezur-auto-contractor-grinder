package com.nezurstandalone.engine;

/** Detect a stopped route without depending on damage or knockback packets. */
public final class RaffleMovementProgress {
    private Object goal;
    private long movedAt;
    private double x,y,z;
    public void reset(){goal=null;}
    public boolean stalled(Object next,long now,double px,double py,double pz) {
        double dx=px-x,dy=py-y,dz=pz-z;
        if(goal==null || !goal.equals(next) || now<movedAt || dx*dx+dy*dy+dz*dz>=.0225) {
            goal=next;movedAt=now;x=px;y=py;z=pz;return false;
        }
        if(now-movedAt<1200)return false;
        movedAt=now;return true;
    }
}

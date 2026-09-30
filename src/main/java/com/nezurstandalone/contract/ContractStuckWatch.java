package com.nezurstandalone.contract;

/** Movement-intent watchdog; stationary GUI/idle/combat without movement is not a stuck path. */
public final class ContractStuckWatch {
    private long since=-1,lastOof=-12000;
    private double x,y,z;
    public void reset(){since=-1;}
    public boolean blocked(long now,boolean eligible,double px,double py,double pz){
        if(!eligible){reset();return false;}
        double dx=px-x,dy=py-y,dz=pz-z;
        if(since<0 || dx*dx+dy*dy+dz*dz>=0.25){since=now;x=px;y=py;z=pz;return false;}
        return now-since>=5000 && now-lastOof>=12000;
    }
    public void dispatched(long now){lastOof=now;reset();}
}
